package com.tradebits.trade;


import java.io.*;
import java.net.*;
import java.util.*;
import javax.net.ssl.*;
import java.security.cert.*;
import com.tradebits.*;
import com.tradebits.exchange.*;
import com.tradebits.exchange.AExchange.CURRENCY;
import com.tradebits.socket.*;
import org.json.*;

/**
 * This class is responsible for:
 * 1. calculating the expected return (or not) of a trade
 * 2. executing that trade when told to
 * 3. damage control if that trade fails
 * 
 * This class is NOT responsible for:
 * 1. rebalancing the wallets post-trade
 */
public class CurrencyTrade{
 
    Log tradeLog;
    OpenExchangeRates exchangeRateModel;
    AExchange fromEx;
    AExchange toEx;
    
    public CurrencyTrade(AExchange from, AExchange to, OpenExchangeRates exchangeRateModel, Log tradeLog){
        this.fromEx = from;
        this.toEx = to;
        this.tradeLog = tradeLog;
        this.exchangeRateModel = exchangeRateModel;
    }
    
    private void prepTradeInformation(){
        JSONObject rates = exchangeRateModel.getUSDRates();
        try{
            JSONObject usdAsk = fromEx.getAsk(0);
            JSONObject exBid = toEx.getBid(0);
            if(usdAsk != null && exBid != null){
                double usdToBTC = usdAsk.getDouble("price");
                double exToBTC = exBid.getDouble("price");
                double exdusd = exToBTC / usdToBTC;
                double actualRate = rates.getDouble(toEx.getCurrency().toString());
                if(toEx == fromEx){
                    actualRate = 1 / rates.getDouble(fromEx.getCurrency().toString());
                }
                
                //
                // the sign of this trigger will tell
                // me which direction to trade
                double trigger = exdusd / actualRate - 1;
                // "how many EXD do we get per USD?"
                
                //
                // only log if it's time to
                tradeLog.log("exchange rate: " + toEx.getName() + "/" + fromEx.getName() + 
                            ": " + exdusd + " vs " + actualRate + " diff= " + (exdusd/actualRate) + " vol= " + 
                            Math.min(usdAsk.getDouble("volume"),exBid.getDouble("volume")) + " trigger=" + trigger);
                if(trigger > .05 || trigger < -.05){
                    // 5% difference in price
                    tradeLog.log(" - " + fromEx.getName() + " bid: " + fromEx.getBid(0));
                    tradeLog.log(" - " + fromEx.getName() + " ask: " + fromEx.getAsk(0));
                    tradeLog.log(" - " + toEx.getName() + " bid: " + toEx.getBid(0));
                    tradeLog.log(" - " + toEx.getName() + " ask: " + toEx.getAsk(0));
                }
            }else if(usdAsk == null){
                tradeLog.log("no bid for: " + fromEx.getCurrency());
            }else if(exBid == null){
                tradeLog.log("no bid for: " + toEx.getCurrency());
            }
        }catch(JSONException e){
            tradeLog.log("error calculating currency exchange rates: ");
            tradeLog.log(Arrays.toString(e.getStackTrace()));
        }
    }
    
    
    
    
    
}