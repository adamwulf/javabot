/**
 * a generic bitcoin exchange
 */
package com.tradebits.exchange;

import java.io.*;
import java.net.*;
import java.util.*;
import org.json.*;

public abstract class AExchange{

    TreeMap bidDepthData = new TreeMap();
    TreeMap askDepthData = new TreeMap();

    public enum CURRENCY {
        BTC, USD, PLN, EUR, GBP, AUD, CAD, CHF, CNY, DKK, HKD, JPY, NZD, RUB, SEK, SGD, THB
    }

    
    private String name;
    
    public AExchange(String name){
        this.name = name;
    }
    
    public String getName(){
        return name;
    }
    
    protected void log(String log){
        System.out.println(this.getName() + ": " + log + "\n");
    }
    
    abstract public boolean isCurrencySupported(CURRENCY curr);
    
    
    // methods for maintaining book
    
    public void setAskData(JSONObject obj) throws ExchangeException{
        // make sure we have the data we need
        try{
            obj.get("price");
            obj.get("volume");
            obj.get("stamp");
        }catch(JSONException e){
            throw new ExchangeException(e);
        }
    }
}