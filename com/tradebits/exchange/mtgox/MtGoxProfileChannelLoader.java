package com.tradebits.exchange.mtgox;


import com.tradebits.*;
import com.tradebits.exchange.*;
import com.tradebits.exchange.AExchange.CURRENCY;
import com.tradebits.socket.*;
import java.util.*;
import java.net.*;
import org.json.*;


/**
 * This class is responsible for loading currency
 * information from the mtgox REST api.
 * 
 * it will try to load initial currency data in
 * intervals of 30s until the data is loaded.
 * this will give it a 30s failover time
 * if the first request fails.
 * 
 * after a successful load, it will load
 * currency data once an hour, so that its listener
 * can validate the currency data
 */
public class MtGoxProfileChannelLoader{
    /********************************************************************************************************
      * Loader Properties
      * 
      * these properties help manage the timer and state
      */
    private String name;
    private CURRENCY currency;
    private Listener listener;
    
    /********************************************************************************************************
      * Timer Properties
      * 
      * these properties help manage the timer and state
      */
    // this is the timer we'll use to run the REST calls
    private Timer requestTimer;
    // set to true when we've loaded the data once
    private boolean hasLoadedDataAtLeastOnce = false;
    // the date that we last checked for currency data
    private Date lastRESTRequestCheck = null;
    // the number of milliseconds to retry the data load if
    // we don't have any data loaded ever
    private long unconnectedIntervalTimeoutInMilliseconds;
    // the number of milliseconds to retry the data load if
    // we do already have data loaded
    private long connectedIntervalTimeoutInMilliseconds;
    
    /**
     * create a new currency loader
     * @param unconnectedIntervalTimeout the interval in seconds that we should wait between REST calls if we're /unconnected/
     * @param connectedIntervalTimeout the interval in seconds that we should wait between REST calls if we're /connected/
     */
    public MtGoxProfileChannelLoader(String name, CURRENCY curr, Listener listener, 
                               long unconnectedIntervalTimeoutInSeconds, long connectedIntervalTimeoutInSeconds){
        this.name = name;
        this.currency = curr;
        this.listener = listener;
        this.unconnectedIntervalTimeoutInMilliseconds = unconnectedIntervalTimeoutInSeconds * 1000;
        this.connectedIntervalTimeoutInMilliseconds = connectedIntervalTimeoutInSeconds * 1000;
    }
    
    
        
    /////////////////////////////////////////////////////////////////////////////////////////////////////////
    /////////////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // PUBLIC
    //
    /////////////////////////////////////////////////////////////////////////////////////////////////////////
    /////////////////////////////////////////////////////////////////////////////////////////////////////////
    
    /**
     * return true only if we have successfully
     * loaded data, and if we're still requesting data
     * once an hour
     */
    public boolean isConnected(){
        return requestTimer != null && hasLoadedDataAtLeastOnce;
    }
    
    /**
     * this will start the connection to request
     * currency data, if we're not already.
     */
    public void connect(){
        if(requestTimer == null){
            requestTimer = new Timer();
            requestTimer.scheduleAtFixedRate(new TimerTask(){
                public void run(){
                    Date now = new Date();
                    //
                    // only load once each hour - yikes!
                    // this is b/c mtgox has an extremely aggressive anti DDOS in place
                    if(lastRESTRequestCheck == null || now.after(new Date(lastRESTRequestCheck.getTime() + connectedIntervalTimeoutInMilliseconds))){
                        lastRESTRequestCheck = now;
                        MtGoxProfileChannelLoader.this.loadProfileChannelData();
                    }
                }
            }, 0, unconnectedIntervalTimeoutInMilliseconds);
        }
    }
    
    /**
     * this will cancel any pending requests on our Timer,
     * and will reset us to default unconnected state
     */
    public void disconnect(){
        hasLoadedDataAtLeastOnce = false;
        if(requestTimer != null) requestTimer.cancel();
        requestTimer = null;
        lastRESTRequestCheck = null;
    }
    
    
        
    /////////////////////////////////////////////////////////////////////////////////////////////////////////
    /////////////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // PROTECTED
    //
    /////////////////////////////////////////////////////////////////////////////////////////////////////////
    /////////////////////////////////////////////////////////////////////////////////////////////////////////
 
    /**
     * This methos is responsible for loading
     * the initial currency data for the market.
     * 
     * after the socket is connected and we have
     * consistent currency data, this will continue to run
     * and validate our currency data
     */
    protected void loadProfileChannelData(){
        (new Thread(this.name + " Profile Channel Fetch"){
            public void run(){
                try{
                    String queryURL = "1/generic/private/idkey";
                    HashMap<String, String> args = new HashMap<String, String>();
                    String response = listener.getRESTClient().query(queryURL, args);
                    listener.getRawDepthDataLog().log("personal feed response: " + response);
                    if(response != null){
                        JSONObject ret = new JSONObject(response);
                        listener.getRawDepthDataLog().log("personal feed info " + ret);
                        String feedID = ret.getString("return");
                        hasLoadedDataAtLeastOnce = true;
                        listener.didLoadProfileChannelData(feedID);
                    }
                }catch(Exception e){
                    // noop
                    listener.getRawDepthDataLog().log(e.toString());
                }
            }
        }).start();
    }
    
    
    /////////////////////////////////////////////////////////////////////////////////////////////////////////
    /////////////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // LISTENER
    //
    /////////////////////////////////////////////////////////////////////////////////////////////////////////
    /////////////////////////////////////////////////////////////////////////////////////////////////////////
 
    /**
     * This is our listener interface.
     * whoever wants updates on our currency data
     * needs to implement this iterface
     */
    public interface Listener{
        
        public void didLoadProfileChannelData(String profileChannelID);
        
        public ASocketFactory getSocketFactory();
        
        public MtGoxRESTClient getRESTClient();
        
        public Log getRawDepthDataLog();
        
    }
}