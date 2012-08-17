package com.tradebits.exchange;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import org.eclipse.jetty.websocket.*;
import java.nio.ByteBuffer;


public class Intersango extends AExchange {
    
    WebSocketClient socket;
    
    public Intersango(){
        super("Intersango");
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
            this.log("time: " + time);
            String socketInfo = "";
            
            try {

                // Construct data
                String data = URLEncoder.encode("t", "UTF-8") + "=" + URLEncoder.encode(time, "UTF-8");
                
                // Send data
                URL url = new URL("https://socketio.intersango.com:8080/socket.io/1/");
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
            
            this.log("output: " + socketInfo);
            
            String urlFragment = socketInfo.substring(0, socketInfo.indexOf(":"));
            this.log(urlFragment);
            
            this.log("sleeping");
            Thread.sleep(300);
            this.log("sleeping2");
            
            String wsURL = "wss://socketio.intersango.com:8080/socket.io/1/websocket/" + urlFragment;
//            wsURL = "wss://api.icbit.se/socket.io/1/websocket/54-l-ILG0xGMm41h1WQX?AuthKey=uCCpUYpoecNEWoCyMxsTdAcjHbw7EcQW8gMrtrF8xFagutAjnNFQT8Hb2Jcu5GDUJvJsRP8uSmKo6mhetr1q2OSXkpxlOj6SDJbabqwzcMXtEbBuHoN4GIpvnMPYbutO&UserId=743";

            this.log(wsURL);
            
            WebSocketClientFactory factory = new WebSocketClientFactory();
            factory.start();
            
            WebSocketClient client = factory.newWebSocketClient();
            // Configure the client
            
            final WebSocket.Connection socketConnection = client.open(new URI(wsURL), new WebSocket.OnTextMessage(){
                
                
                protected Connection foobar;
                
                public void onOpen(Connection socketConnection)
                {
                    Intersango.this.log("open");
                    // open notification
                    foobar = socketConnection;
                }
                
                public void onClose(int closeCode, String message)
                {
                    Intersango.this.log("close");
                    // close notification
                }
                
                public void onMessage(String data)
                {
                    Intersango.this.log("message: " + data);
                    // handle incoming message
                }
            }).get(5, TimeUnit.SECONDS);
//            connection.sendMessage("Hello World");

            Timer foo = new Timer();
            foo.scheduleAtFixedRate(new TimerTask(){
                public boolean cancel(){
                    return false;
                }
                public void run(){
                    String msg = "2::";
                    Intersango.this.log("~h~");
                    try{
                        socketConnection.sendMessage(msg);
                    }catch(Exception e){
                        e.printStackTrace();
                    }
                }
                public long scheduledExecutionTime(){
                    return 0;
                }
            }, 1000, 15000);
            
            /*
            // /icbit
            this.log("sleeping");
            Thread.sleep(1000);
            this.log("sleeping2");

            // subscribe
            //
            // this guy doesn't seem to work, i get all currency pairs back
            // all the time :(
            //
            // i'll have to filter them according to:
            // https://intersango.com/api.php
            //
            // 1 = BTC:GBP
            // 2 = BTC:EUR
            // 3 = BTC:USD
            // 4 = BTC:PLN
            //
            // https://intersango.com/orderbook.php?currency_pair_id=3
            // has a good graph on how to interpret the data
            String msg = "5:::{\"name\":\"depth\",\"args\":[" +
                                         "{\"currency_pair_id\":\"3\"}" +
                                         "]}";
            this.log("sending: " + msg);
            socketConnection.sendMessage(msg);
            */
        }catch(Exception e){
            e.printStackTrace();
        }
    }
    
    public void connect(){
//        socket.connect();
    }
    
    public boolean isConnected(){
        return false;
    }
    
    
    /** AExchange **/
    
    public boolean isCurrencySupported(CURRENCY curr){
        return curr == CURRENCY.BTC || 
            curr == CURRENCY.EUR ||
            curr == CURRENCY.GBP ||
            curr == CURRENCY.USD ||
            curr == CURRENCY.PLN;
    }
}