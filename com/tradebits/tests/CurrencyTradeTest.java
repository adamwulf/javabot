package com.tradebits.tests;

import java.net.*;
import java.io.*;
import java.util.*;
import org.junit.*;
import com.tradebits.*;
import com.tradebits.exchange.*;
import com.tradebits.socket.*;
import com.tradebits.tests.*;
import com.tradebits.trade.*;
import junit.framework.TestCase;
import static org.junit.Assert.*;
import com.tradebits.exchange.AExchange.CURRENCY;
import org.json.*;


public class CurrencyTradeTest extends TestHelper{
    
    private double fakeMtGoxSellingFee = 0.06;
    private double noFeeAtAll = 0;
    
    OpenExchangeRates exchangeRates;
    
    @BeforeClass
    protected void setUp() throws JSONException{
        exchangeRates = new OpenExchangeRates(new URLHelper());
    }
    
    
    /**
     * This tests that the socket connect()
     * method is called when connecting to mtgox
     */
    @Test public void testUnprofitableTrade() throws IOException{
        
        TestExchange fromEx = new TestExchange("Fake MtGox", CURRENCY.USD, fakeMtGoxSellingFee){
            public JSONObject getAsk(int index){
                try{
                    JSONObject ret = new JSONObject();
                    ret.put("stamp", new Date());
                    ret.put("price", 10.379);
                    ret.put("volume", 0.0683872);
                    ret.put("currency", this.getCurrency());
                    return ret;
                }catch(JSONException e){ }
                return null;
            }
        };
        fromEx.connect();
        
        TestExchange toEx = new TestExchange("Fake Intersango", CURRENCY.USD, noFeeAtAll){
            public JSONObject getBid(int index){
                try{
                    JSONObject ret = new JSONObject();
                    ret.put("stamp", new Date());
                    ret.put("price", 10.27569);
                    ret.put("volume", 0.02779);
                    ret.put("currency", this.getCurrency());
                    return ret;
                }catch(JSONException e){ }
                return null;
            }
        };
        toEx.connect();
        
        
        CurrencyTrade testTrade = new CurrencyTrade(fromEx, toEx, exchangeRates, new SysOutLog());
        
        assertEquals(-0.0099537527700162 - fakeMtGoxSellingFee, testTrade.expectedPercentReturn());
    }
    
    
    
}