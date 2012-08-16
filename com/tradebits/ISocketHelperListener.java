package com.tradebits;




public interface ISocketHelperListener{
    
    
    public void onOpen(SocketHelper socket);
    
    public void onClose(SocketHelper socket, int closeCode, String message);
    
    public void onMessage(SocketHelper socket, String data);
    
    public void onHeartbeatSent(SocketHelper socket);
    
}