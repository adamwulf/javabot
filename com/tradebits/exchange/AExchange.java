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

    protected void resetAndReconnect(){
        if(!this.isConnected()){
            this.log("RESET AND RECONNECT");
            bidDepthData = new TreeMap<Double, JSONObject>();
            askDepthData = new TreeMap<Double, JSONObject>();
            connect();
        }
    }

    private String name;
    
    public AExchange(String name){
        this.name = name;
    }
    
    public String getName(){
        return name;
    }
    
    protected void log(String log){
        System.out.println(this.getName() + " " + (new Date()) + ": " + log + "\n");
    }
    
    abstract public boolean isCurrencySupported(CURRENCY curr);
    
    abstract public void connect();
    
    abstract public boolean isConnected();
    
    private JSONObject getBidAskData(double price, TreeMap<Double, JSONObject> treeMap){
        return treeMap.get(price);
    }
    
    public JSONObject getAskData(double price){
        return getBidAskData(price, askDepthData);
    }
    
    public JSONObject getBidData(double price){
        return getBidAskData(price, bidDepthData);
    }
    
    // methods for maintaining book
    
    private void setBidAskData(JSONObject obj, TreeMap<Double, JSONObject> treeMap) throws ExchangeException{
        try{
            obj.get("price");
            obj.get("volume_int");
            obj.get("stamp");
            
            if(!obj.has("log")){
                JSONArray arr = new JSONArray();
                JSONObject firstLog = new JSONObject();
                firstLog.put("volume_int", obj.getDouble("volume_int"));
                firstLog.put("stamp", obj.get("stamp"));
                firstLog.put("first", true);
                arr.put(firstLog);
                obj.put("log", arr);
            }else{
                JSONArray log = obj.getJSONArray("log");
                JSONObject logItem = new JSONObject();
                logItem.put("volume_int", obj.getDouble("volume_int"));
                logItem.put("stamp", obj.get("stamp"));
                logItem.put("diff", true);
                log.put(logItem);
                obj.put("log", log);
            }
        
                treeMap.put(obj.getDouble("price"), obj);
//                this.log("set data for " + obj.getDouble("price") + " to vol " + obj.getDouble("volume_int"));
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
            obj.getDouble("volume_int");
            obj.get("stamp");
            
            JSONObject cachedObj = treeMap.get(obj.getDouble("price"));
            if(cachedObj == null){
                // set
                this.setBidAskData(obj, treeMap);
            }else{
                // update
                long cachedVolumeInt = cachedObj.getLong("volume_int");
                Date cachedStamp = (Date) cachedObj.get("stamp");
                
                if(!cachedStamp.after((Date)obj.get("stamp"))){
                    // the cached data is earlier than the input data
                    JSONObject newCachedObj = new JSONObject();
                    double price = obj.getDouble("price");
                    long newVolumeInt = cachedVolumeInt + obj.getLong("volume_int");
                    
                    
                    newCachedObj.put("price", price);
                    newCachedObj.put("volume_int", newVolumeInt);
                    newCachedObj.put("stamp", obj.get("stamp"));
                    
                    //
                    // update the data
                    this.setBidAskData(newCachedObj, treeMap);

                    if(cachedVolumeInt < 0){
                        this.log("WAS negative volume " + cachedVolumeInt + ", now" + newVolumeInt + " for " + price + ", should reload. log: " + newCachedObj.get("log"));
                    }else if(newVolumeInt < 0){
                        this.log("negative volume data " + newVolumeInt + " for " + price + ", should reload. log: " + newCachedObj.get("log"));
                    }

                    this.log("updating data for " + newCachedObj.getDouble("price") + " to vol " + newCachedObj.getLong("volume_int"));
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