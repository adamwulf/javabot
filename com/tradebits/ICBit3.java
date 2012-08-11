package com.tradebits;

import java.net.*;
import java.util.*;
import com.kaazing.gateway.client.html5.WebSocket;
import com.kaazing.gateway.client.html5.WebSocketAdapter;
import com.kaazing.gateway.client.html5.WebSocketEvent;
import org.java_websocket.client.*;
import org.java_websocket.drafts.*;
import org.java_websocket.handshake.*;
import java.nio.ByteBuffer;


public class ICBit3 {
    
    WebSocketClient socket;
    
    public ICBit3(){
        try{
            Long dt = new Long((new Date()).getTime() / 1000);
            String wsURL = "wss://api.icbit.se:443/socket.io/1/?t=" + dt.toString();
            socket = new WebSocketClient(new URI(wsURL), new Draft_17()){
                @Override
                public void onOpen( ServerHandshake handshakedata ){
                    System.out.println("onOpen");
                }
                @Override
                public void onMessage( String message ){
                    System.out.println("onMessage: " + message);
                }
                @Override
                public void onClose( int code, String reason, boolean remote ){
                    System.out.println("onClose: " + reason + (new Integer(code)).toString());
                }
                @Override
                public void onError( Exception ex ){
                    System.out.println("onError");
                    ex.printStackTrace();
                }
                @Override
                public void onMessage( ByteBuffer bytes ) {
                    System.out.println("onMessage");
                }
            };
        }catch(Exception e){
        }
    }
    
    public void connect(){
        socket.connect();
    }
    
}