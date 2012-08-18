package com.tradebits.socket;

import com.tradebits.*;

public class StandardSocketFactory extends ASocketFactory{
    
    URLHelper helper;
    
    public ASocketHelper getSocketHelperFor(String httpURL, String wsURLFragment){
        return new SocketHelper(this, httpURL, wsURLFragment);
    }

    
    public URLHelper getURLHelper(){
        return new URLHelper();
    }

}