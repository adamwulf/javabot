package com.tradebits.socket;




public interface ISocketHelperListener{
    
    
    public void onOpen(ISocketHelper socket);
    
    public void onClose(ISocketHelper socket, int closeCode, String message);
    
    public void onMessage(ISocketHelper socket, String data);
    
    public void onError(ISocketHelper socket, String error);
    
    public void onHeartbeatSent(ISocketHelper socket);
    
}