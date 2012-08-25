package com.tradebits.exchange;




abstract public class AExchangeListener{
    
    public void didChangeConnectionState(AExchange exchange){
        // default to noop
    }
    
    public void didProcessTrade(AExchange exchange){
        // default to noop
    }
    
    public void didInitializeDepth(AExchange exchange){
        // default to noop
    }
    
    public void didProcessDepth(AExchange exchange){
        // default to noop
    }
    
}