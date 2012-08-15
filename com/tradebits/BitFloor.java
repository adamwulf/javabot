package com.tradebits;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import org.eclipse.jetty.websocket.*;
import java.nio.ByteBuffer;

/**
 * The BitFloor api doesn't let me subscribe to the market data, so i need to 
 * actually request it every X seconds.
 * 
 * REST should be used to place orders: https://bitfloor.com/docs/api
 * 
 * these orders will show up as order_open and order_done events
 */
public class BitFloor extends AExchange {
    
    WebSocketClient socket;
    
    public BitFloor(){
        super("BitFloor");
        try{
            String time = new Long(new Date().getTime() + 30000).toString();
            System.out.println("1344970658118");
            System.out.println(time);
            String socketInfo = "";
            final String authKey = "uCCpUYpoecNEWoCyMxsTdAcjHbw7EcQW8gMrtrF8xFagutAjnNFQT8Hb2Jcu5GDUJvJsRP8uSmKo6mhetr1q2OSXkpxlOj6SDJbabqwzcMXtEbBuHoN4GIpvnMPYbutO";
            final String userID = "743";
            
            try {

                // Construct data
                String data = URLEncoder.encode("t", "UTF-8") + "=" + URLEncoder.encode(time, "UTF-8");
                
                // Send data
                URL url = new URL("https://feed.bitfloor.com/socket.io/1/");
                URLConnection conn = url.openConnection();
                conn.setDoOutput(true);
                OutputStreamWriter wr = new OutputStreamWriter(conn.getOutputStream());
                wr.write(data);
                wr.flush();
                
                // Get the response
                BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                String line;
                while ((line = rd.readLine()) != null) {
                    // Process line...
                    socketInfo += line + "\n";
                }
                wr.close();
                rd.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
            
            System.out.println("output: " + socketInfo);
            
            String urlFragment = socketInfo.substring(0, socketInfo.indexOf(":"));
            System.out.println(urlFragment);
            
            System.out.println("sleeping");
            Thread.sleep(3000);
            System.out.println("sleeping2");
            
            String wsURL = "wss://bitfloor.com/socket.io/1/websocket/" + urlFragment;
//            wsURL = "wss://api.icbit.se/socket.io/1/websocket/54-l-ILG0xGMm41h1WQX?AuthKey=uCCpUYpoecNEWoCyMxsTdAcjHbw7EcQW8gMrtrF8xFagutAjnNFQT8Hb2Jcu5GDUJvJsRP8uSmKo6mhetr1q2OSXkpxlOj6SDJbabqwzcMXtEbBuHoN4GIpvnMPYbutO&UserId=743";

            System.out.println(wsURL);
            
            WebSocketClientFactory factory = new WebSocketClientFactory();
            factory.start();
            
            WebSocketClient client = factory.newWebSocketClient();
            // Configure the client
            
            final WebSocket.Connection socketConnection = client.open(new URI(wsURL), new WebSocket.OnTextMessage(){
                
                
                protected Connection foobar;
                
                public void onOpen(Connection socketConnection)
                {
                    System.out.println("open");
                    // open notification
                    foobar = socketConnection;
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
                    
                    if(data.equals("1::")){
                        try{
                            System.out.println("just got 1::");
                            String msg = "1::/1";
                            System.out.println("sending: " + msg);
                            foobar.sendMessage(msg);
                        }catch(Exception e){
                            e.printStackTrace();
                        }
                    }
                }
            }).get(5, TimeUnit.SECONDS);
//            connection.sendMessage("Hello World");

            Timer foo = new Timer();
            foo.scheduleAtFixedRate(new TimerTask(){
                public boolean cancel(){
                    return false;
                }
                public void run(){
                    System.out.println("~h~");
                    try{
                        String msg = "2::";
                        socketConnection.sendMessage(msg);
                        msg = "5::/1:{\"name\":\"book\"}";
                        socketConnection.sendMessage(msg);
                    }catch(Exception e){
                        e.printStackTrace();
                    }
                }
                public long scheduledExecutionTime(){
                    return 0;
                }
            }, 1000, 15000);
            
            
            // /icbit
            System.out.println("sleeping");
            Thread.sleep(1000);
            System.out.println("sleeping2");

            // wait for confirm of /icbit
            System.out.println("sleeping");
            Thread.sleep(1000);
            System.out.println("sleeping2");
            
            // subscribe
            String msg = "5::/1:{\"name\":\"book\"}";
            System.out.println("sending: " + msg);
            socketConnection.sendMessage(msg);
            System.out.println("sleeping");
            Thread.sleep(600000);
            System.out.println("sleeping2");

        }catch(Exception e){
        }
    }
    
    public void connect(){
//        socket.connect();
    }
    
}