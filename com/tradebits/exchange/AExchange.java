/**
 * a generic bitcoin exchange
 */
package com.tradebits.exchange;


public abstract class AExchange{
    
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
        System.out.println(this.getName() + ": " + log);
    }
    
    abstract public boolean isCurrencySupported(CURRENCY curr);
    
}