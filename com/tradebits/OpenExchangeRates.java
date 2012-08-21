package com.tradebits;


import org.json.*;
import java.io.*;
import java.net.*;
import java.util.*;


public class OpenExchangeRates{
    
    URLHelper urlHelper;
    
    JSONObject lastKnownJSONResponse;
    Date lastRequestDate;
    
    public OpenExchangeRates(URLHelper urlHelper){
        this.urlHelper = urlHelper;
        loadUSDRates();
        Timer foo = new Timer();
        foo.scheduleAtFixedRate(new TimerTask(){
            public void run(){
                OpenExchangeRates.this.loadUSDRates();
            }
        }, 1000*60*60, 1000*60*60);
    }
    
    protected void loadUSDRates(){
        if(lastRequestDate == null || new Date().after(new Date(lastRequestDate.getTime() + 1000*60*60))){
            try{
                lastRequestDate = new Date();
                String exchangeRateURL = "http://openexchangerates.org/api/latest.json?app_id=dd22177bcf3f4e94baa2f1e0786b9edd";
                String jsonOfExchanges = urlHelper.getSynchronousURL(new URL(exchangeRateURL));
                lastKnownJSONResponse = new JSONObject(jsonOfExchanges);
            }catch(Exception e){
                e.printStackTrace();
            }
        }
    }
    
    public JSONObject getUSDRates(){
        try{
            return lastKnownJSONResponse.getJSONObject("rates");
        }catch(Exception e){
            return null;
        }
    }
    
    
    
}