package com.tradebits.socket;

import java.io.*;

public abstract class ASocketHelper{
    
    /** Members **/
    
    private ISocketHelperListener listener;
    
    
    /** Socket **/
    
    abstract public void disconnect();
    
    abstract public void connect() throws Exception;
    
    abstract public void send(String message) throws IOException;
    
    
    
    /** Listener **/
    
    public void setListener(ISocketHelperListener listener){
        this.listener = listener;
    }
    
    public ISocketHelperListener getListener(){
        return this.listener;
    }
}