package com.tradebits.exchange.mtgox;


import com.tradebits.*;
import com.tradebits.exchange.*;
import com.tradebits.exchange.AExchange.CURRENCY;
import com.tradebits.socket.*;
import java.util.*;
import java.net.*;
import org.json.*;

public class MtGoxDepthLoader{
    
    private String name;
    private CURRENCY currency;
    private Listener listener;
    
    public MtGoxDepthLoader(String name, CURRENCY curr, Listener listener){
        this.name = name;
        this.currency = curr;
        this.listener = listener;
    }
    
    
    // the mess that hopefully can be cleaned
    private Timer depthListingTimer;
    private boolean hasLoadedDepthDataAtLeastOnce = false;
    private Date lastRESTDepthCheck = null;
    
    
    public boolean hasLoadedDepthData(){
        return hasLoadedDepthDataAtLeastOnce;
    }
    
    public boolean isConnected(){
        return depthListingTimer != null && hasLoadedDepthDataAtLeastOnce;
    }
    
    public void connect(){
        if(depthListingTimer == null){
            depthListingTimer = new Timer();
            depthListingTimer.scheduleAtFixedRate(new TimerTask(){
                public void run(){
                    Date now = new Date();
                    //
                    // only load once each hour - yikes!
                    // this is b/c mtgox has an extremely aggressive anti DDOS in place
                    if(lastRESTDepthCheck == null || now.after(new Date(lastRESTDepthCheck.getTime() + 1000*60*60))){
                        hasLoadedDepthDataAtLeastOnce = true;
                        lastRESTDepthCheck = now;
                        MtGoxDepthLoader.this.loadInitialDepthData();
                    }
                }
            }, 15000, 30000);
        }
    }
    
    public void disconnect(){
        hasLoadedDepthDataAtLeastOnce = false;
        if(depthListingTimer != null) depthListingTimer.cancel();
        depthListingTimer = null;
        lastRESTDepthCheck = null;
    }
    
    public void stayConnectedButReloadDepthData(){
        lastRESTDepthCheck = null;
        hasLoadedDepthDataAtLeastOnce = false;
    }
    
    
    
    
    
    /**
     * This methos is responsible for loading
     * the initial depth data for the market.
     * 
     * after the socket is connected and we have
     * consistent depth data, this will continue to run
     * and validate our depth data
     */
    public void loadInitialDepthData(){
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
                        MtGoxDepthLoader.this.stayConnectedButReloadDepthData();
                        listener.getRawDepthDataLog().log("Fetching depth failed");
                        return;
                    }
                }
                listener.didLoadFullMarketDepthData(depthData);
            }
        }).start();
    }
    
    
    
    
    
    
    public interface Listener{
        
        public void didLoadFullMarketDepthData(JSONObject depthData);
        
        public ASocketFactory getSocketFactory();
        
        public Log getRawDepthDataLog();
        
    }
}