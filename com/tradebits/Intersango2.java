package com.tradebits;

import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import org.eclipse.jetty.websocket.*;
import java.nio.ByteBuffer;


public class Intersango2 {
    
    WebSocketClient socket;
    
    public Intersango2(){
        try{
            String wsURL = "wss://api.icbit.se/socket.io/1/websocket/QIVhTBg3eS1nw-yL1V-L?AuthKey=X4Rtx6lhFfexnPsibvwQzwx7OInx9swOoJW1MUbcp13Pz3jTZGOAuSvkSgqGfGpHbcWDiAOAqbwh3gB6FOxWwfURmfaV6fZjMVaw4MNeJZcOgZiDdD4rUr5OtNOQJaLO&UserId=743";
            wsURL = "wss://api.icbit.se/icbit?AuthKey=X4Rtx6lhFfexnPsibvwQzwx7OInx9swOoJW1MUbcp13Pz3jTZGOAuSvkSgqGfGpHbcWDiAOAqbwh3gB6FOxWwfURmfaV6fZjMVaw4MNeJZcOgZiDdD4rUr5OtNOQJaLO&UserId=743&t=1344658558923";
            
            WebSocketClientFactory factory = new WebSocketClientFactory();
            factory.start();
            
            WebSocketClient client = factory.newWebSocketClient();
            // Configure the client
            
            WebSocket.Connection connection = client.open(new URI(wsURL), new WebSocket.OnTextMessage()
                                                              {
                public void onOpen(Connection connection)
                {
                    System.out.println("open");
                    // open notification
                }
                
                public void onClose(int closeCode, String message)
                {
                    System.out.println("close");
                    // close notification
                }
                
                public void onMessage(String data)
                {
                    System.out.println("message: " + data);
                    // handle incoming message
                }
            }).get(5, TimeUnit.SECONDS);
            
            connection.sendMessage("Hello World");
            
            
        }catch(Exception e){
        }
    }
    
    public void connect(){
//        socket.connect();
    }
    
}