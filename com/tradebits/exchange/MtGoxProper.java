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
public class MtGoxProper extends AExchange {
    
    SocketHelper socket;
        
    private LinkedList<JSONObject> cachedDepthData = new LinkedList<JSONObject>();
    private boolean depthDataIsInitialized = false;
    boolean socketIsConnected = false;
    
    public MtGoxProper(){
        super("MtGoxProper");
        
        socket = new SocketHelper("https://socketio.mtgox.com/socket.io/1/", "wss://socketio.mtgox.com/socket.io/1/websocket/");
        socket.setListener(new ISocketHelperListener(){
            
            public void onOpen(SocketHelper socket){
                MtGoxProper.this.log("OPEN");
                socketIsConnected = true;
            }
            
            public void onClose(SocketHelper socket, int closeCode, String message){
                MtGoxProper.this.log("CLOSE");
            }
            
            String dataPrefix = "4::/mtgox:";
            public void onMessage(SocketHelper aSocket, String originalData){
                String data = originalData;
                if(data.equals("1::")){
                    // ask to connect to the mtgox channel
                    socket.send("1::/mtgox");
                    return;
                }else if(data.startsWith("1::")){
                    // just print to console, should be our
                    // confirmation of our /mtgox message above
                    MtGoxProper.this.log(data);
                    MtGoxProper.this.beginTimerForDepthData();
                    return;
                }else if(data.equals("2::")){
                    // just heartbeat from the server
                    return;
                }else if(data.startsWith(dataPrefix)){
                    data = data.substring(dataPrefix.length());
                    MtGoxProper.this.processMessage(data);
                    return;
                }
                
                MtGoxProper.this.log(data);
                
            }
            
            public void onHeartbeatSent(SocketHelper socket){
                //
                // update our depth data as often as we heartbeat
                MtGoxProper.this.log("~h~");
            }
            
        });
    }
    
    public void connect(){
        socket.connect();
    }
    
    public void beginTimerForDepthData(){
        Timer foo = new Timer();
        foo.scheduleAtFixedRate(new TimerTask(){
            public boolean cancel(){
                return false;
            }
            public void run(){
                if(socketIsConnected){
                    MtGoxProper.this.loadInitialDepthData(CURRENCY.USD);
                }
            }
            public long scheduledExecutionTime(){
                return 0;
            }
//        }, 15000, 240000);
        }, 480000, 10000);
    }
    

    /** helper processing methods **/
    
