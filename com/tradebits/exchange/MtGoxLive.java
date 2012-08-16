package com.tradebits.exchange;

import java.io.*;
import java.net.*;
import java.util.*;
import com.tradebits.*;
import com.kaazing.gateway.client.html5.WebSocket;
import com.kaazing.gateway.client.html5.WebSocketAdapter;
import com.kaazing.gateway.client.html5.WebSocketEvent;
import org.java_websocket.client.*;
import org.java_websocket.drafts.*;
import org.java_websocket.handshake.*;
import java.nio.ByteBuffer;
import org.json.*;


public class MtGoxLive extends AExchange {
    
    SocketHelper socket;
    
    LinkedList<JSONObject> cachedDepthData = new LinkedList<JSONObject>();
    boolean socketIsConnected = false;
    
    public MtGoxLive(){
        super("MtGoxLive");
        
        socket = new SocketHelper("http://mtgoxlive.com:3457/socket.io/1/", "ws://mtgoxlive.com:3457/socket.io/1/websocket/");
        socket.setListener(new ISocketHelperListener(){
            
            public void onOpen(SocketHelper socket){
                System.out.println("OPEN");
                socketIsConnected = true;
            }
            
            public void onClose(SocketHelper socket, int closeCode, String message){
                System.out.println("CLOSE");
            }
            
            public void onMessage(SocketHelper socket, String originalData){
                String data = originalData;
                if(data.equals("1::") || data.equals("2::")){
                    // just a connection message
                    // or heartbeat from the server
                    return;
                }
                if(data.indexOf(":::") > -1){
                    data = data.substring(data.indexOf(":::") + 3);
                }
                
                
                try{
                    System.out.println(data.substring(0, Math.min(300, data.length())));

                    JSONArray arrData = new JSONArray(data);
                    System.out.println("length of data " + arrData.length());
                    
                    if(arrData.getString(0).equals("depth")){
                        String depthData = arrData.getString(1);
                        depthData = URLDecoder.decode(depthData, "UTF-8");
                        depthData = depthData.substring(depthData.indexOf("{"));
                        originalData = depthData;
                        System.out.println(depthData.substring(0, Math.min(300, depthData.length())));

                        JSONObject depthObj = new JSONObject(depthData);
                        
                    }
                    
                    
                    
                }catch(Exception e){
                    e.printStackTrace();
                    System.out.println("========");
                    System.out.println(originalData);
                    System.out.println("========");
                }
            }
            
            public void onHeartbeatSent(SocketHelper socket){
                //
                // update our depth data as often as we heartbeat
                System.out.println("~h~");
            }
            
        });
    }
    
    public void connect(){
        socket.connect();
        
        Timer foo = new Timer();
        foo.scheduleAtFixedRate(new TimerTask(){
            public boolean cancel(){
                return false;
            }
            public void run(){
                if(socketIsConnected){
                    String msg = "3:::{\"currency\":\"USD\"}";
                    socket.send(msg);
                }
            }
            public long scheduledExecutionTime(){
                return 0;
            }
        }, 1000, 200000);
    }
    

    /** AExchange **/
    
    public boolean isCurrencySupported(CURRENCY curr){
        return curr == CURRENCY.BTC ||
            curr == CURRENCY.USD ||
            curr == CURRENCY.AUD ||
            curr == CURRENCY.CAD ||
            curr == CURRENCY.CHF ||
            curr == CURRENCY.CNY ||
            curr == CURRENCY.DKK ||
            curr == CURRENCY.EUR ||
            curr == CURRENCY.GBP ||
            curr == CURRENCY.HKD ||
            curr == CURRENCY.JPY ||
            curr == CURRENCY.NZD ||
            curr == CURRENCY.PLN ||
            curr == CURRENCY.RUB ||
            curr == CURRENCY.SEK ||
            curr == CURRENCY.SGD ||
            curr == CURRENCY.THB;
    }
}