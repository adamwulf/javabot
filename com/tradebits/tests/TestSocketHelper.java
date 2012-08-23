package com.tradebits.tests;


import com.tradebits.*;
import com.tradebits.exchange.*;
import com.tradebits.socket.*;


public class TestSocketHelper extends ASocketHelper{
    boolean connected = false;
    public void disconnect(){
        connected = false;
    }
    public void connect() throws Exception{
        connected = true;
    }
    public void send(String message){
        // noop
    }
    public boolean isConnected(){
        return connected;
    }
}