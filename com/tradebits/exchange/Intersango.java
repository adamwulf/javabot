package com.tradebits.exchange;


import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.TimeoutException;
import com.tradebits.*;
import com.tradebits.socket.*;
import org.json.*;
import com.tradebits.trade.*;


//
// backup plan: http://db.intersango.com:1337/api.php
//
// Intersango connects to the above socket (db.intersango.com:1337)
// to stream the data. when the socket opens, its first message
// is the full orderbook. every message after that is an update
// to the book
public class Intersango extends AExchange{
    
    String configHost;
    int configPort;
    CURRENCY currencyEnum;
    ISocketHelper socket;
    ASocketFactory socketFactory;
    Log rawDepthDataLog;
    Log rawSocketMessagesLog;
    boolean socketIsConnected = false;
    boolean socketHasReceivedAnyMessage = false;
    Integer intersangoCurrencyEnum;
    boolean didInitializeDepthData = false;
    Timer depthCheckTimer;
    
    /**
     * https://intersango.com/api.php
     * 
     * 1 = BTC:GBP
     * 2 = BTC:EUR
     * 3 = BTC:USD
     * 4 = BTC:PLN
     */
    public Intersango(JSONObject config, ASocketFactory factory, CURRENCY curr) throws ExchangeException{
        super("Intersango");
        try{
            this.configHost = config.getString("host");
            this.configPort = config.getInt("port");
            this.currencyEnum = curr;
            this.socketFactory = factory;
            if(curr.equals(CURRENCY.GBP)){
                intersangoCurrencyEnum = 1;
            }else if(curr.equals(CURRENCY.EUR)){
                intersangoCurrencyEnum = 2;
            }else if(curr.equals(CURRENCY.USD)){
                intersangoCurrencyEnum = 3;
            }else if(curr.equals(CURRENCY.PLN)){
                intersangoCurrencyEnum = 4;
            }
            try{
                rawDepthDataLog = new Log(this.getName() + " Depth");
                rawSocketMessagesLog = new Log(this.getName() + " Socket");
            }catch(IOException e){ }
        }catch(JSONException e){
            throw new ExchangeException(e);
        }
    }
    
    
    public String getName(){
        return super.getName() + " " + this.getCurrency();
    }
    
    public void disconnect(){
        if(this.socket != null) this.socket.disconnect();
        this.socket = null;
        socketIsConnected = false;
        socketHasReceivedAnyMessage = false;
        didInitializeDepthData = false;
        this.notifyDidChangeConnectionState();
    }
    
    /**
     * this will connect to the host/port and begin
     * streaming down data. the first message will be
     * the entire book, then updates after that.
     * 
     * the RawSocket will mimic a websocket for us
     */
    public void connect(){
        try{
            this.log("Connecting to: " + configHost + ":" + configPort + "...");
            if(!this.isConnected()){
                
                socket = socketFactory.getRawSocketTo(configHost, configPort, rawSocketMessagesLog);
                socket.setListener(new ISocketHelperListener(){
                    
                    //
                    // open is pretty boring for intersango.
                    // we don't get data until a message happens
                    // soon after open
                    public void onOpen(ISocketHelper socket){
                        Intersango.this.log("OPEN");
                        socketIsConnected = true;
                        Intersango.this.notifyDidChangeConnectionState();
                    }
                    
                    // our socket connection to intersango died
                    public void onClose(ISocketHelper socket, int closeCode, String message){
                        Intersango.this.log("CLOSE");
                        socketIsConnected = false;
                        // if this flag is still true,
                        // then Intersango disconnect() has
                        // not been called
                        Intersango.this.disconnect();
                    }
                    
                    // something very unexpected happened...
                    public void onError(ISocketHelper socket, String message){
                        // noop
                        Intersango.this.log("ERROR: " + message);
                        Intersango.this.disconnect();
                    }
                    
                    //
                    // these messages are faked from the raw socket
                    // backing the intersango api.
                    //
                    // most will be the data prefix messages for either
                    // the entire depth book or an incremental update
                    public void onMessage(ISocketHelper aSocket, String data){
                        try{
                            String dataPrefix = "5:::";
                            if(data.startsWith("1::")){
                                // socket is saying hello
                                return;
                            }else if(data.equals("2::")){
                                // just heartbeat from the server
                                return;
                            }else if(data.startsWith(dataPrefix)){
                                data = data.substring(dataPrefix.length());
                                Intersango.this.processMessage(data);
                                return;
                            }
                            Intersango.this.log(data);
                        }catch(Exception e){
                            aSocket.disconnect();
                            socketIsConnected = false;
                            Intersango.this.disconnect();
                        }
                    }
                    
                    public void onHeartbeatSent(ISocketHelper socket){
                        //
                        // update our depth data as often as we heartbeat
//                        Intersango.this.log("~h~");
                    }
                });
            }
            socket.connect();
            this.notifyDidChangeConnectionState();
  
        }catch(Exception e){
            e.printStackTrace();
            if(socket != null){
                socket.setListener(null);
            }
            socketIsConnected = false;
            this.disconnect();
        }
    }
    
