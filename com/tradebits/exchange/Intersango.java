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
// backup plan: http://db.intersango.com:1337/a
public class Intersango extends AExchange{
    
    CURRENCY currencyEnum;
    ASocketHelper socket;
    ASocketFactory socketFactory;
    Log rawDepthDataLog;
    Log rawSocketMessagesLog;
    boolean socketIsConnected = false;
    boolean socketHasReceivedAnyMessage = false;
    Integer intersangoCurrencyEnum;
            
    /**
     * https://intersango.com/api.php
     * 
     * 1 = BTC:GBP
     * 2 = BTC:EUR
     * 3 = BTC:USD
     * 4 = BTC:PLN
     */
    public Intersango(ASocketFactory factory, CURRENCY curr){
        super("Intersango");
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
    }
    
    
    public String getName(){
        return super.getName() + " " + this.getCurrency();
    }
    
    public void disconnect(){
        if(this.socket != null) this.socket.disconnect();
        this.socket = null;
        socketIsConnected = false;
        socketHasReceivedAnyMessage = false;
    }
    
    public void connect(){
        try{
            this.log("Connecting...");
            if(!this.isConnected()){
                
                
                //
                // now, connect to the realtime feed
                
                this.socket = this.socketFactory.getSocketHelperFor("https://socketio.intersango.com:8080/socket.io/1/", "wss://socketio.intersango.com:8080/socket.io/1/websocket/");
                socket.setListener(new ISocketHelperListener(){
                    
                    public void onOpen(ASocketHelper socket){
                        Intersango.this.log("OPEN");
                        socketIsConnected = true;
                    }
                    
                    public void onClose(ASocketHelper socket, int closeCode, String message){
                        Intersango.this.log("CLOSE");
                        socketIsConnected = false;
                        // if this flag is still true,
                        // then MtGox disconnect() has
                        // not been called
                        Intersango.this.disconnect();
                    }
                    
                    public void onError(ASocketHelper socket, String message){
                        // noop
                    }
                    
                    public void onMessage(ASocketHelper aSocket, String data){
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
                    
                    public void onHeartbeatSent(ASocketHelper socket){
                        //
                        // update our depth data as often as we heartbeat
//                        Intersango.this.log("~h~");
                    }
                });
            }
            socket.connect();
        }catch(java.net.ConnectException e){
            this.log("Connection Refused. Waiting to reconnect.");
            try{
                Thread.sleep(5000);
            }catch(Exception e2){ }
            if(socket != null){
                socket.setListener(null);
            }
            socketIsConnected = false;
            this.disconnect();
        }catch(Exception e){
            e.printStackTrace();
            if(socket != null){
                socket.setListener(null);
            }
            socketIsConnected = false;
            this.disconnect();
        }
    }
    
    protected void processMessage(String messageText){
        try{
            JSONObject msg = new JSONObject(messageText);
            socketHasReceivedAnyMessage = true;
            if(msg.getString("name").equals("orderbook")){
                this.processDepthData(msg);
            }else if(msg.getString("name").equals("depth")){
                this.processDepthUpdate(msg);
            }else if(msg.getString("name").equals("tickers")){
                // ignore
            }else if(msg.getString("name").equals("trade")){
                // ignore
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
    
    
    /**
     * to be used for currencyEnum balances only
     */
    private double intToDouble(int number){
        return (double) number / Math.pow(10, 5);
    }
    
    //
    // returns an int, assuming 5 places
    // after the decimal
    private int doubleToInt(double number){
        return (int) (number * Math.pow(10, 5));
    }
    
    
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
        }else{
            // wrong currency
        }
    }
    
    protected void processDepthData(JSONObject depthMessage) throws JSONException, ExchangeException{
        synchronized(this){
            JSONObject orderBooks = depthMessage.getJSONArray("args").getJSONObject(0);
            JSONObject orderBook = orderBooks.getJSONObject(intersangoCurrencyEnum.toString());
            
            JSONObject bids = orderBook.getJSONObject("bids");
            JSONObject asks = orderBook.getJSONObject("asks");
            
            for(Iterator i = bids.keys(); i.hasNext();){
                String price = (String)i.next();
                JSONObject cachableData = new JSONObject();
                cachableData.put("price", new Double(price));
                cachableData.put("volume_int", this.doubleToInt(bids.getDouble(price)));
                cachableData.put("stamp",new Date());
                this.setBidData(cachableData);
            }
            
            for(Iterator i = asks.keys(); i.hasNext();){
                String price = (String)i.next();
                JSONObject cachableData = new JSONObject();
                cachableData.put("price", new Double(price));
                cachableData.put("volume_int", this.doubleToInt(asks.getDouble(price)));
                cachableData.put("stamp",new Date());
                this.setAskData(cachableData);
            }
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