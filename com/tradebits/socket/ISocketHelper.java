package com.tradebits.socket;

import java.io.*;

public interface ISocketHelper{
    
    /** Socket **/
    
    public void disconnect();
    
    public void connect() throws Exception;
    
    public void send(String message) throws IOException;
    
    public boolean isConnected();
    
    
    /** Listener **/
    
    public void setListener(ISocketHelperListener listener);
    
    public ISocketHelperListener getListener();
}