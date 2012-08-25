package com.tradebits.socket;

import com.tradebits.*;

public class StandardSocketFactory extends ASocketFactory{
    
    URLHelper helper;
    
    public ISocketHelper getSocketHelperFor(String httpURL, String wsURLFragment){
        return new SocketHelper(this, httpURL, wsURLFragment);
    }
    
    public ISocketHelper getRawSocketTo(String host, int port, Log logFile){
        return new RawSocketConnection(host, port, logFile);
    }

    
    public URLHelper getURLHelper(){
        return new URLHelper();
    }

}