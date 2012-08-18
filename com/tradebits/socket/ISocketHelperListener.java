package com.tradebits.socket;




public interface ISocketHelperListener{
    
    
    public void onOpen(ASocketHelper socket);
    
    public void onClose(ASocketHelper socket, int closeCode, String message);
    
    public void onMessage(ASocketHelper socket, String data);
    
    public void onHeartbeatSent(ASocketHelper socket);
    
}