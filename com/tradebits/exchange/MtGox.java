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
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import sun.misc.BASE64Decoder;
import sun.misc.BASE64Encoder;

/**
 * a class to connect to mtgox exchange
 */
public class MtGox extends MtGoxBase{
    
    /********************************************************************************************************
      * DEPTH Properties
      * 
      * store the realtime depth updates
      * until we've loaded the entire depth data first
      */
    private LinkedList<JSONObject> cachedDepthData = new LinkedList<JSONObject>();
    /////////////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // Constructor
    //
    public MtGox(JSONObject config, ASocketFactory factory, CURRENCY curr) throws ExchangeException{
        super(config, factory, curr);
    }
    
    
    
    /////////////////////////////////////////////////////////////////////////////////////////////////////////
    /////////////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // DEPTH
    //
    /////////////////////////////////////////////////////////////////////////////////////////////////////////
    /////////////////////////////////////////////////////////////////////////////////////////////////////////
    
    public int numberOfCachedDepthData(){
        return cachedDepthData.size();
    }
    
    protected void processDepthData(JSONObject depthMessage) throws JSONException, ExchangeException{
        synchronized(this){
            CURRENCY curr = CURRENCY.valueOf(depthMessage.getJSONObject("depth").getString("currency"));
            if(curr.equals(currencyEnum)){
                if(this.isConnected()){
                    String type = depthMessage.getJSONObject("depth").getString("type_str");
                    rawSocketMessagesLog.log(depthMessage.toString());
                    JSONObject depthData = depthMessage.getJSONObject("depth");
                    long totalVolInt = depthData.getLong("total_volume_int");
                    
                    JSONObject cachableData = new JSONObject();
                    cachableData.put("price", depthData.getDouble("price"));
                    cachableData.put("volume_int", totalVolInt);
                    cachableData.put("stamp",new Date(depthData.getLong("now") / 1000));
                    if(type.equals("ask")){
                        this.setAskData(cachableData);
                    }else if(type.equals("bid")){
                        this.setBidData(cachableData);
                    }else{
                        throw new RuntimeException("unknown depth type: " + type);
                    }
                    this.notifyDidProcessDepth();
                }else{
                    this.cacheDepthMessageForLaterReplay(depthMessage);
                }
                rawSocketMessagesLog.log("bid: " + MtGox.this.getBid(0));
                rawSocketMessagesLog.log("ask: " + MtGox.this.getAsk(0));
                
            }else{
                // wrong currency!
//                this.log(" did NOT load depth message for " + curr);
            }
        }
    }
    
    
    
    protected void cacheDepthMessageForLaterReplay(JSONObject depthMessage) throws JSONException{
        String type = depthMessage.getJSONObject("depth").getString("type_str");
        cachedDepthData.add(depthMessage);
        this.log("caching " + type + " (" + cachedDepthData.size() + ")");
    }
    
    
    public void didLoadFullMarketDepthData(JSONObject depthData){
        MtGox.this.log("-- Processing Depth and Cache Data");
        rawDepthDataLog.log(depthData.toString());
        
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
                    if(formerlyCached == null){
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
                            MtGox.this.log("Different volume for " + cachedData.getDouble("price")
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
                    JSONObject formerlyCached = MtGox.this.getBidData(bid.getDouble("price"));
                    if(formerlyCached == null){
                        MtGox.this.setBidData(cachedData);
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
                            MtGox.this.log("Different volume for " + cachedData.getDouble("price")
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
        
        MtGox.this.notifyDidInitializeDepth();
        
        synchronized(MtGox.this){
            try{
                while(cachedDepthData.size() > 0){
                    JSONObject obj = cachedDepthData.removeFirst();
                    MtGox.this.processDepthData(obj);
                }
            }catch(Exception e){
                e.printStackTrace();
            }
        }
        
        MtGox.this.log("Done Processing Depth and Cache Data --");
        MtGox.this.notifyDidChangeConnectionState();
    }
    
    
    
    /////////////////////////////////////////////////////////////////////////////////////////////////////////
    /////////////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // TRADES
    //
    /////////////////////////////////////////////////////////////////////////////////////////////////////////
    /////////////////////////////////////////////////////////////////////////////////////////////////////////
    
    /**
     * mtgox only has a trading fee if I
     * am buying bitcoins on the exchange
     */
    public double getTradingFeeFor(CurrencyTrade trade){
        if(trade.getFromExchange() == this){
            // I am buying bitcoins from
            // the exchange
            return tradeFee;
        }
        return 0;
    }
    
    
    protected void processTradeData(JSONObject tradeMessage) throws JSONException, ExchangeException{
        synchronized(this){
            CURRENCY curr = CURRENCY.valueOf(tradeMessage.getJSONObject("trade").getString("price_currency"));
            if(curr.equals(currencyEnum)){
                rawSocketMessagesLog.log(tradeMessage.toString());
            }else{
                // noop, wrong currency
            }
            this.notifyDidProcessTrade();
        }
    }
    
    
    
}