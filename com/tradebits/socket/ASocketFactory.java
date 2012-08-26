package com.tradebits.socket;


import com.tradebits.*;
import com.tradebits.exchange.mtgox.*;


abstract public class ASocketFactory{
    
    
    /**
     * return a new SocketHelper that handshakes with
     * the httpURL and connects to the socket at the wsURL
     */
    abstract public ISocketHelper getSocketHelperFor(String httpURL, String wsURLFragment);
    
    abstract public ISocketHelper getRawSocketTo(String host, int port, Log logFile);
    
    abstract public MtGoxRESTClient getMtGoxRESTClient(String key, String secret, Log rawSocketMessagesLog);
    
    public URLHelper getURLHelper(){
        return new URLHelper();
    }
    
}