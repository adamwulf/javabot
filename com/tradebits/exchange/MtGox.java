package com.tradebits.exchange;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.TimeoutException;
import com.tradebits.*;
import com.tradebits.socket.*;
import org.json.*;
import com.tradebits.trade.*;


/**
 * a class to connect to mtgox exchange
 */
public class MtGox extends AExchange {
    ASocketHelper socket;
    
    ASocketFactory socketFactory;
    private LinkedList<JSONObject> cachedDepthData = new LinkedList<JSONObject>();
    private boolean depthDataIsInitialized = false;
    boolean socketIsConnected = false;
    Timer depthListingTimer;
    Timer currencyInformationTimer;
    CURRENCY currencyEnum;
    MtGoxCurrency cachedCurrencyData = null;
    boolean hasLoadedDepthDataAtLeastOnce = false;
    boolean wasToldToConnect = false;
    boolean socketHasReceivedAnyMessage = false;
    Date lastRESTDepthCheck = null;
    
    Log rawDepthDataLog;
    Log rawSocketMessagesLog;
    
    
    public MtGox(ASocketFactory factory, CURRENCY curr){
        super("MtGox");
        this.currencyEnum = curr;
        this.socketFactory = factory;
        try{
            rawDepthDataLog = new NullLog(curr + " Depth");
            rawSocketMessagesLog = new NullLog(curr + " Socket");
        }catch(IOException e){ }
    }
    
    public String getName(){
        return super.getName() + " " + this.getCurrency();
    }

    public CURRENCY getCurrency(){
        return this.currencyEnum;
    }
    
    public int numberOfCachedDepthData(){
        return cachedDepthData.size();
    }
    
    /**
     * returns true if the socket is
     * connected and active, false
     * otherwise
     */
    public boolean isConnected(){
        return socketIsConnected && hasLoadedDepthDataAtLeastOnce;
    }
    
    public boolean isConnecting(){
        return !this.isOffline() && !this.isConnected();
    }
    
    public boolean isOffline(){
        return !wasToldToConnect;
    }
    
    public void disconnect(){
        wasToldToConnect = false;
        this.disconnectHelper();
    }
    
    protected void disconnectHelper(){
        if(socketIsConnected || socket != null){
            if(socket != null) socket.disconnect();
            socket = null;
            hasLoadedDepthDataAtLeastOnce = false;
            socketIsConnected = false;
            depthDataIsInitialized = false;
            cachedDepthData = new LinkedList<JSONObject>();
            if(depthListingTimer != null) depthListingTimer.cancel();
            if(currencyInformationTimer != null) currencyInformationTimer.cancel();
            depthListingTimer = null;
            currencyInformationTimer = null;
            cachedCurrencyData = null;
            socketHasReceivedAnyMessage = false;
            lastRESTDepthCheck = null;
            super.disconnect();
        }
    }
    
