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
public abstract class MtGoxBase extends AExchange implements MtGoxDepthLoader.Listener, 
    MtGoxCurrencyLoader.Listener, MtGoxProfileChannelLoader.Listener, MtGoxWalletLoader.Listener{
    
    // necessary connection properties
    private ISocketHelper socket;
    private MtGoxRESTClient restClient;
    private ASocketFactory socketFactory;
    // a boolean for if the user expects us to stay connected or not
    // if the user manually disconnects, then we shouldn't try to reconnect
    private boolean wasToldToConnect = false;
    
    // Loaders for MtGox profile / market data
    private MtGoxDepthLoader depthLoader;
    private MtGoxCurrencyLoader currencyLoader;
    private MtGoxWalletLoader walletLoader;
    private MtGoxProfileChannelLoader profileChannelLoader;
    
    // necessary config/state properties
    protected CURRENCY currencyEnum;
    protected JSONObject config;
    protected Log rawDepthDataLog;
    protected Log rawSocketMessagesLog;
    protected JSONObject walletJSON;
    
    /********************************************************************************************************
      * CURRENCY Properties
      * 
      * MtGox has tons of properties per currency,
      * so cache that info here
      */
    protected MtGoxCurrency cachedCurrencyData = null;
    
    
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
        return wasToldToConnect && 
            this.getAsk(0) != null && this.getBid(0) != null && 
            socket.isConnected() && 
            depthLoader.isConnected() &&
            currencyLoader.isConnected() &&
            profileChannelLoader.isConnected() &&
            walletLoader.isConnected();
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
        if(socket == null || socket.isConnected()){
            if(socket != null) socket.disconnect();
            socket = null;
            if(depthLoader != null) depthLoader.disconnect();
            depthLoader = null;
            if(currencyLoader != null) currencyLoader.disconnect();
            currencyLoader = null;
            if(profileChannelLoader != null) profileChannelLoader.disconnect();
            profileChannelLoader = null;
            if(walletLoader != null) walletLoader.disconnect();
            walletLoader = null;
            
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
                //
                // currency and wallet should load immediately
                currencyLoader = new MtGoxCurrencyLoader(this.getName(), this.getCurrency(), this, 30, 60*60);
                currencyLoader.connect();
                walletLoader = new MtGoxWalletLoader(this.getName(), this.getCurrency(), this, 30, 60*60);
                walletLoader.connect();
                //
                // depth and profile should wait until
                // the socket is connected and alive
                depthLoader = new MtGoxDepthLoader(this.getName(), this.getCurrency(), this, 30, 60*60);
                profileChannelLoader = new MtGoxProfileChannelLoader(this.getName(), this.getCurrency(), this, 30, 60*60);

                
                
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
                                MtGoxBase.this.log("Socket connection complete");
                                profileChannelLoader.connect();
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
                            aSocket = null;
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
    // SOCKET MESSAGES
    //
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
            socket = null;
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
    
    /////////////////////////////////////////////////////////////////////////////////////////////////////////
    /////////////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // LOADING WALLET
    //
    /////////////////////////////////////////////////////////////////////////////////////////////////////////
    /////////////////////////////////////////////////////////////////////////////////////////////////////////
    
    
    public void didLoadWalletData(JSONObject walletJSON){
        try{
            this.log("WALLET: " + walletJSON.toString().substring(0, Math.min(200, walletJSON.toString().length())));
            //
            // the USD balance is
            long walletBalanceEXD = walletJSON.getJSONObject("return").getJSONObject("Wallets")
                .getJSONObject(currencyEnum.toString()).getJSONObject("Balance").getLong("value_int");
            long walletBalanceBTC = walletJSON.getJSONObject("return").getJSONObject("Wallets")
                .getJSONObject("BTC").getJSONObject("Balance").getLong("value_int");
            double tradeFee = walletJSON.getJSONObject("return").getDouble("Trade_Fee");
            this.walletJSON = walletJSON;
            this.log("loaded balance of " + walletBalanceEXD + " " + currencyEnum + " and " + walletBalanceBTC + " BTC");
        }catch(Exception e){
            this.log("problem with wallet");
            this.log(e.toString());
            this.disconnect();
        }
    }
    
    /**
     * mtgox only has a trading fee if I
     * am buying bitcoins on the exchange
     */
    public double getTradingFeeFor(CurrencyTrade trade){
        try{
            if(trade.getFromExchange() == this && walletLoader.isConnected()){
                // I am buying bitcoins from
                // the exchange
                return walletJSON.getJSONObject("return").getDouble("Trade_Fee");
            }
        }catch(JSONException e){
            this.log("error with wallet");
            this.log(e.toString());
            this.disconnect();
        }catch(NullPointerException e){
            this.log(walletJSON == null ? "wallet is null" : walletJSON.toString());
        }
        return 0;
    }
    
    
    /////////////////////////////////////////////////////////////////////////////////////////////////////////
    /////////////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // CURRENCY
    //
    /////////////////////////////////////////////////////////////////////////////////////////////////////////
    /////////////////////////////////////////////////////////////////////////////////////////////////////////
    
    public void didLoadCurrencyData(MtGoxCurrency currencyData){
        this.log("done loading currency data: " + currencyData);
        cachedCurrencyData = currencyData;
        if(profileChannelLoader.isConnected()){
            depthLoader.connect();
        }
    }
    
    public void didUnloadCurrencyData(){
        cachedCurrencyData = null;
    }
    
    /////////////////////////////////////////////////////////////////////////////////////////////////////////
    /////////////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // PROFILE STREAM
    //
    /////////////////////////////////////////////////////////////////////////////////////////////////////////
    /////////////////////////////////////////////////////////////////////////////////////////////////////////
   
    public void didLoadProfileChannelData(String profileChannelID){
        try{
            JSONObject subscribeOp = new JSONObject();
            subscribeOp.put("op","mtgox.subscribe");
            subscribeOp.put("key", profileChannelID);
            
            if(socket != null){
                this.log("subscribing to personal channel...");
                socket.send("4::/mtgox:" + subscribeOp.toString());
            }else{
                throw new ExchangeException("socket not connected when trying to subscribe to personal channel");
            }
            if(currencyLoader.isConnected()){
                depthLoader.connect();
            }

        }catch(Exception e){
            this.disconnect();
        }
    }
    
    public MtGoxRESTClient getRESTClient(){
        return restClient;
    }
    
    /////////////////////////////////////////////////////////////////////////////////////////////////////////
    /////////////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // AExchange
    //
    /////////////////////////////////////////////////////////////////////////////////////////////////////////
    /////////////////////////////////////////////////////////////////////////////////////////////////////////
    
    
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
    
}