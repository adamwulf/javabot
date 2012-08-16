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

//
//
// TODO: this doesn't remove keys from the data after each refresh
public class MtGoxLive extends AExchange {
    
    SocketHelper socket;
    
    LinkedList<JSONObject> cachedDepthData = new LinkedList<JSONObject>();
    boolean socketIsConnected = false;
    
    boolean hasEverLoadedData = false;
    
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

                    JSONArray arrData = new JSONArray(data);

                    //
                    // only process depth data,
                    // ignore trade history data and metadata, etc
                    if(arrData.getString(0).equals("depth")){
                        String depthDataStr = arrData.getString(1);
                        depthDataStr = URLDecoder.decode(depthDataStr, "UTF-8");
                        depthDataStr = depthDataStr.substring(depthDataStr.indexOf("{"));
                        originalData = depthDataStr;
                        JSONObject depthData = new JSONObject(depthDataStr);
                        
                        //
                        // ok, now i have the depth data
                        // let's process it
                        try{
                            JSONArray asks = depthData.getJSONArray("asks");
                            JSONArray bids = depthData.getJSONArray("bids");
                            MtGoxLive.this.log("got ask data " + asks.length());
                            MtGoxLive.this.log("got bid data " + bids.length());
                            
                            synchronized(MtGoxLive.this){
                                MtGoxLive.this.log("-- Processing Depth Data");
                                for(int i=0;i<asks.length();i++){
                                    JSONArray ask = asks.getJSONArray(i);
                                    JSONObject cachedData = new JSONObject();
                                    cachedData.put("price", ask.getDouble(0));
                                    cachedData.put("volume_int", ask.getDouble(1));
                                    cachedData.put("stamp",new Date(ask.getLong(2) / 1000));
                                    JSONObject formerlyCachedData = MtGoxLive.this.getAskData(ask.getDouble(0));
                                    if(hasEverLoadedData){
                                        if(formerlyCachedData == null){
                                            System.out.println("ASK: price was null: " + ask.getDouble(0));
                                        }else if(formerlyCachedData.getDouble("volume_int") != cachedData.getDouble("volume_int")){
                                            System.out.println("ASK: updated volume for price: " + ask.getDouble(0));
                                        }else if(((Date)formerlyCachedData.get("stamp")).after(((Date)cachedData.get("stamp")))){
                                            System.out.println("ASK: updated stamp for price: " + ask.getDouble(0));
                                        }
                                    }
                                    
                                    MtGoxLive.this.setAskData(cachedData);
                                }
                                
                                for(int i=0;i<bids.length();i++){
                                    JSONArray bid = bids.getJSONArray(i);
                                    JSONObject cachedData = new JSONObject();
                                    cachedData.put("price", bid.getDouble(0));
                                    cachedData.put("volume_int", bid.getDouble(1));
                                    cachedData.put("stamp",new Date(bid.getLong(2) / 1000));
                                    JSONObject formerlyCachedData = MtGoxLive.this.getBidData(bid.getDouble(0));
                                    if(hasEverLoadedData){
                                        if(formerlyCachedData == null){
                                            System.out.println("BID: price was null: " + bid.getDouble(0));
                                        }else if(formerlyCachedData.getDouble("volume_int") != cachedData.getDouble("volume_int")){
                                            System.out.println("BID: updated volume for price: " + bid.getDouble(0));
                                        }else if(((Date)formerlyCachedData.get("stamp")).after(((Date)cachedData.get("stamp")))){
                                            System.out.println("BID: updated stamp for price: " + bid.getDouble(0));
                                        }
                                    }
                                    MtGoxLive.this.setBidData(cachedData);
                                }
                                MtGoxLive.this.log("Done Processing Depth Data --");
                                hasEverLoadedData = true;
                            }  
                        }catch(Exception e){
                            e.printStackTrace();
                        }
                        
                    }
                    
                }catch(Exception e){
                    e.printStackTrace();
                    System.out.println("========");
                    System.out.println(originalData.substring(0, Math.min(200, originalData.length())));
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
        }, 1000, 5000);
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