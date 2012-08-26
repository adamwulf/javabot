package com.tradebits.exchange;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.TimeoutException;
import com.tradebits.*;
import com.tradebits.socket.*;
import com.tradebits.exchange.mtgox.*;
import org.json.*;
import com.tradebits.trade.*;
import java.net.HttpURLConnection;

/**
 * a class to connect to mtgox exchange
 */
public abstract class MtGoxBase extends AExchange implements MtGoxDepthLoader.Listener{
    
    // necessary connection properties
    private ISocketHelper socket;
    private MtGoxRESTClient restClient;
    private ASocketFactory socketFactory;
    // a boolean for if the user expects us to stay connected or not
    // if the user manually disconnects, then we shouldn't try to reconnect
    private boolean wasToldToConnect = false;
    
    private MtGoxDepthLoader depthLoader;

    // settings for the personal feed
    private Timer personalFeedListingTimer;
    private Date lastRESTPersonalFeedCheck;
    private boolean hasLoadedPersonalFeedDataAtLeastOnce;

        private Timer currencyInformationTimer;

    // necessary config/state properties
    protected CURRENCY currencyEnum;
    protected JSONObject config;
    protected MtGoxCurrency cachedCurrencyData = null;
    protected Log rawDepthDataLog;
    protected Log rawSocketMessagesLog;
    protected double tradeFee;
    
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
            restClient = factory.getMtGoxRESTClient(key, secret, rawSocketMessagesLog);
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
    
    public ASocketFactory getSocketFactory(){
        return socketFactory;
    }
    
    public Log getRawDepthDataLog(){
        return rawDepthDataLog;
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
        return wasToldToConnect && this.getAsk(0) != null && this.getBid(0) != null && socket.isConnected() && depthLoader.isConnected();
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
            if(depthLoader != null) depthLoader.disconnect();
            depthLoader = null;
            if(currencyInformationTimer != null) currencyInformationTimer.cancel();
            if(personalFeedListingTimer != null) personalFeedListingTimer.cancel();
            currencyInformationTimer = null;
            personalFeedListingTimer = null;
            cachedCurrencyData = null;
            lastRESTPersonalFeedCheck = null;
            hasLoadedPersonalFeedDataAtLeastOnce = false;
            super.disconnect();
            this.notifyDidChangeConnectionState();
        }
    }
    
    /**
     * create the socket if it doesn't exist
     * and then begin to connect
     */
    public void connect(){
        wasToldToConnect = true;
        this.connectHelper();
        this.notifyDidChangeConnectionState();
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
                depthLoader = new MtGoxDepthLoader(this.getName(), this.getCurrency(), this);
                
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
                        }else{
                            MtGoxBase.this.notifyDidChangeConnectionState();
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
                                MtGoxBase.this.didFinishConnectToSocketNowWaitingOnPersonalFeed();
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
            
            System.out.println("wallet");
            System.out.println(response);
            
            if(response != null){
                JSONObject ret = new JSONObject(response);
                //
                // the USD balance is
                walletBalanceEXD = ret.getJSONObject("return").getJSONObject("Wallets")
                    .getJSONObject(currencyEnum.toString()).getJSONObject("Balance").getLong("value_int");
                walletBalanceBTC = ret.getJSONObject("return").getJSONObject("Wallets")
                    .getJSONObject("BTC").getJSONObject("Balance").getLong("value_int");
                tradeFee = ret.getJSONObject("return").getDouble("Trade_Fee");
                
                hasLoadedWallet = true;
                this.log("loaded balance of " + walletBalanceEXD + " " + currencyEnum + " and " + walletBalanceBTC + " BTC");
            }
        }catch(Exception e){
            e.printStackTrace();
        }
    }
    
    
    
    protected void beginTimerForPersonalFeedData(){
        this.log("beginning personal feed");
        personalFeedListingTimer = new Timer();
        personalFeedListingTimer.scheduleAtFixedRate(new TimerTask(){
            public void run(){
                //
                // only allowed to initialize depth data
                // after we start receiving realtime data
                if(MtGoxBase.this.isConnected() || !hasLoadedPersonalFeedDataAtLeastOnce){
                    Date now = new Date();
                    //
                    // only load once each hour - yikes!
                    // this is b/c mtgox has an extremely aggressive anti DDOS in place
                    if(lastRESTPersonalFeedCheck == null || now.after(new Date(lastRESTPersonalFeedCheck.getTime() + 1000*60*60))){
                        hasLoadedPersonalFeedDataAtLeastOnce = true;
                        lastRESTPersonalFeedCheck = now;
                        MtGoxBase.this.log("requesting personal feed");
                        MtGoxBase.this.subscribeToPersonalFeed();
                    }
                }
            }
        }, 0, 30000);
    }
    /**
     * get our websocket connected to our private API feed
     */
    protected void subscribeToPersonalFeed(){
        try{
            String queryURL = "1/generic/private/idkey";
            HashMap<String, String> args = new HashMap<String, String>();
            String response = restClient.query(queryURL, args);
            this.log("personal feed response: " + response);
            if(response != null){
                JSONObject ret = new JSONObject(response);
                this.log("personal feed info " + ret);
                String feedID = ret.getString("return");
                
                JSONObject subscribeOp = new JSONObject();
                subscribeOp.put("op","mtgox.subscribe");
                subscribeOp.put("key", feedID);
                
                if(socket != null){
                    this.log("subscribing..." + ret);
                    socket.send("4::/mtgox:" + subscribeOp.toString());
                }
                
                this.didFinishConnectToPersonalFeedNowWaitingOnRealtimeData();
            }
        }catch(Exception e){
            // noop
            this.log(e.toString());
        }
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
    private void didFinishConnectToSocketNowWaitingOnPersonalFeed(){
        MtGoxBase.this.log("Socket connection complete");
        MtGoxBase.this.beginTimerForPersonalFeedData();
    }
    /**
     * we have finished the handshake with the socket,
     * and are now awaiting our first realtime data
     * 
     * we're going to start a timer for the depth data,
     * once it loads we should have some realtime data
     * ready
     */
    private void didFinishConnectToPersonalFeedNowWaitingOnRealtimeData(){
        MtGoxBase.this.log("Socket connection complete");
        depthLoader.connect();
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
                this.log(msg.toString());
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
                    this.log(msg.toString());
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
    
    
    
    
    
}