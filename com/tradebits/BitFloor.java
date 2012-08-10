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


public class BitFloor {
    
    WebSocketClient socket;
    
    public BitFloor(){
        try{
            String wsURL = "ws://feed.bitfloor.com/1";
            socket = new WebSocketClient(new URI(wsURL), new Draft_10()){
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
                    System.out.println("onClose");
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