    /**
     * create the socket if it doesn't exist
     * and then begin to connect
     */
    public void connect(){
        wasToldToConnect = true;
        this.connectHelper();
    }
    
    
    protected void connectHelper(){
        try{
            this.log("Connecting...");
            if(!this.isConnected() && wasToldToConnect){
                
                //
                // force loading currency information for USD
                // this will block until done
                MtGox.this.loadCurrencyDataFor(this.currencyEnum);
                
                
                //
                // setup a timer to refresh the currency data
                currencyInformationTimer = new Timer();
                currencyInformationTimer.scheduleAtFixedRate(new TimerTask(){
                    public void run(){
                        MtGox.this.loadCurrencyDataFor(MtGox.this.currencyEnum);
                    }
                }, 1000*60*60, 1000*60*60);
                
                
                //
                // now, connect to the realtime feed
                
                this.socket = this.socketFactory.getSocketHelperFor("https://socketio.mtgox.com/socket.io/1/", "wss://socketio.mtgox.com/socket.io/1/websocket/");
                socket.setListener(new ISocketHelperListener(){
                    
                    public void onOpen(ASocketHelper socket){
                        MtGox.this.log("OPEN");
                        socketIsConnected = true;
                        if(!wasToldToConnect){
                            MtGox.this.disconnectHelper();
                        }
                    }
                    
                    public void onClose(ASocketHelper socket, int closeCode, String message){
                        MtGox.this.log("CLOSE");
                        socketIsConnected = false;
                        // if this flag is still true,
                        // then MtGox disconnect() has
                        // not been called
                        MtGox.this.disconnect();
                    }
                    
                    public void onError(ASocketHelper socket, String message){
                        // noop
                    }
                    
                    String dataPrefix = "4::/mtgox:";
                    public void onMessage(ASocketHelper aSocket, String data){
                        try{
                            if(data.equals("1::")){
                                // ask to connect to the mtgox channel
                                String outgoing = "1::/mtgox";
                                MtGox.this.log("sending: " + outgoing);
                                socket.send(outgoing);
                                return;
                            }else if(data.startsWith("1::")){
                                // just print to console, should be our
                                // confirmation of our /mtgox message above
                                MtGox.this.log("Confirmed subscription: " + data);
                                MtGox.this.beginTimerForDepthData();
                                return;
                            }else if(data.equals("2::")){
                                // just heartbeat from the server
                                return;
                            }else if(data.startsWith(dataPrefix)){
                                data = data.substring(dataPrefix.length());
                                MtGox.this.processMessage(data);
                                return;
                            }
                            MtGox.this.log(data);
                        }catch(Exception e){
                            aSocket.disconnect();
                            socketIsConnected = false;
                            MtGox.this.disconnect();
                        }
                    }
                    
                    public void onHeartbeatSent(ASocketHelper socket){
                        //
                        // update our depth data as often as we heartbeat
//                        MtGox.this.log("~h~");
                    }
                });
            }
            socket.connect();
        }catch(ConnectException e){
            //
            // can happen if the POST to handshake the
            // socket dies on connection
            if(socket != null){
                socket.setListener(null);
            }
            socketIsConnected = false;
            this.disconnect();
        }catch(TimeoutException e){
            //
            // happens w/ mtgox socket a lot
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
    
    
    protected void beginTimerForDepthData(){
        depthListingTimer = new Timer();
        depthListingTimer.scheduleAtFixedRate(new TimerTask(){
            public void run(){
                //
                // only allowed to initialize depth data
                // after we start receiving realtime data
                if(socketIsConnected || !hasLoadedDepthDataAtLeastOnce){
                    Date now = new Date();
                    //
                    // only load once each hour - yikes!
                    // this is b/c mtgox has an extremely aggressive anti DDOS in place
                    if(lastRESTDepthCheck == null || now.after(new Date(lastRESTDepthCheck.getTime() + 1000*60*60))){
                        hasLoadedDepthDataAtLeastOnce = true;
                        lastRESTDepthCheck = now;
                        MtGox.this.loadInitialDepthData(MtGox.this.currencyEnum);
                    }
                }
            }
        }, 15000, 30000);
    }
    
    
    /////////////////////////////////////////////////////////////////////////////////////////////////////////
    /////////////////////////////////////////////////////////////////////////////////////////////////////////
    
    // LOADING
    
    /////////////////////////////////////////////////////////////////////////////////////////////////////////
    /////////////////////////////////////////////////////////////////////////////////////////////////////////
    
    

    
    /**
     * This methos is responsible for loading
     * the initial depth data for the market.
     * 
     * after the socket is connected and we have
     * consistent depth data, this will continue to run
     * and validate our depth data
     */
    public void loadInitialDepthData(final CURRENCY curr){
        (new Thread(this.getName() + " First Depth Fetch"){
            public void run(){
                //
                // ok
                // we're going to download the depth data
                //
                // and only after that we're going to re-run the
                // realtime data on top of it
                //
                // track how many times we try to load
                JSONObject depthData = null;
                while(depthData == null){
                    try {
                        String depthString = "";
                        URLHelper urlHelper = socketFactory.getURLHelper();
                        // Send data
                        URL url = new URL("https://mtgox.com/api/1/BTC" + curr + "/depth");
                        depthString = urlHelper.getSynchronousURL(url);
                        
                        //
                        // ok, we have the string data,
                        // now parse it
                        if(depthString != null && depthString.length() > 0){
                            JSONObject parsedDepthData = new JSONObject(depthString);
                            if(parsedDepthData != null &&
                               parsedDepthData.getString("result").equals("success")){
                                depthData = parsedDepthData;
                                rawDepthDataLog.log(depthString);
                            }else if(parsedDepthData != null &&
                               parsedDepthData.getString("result").equals("error")){
                                MtGox.this.log("ERROR LOADING DEPTH: " + depthString);
                                MtGox.this.log("Sleeping and will try again later");
                                return;
                            }else{
                                MtGox.this.log("UNKNOWN ERROR LOADING DEPTH: " + depthString);
                                MtGox.this.log("Sleeping and will try again later");
                                return;
                            }
                        }
                        
                        MtGox.this.log("-- Loading from " + url);
                    }catch (Exception e) {
                        e.printStackTrace();
                        try{
                            Thread.sleep(300);
                        }catch(InterruptedException e2){}
                    }
                }
                
                MtGox.this.log("-- Processing Depth and Cache Data");

                /**
                 * now that we've downloaded the depth data,
                 * it's time to process the asks/bids and store
                 * the results
                 */
                try{
                    
                    JSONArray asks = depthData.getJSONObject("return").getJSONArray("asks");
                    JSONArray bids = depthData.getJSONObject("return").getJSONArray("bids");
                    
                    synchronized(MtGox.this){
                        for(int i=0;i<asks.length();i++){
                            JSONObject ask = asks.getJSONObject(i);
                            JSONObject cachedData = new JSONObject();
                            cachedData.put("price", ask.getDouble("price"));
                            cachedData.put("volume_int", ask.getDouble("amount_int"));
                            cachedData.put("stamp",new Date(ask.getLong("stamp") / 1000));
                            JSONObject formerlyCached = MtGox.this.getAskData(ask.getDouble("price"));
                            if(!depthDataIsInitialized || formerlyCached == null){
                                MtGox.this.setAskData(cachedData);
                            }else{
                                JSONArray log = formerlyCached.getJSONArray("log");
                                JSONObject logItem = new JSONObject();
                                logItem.put("volume_int", cachedData.getDouble("volume_int"));
                                logItem.put("stamp", cachedData.get("stamp"));
                                logItem.put("check", true);
                                log.put(logItem);
                                formerlyCached.put("log", log);
                            }
                            if(depthDataIsInitialized){
                                //
                                // check to see if anything has changed or not
                                if(formerlyCached != null){
                                    if(formerlyCached.getDouble("volume_int") != cachedData.getDouble("volume_int")){
                                        MtGox.this.log("Different volume for " + cachedData.getDouble("price")
                                                                 + ": " + formerlyCached.getDouble("volume_int")
                                                                 + " vs " + cachedData.getDouble("volume_int") + " with log "
                                                                 + formerlyCached.get("log"));
                                    }
                                }
                            }
                        }
                        if(!depthDataIsInitialized){
                            //
                            // for now, we're only going to compare
                            // values for anything after the first load
                            for(int i=0;i<bids.length();i++){
                                JSONObject bid = bids.getJSONObject(i);
                                JSONObject cachedData = new JSONObject();
                                cachedData.put("price", bid.getDouble("price"));
                                cachedData.put("volume_int", bid.getDouble("amount_int"));
                                cachedData.put("stamp",new Date(bid.getLong("stamp") / 1000));
                                MtGox.this.setBidData(cachedData);
                            }
                        }
                    }  
                }catch(Exception e){
                    e.printStackTrace();
                }
                
                synchronized(MtGox.this){
                    try{
                        depthDataIsInitialized = true;
                        while(cachedDepthData.size() > 0){
                            JSONObject obj = cachedDepthData.removeFirst();
                            MtGox.this.processDepthData(obj);
                        }
                    }catch(Exception e){
                        e.printStackTrace();
                    }
                }
                
                MtGox.this.log("Done Processing Depth and Cache Data --");
            }
        }).start();
    }
    
    private void loadCurrencyDataFor(CURRENCY currency){
        do{
            try{
                URLHelper urlHelper = socketFactory.getURLHelper();
                URL url = new URL("https://mtgox.com/api/1/generic/currency?currency=" + currency);
                String currencyJSON = urlHelper.getSynchronousURL(url);
                
                //
                // ok, we have the string data,
                // now parse it
                if(currencyJSON != null && currencyJSON.length() > 0){
                    JSONObject parsedCurrencyData = new JSONObject(currencyJSON);
                    if(parsedCurrencyData != null &&
                       parsedCurrencyData.getString("result").equals("success")){
                        parsedCurrencyData = parsedCurrencyData.getJSONObject("return");;
                        MtGoxCurrency currObj = new MtGoxCurrency(currency, parsedCurrencyData);
                        MtGox.this.log("loaded currency information for " + currency);
                        cachedCurrencyData = currObj;
                    }
                }
            }catch(Exception e){
                e.printStackTrace();
            }
        }while(cachedCurrencyData == null);
    }
    
    
    
    /////////////////////////////////////////////////////////////////////////////////////////////////////////
    /////////////////////////////////////////////////////////////////////////////////////////////////////////
    
    // PROCESSING
    
    /////////////////////////////////////////////////////////////////////////////////////////////////////////
    /////////////////////////////////////////////////////////////////////////////////////////////////////////
    
    
    /**
     * process a socket message
     * 
     * these will be either depth, tricker, or trade details
     */
    protected void processMessage(String messageText){
        try{
            JSONObject msg = new JSONObject(messageText);
            if(msg.getString("op").equals("error") &&
               msg.getString("suggest").equals("retry")){
                //
                // mtgox is having trouble, re-connect
                this.log("ERROR: command failed on socket - reconnecting: " + messageText);
                socketIsConnected = false;
                this.disconnect();
            }else if(msg.getString("op").equals("subscribe")){
                // ignore
            }else if(msg.getString("op").equals("private")){
                // ok, we got a valid message from the socket,
                // so remeber that fact. we'll use this info
                // to decide when our depth data is accurate
                socketHasReceivedAnyMessage = true;
                if(msg.getString("private").equals("ticker")){
//                    this.log("ticker data" + "\n" + messageText);
                }else if(msg.getString("private").equals("depth")){
//                    this.log("depth data" + "\n" + messageText.substring(0, Math.min(100, messageText.length())));
                    this.processDepthData(msg);
                }else if(msg.getString("private").equals("trade")){
                    this.processTradeData(msg);
                }else{
                    this.log("unknown feed type: " + msg.getString("private"));
                }
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
    
    protected void processTradeData(JSONObject tradeMessage) throws JSONException, ExchangeException{
        synchronized(this){
            CURRENCY curr = CURRENCY.valueOf(tradeMessage.getJSONObject("trade").getString("price_currency"));
            if(curr.equals(currencyEnum)){
                rawSocketMessagesLog.log(tradeMessage.toString());
            }else{
                // noop, wrong currency
            }
        }
    }
    
    protected void processDepthData(JSONObject depthMessage) throws JSONException, ExchangeException{
        synchronized(this){
            
            CURRENCY curr = CURRENCY.valueOf(depthMessage.getJSONObject("depth").getString("currency"));
            if(curr.equals(currencyEnum)){
                rawSocketMessagesLog.log(depthMessage.toString());
                String type = depthMessage.getJSONObject("depth").getString("type_str");
                JSONObject depthData = depthMessage.getJSONObject("depth");
                long totalVolInt = depthData.getLong("total_volume_int");
                
                JSONObject cachableData = new JSONObject();
                cachableData.put("price", depthData.getDouble("price"));
                cachableData.put("volume_int", totalVolInt);
                cachableData.put("stamp",new Date(depthData.getLong("now") / 1000));
                if(depthDataIsInitialized){
                    if(type.equals("ask")){
                        this.setAskData(cachableData);
                    }else if(type.equals("bid")){
                        this.setBidData(cachableData);
                    }else{
                        throw new RuntimeException("unknown depth type: " + type);
                    }
                }else{
                    cachedDepthData.add(depthMessage);
                    this.log("caching " + type + " (" + cachedDepthData.size() + ")");
                }
                rawSocketMessagesLog.log("bid: " + MtGox.this.getBid(0));
                rawSocketMessagesLog.log("ask: " + MtGox.this.getAsk(0));

            }else{
                // wrong currency!
//                this.log(" did NOT load depth message for " + curr);
            }
        }
    }
    
    
    
    
    /** AExchange **/
    
    public boolean isCurrencySupported(CURRENCY curr){
        return curr == CURRENCY.BTC ||
            curr == CURRENCY.USD ||
            curr == CURRENCY.AUD ||
            curr == CURRENCY.CAD ||
            curr == CURRENCY.CHF ||
            curr == CURRENCY.CNY ||
            curr == CURRENCY.DKK ||
            curr == CURRENCY.EUR ||
            curr == CURRENCY.GBP ||
            curr == CURRENCY.HKD ||
            curr == CURRENCY.JPY ||
            curr == CURRENCY.NZD ||
            curr == CURRENCY.PLN ||
            curr == CURRENCY.RUB ||
            curr == CURRENCY.SEK ||
            curr == CURRENCY.SGD ||
            curr == CURRENCY.THB;
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
                Long volumeL = bid.getLong("volume_int");
                double volume = cachedCurrencyData.parseVolumeFromLong(volumeL);
                
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
                Long volumeL = ask.getLong("volume_int");
                double volume = cachedCurrencyData.parseVolumeFromLong(volumeL);
                
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
    
    
    public double calculateBTCFeeRateForTransaction(Trade tr){
        return 0;
    }
    
    public double calculateEXDFeeRateForTransaction(Trade tr){
        return 0;
    }

    
    /**
     * currency info
     */
    protected class MtGoxCurrency{
        
        CURRENCY currency;
        JSONObject properties;
        
        public MtGoxCurrency(CURRENCY currency, JSONObject properties){
            this.properties = properties;
        }
        
        public CURRENCY getKey(){
            return currency;
        }
        
        public double parseVolumeFromLong(Long volume){
            // 10^8 comes from https://en.bitcoin.it/wiki/MtGox/API/HTTP/v1#Multi_currency_trades
            return volume / Math.pow(10, 8);
        }
    }
}