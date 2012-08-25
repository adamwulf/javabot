package com.tradebits.exchange;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.TimeoutException;
import com.tradebits.*;
import com.tradebits.socket.*;
import org.json.*;
import com.tradebits.trade.*;
import java.net.HttpURLConnection;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import sun.misc.BASE64Decoder;
import sun.misc.BASE64Encoder;

/**
 * a class to connect to mtgox exchange
 */
public abstract class MtGoxBase extends AExchange {
    
    // necessary connection properties
    private ASocketHelper socket;
    private MtGoxRESTClient restClient;
    private LinkedList<JSONObject> cachedDepthData = new LinkedList<JSONObject>();
    private ASocketFactory socketFactory;
    // a boolean for if the user expects us to stay connected or not
    // if the user manually disconnects, then we shouldn't try to reconnect
    private boolean wasToldToConnect = false;
    
    // the mess that hopefully can be cleaned
    private Timer depthListingTimer;
    private Timer currencyInformationTimer;
    private boolean hasLoadedDepthDataAtLeastOnce = false;
    private Date lastRESTDepthCheck = null;
    
    // necessary config/state properties
    protected CURRENCY currencyEnum;
    protected JSONObject config;
    protected MtGoxCurrency cachedCurrencyData = null;
    protected Log rawDepthDataLog;
    protected Log rawSocketMessagesLog;
    
    
    public MtGoxBase(JSONObject config, ASocketFactory factory, CURRENCY curr) throws ExchangeException{
        super("MtGox");
        this.config = config;
        this.currencyEnum = curr;
        this.socketFactory = factory;
        try{
            rawDepthDataLog = new Log(this.getName() + " Depth");
            rawSocketMessagesLog = new Log(this.getName() + " Socket");
            String key = config.getString("key");
            String secret = config.getString("secret");
            restClient = new MtGoxRESTClient(key, secret, rawSocketMessagesLog);
        }
        catch(IOException e){ }
        catch(JSONException e){
            throw new ExchangeException(e);
        }
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
    
    protected void cacheDepthMessageForLaterReplay(JSONObject depthMessage) throws JSONException{
        String type = depthMessage.getJSONObject("depth").getString("type_str");
        cachedDepthData.add(depthMessage);
        this.log("caching " + type + " (" + cachedDepthData.size() + ")");
    }
    
    /**
     * returns true if the socket is
     * connected and active, false
     * otherwise
     * 
     * this confirms that:
     * 1. we were told to connect in the first place, and expect to me
     * 2. that we have depth data loaded
     * 3. that the socket is still connected
     */
    public boolean isConnected(){
        return wasToldToConnect && this.getAsk(0) != null && this.getBid(0) != null && socket.isConnected();
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
        if(socket == null ||  socket.isConnected()){
            if(socket != null) socket.disconnect();
            socket = null;
            hasLoadedDepthDataAtLeastOnce = false;
            cachedDepthData = new LinkedList<JSONObject>();
            if(depthListingTimer != null) depthListingTimer.cancel();
            if(currencyInformationTimer != null) currencyInformationTimer.cancel();
            depthListingTimer = null;
            currencyInformationTimer = null;
            cachedCurrencyData = null;
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
                MtGoxBase.this.loadCurrencyDataFor(this.currencyEnum);
                MtGoxBase.this.loadWalletData();
                
                
                //
                // setup a timer to refresh the currency data
                currencyInformationTimer = new Timer();
                currencyInformationTimer.scheduleAtFixedRate(new TimerTask(){
                    public void run(){
                        MtGoxBase.this.loadCurrencyDataFor(MtGoxBase.this.currencyEnum);
                    }
                }, 1000*60*60, 1000*60*60);
                
                
                //
                // now, connect to the realtime feed
                
                this.socket = this.socketFactory.getSocketHelperFor("https://socketio.mtgox.com/socket.io/1/",
                                                                    "wss://socketio.mtgox.com/socket.io/1/websocket/");
                socket.setListener(new ISocketHelperListener(){
                    
                    public void onOpen(ISocketHelper socket){
                        MtGoxBase.this.log("OPEN");
                        if(!wasToldToConnect){
                            MtGoxBase.this.disconnectHelper();
                        }
                    }
                    
                    public void onClose(ISocketHelper socket, int closeCode, String message){
                        MtGoxBase.this.log("CLOSE");
                        // if this flag is still true,
                        // then MtGox disconnect() has
                        // not been called
                        MtGoxBase.this.disconnect();
                    }
                    
                    public void onError(ISocketHelper socket, String message){
                        // noop
                    }
                    
                    String dataPrefix = "4::/mtgox:";
                    public void onMessage(ISocketHelper aSocket, String data){
                        try{
                            if(data.equals("1::")){
                                // ask to connect to the mtgox channel
                                String outgoing = "1::/mtgox";
                                MtGoxBase.this.log("sending: " + outgoing);
                                socket.send(outgoing);
                                return;
                            }else if(data.startsWith("1::")){
                                // just print to console, should be our
                                // confirmation of our /mtgox message above
                                //
                                // we are now confirmed connected to the socket,
                                // and are awaiting our first realtime message
                                MtGoxBase.this.didFinishConnectToSocketNowWaitingOnRealtimeData();
                                return;
                            }else if(data.equals("2::")){
                                // just heartbeat from the server
                                return;
                            }else if(data.startsWith(dataPrefix)){
                                data = data.substring(dataPrefix.length());
                                MtGoxBase.this.processMessage(data);
                                return;
                            }
                            MtGoxBase.this.log(data);
                        }catch(Exception e){
                            aSocket.disconnect();
                            MtGoxBase.this.disconnect();
                        }
                    }
                    
                    public void onHeartbeatSent(ISocketHelper socket){
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
            this.disconnect();
        }catch(TimeoutException e){
            //
            // happens w/ mtgox socket a lot
            if(socket != null){
                socket.setListener(null);
            }
            this.disconnect();
        }catch(Exception e){
            e.printStackTrace();
            if(socket != null){
                socket.setListener(null);
            }
            this.disconnect();
        }
    }
    
    
    /////////////////////////////////////////////////////////////////////////////////////////////////////////
    /////////////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // LOADING CURRENCY
    //
    /////////////////////////////////////////////////////////////////////////////////////////////////////////
    /////////////////////////////////////////////////////////////////////////////////////////////////////////
    

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
                        MtGoxBase.this.log("loaded currency information for " + currency);
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
    //
    // LOADING WALLET
    //
    /////////////////////////////////////////////////////////////////////////////////////////////////////////
    /////////////////////////////////////////////////////////////////////////////////////////////////////////
    

    protected void loadWalletData(){
        try{
            //
            // only send 1 trade
            String queryURL = "1/generic/private/info";
            HashMap<String, String> args = new HashMap<String, String>();
            String response = restClient.query(queryURL, args);
            if(response != null){
                JSONObject ret = new JSONObject(response);
                //
                // the USD balance is
                walletBalanceEXD = ret.getJSONObject("return").getJSONObject("Wallets")
                    .getJSONObject(currencyEnum.toString()).getJSONObject("Balance").getLong("value_int");
                walletBalanceBTC = ret.getJSONObject("return").getJSONObject("Wallets")
                    .getJSONObject("BTC").getJSONObject("Balance").getLong("value_int");
                
                hasLoadedWallet = true;
                this.log("loaded balance of " + walletBalanceEXD + " " + currencyEnum + " and " + walletBalanceBTC + " BTC");
            }
        }catch(Exception e){
            e.printStackTrace();
        }
    }
    
    
    
    /////////////////////////////////////////////////////////////////////////////////////////////////////////
    /////////////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // LOADING DEPTH
    //
    /////////////////////////////////////////////////////////////////////////////////////////////////////////
    /////////////////////////////////////////////////////////////////////////////////////////////////////////
    
    protected void beginTimerForDepthData(){
        depthListingTimer = new Timer();
        depthListingTimer.scheduleAtFixedRate(new TimerTask(){
            public void run(){
                //
                // only allowed to initialize depth data
                // after we start receiving realtime data
                if(MtGoxBase.this.isConnected() || !hasLoadedDepthDataAtLeastOnce){
                    Date now = new Date();
                    //
                    // only load once each hour - yikes!
                    // this is b/c mtgox has an extremely aggressive anti DDOS in place
                    if(lastRESTDepthCheck == null || now.after(new Date(lastRESTDepthCheck.getTime() + 1000*60*60))){
                        hasLoadedDepthDataAtLeastOnce = true;
                        lastRESTDepthCheck = now;
                        MtGoxBase.this.loadInitialDepthData(MtGoxBase.this.currencyEnum);
                    }
                }
            }
        }, 15000, 30000);
    }
    
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
                                MtGoxBase.this.log("ERROR LOADING DEPTH: " + depthString);
                                MtGoxBase.this.log("Sleeping and will try again later");
                                return;
                            }else{
                                MtGoxBase.this.log("UNKNOWN ERROR LOADING DEPTH: " + depthString);
                                MtGoxBase.this.log("Sleeping and will try again later");
                                return;
                            }
                        }
                        
                        MtGoxBase.this.log("-- Loading from " + url);
                    }catch (Exception e) {
                        //
                        // something went terribly wrong
                        // so log the error and mark our
                        // last depth load as never so that 
                        // the next timer cycle will try again in 30s
                        e.printStackTrace();
                        lastRESTDepthCheck = null;
                        MtGoxBase.this.log("Fetching depth failed");
                        return;
                    }
                }
                
