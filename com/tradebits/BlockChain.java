package com.tradebits;

import java.net.*;
import java.util.*;
import org.java_websocket.client.*;
import org.java_websocket.drafts.*;
import org.java_websocket.handshake.*;
import java.nio.ByteBuffer;

/**
 * this is info about bitcoin as a currency, but not an exchange of itself
 */
public class BlockChain{
    
    WebSocketClient socket;
    
    public BlockChain(){
        try{
            String wsURL = "ws://api.blockchain.info:8335/inv";
            socket = new WebSocketClient(new URI(wsURL), new Draft_76()){
                @Override
                public void onOpen( ServerHandshake handshakedata ){
                    System.out.println("onOpen");
                    this.send("{\"op\":\"unconfirmed_sub\"}");
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