    protected void loadInitialDepthData(CURRENCY curr){
        (new Thread(this.getName() + " First Depth Fetch"){
            public void run(){
                //
                // ok
                // we're going to download the dept data 5 times
                //
                // and only after that we're going to re-run the
                // realtime data on top of it
                for(int j=0;j<1;j++){
                    
                    
                    JSONObject depthData = null;
                    while(depthData == null){
                        try {
                            
                            String depthString = "";
                            // Send data
                            URL url = new URL("https://mtgox.com/api/1/BTCUSD/depth");
                            URLConnection conn = url.openConnection();
                            
                            // Get the response
                            BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                            String line;
                            while ((line = rd.readLine()) != null) {
                                // Process line...
                                depthString += line + "\n";
                            }
                            rd.close();
                            
                            //
                            // ok, we have the string data,
                            // now parse it
                            if(depthString.length() > 0){
                                JSONObject parsedDepthData = new JSONObject(depthString);
                                if(parsedDepthData != null &&
                                   parsedDepthData.getString("result").equals("success")){
                                    depthData = parsedDepthData;
                                }
                            }
                            
                        }catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    
                    
                    try{
                        
                        JSONArray asks = depthData.getJSONObject("return").getJSONArray("asks");
                        JSONArray bids = depthData.getJSONObject("return").getJSONArray("bids");
                        MtGoxProper.this.log("got ask data " + asks.length());
                        MtGoxProper.this.log("got bid data " + bids.length());
                        
                        synchronized(MtGoxProper.this){
                            MtGoxProper.this.log("-- Processing Depth Data");
                            for(int i=0;i<asks.length();i++){
                                JSONObject ask = asks.getJSONObject(i);
                                JSONObject cachedData = new JSONObject();
                                cachedData.put("price", ask.getDouble("price"));
                                cachedData.put("volume", ask.getDouble("amount"));
                                cachedData.put("volume_int", ask.getDouble("amount_int"));
                                cachedData.put("stamp",new Date(ask.getLong("stamp") / 1000));
                                JSONObject formerlyCached = MtGoxProper.this.getAskData(ask.getDouble("price"));
                                if(!depthDataIsInitialized || formerlyCached == null){
                                    MtGoxProper.this.setAskData(cachedData);
                                }else{
                                    JSONArray log = formerlyCached.getJSONArray("log");
                                    JSONObject logItem = new JSONObject();
                                    logItem.put("volume", cachedData.getDouble("volume"));
                                    logItem.put("volume_int", cachedData.getDouble("volume_int"));
                                    logItem.put("stamp", cachedData.get("stamp"));
                                    logItem.put("check", true);
                                    log.put(logItem);
                                    formerlyCached.put("log", log);
                                }
                                if(depthDataIsInitialized){
                                    if(formerlyCached != null){
                                        if(formerlyCached.getDouble("volume") != cachedData.getDouble("volume")){
                                            MtGoxProper.this.log("Different volume for " + cachedData.getDouble("price")
                                                                     + ": " + formerlyCached.getDouble("volume")
                                                                     + " vs " + cachedData.getDouble("volume") + " with log "
                                                                     + formerlyCached.get("log"));
                                        }
                                    }
                                }
                            }
                            if(!depthDataIsInitialized){
                                //
                                // for now, we're only going to compare
                                // values for anything after the first load
                                for(int i=0;i<bids.length();i++){
                                    JSONObject bid = bids.getJSONObject(i);
                                    JSONObject cachedData = new JSONObject();
                                    cachedData.put("price", bid.getDouble("price"));
                                    cachedData.put("volume", bid.getDouble("amount"));
                                    cachedData.put("volume_int", bid.getDouble("amount_int"));
                                    cachedData.put("stamp",new Date(bid.getLong("stamp") / 1000));
                                    MtGoxProper.this.setBidData(cachedData);
                                }
                            }
                            MtGoxProper.this.log("Done Processing Depth Data --");
                        }  
                    }catch(Exception e){
                        e.printStackTrace();
                    }
                }
                
                synchronized(MtGoxProper.this){
                    try{
                        MtGoxProper.this.log("-- Replay Depth Stream");
                        depthDataIsInitialized = true;
                        while(cachedDepthData.size() > 0){
                            JSONObject obj = cachedDepthData.removeFirst();
                            MtGoxProper.this.processDepthData(obj);
                        }
                        MtGoxProper.this.log("Done Replaying Depth Data --"); 
                    }catch(Exception e){
                        e.printStackTrace();
                    }
                }
                
                
            }
        }).start();
    }
    
    
    
    
    protected void processMessage(String messageText){
        try{
            JSONObject msg = new JSONObject(messageText);
            if(msg.getString("op").equals("private")){
                if(msg.getString("private").equals("ticker")){
//                    this.log("ticker data" + "\n" + messageText);
                }else if(msg.getString("private").equals("depth")){
                    this.processDepthData(msg);
                }else if(msg.getString("private").equals("trade")){
//                    this.log("trade data" + "\n" + messageText);
                }else{
                    this.log("unknown feed type: " + msg.getString("private"));
                }
            }
        }catch(JSONException e){
            e.printStackTrace();
        }catch(ExchangeException e){
            e.printStackTrace();
        }
    }
    
    protected void processDepthData(JSONObject depthMessage) throws JSONException, ExchangeException{
        synchronized(this){
            String type = depthMessage.getJSONObject("depth").getString("type_str");
            JSONObject depthData = depthMessage.getJSONObject("depth");
            JSONObject cachableData = new JSONObject();
            cachableData.put("price", depthData.getDouble("price"));
            cachableData.put("volume", depthData.getDouble("volume"));
            cachableData.put("volume_int", depthData.getDouble("volume_int"));
            long totalVol = depthData.getLong("total_volume_int");
            cachableData.put("stamp",new Date(depthData.getLong("now") / 1000));
            if(depthDataIsInitialized){
//                this.log("processing depth data" + "\n" + depthMessage);
                this.log("expecting new vol for " + depthData.getDouble("price") + " to be " + totalVol);
                if(type.equals("ask")){
                    this.updateAskData(cachableData);
                }else if(type.equals("bid")){
                    this.updateBidData(cachableData);
                }else{
                    throw new RuntimeException("unknown depth type: " + type);
                }
            }else{
                cachedDepthData.add(depthMessage);
                this.log("caching " + type + " (" + cachedDepthData.size() + ")");
            }
        }
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