                MtGoxBase.this.log("-- Processing Depth and Cache Data");
                
                /**
                 * now that we've downloaded the depth data,
                 * it's time to process the asks/bids and store
                 * the results
                 */
                try{
                    
                    JSONArray asks = depthData.getJSONObject("return").getJSONArray("asks");
                    JSONArray bids = depthData.getJSONObject("return").getJSONArray("bids");
                    
                    synchronized(MtGoxBase.this){
                        for(int i=0;i<asks.length();i++){
                            JSONObject ask = asks.getJSONObject(i);
                            JSONObject cachedData = new JSONObject();
                            cachedData.put("price", ask.getDouble("price"));
                            cachedData.put("volume_int", ask.getDouble("amount_int"));
                            cachedData.put("stamp",new Date(ask.getLong("stamp") / 1000));
                            JSONObject formerlyCached = MtGoxBase.this.getAskData(ask.getDouble("price"));
                            if(formerlyCached == null){
                                MtGoxBase.this.setAskData(cachedData);
                            }else{
                                JSONArray log = formerlyCached.getJSONArray("log");
                                JSONObject logItem = new JSONObject();
                                logItem.put("volume_int", cachedData.getDouble("volume_int"));
                                logItem.put("stamp", cachedData.get("stamp"));
                                logItem.put("check", true);
                                log.put(logItem);
                                formerlyCached.put("log", log);
                            }
                            /////////////////////////////////////////////////////////////////////////////////////////////
                            //
                            // Log to see if we're staying in sync with the depth or not
                            //
                            // this should probably show some errors sometimes, and if we're getting out of sync
                            // then the # of errors will continue to increase.
                            //
                            // right now, i'm only logging this, but not doing anything programatically to check
                            // and verify the error rate. TODO future improvement to check this
                            //
                            // this is strictly a sanity check that the MtGox API is working properly and sending all
                            // depth data back to me
                            //
                            // check to see if anything has changed or not
                            if(formerlyCached != null){
                                if(formerlyCached.getDouble("volume_int") != cachedData.getDouble("volume_int")){
                                    MtGoxBase.this.log("Different volume for " + cachedData.getDouble("price")
                                                           + ": " + formerlyCached.getDouble("volume_int")
                                                           + " vs " + cachedData.getDouble("volume_int") + " with log "
                                                           + formerlyCached.get("log"));
                                }
                            }
                            //
                            /////////////////////////////////////////////////////////////////////////////////////////////
                        }
                        
                        
                        
                        for(int i=0;i<bids.length();i++){
                            JSONObject bid = bids.getJSONObject(i);
                            JSONObject cachedData = new JSONObject();
                            cachedData.put("price", bid.getDouble("price"));
                            cachedData.put("volume_int", bid.getDouble("amount_int"));
                            cachedData.put("stamp",new Date(bid.getLong("stamp") / 1000));
                            JSONObject formerlyCached = MtGoxBase.this.getBidData(bid.getDouble("price"));
                            if(formerlyCached == null){
                                MtGoxBase.this.setBidData(cachedData);
                            }else{
                                JSONArray log = formerlyCached.getJSONArray("log");
                                JSONObject logItem = new JSONObject();
                                logItem.put("volume_int", cachedData.getDouble("volume_int"));
                                logItem.put("stamp", cachedData.get("stamp"));
                                logItem.put("check", true);
                                log.put(logItem);
                                formerlyCached.put("log", log);
                            }
                            /////////////////////////////////////////////////////////////////////////////////////////////
                            //
                            // Another check
                            //
                            if(formerlyCached != null){
                                if(formerlyCached.getDouble("volume_int") != cachedData.getDouble("volume_int")){
                                    MtGoxBase.this.log("Different volume for " + cachedData.getDouble("price")
                                                           + ": " + formerlyCached.getDouble("volume_int")
                                                           + " vs " + cachedData.getDouble("volume_int") + " with log "
                                                           + formerlyCached.get("log"));
                                }
                            }
                            //
                            /////////////////////////////////////////////////////////////////////////////////////////////
                        }
                    }  
                }catch(Exception e){
                    e.printStackTrace();
                }
                