    /**
     * this funciton creates a 2nd socket to Intersango
     * for the sole purpose of confirming the book data
     * that we have on file.
     * 
     * the goal is to download the entire book again,
     * and compare it to what we've been incrementally building
     * over the past hours.
     * 
     * once we've confirmed the book is correct, immediately close
     * this 2nd socket
     */
    protected void checkDepthBook(){
        final ISocketHelper checkSocket = socketFactory.getRawSocketTo(configHost, configPort, rawSocketMessagesLog);
        try{
            checkSocket.setListener(new ISocketHelperListener(){
                // tell everyone that we've begun to check our orderbook
                public void onOpen(ISocketHelper socket){
                    // Intersango.this.log("CHECK OPEN");
                }
                // ok, we're done with the orderbook validation
                public void onClose(ISocketHelper socket, int closeCode, String message){
                    // Intersango.this.log("CHECK CLOSE");
                }
                // something unexpected happen, log it
                public void onError(ISocketHelper socket, String message){
                    Intersango.this.log("CHECK ERROR: " + message);
                    checkSocket.disconnect();
                }
                //
                // since we're only checking teh orderbook and we
                // don't care about updates, we should immediately
                // disconnect this socket after the first message
                public void onMessage(ISocketHelper aSocket, String data){
                    try{
                        String dataPrefix = "5:::";
                        if(data.startsWith(dataPrefix)){
                            data = data.substring(dataPrefix.length());
                            Intersango.this.processMessage(data);
                            //
                            // ok, we checked the orderbook, now we
                            // should immediately disconnect this 2nd
                            // socket
                            checkSocket.disconnect();
                            return;
                        }
                    }catch(Exception e){
                        checkSocket.disconnect();
                    }
                }
                
                public void onHeartbeatSent(ISocketHelper socket){
                    // noop
                }
            });
            checkSocket.connect();
            
            
        }catch(Exception e){
            e.printStackTrace();
            if(checkSocket != null){
                checkSocket.setListener(null);
            }
            checkSocket.disconnect();
        }
    }
    
    
    /**
     * this will process any input message from intersango
     * and update our backing store appropriately.
     */
    protected void processMessage(String messageText){
        try{
            JSONObject msg = new JSONObject(messageText);
            socketHasReceivedAnyMessage = true;
            if(msg.getString("name").equals("orderbook")){
                rawDepthDataLog.log(messageText);
            }
            if(msg.getString("name").equals("orderbook")){
                this.processOrderBookData(msg);
            }else if(msg.getString("name").equals("depth")){
                this.processDepthUpdate(msg);
//            }else if(msg.getString("name").equals("tickers")){
//                // ignore
            }else if(msg.getString("name").equals("trade")){
//                // ignore
            }else if(msg.getString("name").equals("ping")){
                // heartbeat
                this.notifyDidReceiveHeartbeat();
            }else{
                this.log("UNKNOWN MESSAGE: " + messageText);
            }
        }catch(Exception e){
            socket.disconnect();
            socketIsConnected = false;
            this.disconnect();
            e.printStackTrace();
        }
    }
    
