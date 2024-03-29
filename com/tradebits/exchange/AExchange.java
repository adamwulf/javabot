/**
 * a generic bitcoin exchange
 */
package com.tradebits.exchange;

import java.io.*;
import java.net.*;
import java.util.*;
import org.json.*;
import com.tradebits.trade.*;

public abstract class AExchange{
    
    private LinkedList<AExchangeListener> listeners = new LinkedList<AExchangeListener>();
  
    
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
    
    abstract public CURRENCY getCurrency();
    
    /**
     * right now, each exchange will log to std out prefixed
     * by that exchanges name. other file logs exist as well
     * with the Log class
     */
    public void log(String log){
        System.out.println(this.getName() + " " + (new Date()) + ": " + log + "\n");
    }
    
    public void disconnect(){
        bidDepthData = new TreeMap<Double, JSONObject>();
        askDepthData = new TreeMap<Double, JSONObject>();
    }
    
    abstract public boolean isCurrencySupported(CURRENCY curr);
    
    abstract public void connect();
    
    abstract public boolean isConnected();
    
    abstract public boolean isOffline();
    
    abstract public boolean isConnecting();
    
    
    
    /**
     * mtgox only has a trading fee if I
     * am buying bitcoins on the exchange
     */
    abstract public double getTradingFeeFor(CurrencyTrade trade);

    /**
     * all bid/ask data is stored in one of two maps. the map
     * key is the price, and the value is the volumn and history
     * for that price.
     * 
     * when the volume of an price goes to zero, it is deleted from
     * the map entirely
     */
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
        synchronized(treeMap){
            try{
                obj.get("price");
                obj.get("volume_int");
                obj.get("stamp");
                
                if(!obj.has("log")){
                    JSONArray arr = new JSONArray();
                    JSONObject firstLog = new JSONObject();
                    firstLog.put("volume_int", obj.getLong("volume_int"));
                    firstLog.put("stamp", obj.get("stamp"));
                    firstLog.put("first", true);
                    arr.put(firstLog);
                    obj.put("log", arr);
                }else{
                    JSONArray log = obj.getJSONArray("log");
                    JSONObject logItem = new JSONObject();
                    logItem.put("volume_int", obj.getLong("volume_int"));
                    logItem.put("stamp", obj.get("stamp"));
                    logItem.put("diff", true);
                    log.put(logItem);
                    obj.put("log", log);
                }
                
                if(obj.getLong("volume_int") > 0){
                    // only store the value if there actually
                    // is a volume
                    treeMap.put(obj.getDouble("price"), obj);
                }else{
                    // if no volume, delete it entirely
                    treeMap.remove(obj.getDouble("price"));
                }
//                this.log("set data for " + obj.getDouble("price") + " to vol " + obj.getDouble("volume_int"));
            }catch(JSONException e){
                throw new ExchangeException(e);
            }
        }
    }
    
    public void setAskData(JSONObject obj) throws ExchangeException{
        // make sure we have the data we need
        this.setBidAskData(obj, askDepthData);
    }
    
    public void setBidData(JSONObject obj) throws ExchangeException{
        this.setBidAskData(obj, bidDepthData);
    }
    
    
    
    //
    //
    // TODO:
    //
    // i have updated this method to properly preserve the log of the item.
    // now i need to run the test again until intersango goes out of sync.
    // last time, it happened when it was ~20 hours into it.
    private void updateBidAskData(JSONObject obj, TreeMap<Double, JSONObject> treeMap) throws ExchangeException{
        synchronized(treeMap){
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
                        newCachedObj.put("log", cachedObj.get("log"));
                        
                        //
                        // update the data
                        this.setBidAskData(newCachedObj, treeMap);
                        
                        if(cachedVolumeInt < 0){
                            this.log("WAS negative volume " + cachedVolumeInt + ", now" + 
                                     newVolumeInt + " for " + price + ", should reload. log: " + newCachedObj.get("log"));
                        }else if(newVolumeInt < 0){
                            this.log("negative volume data " + newVolumeInt + " for " + price + 
                                     ", should reload. log: " + newCachedObj.get("log"));
                            throw new ExchangeException("negative volume data " + newVolumeInt + 
                                                        " for " + price + ", should reload. log: " + newCachedObj.get("log"));
                        }
                        
//                        this.log("updating data for " + newCachedObj.getDouble("price") + " to vol " + newCachedObj.getLong("volume_int"));
                    }else{
                        // the input data is earlier than the cached data
                        //
                        // noop
//                        this.log("ignoring data, timestamp was earlier than cached data");
                    }
                }
            }catch(JSONException e){
                throw new ExchangeException(e);
            }
        }
    }
    public void updateAskData(JSONObject obj) throws ExchangeException{
        this.updateBidAskData(obj, askDepthData);
    }
    
    public void updateBidData(JSONObject obj) throws ExchangeException{
        this.updateBidAskData(obj, bidDepthData);
    }
    
    
    /** retrieve bid/ask data **/
    
    /**
     * a zero index is closest to the trade window
     * and increases as prices move away.
     * 
     * so an index 0 is the highest bid or
     * lowest ask
     */
    public JSONObject getBid(int index){
        synchronized(bidDepthData){
            List<Double> keys = new ArrayList<Double>(bidDepthData.keySet());
            for (int i = keys.size() - 1; i >= 0; i--) {
                if(keys.size() - 1 - index == i){
                    Double key = keys.get(i);
                    return bidDepthData.get(key);
                }
            }
        }
        return null;
    }
    
    public JSONObject getAsk(int index){
        synchronized(askDepthData){
            List<Double> keys = new ArrayList<Double>(askDepthData.keySet());
            for (int i = 0; i < keys.size(); i++) {
                if(i == index){
                    Double key = keys.get(i);
                    return askDepthData.get(key);
                }
            }
        }
        return null;
    }
    
    
    /**
     * listeners
     */
    public void addListener(AExchangeListener listener){
        synchronized(listeners){
            listeners.add(listener);
        }
    }
    public void removeListener(AExchangeListener listener){
        synchronized(listeners){
            listeners.remove(listener);
        }
    }
    
    public void notifyDidChangeConnectionState(){
        synchronized(listeners){
            for(AExchangeListener listener : listeners){
                listener.didChangeConnectionState(this);
            }
        }
    }
    
    public void notifyDidProcessTrade(){
        synchronized(listeners){
            for(AExchangeListener listener : listeners){
                listener.didProcessTrade(this);
            }
        }
    }
    
    public void notifyDidInitializeDepth(){
        synchronized(listeners){
            for(AExchangeListener listener : listeners){
                listener.didInitializeDepth(this);
            }
        }
    }
    
    public void notifyDidProcessDepth(){
        synchronized(listeners){
            for(AExchangeListener listener : listeners){
                listener.didProcessDepth(this);
            }
        }
    }
    
    public void notifyDidReceiveHeartbeat(){
        synchronized(listeners){
            for(AExchangeListener listener : listeners){
                listener.didReceiveHeartbeat(this);
            }
        }
    }
    
    
}