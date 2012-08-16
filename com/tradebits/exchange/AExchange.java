/**
 * a generic bitcoin exchange
 */
package com.tradebits.exchange;

import java.io.*;
import java.net.*;
import java.util.*;
import org.json.*;

public abstract class AExchange{

    TreeMap<Double, JSONObject> bidDepthData = new TreeMap<Double, JSONObject>();
    TreeMap<Double, JSONObject> askDepthData = new TreeMap<Double, JSONObject>();

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
    
    private void setBidAskData(JSONObject obj, TreeMap<Double, JSONObject> treeMap) throws ExchangeException{
        try{
            obj.get("price");
            obj.get("volume");
            obj.get("stamp");
            
            JSONArray arr = new JSONArray();
            arr.put(obj.getDouble("volume"));
            obj.put("log", arr);
        
            if(obj.getDouble("volume") < 0){
                // noop
//                this.log("SETTING " + obj.getDouble("volume"));
            }else{
                treeMap.put(obj.getDouble("price"), obj);
            }
        }catch(JSONException e){
            throw new ExchangeException(e);
        }
    }
    
    public void setAskData(JSONObject obj) throws ExchangeException{
        // make sure we have the data we need
        this.setBidAskData(obj, askDepthData);
    }
    
    public void setBidData(JSONObject obj) throws ExchangeException{
        this.setBidAskData(obj, bidDepthData);
    }
    
    
    
    
    private void updateBidAskData(JSONObject obj, TreeMap<Double, JSONObject> treeMap) throws ExchangeException{
        try{
            obj.getDouble("price");
            obj.getDouble("volume");
            obj.get("stamp");
            
            JSONObject cachedObj = treeMap.get(obj.getDouble("price"));
            if(cachedObj == null){
                // set
                this.setBidAskData(obj, treeMap);
            }else{
                // update
                double cachedVolume = cachedObj.getDouble("volume");
                Date cachedStamp = (Date) cachedObj.get("stamp");
                
                if(!cachedStamp.after((Date)obj.get("stamp"))){
                    // the cached data is earlier than the input data
                    JSONObject newCachedObj = new JSONObject();
                    newCachedObj.put("price", obj.get("price"));
                    newCachedObj.put("volume", cachedVolume + obj.getDouble("volume"));
                    newCachedObj.put("stamp", obj.get("stamp"));
                    
                    JSONArray log = cachedObj.getJSONArray("log");
                    log.put(obj.getDouble("volume"));
                    newCachedObj.put("log", log);
                    
                    double price = newCachedObj.getDouble("price");
                    double newVolume = newCachedObj.getDouble("volume");
                    if(obj.getDouble("volume") == 0){
                        this.log("has zero volume for depth");
                    }else if(cachedVolume < 0){
                        this.log("WAS negative volume " + cachedVolume + ", now" + newVolume + " for " + price + ", should reload. log: " + newCachedObj.get("log"));
                    }else if(newVolume < 0){
                        this.log("negative volume data " + newVolume + " for " + price + ", should reload. log: " + newCachedObj.get("log"));
                    }
                    
                    //
                    // update the data
                    this.setBidAskData(newCachedObj, treeMap);
                    this.log("updating data for " + newCachedObj.getDouble("price"));
                }else{
                    // the input data is earlier than the cached data
                    //
                    // noop
                    this.log("ignoring data, timestamp was earlier than cached data");
                }
            }
        }catch(JSONException e){
            throw new ExchangeException(e);
        }
    }
    public void updateAskData(JSONObject obj) throws ExchangeException{
        this.updateBidAskData(obj, askDepthData);
    }
    
    public void updateBidData(JSONObject obj) throws ExchangeException{
        this.updateBidAskData(obj, bidDepthData);
    }
}