    public boolean isConnected(){
        return socketIsConnected && socketHasReceivedAnyMessage;
    }
    
    public boolean isOffline(){
        return !socketIsConnected;
    }
    
    public boolean isConnecting(){
        return socketIsConnected && !socketHasReceivedAnyMessage;
    }
    
    public CURRENCY getCurrency(){
        return currencyEnum;
    }
    
    public double getTradingFeeFor(CurrencyTrade trade){
        return 0;
    }

    
    /**
     * to be used for currencyEnum balances only
     */
    private double intToDouble(long number){
        return (double) number / Math.pow(10, 5);
    }
    
    //
    // returns an int, assuming 5 places
    // after the decimal
    //
    // TODO
    // should be (long)(Math.round(0.30892 * Math.pow(10, 5)))
    //
    // test case:
    //> 0.30892 * Math.pow(10, 5)
    //30891.999999999996
    //> (long)(0.30892 * Math.pow(10, 5))
    //30891
    //> (long)(Math.round(0.30892 * Math.pow(10, 5)))
    //30892
    private long doubleToInt(double number){
        return (long) (Math.round(number * Math.pow(10, 5)));
    }
    
    /**
     * this function will process any changes to our depth data backing store.
     * 
     * if we've received an update from the socket, then this method
     * will update our store to reflect the new input data.
     */
    protected void processDepthUpdate(JSONObject depthMessage) throws JSONException, ExchangeException{
        JSONObject depthData = depthMessage.getJSONArray("args").getJSONObject(0);
        if(intersangoCurrencyEnum.intValue() == depthData.getInt("currency_pair_id")){
            JSONObject cachableData = new JSONObject();
            cachableData.put("price", depthData.getDouble("rate"));
            cachableData.put("volume_int", this.doubleToInt(depthData.getDouble("amount")));
            cachableData.put("stamp",new Date());
            
            if(depthData.get("type").equals("bids")){
                this.updateBidData(cachableData);
            }else if(depthData.get("type").equals("asks")){
                this.updateAskData(cachableData);
            }
            this.notifyDidProcessDepth();
            rawSocketMessagesLog.log(depthMessage.toString());
        }else{
            // wrong currency
        }
    }
    
