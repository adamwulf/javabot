package com.tradebits.tests;


import com.tradebits.*;
import com.tradebits.exchange.*;
import com.tradebits.socket.*;


public class TestSocketHelper extends ASocketHelper{
    boolean connected = false;
    public void disconnect(){
        if(connected){
            connected = false;
            if(getListener() != null) getListener().onClose(this, 0, null);
        }
    }
    public void connect() throws Exception{
        if(!connected){
            connected = true;
            if(getListener() != null) getListener().onOpen(this);
        }
    }
    public void send(String message){
        // noop
    }
    public boolean isConnected(){
        return connected;
    }
}