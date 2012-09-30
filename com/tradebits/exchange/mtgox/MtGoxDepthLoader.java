package com.tradebits.exchange.mtgox;


import com.tradebits.*;
import com.tradebits.exchange.*;
import com.tradebits.exchange.AExchange.CURRENCY;
import com.tradebits.socket.*;
import java.util.*;
import java.net.*;
import org.json.*;


/**
 * This class is responsible for loading depth
 * information from the mtgox REST api.
 * 
 * it will try to load initial depth data in
 * intervals of 30s until the data is loaded.
 * this will give it a 30s failover time
 * if the first request fails.
 * 
 * after a successful load, it will load
 * depth data once an hour, so that its listener
 * can validate the depth data
 */
public class MtGoxDepthLoader{
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
    // the date that we last checked for depth data
    private Date lastRESTRequestCheck = null;
    // the number of milliseconds to retry the data load if
    // we don't have any data loaded ever
    private long unconnectedIntervalTimeoutInMilliseconds;
    // the number of milliseconds to retry the data load if
    // we do already have data loaded
    private long connectedIntervalTimeoutInMilliseconds;
    
    
    /**
     * create a new depth loader
     * @param unconnectedIntervalTimeout the interval in seconds that we should wait between REST calls if we're /unconnected/
     * @param connectedIntervalTimeout the interval in seconds that we should wait between REST calls if we're /connected/
     */
    public MtGoxDepthLoader(String name, CURRENCY curr, Listener listener, 
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
     * depth data, if we're not already.
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
                        MtGoxDepthLoader.this.loadInitialData();
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
     * This is called if our REST request fails for
     * some reason. we still want to stay connected,
     * and will reset our values so that our next Timer
     * tick will cause another REST request
     */
    protected void stayConnectedButReloadData(){
        lastRESTRequestCheck = null;
        hasLoadedDataAtLeastOnce = false;
    }
    
    
    /**
     * This methos is responsible for loading
     * the initial depth data for the market.
     * 
     * after the socket is connected and we have
     * consistent depth data, this will continue to run
     * and validate our depth data
     */
    protected void loadInitialData(){
        (new Thread(this.name + " First Depth Fetch"){
            public void run(){
                //
                // ok
                // we're going to download the depth data
                //
                // and only after that we're going to re-run the
                // realtime data on top of it
                //
                // track how many times we try to load
                JSONObject depthData = null;
                while(depthData == null){
                    try {
                        String depthString = "";
                        URLHelper urlHelper = listener.getSocketFactory().getURLHelper();
                        // Send data
                        URL url = new URL("https://mtgox.com/api/1/BTC" + currency + "/depth");
                        depthString = urlHelper.getSynchronousURL(url);
                        
                        //
                        // ok, we have the string data,
                        // now parse it
                        if(depthString != null && depthString.length() > 0){
                            JSONObject parsedDepthData = new JSONObject(depthString);
                            if(parsedDepthData != null &&
                               parsedDepthData.getString("result").equals("success")){
                                depthData = parsedDepthData;
                            }else if(parsedDepthData != null &&
                                     parsedDepthData.getString("result").equals("error")){
                                listener.getRawDepthDataLog().log("ERROR LOADING DEPTH: " + depthString);
                                listener.getRawDepthDataLog().log("Sleeping and will try again later");
                                return;
                            }else{
                                listener.getRawDepthDataLog().log("UNKNOWN ERROR LOADING DEPTH: " + depthString);
                                listener.getRawDepthDataLog().log("Sleeping and will try again later");
                                return;
                            }
                        }
                        
                        listener.getRawDepthDataLog().log("-- Loading from " + url);
                    }catch (Exception e) {
                        //
                        // something went terribly wrong
                        // so log the error and mark our
                        // last depth load as never so that 
                        // the next timer cycle will try again in 30s
                        e.printStackTrace();
                        lastRESTRequestCheck = null;
                        MtGoxDepthLoader.this.stayConnectedButReloadData();
                        listener.getRawDepthDataLog().log("Fetching depth failed");
                        return;
                    }
                }
                hasLoadedDataAtLeastOnce = true;
                listener.didLoadFullMarketDepthData(depthData);
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
     * whoever wants updates on our data
     * needs to implement this iterface
     */
    public interface Listener{
        
        public void didLoadFullMarketDepthData(JSONObject depthData);
        
        public ASocketFactory getSocketFactory();
        
        public Log getRawDepthDataLog();
        
    }
}