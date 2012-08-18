package com.tradebits.socket;



public class StandardSocketFactory implements ISocketFactory{
    
    public ASocketHelper getSocketHelperFor(String httpURL, String wsURLFragment){
   
        return new SocketHelper(httpURL, wsURLFragment);
    
    }
    
}