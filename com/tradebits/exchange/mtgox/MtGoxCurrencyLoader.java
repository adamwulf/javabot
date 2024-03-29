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
public class MtGoxCurrencyLoader{
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
    public MtGoxCurrencyLoader(String name, CURRENCY curr, Listener listener, 
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
                        MtGoxCurrencyLoader.this.loadInitialCurrencyData();
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
        listener.didUnloadCurrencyData();
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
    protected void loadInitialCurrencyData(){
        (new Thread(this.name + " Currency Fetch"){
            public void run(){
                MtGoxCurrency cachedCurrencyData = null;
                do{
                    try{
                        System.out.println(this.getName() + ": loading currency data");
                        URLHelper urlHelper = listener.getSocketFactory().getURLHelper();
                        URL url = new URL("https://mtgox.com/api/1/generic/currency?currency=" + currency);
                        String currencyJSON = urlHelper.getSynchronousURL(url);
                        
                        //
                        // ok, we have the string data,
                        // now parse it
                        if(currencyJSON != null && currencyJSON.length() > 0){
                            JSONObject parsedCurrencyData = new JSONObject(currencyJSON);
                            if(parsedCurrencyData != null &&
                               parsedCurrencyData.getString("result").equals("success")){
                                parsedCurrencyData = parsedCurrencyData.getJSONObject("return");;
                                cachedCurrencyData = new MtGoxCurrency(currency, parsedCurrencyData);
                                listener.getRawDepthDataLog().log("loaded currency information for " + currency);
                                hasLoadedDataAtLeastOnce = true;
                                listener.didLoadCurrencyData(cachedCurrencyData);
                                System.out.println(this.getName() + ": finished loading currency data");
                            }
                        }
                    }catch(Exception e){
                        lastRESTRequestCheck = null;
                        e.printStackTrace();
                        try{
                            Thread.sleep(unconnectedIntervalTimeoutInMilliseconds);
                        }catch(InterruptedException e2){ }
                    }
                }while(cachedCurrencyData == null);
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
        
        public void didLoadCurrencyData(MtGoxCurrency currencyData);
        
        public void didUnloadCurrencyData();
        
        public ASocketFactory getSocketFactory();
        
        public Log getRawDepthDataLog();
        
    }
}