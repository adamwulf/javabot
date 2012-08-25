/**
 * a generic bitcoin exchange
 */
package com.tradebits.tests;

import java.io.*;
import java.net.*;
import java.util.*;
import org.json.*;
import com.tradebits.trade.*;
import com.tradebits.exchange.*;

public class TestExchange extends AExchange{
    

    private CURRENCY currency;
    private double fee;
    
    public TestExchange(String name, CURRENCY currency, double fee){
        super(name);
        this.fee = fee;
        this.currency = currency;
    }
    
    public CURRENCY getCurrency(){
        return currency;
    }
    
    public boolean isCurrencySupported(CURRENCY curr){
        return curr.equals(currency);
    }
    
    public void connect(){
        // noop
    }
    
    public boolean isConnected(){
        return true;
    }
    
    public boolean isOffline(){
        return false;
    }
    
    public boolean isConnecting(){
        return false;
    }
    
    public double getTradingFeeFor(CurrencyTrade trade){
        return fee;
    }

}