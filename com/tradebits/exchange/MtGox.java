package com.tradebits.exchange;

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
import org.json.*;


public class MtGox extends AExchange {
    
    WebSocketClient socket;
    
    LinkedList<JSONObject> cachedDepthData = new LinkedList<JSONObject>();
    boolean depthDataIsInitialized = false;
    
    public MtGox(){
        super("MtGox");
        try{
            String wsURL = "ws://websocket.mtgox.com/mtgox";
            socket = new WebSocketClient(new URI(wsURL), new Draft_76()){
                @Override
                public void onOpen( ServerHandshake handshakedata ){
//                    System.out.println("onOpen");
                }
                @Override
                public void onMessage( String message ){
                    MtGox.this.processMessage(message);
                }
                @Override
                public void onClose( int code, String reason, boolean remote ){
//                    System.out.println("onClose");
                }
                @Override
                public void onError( Exception ex ){
                    System.out.println("onError");
                    ex.printStackTrace();
                }
                @Override
                public void onMessage( ByteBuffer bytes ) {
//                    System.out.println("onMessage");
                }
            };
        }catch(Exception e){
            e.printStackTrace();
        }
    }
    
    public void connect(){
        socket.connect();
        this.loadInitialDepthData(CURRENCY.USD);
    }
    
    
    
    
    
    
    /** helper processing methods **/
    
    private void loadInitialDepthData(CURRENCY curr){
        (new Thread(this.getName() + " First Depth Fetch"){
            public void run(){
                JSONObject depthData = null;
                while(depthData == null){
                    try {
                        
                        String depthString = "";
                        // Send data
                        URL url = new URL("https://mtgox.com/api/1/BTCUSD/fulldepth");
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
                    MtGox.this.log("got ask data " + asks.length());
                    MtGox.this.log("got bid data " + bids.length());
                    
                    synchronized(MtGox.this){
                        
                        for(int i=0;i<asks.length();i++){
                            JSONObject ask = asks.getJSONObject(i);
                            MtGox.this.setAskData(ask);
                        }
                        
                        
                        
                    }
                    
                }catch(Exception e){
                    e.printStackTrace();
                }
                
            }
        }).start();
    }
    
    
    
    protected void processMessage(String messageText){
        try{
            JSONObject msg = new JSONObject(messageText);
            if(msg.getString("private").equals("ticker")){
//                this.log("ticker data" + "\n" + messageText);
            }else if(msg.getString("private").equals("depth")){
                this.processDepthData(msg);
            }else if(msg.getString("private").equals("trade")){
//                this.log("trade data" + "\n" + messageText);
            }else{
                this.log("unknown feed type: " + msg.getString("private"));
            }
        }catch(JSONException e){
            e.printStackTrace();
        }
    }
    
    protected void processDepthData(JSONObject depthMessage) throws JSONException{
        synchronized(this){
            String type = depthMessage.getJSONObject("depth").getString("type_str");
            if(depthDataIsInitialized){
                this.log("depth data" + "\n" + depthMessage);
                if(type.equals("ask")){
                    
                }else if(type.equals("bid")){
                    
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