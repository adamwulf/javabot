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
            String topLine = "";

            URL url = new URL("https://api.icbit.se/socket.io/1/?t=1344644213395");
            URLConnection connection = url.openConnection();
            connection.addRequestProperty("Connection", "Upgrade");
            connection.addRequestProperty("Upgrade", "websocket");
            connection.addRequestProperty("Sec-WebSocket-Extensions", "x-webkit-deflate-frame");
            connection.addRequestProperty("Sec-WebSocket-Key", "FjPYWY3tVVrz8ls+HL9rdQ==");
            connection.addRequestProperty("Sec-WebSocket-Version", "13");
//            connection.addRequestProperty("(Key3)", "00:00:00:00:00:00:00:00");
  //          connection.addRequestProperty("Origin", "null");
            
            for(int i=0;;i++){
                String headerName = connection.getHeaderField(i);
                if(headerName == null) break;
                String headerValue = connection.getHeaderField(headerName);
                System.out.println(headerName + " " + headerValue);
                topLine += headerName + " " + headerValue + "\r\n";
            }
            
            topLine = "HTTP/1.1 101 OK null\r\n";
            topLine += "Connection:Upgrade\r\n";
            topLine += "Sec-WebSocket-Accept:+IA3T46RtrlXofloaPFyGv7tZ30=\r\n";
            topLine += "Upgrade:websocket\r\n";
            topLine += "(Challenge Response):00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00\r\n";
            
            topLine += "\r\n";
            
            InputStream response = connection.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(response));
            for (String line; (line = reader.readLine()) != null;) {
                topLine += line + "\r\n";
            }
            reader.close();
            
            System.out.println("done");
            
            System.out.println(topLine);
            
            topLine = topLine.replace("200", "101");
            
            String foo = topLine.substring(0, topLine.indexOf(":"));
                                            
            System.out.println(topLine);
                                          
                                            
                                            
            Long dt = new Long((new Date()).getTime() / 1000);
            socket2 = new de.roderick.weberknecht.WebSocketConnection(new URI("wss://api.icbit.se/socket.io/1/?t=" + dt.toString()));
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
            
            //
            // hoping this'll work
//            socket2.setLie(topLine + "\r\n");
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