package com.tradebits.exchange;

import java.io.*;
import java.net.*;
import java.util.*;
import com.tradebits.*;
import com.tradebits.socket.*;
import com.kaazing.gateway.client.html5.WebSocket;
import com.kaazing.gateway.client.html5.WebSocketAdapter;
import com.kaazing.gateway.client.html5.WebSocketEvent;
import org.java_websocket.client.*;
import org.java_websocket.drafts.*;
import org.java_websocket.handshake.*;
import java.nio.ByteBuffer;
import org.json.*;


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
    TreeMap<CURRENCY, MtGoxCurrency> cachedCurrencyData = new TreeMap<CURRENCY, MtGoxCurrency>();
    // TODO https://mtgox.com/api/1/generic/currency?currency=USD&raw
    boolean hasLoadedDepthDataAtLeastOnce = false;
    boolean wasToldToConnect = false;
    
    
    public MtGox(ASocketFactory factory){
        super("MtGox");
        this.socketFactory = factory;
    }
    
    protected void resetAndReconnect(){
        if(!this.isConnected() && wasToldToConnect){
            this.log("RESET AND RECONNECT");
            this.disconnectHelper();
            this.connectHelper();
        }
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
        return socketIsConnected;
    }
    
    public void disconnect(){
        wasToldToConnect = false;
        this.disconnectHelper();
    }
    
    protected void disconnectHelper(){
        if(this.isConnected() || socket != null){
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
            cachedCurrencyData = new TreeMap<CURRENCY, MtGoxCurrency>();
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
            
            if(!this.isConnected() && wasToldToConnect){
                
                //
                // force loading currency information for USD
                // this will block until done
                MtGox.this.loadCurrencyDataFor(CURRENCY.USD);
                
                
                //
                // setup a timer to refresh the currency data
                currencyInformationTimer = new Timer();
                currencyInformationTimer.scheduleAtFixedRate(new TimerTask(){
                    public void run(){
                        MtGox.this.loadCurrencyDataFor(CURRENCY.USD);
                    }
                }, 1000*60*5, 1000*60*5);
                
                
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
                        MtGox.this.resetAndReconnect();
                    }
                    
                    public void onError(ASocketHelper socket, String message){
                        // noop
                    }
                    
                    String dataPrefix = "4::/mtgox:";
                    public void onMessage(ASocketHelper aSocket, String data){
                        try{
                            if(data.equals("1::")){
                                // ask to connect to the mtgox channel
                                socket.send("1::/mtgox");
                                return;
                            }else if(data.startsWith("1::")){
                                // just print to console, should be our
                                // confirmation of our /mtgox message above
                                MtGox.this.log(data);
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
                            MtGox.this.resetAndReconnect();
                        }
                    }
                    
                    public void onHeartbeatSent(ASocketHelper socket){
                        //
                        // update our depth data as often as we heartbeat
                        MtGox.this.log("~h~");
                    }
                });
            }
            socket.connect();
        }catch(Exception e){
            e.printStackTrace();
            if(socket != null){
                socket.setListener(null);
            }
            socketIsConnected = false;
            this.resetAndReconnect();
        }
    }
    
    
    protected void beginTimerForDepthData(){
        depthListingTimer = new Timer();
        depthListingTimer.scheduleAtFixedRate(new TimerTask(){
            public void run(){
                //
                // only allowed to initialize depth data
                // after we start receiving realtime data
                if(socketIsConnected && cachedDepthData.size() > 0 || hasLoadedDepthDataAtLeastOnce){
                    hasLoadedDepthDataAtLeastOnce = true;
                    MtGox.this.loadInitialDepthData(CURRENCY.USD);
                }
            }
        }, 15000, 15000);
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
                            }
                        }
                        
                    }catch (Exception e) {
                        e.printStackTrace();
                        try{
                            Thread.sleep(300);
                        }catch(InterruptedException e2){}
                    }
                }
                
                
                /**
                 * now that we've downloaded the depth data,
                 * it's time to process the asks/bids and store
                 * the results
                 */
                try{
                    
                    JSONArray asks = depthData.getJSONObject("return").getJSONArray("asks");
                    JSONArray bids = depthData.getJSONObject("return").getJSONArray("bids");
                    MtGox.this.log("got ask data " + asks.length());
                    MtGox.this.log("got bid data " + bids.length());
                    
                    synchronized(MtGox.this){
                        MtGox.this.log("-- Processing Depth Data");
                        for(int i=0;i<asks.length();i++){
                            JSONObject ask = asks.getJSONObject(i);
                            JSONObject cachedData = new JSONObject();
                            cachedData.put("price", ask.getDouble("price"));
                            cachedData.put("volume_int", ask.getDouble("amount_int"));
                            cachedData.put("stamp",new Date(ask.getLong("stamp") / 1000));
                            JSONObject formerlyCached = MtGox.this.getAskData(curr, ask.getDouble("price"));
                            if(!depthDataIsInitialized || formerlyCached == null){
                                MtGox.this.setAskData(curr, cachedData);
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
                                MtGox.this.setBidData(curr, cachedData);
                            }
                        }
                        MtGox.this.log("Done Processing Depth Data --");
                    }  
                }catch(Exception e){
                    e.printStackTrace();
                }
                
                synchronized(MtGox.this){
                    try{
                        MtGox.this.log("-- Replay Depth Stream");
                        depthDataIsInitialized = true;
                        while(cachedDepthData.size() > 0){
                            JSONObject obj = cachedDepthData.removeFirst();
                            MtGox.this.processDepthData(obj);
                        }
                        MtGox.this.log("Done Replaying Depth Data --"); 
                    }catch(Exception e){
                        e.printStackTrace();
                    }
                }
                
                
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
                        cachedCurrencyData.put(currency, currObj);
                    }
                }
            }catch(Exception e){
                e.printStackTrace();
            }
        }while(cachedCurrencyData.get(currency) == null);
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
            if(msg.getString("op").equals("private")){
                if(msg.getString("private").equals("ticker")){
//                    this.log("ticker data" + "\n" + messageText);
                }else if(msg.getString("private").equals("depth")){
//                    this.log("depth data" + "\n" + messageText.substring(0, Math.min(100, messageText.length())));
                    this.processDepthData(msg);
                }else if(msg.getString("private").equals("trade")){
//                    this.log("trade data" + "\n" + messageText);
                }else{
                    this.log("unknown feed type: " + msg.getString("private"));
                }
            }
        }catch(Exception e){
            socket.disconnect();
            socketIsConnected = false;
            this.resetAndReconnect();
            e.printStackTrace();
        }
    }
    
    protected void processDepthData(JSONObject depthMessage) throws JSONException, ExchangeException{
        synchronized(this){

            CURRENCY curr = CURRENCY.valueOf(depthMessage.getJSONObject("depth").getString("currency"));;
            String type = depthMessage.getJSONObject("depth").getString("type_str");
            JSONObject depthData = depthMessage.getJSONObject("depth");
            long totalVolInt = depthData.getLong("total_volume_int");
            
            JSONObject cachableData = new JSONObject();
            cachableData.put("price", depthData.getDouble("price"));
            cachableData.put("volume_int", totalVolInt);
            cachableData.put("stamp",new Date(depthData.getLong("now") / 1000));
            if(depthDataIsInitialized){
//                this.log("processing depth data" + "\n" + depthMessage);
//                this.log("expecting new vol for " + depthData.getDouble("price") + " to be " + totalVolInt);
                if(type.equals("ask")){
                    this.setAskData(curr, cachableData);
                }else if(type.equals("bid")){
                    this.setBidData(curr, cachableData);
                }else{
                    throw new RuntimeException("unknown depth type: " + type);
                }
            }else{
                cachedDepthData.add(depthMessage);
                this.log("caching " + type + " (" + cachedDepthData.size() + ")");
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
    public JSONObject getBid(CURRENCY curr, int index){
        JSONObject bid = super.getBid(curr, index);
        if(bid != null){
            try{
                // format int values as proper values
                Long volume = bid.getLong("volume_int");
                Long price = bid.getLong("price");
                MtGoxCurrency currency = cachedCurrencyData.get(bid.getString("currency"));
                // add the decimal 5 places from the right
                String volumeStr = volume.toString();
                String priceStr = price.toString();
            }catch(Exception e){
                return null;
            }
        }
        return bid;
    }
    
    public JSONObject getAsk(CURRENCY curr, int index){
        JSONObject ask = super.getAsk(curr, index);
        if(ask != null){
            // format int values as proper values
        }
        return null;
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
        
        public double parsePriceFromLong(Long price){
            return 0;
        }
        
        public double parsePriceFromVolume(Long volume){
            return 0;
        }
    }
}