    /**
     * this will entirely overwrite the depth data for the input data.
     * this is not an update method, this is an initialization method
     * for our data store.
     */
    protected void processOrderBookData(JSONObject depthMessage) throws JSONException, ExchangeException{
        synchronized(this){
            JSONObject orderBooks = depthMessage.getJSONArray("args").getJSONObject(0);
            JSONObject orderBook = orderBooks.getJSONObject(intersangoCurrencyEnum.toString());
            
            JSONObject bids = orderBook.getJSONObject("bids");
            JSONObject asks = orderBook.getJSONObject("asks");
            
            //
            // first, process all bids
            for(Iterator i = bids.keys(); i.hasNext();){
                String price = (String)i.next();
                JSONObject cachableData = new JSONObject();
                cachableData.put("price", new Double(price));
                cachableData.put("volume_int", this.doubleToInt(bids.getDouble(price)));
                cachableData.put("stamp",new Date());
                if(!didInitializeDepthData){
                    //
                    // have never initialized, so just
                    // blindly set the data
                    this.setBidData(cachableData);
                }else{
                    //
                    // check the depth data is correct, but don't
                    // update anything
                    JSONObject data = this.getBidData(new Double(price));
                    if(data == null){
                        System.out.println("ERROR IN CHECK FOR BID " + price + ". no cached data.");
                    }else if(!new Long(this.doubleToInt(bids.getDouble(price))).equals(data.getLong("volume_int"))){
                        System.out.println("ERROR IN CHECK FOR BID " + price + ". is " + data.getLong("volume_int") + 
                                           " and should be " + this.doubleToInt(bids.getDouble(price)));
                        System.out.println(cachableData);
                        System.out.println("vs");
                        System.out.println(data);
                    }
                }
            }
            
            //
            // ok, bid data is done, now we need to
            // set ask data
            for(Iterator i = asks.keys(); i.hasNext();){
                String price = (String)i.next();
                JSONObject cachableData = new JSONObject();
                cachableData.put("price", new Double(price));
                cachableData.put("volume_int", this.doubleToInt(asks.getDouble(price)));
                cachableData.put("stamp",new Date());
                if(!didInitializeDepthData){
                    // we've never intiailized, so just set
                    // the data
                    this.setAskData(cachableData);
                }else{
                    //
                    // check the depth data is correct,
                    // but don't overwrite anything
                    JSONObject data = this.getAskData(new Double(price));
                    if(data == null){
                        System.out.println("ERROR IN CHECK FOR ASK " + price + ". no cached data.");
                    }else if(!new Long(this.doubleToInt(asks.getDouble(price))).equals(data.getLong("volume_int"))){
                        System.out.println("ERROR IN CHECK FOR ASK " + price + ". is " + data.getLong("volume_int") + 
                                           " and should be " + this.doubleToInt(asks.getDouble(price)));
                        System.out.println(cachableData);
                        System.out.println("vs");
                        System.out.println(data);
                    }
                }
            }
            if(!didInitializeDepthData){
                // if this is my first time processing
                // the orderbook, then set up a recurring
                // timer that will periodically re-check the
                // orderbook with a new raw connection
                depthCheckTimer = new Timer();
                depthCheckTimer.scheduleAtFixedRate(new TimerTask(){
                    public void run(){
                        Intersango.this.checkDepthBook();
                    }
                }, 1000*60, 1000*60);
            }
            didInitializeDepthData = true;
            this.notifyDidInitializeDepth();
        }
    }
    
    
    /**
     * https://intersango.com/api.php
     * 
     * 1 = BTC:GBP
     * 2 = BTC:EUR
     * 3 = BTC:USD
     * 4 = BTC:PLN
     */
    public boolean isCurrencySupported(CURRENCY curr){
        return curr == CURRENCY.BTC || 
            curr == CURRENCY.EUR ||
            curr == CURRENCY.GBP ||
            curr == CURRENCY.USD ||
            curr == CURRENCY.PLN;
    }
    
    
    
    /**
     * a zero index is closest to the trade window
     * and increases as prices move away.
     * 
     * so an index 0 is the highest bid or
     * lowest ask
     */
    public JSONObject getBid(int index){
        JSONObject bid = super.getBid(index);
        if(bid != null){
            try{
                // format int values as proper values
                double price = bid.getDouble("price");
                int volumeL = bid.getInt("volume_int");
                double volume = this.intToDouble(volumeL);
                
                JSONObject ret = new JSONObject();
                ret.put("price", price);
                ret.put("volume", volume);
                ret.put("currency", currencyEnum);
                ret.put("stamp", bid.get("stamp"));
                return ret;
            }catch(Exception e){
                return null;
            }
        }
        return null;
    }
    
    public JSONObject getAsk(int index){
        JSONObject ask = super.getAsk(index);
        if(ask != null){
            try{
                // format int values as proper values
                double price = ask.getDouble("price");
                int volumeL = ask.getInt("volume_int");
                double volume = this.intToDouble(volumeL);
                
                JSONObject ret = new JSONObject();
                ret.put("price", price);
                ret.put("volume", volume);
                ret.put("currency", currencyEnum);
                ret.put("stamp", ask.get("stamp"));
                return ret;
            }catch(Exception e){
                return null;
            }
        }
        return null;
    }
}