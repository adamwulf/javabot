package com.tradebits.exchange;


import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.TimeoutException;
import com.tradebits.*;
import com.tradebits.socket.*;
import org.json.*;



public class Intersango2 extends AExchange{
    
    CURRENCY currencyEnum;
    ASocketHelper socket;
    ASocketFactory socketFactory;
    Log rawDepthDataLog;
    Log rawSocketMessagesLog;
    boolean socketIsConnected = false;
    boolean socketHasReceivedAnyMessage = false;
    boolean hasLoadedDepthDataAtLeastOnce = false;
    int intersangoCurrencyEnum;
            
    /**
     * https://intersango.com/api.php
     * 
     * 1 = BTC:GBP
     * 2 = BTC:EUR
     * 3 = BTC:USD
     * 4 = BTC:PLN
     */
    public Intersango2(ASocketFactory factory, CURRENCY curr){
        super("Intersango2");
        this.currencyEnum = curr;
        this.socketFactory = factory;
        if(curr.equals(CURRENCY.GBP)){
            intersangoCurrencyEnum = 1;
        }else if(curr.equals(CURRENCY.EUR)){
            intersangoCurrencyEnum = 2;
        }else if(curr.equals(CURRENCY.USD)){
            intersangoCurrencyEnum = 3;
        }else if(curr.equals(CURRENCY.PLN)){
            intersangoCurrencyEnum = 4;
        }
        try{
            rawDepthDataLog = new NullLog(curr + " Depth");
            rawSocketMessagesLog = new NullLog(curr + " Socket");
        }catch(IOException e){ }
    }
    
    
    public String getName(){
        return super.getName() + " " + this.getCurrency();
    }
    
    public void disconnect(){
        if(socket != null) socket.disconnect();
        socket = null;
        socketIsConnected = false;
        socketHasReceivedAnyMessage = false;
        hasLoadedDepthDataAtLeastOnce = false;
    }
    
    public void connect(){
        try{
            this.log("Connecting...");
            if(!this.isConnected()){
                
                
                //
                // now, connect to the realtime feed
                
                this.socket = this.socketFactory.getSocketHelperFor("https://socketio.intersango.com:8080/socket.io/1/", "wss://socketio.intersango.com:8080/socket.io/1/websocket/");
                socket.setListener(new ISocketHelperListener(){
                    
                    public void onOpen(ASocketHelper socket){
                        Intersango2.this.log("OPEN");
                        socketIsConnected = true;
                    }
                    
                    public void onClose(ASocketHelper socket, int closeCode, String message){
                        Intersango2.this.log("CLOSE");
                        socketIsConnected = false;
                        // if this flag is still true,
                        // then MtGox disconnect() has
                        // not been called
                        Intersango2.this.disconnect();
                    }
                    
                    public void onError(ASocketHelper socket, String message){
                        // noop
                    }
                    
                    public void onMessage(ASocketHelper aSocket, String data){
                        try{
                            String dataPrefix = "5:::";
                            if(data.startsWith("1::")){
                                // socket is saying hello
                                return;
                            }else if(data.equals("2::")){
                                // just heartbeat from the server
                                return;
                            }else if(data.startsWith(dataPrefix)){
                                data = data.substring(dataPrefix.length());
                                Intersango2.this.processMessage(data);
                                return;
                            }
                            Intersango2.this.log(data);
                        }catch(Exception e){
                            aSocket.disconnect();
                            socketIsConnected = false;
                            Intersango2.this.disconnect();
                        }
                    }
                    
                    public void onHeartbeatSent(ASocketHelper socket){
                        //
                        // update our depth data as often as we heartbeat
//                        Intersango2.this.log("~h~");
                    }
                });
            }
            socket.connect();
        }catch(Exception e){
            e.printStackTrace();
            if(socket != null){
                socket.setListener(null);
            }
            socketIsConnected = false;
            this.disconnect();
        }
    }
    
    protected void processMessage(String messageText){
        try{
            JSONObject msg = new JSONObject(messageText);
            socketHasReceivedAnyMessage = true;
            if(msg.getString("name").equals("orderbook")){
                hasLoadedDepthDataAtLeastOnce = true;
                this.log("got depth table: " + messageText);
            }else if(msg.getString("name").equals("depth")){
                this.log("got depth update: " + messageText);
            }else if(msg.getString("name").equals("tickers")){
                // ignore
            }else{
                this.log("UNKNOWN MESSAGE: " + messageText);
            }
        }catch(Exception e){
            socket.disconnect();
            socketIsConnected = false;
            this.disconnect();
            e.printStackTrace();
        }
    }
    
    public boolean isConnected(){
        return socketIsConnected && socketHasReceivedAnyMessage;
    }
    
    public boolean isOffline(){
        return !socketIsConnected;
    }
    
    public boolean isConnecting(){
        return socketIsConnected && !socketHasReceivedAnyMessage;
    }
    
    public CURRENCY getCurrency(){
        return currencyEnum;
    }
    
    
    /** AExchange **/
    
    /**
     * https://intersango.com/api.php
     * 
     * 1 = BTC:GBP
     * 2 = BTC:EUR
     * 3 = BTC:USD
     * 4 = BTC:PLN
     */
    public boolean isCurrencySupported(CURRENCY curr){
        return curr == CURRENCY.BTC || 
            curr == CURRENCY.EUR ||
            curr == CURRENCY.GBP ||
            curr == CURRENCY.USD ||
            curr == CURRENCY.PLN;
    }
}