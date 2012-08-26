package com.tradebits.socket;

import com.tradebits.*;
import com.tradebits.exchange.mtgox.*;

public class StandardSocketFactory extends ASocketFactory{
    
    URLHelper helper;
    
    public ISocketHelper getSocketHelperFor(String httpURL, String wsURLFragment){
        return new SocketHelper(this, httpURL, wsURLFragment);
    }
    
    public ISocketHelper getRawSocketTo(String host, int port, Log logFile){
        return new RawSocketConnection(host, port, logFile);
    }
    
    public MtGoxRESTClient getMtGoxRESTClient(String key, String secret, Log rawSocketMessagesLog){
        return new MtGoxRESTClient(key, secret, rawSocketMessagesLog);
    }

    
    public URLHelper getURLHelper(){
        return new URLHelper();
    }

}