package com.tradebits.exchange.mtgox;



import com.tradebits.exchange.*;
import com.tradebits.*;
import java.util.*;
import java.net.*;
import com.tradebits.exchange.AExchange.CURRENCY;
import org.json.*;

/**
 * currency info
 */
public class MtGoxCurrency{
    
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