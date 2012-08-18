package com.tradebits.socket;




public interface ISocketFactory{
    
    
    /**
     * return a new SocketHelper that handshakes with
     * the httpURL and connects to the socket at the wsURL
     */
    public ASocketHelper getSocketHelperFor(String httpURL, String wsURLFragment);
    
    
}