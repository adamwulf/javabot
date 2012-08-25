package com.tradebits.tests;


import com.tradebits.*;
import com.tradebits.socket.*;


public class TestSocketFactory extends ASocketFactory{
    
    /**
     * return a new SocketHelper that handshakes with
     * the httpURL and connects to the socket at the wsURL
     */
    public ISocketHelper getSocketHelperFor(String httpURL, String wsURLFragment){
        return null;
    }
    
    public ISocketHelper getRawSocketTo(String host, int port, ISocketHelperListener listener, Log logFile){
        return null;
    }
}