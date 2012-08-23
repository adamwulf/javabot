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
public class CurrencyTrade extends Trade{
 
    public CurrencyTrade(AExchange from, AExchange to, OpenExchangeRates exchangeRateModel, Log tradeLog){
        super(from, to, exchangeRateModel, tradeLog);
    }
    
    public boolean prepTradeInformation(){
        JSONObject rates = exchangeRateModel.getUSDRates();
        try{
            //
            // it's not necessarily USD here, but it helps
            // me conceptualize the math below
            JSONObject fromAsk = fromEx.getAsk(0);
            JSONObject toBid = toEx.getBid(0);
            if(fromAsk != null && toBid != null){
                //
                // first, let's find the exchange rate that we'll
                // be able to get by trading through the bitcoin
                // market
                double priceIPay = fromAsk.getDouble("price");  // price I pay for BTC in $$.   EXTFrom/BTC
                double priceIGet = toBid.getDouble("price");    // price I get for BTC in $$.   ETXTo/BTC
                double throughBTCRate = priceIGet / priceIPay;
                String throughRateType = toEx.getName() + "/" + fromEx.getName();
                
                //
                // ok, now let's calculate the exchange rate that
                // we'd get through the proper money market
                //
                // get the CURR/USD rate for each exchange
                double actualFromRate = rates.getDouble(fromEx.getCurrency().toString());
                double actualToRate = rates.getDouble(toEx.getCurrency().toString());
                // calculate what the exchange rate should be
                // between these currencies
                double actualRate = actualFromRate / actualToRate;
                
                
                //
                // right now, throughBTCRate is the exchange rate that we'll
                // get exchanging our $$ from the fromEx to the toEx
                //
                // actualRate is the exchange rate that's calculated
                // from the openexchangerate data
                
                //
                // trigger is the % difference between BTC market
                // and money market for changing currencies
                //
                // if trigger is negative, then i lose that %
                // by going through BTC
                //
                // if trigger is positive, then i make that %
                // by going through BTC
                double trigger = throughBTCRate / actualRate - 1;
                
                
                if(trigger > 0.3){
                    // only trade if trigger is > 0.3){
                    //
                    // only log if it's time to
                    tradeLog.log("exchange rate: " + throughRateType + ": " + throughBTCRate + " vs " + actualRate);
                    tradeLog.log("   normalized to actual= " + (throughBTCRate/actualRate)+ " trigger=" + trigger + " vol= " + 
                                 Math.min(fromAsk.getDouble("volume"),toBid.getDouble("volume")) );
                    if(trigger > .05 || trigger < -.05){
                        // 5% difference in price
                        tradeLog.log(" - " + fromEx.getName() + " ask: " + fromEx.getAsk(0));
                        tradeLog.log(" - " + toEx.getName() + " bid: " + toEx.getBid(0));
                    }
                return true;
                }
            }else if(fromAsk == null){
                tradeLog.log("no bid for: " + fromEx.getCurrency());
            }else if(toBid == null){
                tradeLog.log("no bid for: " + toEx.getCurrency());
            }
        }catch(JSONException e){
            tradeLog.log("error calculating currency exchange rates: ");
            tradeLog.log(Arrays.toString(e.getStackTrace()));
        }
        return false;
    }
    
    
    
    
    
}