package com.tradebits.trade;

import com.tradebits.*;
import com.tradebits.exchange.*;

public abstract class Trade{
    
    AExchange fromEx;
    AExchange toEx;
    Log tradeLog;
    OpenExchangeRates exchangeRateModel;
    

    public AExchange getFromExchange(){
        return fromEx;
    }
    
    public AExchange getToExchange(){
        return toEx;
    }

    
     public Trade(AExchange from, AExchange to, OpenExchangeRates exchangeRateModel, Log tradeLog){
        this.fromEx = from;
        this.toEx = to;
        this.tradeLog = tradeLog;
        this.exchangeRateModel = exchangeRateModel;
    }
    
}