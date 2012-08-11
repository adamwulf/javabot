package com.tradebits;

import java.io.*;
import java.net.*;
import java.util.*;
import com.kaazing.gateway.client.html5.WebSocket;
import com.kaazing.gateway.client.html5.WebSocketAdapter;
import com.kaazing.gateway.client.html5.WebSocketEvent;
import org.java_websocket.client.*;
import org.java_websocket.drafts.*;
import org.java_websocket.handshake.*;
import java.nio.ByteBuffer;
import com.glines.socketio.client.common.SocketIOConnection;
import com.glines.socketio.client.common.SocketIOConnectionListener;
import com.glines.socketio.common.ConnectionState;
import com.glines.socketio.common.DisconnectReason;
import com.glines.socketio.common.SocketIOException;
import com.glines.socketio.client.jre.*;


public class ICBit2{
    
    de.roderick.weberknecht.WebSocket socket2;
    
    public ICBit2(){
        
        try{
            
            
            
            URL icbitSite = new URL("https://api.icbit.se/socket.io/1/?t=1344644213395");
            BufferedReader in = new BufferedReader(
                                                   new InputStreamReader(
                                                                         icbitSite.openStream()));
            
            String topLine = null;
            String inputLine;
            while ((inputLine = in.readLine()) != null){
                topLine = inputLine;
            }
            in.close();
            
            System.out.println(topLine);
            
            String foo = topLine.substring(0, topLine.indexOf(":"));
                                            
            System.out.println(foo);
                                            
                                            
                                            
            socket2 = new de.roderick.weberknecht.WebSocketConnection(new URI("wss://api.icbit.se/socket.io/1/websocket/" + foo));
            de.roderick.weberknecht.WebSocketEventHandler handler = new de.roderick.weberknecht.WebSocketEventHandler(){
                public void onOpen(){
                    System.out.println("--open");
                    try{
                    socket2.send("{ "
                                     + "\"op\":\"subscribe\", "
                                     + "\"channel\":\"orderbook_3\""
                                     + "}");
                    }catch(Exception e){
                        e.printStackTrace();
                    }
                }
                public void onMessage(de.roderick.weberknecht.WebSocketMessage message){
                    System.out.println("--received message: " + message.getText());
                }
                public void onClose(){
                    System.out.println("--close: ");
                }
            };
            socket2.setEventHandler(handler);
        }catch(Exception e){
            e.printStackTrace();
        }
        
    }
    
    public void connect(){
        try{
            socket2.connect();
        }catch(Exception e){
            e.printStackTrace();
        }
    }
    
}