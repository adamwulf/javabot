package com.tradebits;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import org.eclipse.jetty.websocket.*;
import java.nio.ByteBuffer;


public class Intersango2 {
    
    WebSocketClient socket;
    
    public Intersango2(){
        try{
            
            //
            // i loaded this in the web browser. to help find urls
            //
            // i had to get a fresh API key, they seem to be for single sessions only
            //
            // i also had to find the tKCAqhf-N03a546H1WOp of the url below through the web browser.
            // this can hopefully be found by GETing a url from the web server.
            //
            // that url fragment can be fetched from:
            // https://api.icbit.se/socket.io/1/?AuthKey=uCCpUYpoecNEWoCyMxsTdAcjHbw7EcQW8gMrtrF8xFagutAjnNFQT8Hb2Jcu5GDUJvJsRP8uSmKo6mhetr1q2OSXkpxlOj6SDJbabqwzcMXtEbBuHoN4GIpvnMPYbutO&UserId=743&t=1344970658118
            // with the current timestamp.
            //
            // i should test this with my browser off in case the browser + this client interfere with each other.
            //
            // these may only stay alive for ~30s or so before the connection dies.
            //
            // especially if the browser is dead, it resets the connection and it closes immediately
            
            String time = new Long(new Date().getTime() + 30000).toString();
            System.out.println("1344970658118");
            System.out.println(time);
            String socketInfo = "";
            
            try {
                String authKey = "uCCpUYpoecNEWoCyMxsTdAcjHbw7EcQW8gMrtrF8xFagutAjnNFQT8Hb2Jcu5GDUJvJsRP8uSmKo6mhetr1q2OSXkpxlOj6SDJbabqwzcMXtEbBuHoN4GIpvnMPYbutO";
                String userID = "743";

                // Construct data
                String data = URLEncoder.encode("AuthKey", "UTF-8") + "=" + URLEncoder.encode(authKey, "UTF-8");
                data += "&" + URLEncoder.encode("UserId", "UTF-8") + "=" + URLEncoder.encode(userID, "UTF-8");
                data += "&" + URLEncoder.encode("t", "UTF-8") + "=" + URLEncoder.encode(time, "UTF-8");
                
                // Send data
                URL url = new URL("https://api.icbit.se/socket.io/1/");
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
            }
            /*
            URL url = new URL("https://api.icbit.se/socket.io/1/?AuthKey=uCCpUYpoecNEWoCyMxsTdAcjHbw7EcQW8gMrtrF8xFagutAjnNFQT8Hb2Jcu5GDUJvJsRP8uSmKo6mhetr1q2OSXkpxlOj6SDJbabqwzcMXtEbBuHoN4GIpvnMPYbutO&UserId=743&t=" + time);
            URLConnection connection2 = url.openConnection();

            String socketInfo = null;
            try {
                socketInfo = new java.util.Scanner(connection2.getInputStream()).useDelimiter("\\A").next();
            } catch (java.util.NoSuchElementException e) {
                socketInfo = "";
            }
            */
            
            System.out.println("output: " + socketInfo);
            
            String urlFragment = socketInfo.substring(0, socketInfo.indexOf(":"));
            System.out.println(urlFragment);
            
            System.out.println("sleeping");
            Thread.sleep(3000);
            System.out.println("sleeping2");
            
            String wsURL = "wss://api.icbit.se:443/socket.io/1/websocket/" + urlFragment + "?AuthKey=uCCpUYpoecNEWoCyMxsTdAcjHbw7EcQW8gMrtrF8xFagutAjnNFQT8Hb2Jcu5GDUJvJsRP8uSmKo6mhetr1q2OSXkpxlOj6SDJbabqwzcMXtEbBuHoN4GIpvnMPYbutO&UserId=743";
//            wsURL = "wss://api.icbit.se/socket.io/1/websocket/54-l-ILG0xGMm41h1WQX?AuthKey=uCCpUYpoecNEWoCyMxsTdAcjHbw7EcQW8gMrtrF8xFagutAjnNFQT8Hb2Jcu5GDUJvJsRP8uSmKo6mhetr1q2OSXkpxlOj6SDJbabqwzcMXtEbBuHoN4GIpvnMPYbutO&UserId=743";

            System.out.println(wsURL);
            
            WebSocketClientFactory factory = new WebSocketClientFactory();
            factory.start();
            
            WebSocketClient client = factory.newWebSocketClient();
            // Configure the client
            
            final WebSocket.Connection socketConnection = client.open(new URI(wsURL), new WebSocket.OnTextMessage()
                                                              {
                public void onOpen(Connection socketConnection)
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
//            connection.sendMessage("Hello World");

            // /icbit
            System.out.println("sleeping");
            Thread.sleep(1000);
            System.out.println("sleeping2");
            System.out.println("should have 1:: by now");
            String msg = "1::/icbit";
            System.out.println("sending: " + msg);
            socketConnection.sendMessage(msg);

            // wait for confirm of /icbit
            System.out.println("sleeping");
            Thread.sleep(1000);
            System.out.println("sleeping2");
            
            // subscribe
            msg = "5::/icbit:{\"name\":\"message\",\"args\":[" +
                                         "{\"op\":\"subscribe\",\"channel\":\"orderbook_3\"}" +
                                         "]}";
            System.out.println("sending: " + msg);
            socketConnection.sendMessage(msg);
            System.out.println("sleeping");
            Thread.sleep(60000);
            System.out.println("sleeping2");

        }catch(Exception e){
        }
    }
    
    public void connect(){
//        socket.connect();
    }
    
}