package com.tradebits;

import java.net.*;
import java.util.*;
import org.java_websocket.client.*;
import org.java_websocket.drafts.*;
import org.java_websocket.handshake.*;
import java.nio.ByteBuffer;
import de.roderick.weberknecht.*;

public class ICBit {
    
    WebSocketClient socket;
    
    public ICBit(){
        try {
            String wsURL = "wss://api.icbit.se:443/icbit?AuthKey=X4Rtx6lhFfexnPsibvwQzwx7OInx9swOoJW1MUbcp13Pz3jTZGOAuSvkSgqGfGpHbcWDiAOAqbwh3gB6FOxWwfURmfaV6fZjMVaw4MNeJZcOgZiDdD4rUr5OtNOQJaLO&UserId=743";
            wsURL = "wss://feed.bitfloor.com/1";
            URI url = new URI(wsURL);
            WebSocket websocket = new WebSocketConnection(url);
            
            // Register Event Handlers
            websocket.setEventHandler(new WebSocketEventHandler() {
                public void onOpen()
                {
                    System.out.println("--open");
                }
                
                public void onMessage(WebSocketMessage message)
                {
                    System.out.println("--received message: " + message.getText());
                }
                
                public void onClose()
                {
                    System.out.println("--close");
                }
            });
            
            // Establish WebSocket Connection
            websocket.connect();
            
            // Send UTF-8 Text
            websocket.send("hello world");
            
            // Close WebSocket Connection
            websocket.close();
        }
        catch (WebSocketException wse) {
            wse.printStackTrace();
        }
        catch (URISyntaxException use) {
            use.printStackTrace();
        }
        /*
        try{
        
        final WebSocket ws = new WebSocket();
        String wsURL = "ws://api.icbit.se/icbit/?AuthKey=X4Rtx6lhFfexnPsibvwQzwx7OInx9swOoJW1MUbcp13Pz3jTZGOAuSvkSgqGfGpHbcWDiAOAqbwh3gB6FOxWwfURmfaV6fZjMVaw4MNeJZcOgZiDdD4rUr5OtNOQJaLO&UserId=74";
        
         
            socket = new WebSocketClient(new URI(wsURL), new Draft_76()){
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
            throw new RuntimeException(e);
        }
        */
    }
    
    public void connect(){
        socket.connect();
    }
    
}