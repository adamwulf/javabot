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
public class MtGox extends MtGoxBase {
    
    
    
    public MtGox(JSONObject config, ASocketFactory factory, CURRENCY curr) throws ExchangeException{
        super(config, factory, curr);
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
}