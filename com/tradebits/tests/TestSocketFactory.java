package com.tradebits.tests;


import com.tradebits.*;
import com.tradebits.socket.*;
import com.tradebits.exchange.mtgox.*;

public class TestSocketFactory extends ASocketFactory{
    
    /**
     * return a new SocketHelper that handshakes with
     * the httpURL and connects to the socket at the wsURL
     */
    public ISocketHelper getSocketHelperFor(String httpURL, String wsURLFragment){
        return null;
    }
    
    public ISocketHelper getRawSocketTo(String host, int port, Log logFile){
        return null;
    }
    
    public MtGoxRESTClient getMtGoxRESTClient(String key, String secret, Log rawSocketMessagesLog){
        return null;
    }
}