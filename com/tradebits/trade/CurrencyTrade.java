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
 
    JSONObject fromAsk;
    JSONObject toBid;
    double expectedPercentReturn;
    
    public CurrencyTrade(AExchange from, AExchange to, OpenExchangeRates exchangeRateModel, Log tradeLog){
        super(from, to, exchangeRateModel, tradeLog);
        fromAsk = fromEx.getAsk(0);
        toBid = toEx.getBid(0);
        this.prepTradeInformation();
    }
    
    
    /**
     * returns the exchange rate between toEx/fromEx
     * if i had used money market instead of BTC market
     * for the exchange
     */
    private double getActualExchangeRate() throws JSONException{
        JSONObject rates = exchangeRateModel.getUSDRates();
        //
        // ok, now let's calculate the exchange rate that
        // we'd get through the proper money market
        //
        // get the CURR/USD rate for each exchange
        double actualFromRate = rates.getDouble(fromEx.getCurrency().toString());
        double actualToRate = rates.getDouble(toEx.getCurrency().toString());
        // calculate what the exchange rate should be
        // between these currencies
        return actualToRate / actualFromRate;
    }
    
    /**
     * returns the exchange rate between toEx/fromEx
     * if i had used the BTC market instead of the
     * money market
     */
    private double getBitcoinMarketExchangeRate() throws JSONException{
        //
        // first, let's find the exchange rate that we'll
        // be able to get by trading through the bitcoin
        // market
        double priceIPay = fromAsk.getDouble("price");  // price I pay for BTC in $$.   EXTFrom/BTC
        double priceIGet = toBid.getDouble("price");    // price I get for BTC in $$.   ETXTo/BTC
        return priceIGet / priceIPay;
    }
    
    
    private boolean prepTradeInformation(){
        try{
            if(fromAsk != null && toBid != null){
                
                // get bitcoin market rate
                double throughBTCRate = this.getBitcoinMarketExchangeRate();
                String throughRateType = toEx.getName() + "/" + fromEx.getName();

                // get money market rate
                double actualRate = this.getActualExchangeRate();
                //
                // right now, throughBTCRate is the exchange rate that we'll
                // get exchanging our $$ from the fromEx to the toEx
                //
                // actualRate is the exchange rate that's calculated
                // from the openexchangerate data
                //
                //
                //
                // trigger is the % difference between BTC market
                // and money market for changing currencies
                //
                // if trigger is negative, then i lose that %
                // by going through BTC
                //
                // if trigger is positive, then i make that %
                // by going through BTC
                expectedPercentReturn = throughBTCRate / actualRate - 1;
                
                double toFee = toEx.getTradingFeeFor(this);
                double fromFee = fromEx.getTradingFeeFor(this);
                
                //
                // trigger will be positive if i can make money
                // excluding fees
                if(expectedPercentReturn > (toFee + fromFee) + 0.01 || tradeLog instanceof SysOutLog){
                    // only trade if trigger is > 0.3){
                    //
                    // only log if it's time to
                    tradeLog.log("exchange rate: " + throughRateType + ": " + throughBTCRate + " vs " + actualRate);
                    tradeLog.log("   normalized to actual= " + (throughBTCRate/actualRate) +  " expectedPercentReturn=" + 
                                 expectedPercentReturn + " vol= " +  this.getAmountToTrade());
                    tradeLog.log(" - " + fromEx.getName() + " ask: " + fromAsk);
                    tradeLog.log(" - " + toEx.getName() + " bid: " + toBid);
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
    
    /**
     * returns true if this potential trade
     * expects a positive return
     * (not including fees)
     */
    public boolean expectsToMakeProfit(){
        return this.expectedPercentReturn() > 0;
    }
    
    /**
     * returns the expected return of the trade
     */
    public double expectedPercentReturn(){
        double toFee = toEx.getTradingFeeFor(this);
        double fromFee = fromEx.getTradingFeeFor(this);
        return expectedPercentReturn - toFee - fromFee;
    }
    
    /**
     * returns the number of bitcoins that
     * can be traded with this trade
     */
    public double getAmountToTrade() throws JSONException{
        return Math.min(fromAsk.getDouble("volume"),toBid.getDouble("volume"));
    }
    
    public JSONObject getFromAsk(){
        return fromAsk;
    }
    
    public JSONObject getToBid(){
        return toBid;
    }
}