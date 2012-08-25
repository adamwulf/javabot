package com.tradebits.socket;


import com.tradebits.*;



abstract public class ASocketFactory{
    
    
    /**
     * return a new SocketHelper that handshakes with
     * the httpURL and connects to the socket at the wsURL
     */
    abstract public ISocketHelper getSocketHelperFor(String httpURL, String wsURLFragment);
    
    abstract public ISocketHelper getRawSocketTo(String host, int port, Log logFile);
    
    
    public URLHelper getURLHelper(){
        return new URLHelper();
    }
    
}