                synchronized(MtGoxBase.this){
                    try{
                        while(cachedDepthData.size() > 0){
                            JSONObject obj = cachedDepthData.removeFirst();
                            MtGoxBase.this.processDepthData(obj);
                        }
                    }catch(Exception e){
                        e.printStackTrace();
                    }
                }
                
                MtGoxBase.this.log("Done Processing Depth and Cache Data --");
            }
        }).start();
    }
    
    
        
    /////////////////////////////////////////////////////////////////////////////////////////////////////////
    /////////////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // SOCKET MESSAGES
    //
    /////////////////////////////////////////////////////////////////////////////////////////////////////////
    /////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * we have finished the handshake with the socket,
     * and are now awaiting our first realtime data
     * 
     * we're going to start a timer for the depth data,
     * once it loads we should have some realtime data
     * ready
     */
    private void didFinishConnectToSocketNowWaitingOnRealtimeData(){
        MtGoxBase.this.log("Socket connection complete");
        MtGoxBase.this.beginTimerForDepthData();
    }
    
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
                this.disconnect();
            }else if(msg.getString("op").equals("subscribe")){
                // ignore
            }else if(msg.getString("op").equals("private")){
                // ok, we got a valid message from the socket,
                // so remeber that fact. we'll use this info
                // to decide when our depth data is accurate
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
            this.disconnect();
            e.printStackTrace();
        }
    }
    
    
    protected abstract void processDepthData(JSONObject depthMessage) throws JSONException, ExchangeException;
    
    protected abstract void processTradeData(JSONObject tradeMessage) throws JSONException, ExchangeException;
    
    
    
    
    
        
    /////////////////////////////////////////////////////////////////////////////////////////////////////////
    /////////////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // ORDER API
    //
    /////////////////////////////////////////////////////////////////////////////////////////////////////////
    /////////////////////////////////////////////////////////////////////////////////////////////////////////
    

    
    
    public void getCurrentOrderStatus(){
        try{
            JSONObject lowAsk = this.getAsk(0);
            System.out.println(lowAsk);
            //
            // only send 1 trade
            String queryURL = "1/generic/private/orders";
            HashMap<String, String> args = new HashMap<String, String>();
            
            String response = restClient.query(queryURL, args);
            if(response != null){
                JSONObject ret = new JSONObject(response);
                this.log("order status " + ret);
            }
        }catch(Exception e){
            e.printStackTrace();
        }
    }
    
    
    /**
     * Fees:
     * the fee will always keep the transaction exactly as
     * you entered it
     * 
     * so i said to buy .1 BTC at price X
     * 
     * so it buys .1 BTC and deducts X from my USD
     * then it charges me a fee from that .1 BTC i just bought
     * and i'm deducted .6% of .1 BTC
     * 
     * 
     * for a sale:
     * there is no fee for mtgox when selling
     * 
     */
    public void executeOrderToBuyBTC(double amount){
        try{
            
            JSONObject lowAsk = this.getAsk(0);
            System.out.println(lowAsk);
            //
            // only send 1 trade
            String queryURL = "1/BTC" + currencyEnum + "/private/order/add";
            HashMap<String, String> args = new HashMap<String, String>();
            args.put("type","bid");
            args.put("amount_int", (new Long((long)(amount * Math.pow(10, 8))).toString()));
            args.put("price_int", (new Long((long)((lowAsk.getDouble("price")) * Math.pow(10, 5))).toString()));
            
            String response = restClient.query(queryURL, args);
            if(response != null){
                JSONObject ret = new JSONObject(response);
                this.log("new order sent " + ret);
            }
        }catch(Exception e){
            e.printStackTrace();
        }
    }
    
    public void executeOrderToSellBTC(double amount){
        try{
            
            JSONObject highBid = this.getBid(0);
            System.out.println(highBid);
            //
            // only send 1 trade
            String queryURL = "1/BTC" + currencyEnum + "/private/order/add";
            HashMap<String, String> args = new HashMap<String, String>();
            args.put("type","ask");
            args.put("amount_int", (new Long((long)(amount * Math.pow(10, 8))).toString()));
            args.put("price_int", (new Long((long)((highBid.getDouble("price")) * Math.pow(10, 5))).toString()));
            
            String response = restClient.query(queryURL, args);
            if(response != null){
                JSONObject ret = new JSONObject(response);
                this.log("new order sent " + ret);
            }
        }catch(Exception e){
            e.printStackTrace();
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
    
    
    
    
    public class MtGoxRESTClient {
        protected Log logFile;
        protected String key;
        protected String secret;
        
        /**
         * @param args the command line arguments
         public static void main(String[] args) {
         MtGoxRESTClient client = new MtGoxRESTClient(
         "your key here",
         "your secret here"
         );
         HashMap<String, String> query_args = new HashMap<String, String>();
         query_args.put("currency", "BTC");
         query_args.put("amount", "5.0");
         query_args.put("return_success", "https://mtgox.com/success");
         query_args.put("return_failure", "https://mtgox.com/failure");
         
         client.query("1/generic/private/merchant/order/create", query_args);
         }
         */
        
        public MtGoxRESTClient(String key, String secret, Log logFile) {
            this.key = key;
            this.secret = secret;
            this.logFile = logFile;
        }
        
        public String query(String path, HashMap<String, String> args) {
            try {
                // add nonce and build arg list
                args.put("nonce", String.valueOf(System.currentTimeMillis()));
                String post_data = this.buildQueryString(args);
                
                // args signature
                Mac mac = Mac.getInstance("HmacSHA512");
                SecretKeySpec secret_spec = new SecretKeySpec((new BASE64Decoder()).decodeBuffer(this.secret), "HmacSHA512");
                mac.init(secret_spec);
                String signature = (new BASE64Encoder()).encode(mac.doFinal(post_data.getBytes()));
                
                
                // build URL
                URL queryUrl = new URL("https://mtgox.com/api/" + path);
                
                // create connection
                HttpURLConnection connection = (HttpURLConnection)queryUrl.openConnection();
                connection.setDoOutput(true);
                // set signature
                connection.setRequestProperty("User-Agent", "Mozilla/4.0 (compatible; Java Test client)");
                connection.setRequestProperty("Rest-Key", this.key);
                connection.setRequestProperty("Rest-Sign", signature.replaceAll("\n", ""));
                
                // write post
                connection.getOutputStream().write(post_data.getBytes());
                
                // read info
                byte buffer[] = new byte[16384];
                int len = connection.getInputStream().read(buffer, 0, 16384);
                return new String(buffer, 0, len, "UTF-8");
            } catch (Exception ex) {
                logFile.log(ex.toString());
            }
            return null;
        }
        
        protected String buildQueryString(HashMap<String, String> args) {
            String result = new String();
            for (String hashkey : args.keySet()) {
                if (result.length() > 0) result += '&';
                try {
                    result += URLEncoder.encode(hashkey, "UTF-8") + "="
                        + URLEncoder.encode(args.get(hashkey), "UTF-8");
                } catch (Exception ex) {
                    logFile.log(Arrays.toString(ex.getStackTrace()));
                }
            }
            return result;
        }
    }
}