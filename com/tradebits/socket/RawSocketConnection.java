package com.tradebits.socket;



import java.io.*;
import java.net.*;
import java.util.*;
import com.tradebits.*;
import org.json.*;

public class RawSocketConnection implements ISocketHelper{
    
    private String host;
    private int port;
    
    private boolean connected;
    private boolean connecting;
    private ISocketHelperListener listener;
    private SocketThread socketThread;
    private Log logFile;
    
    public RawSocketConnection(String host, int port, Log logFile){
        this.host = host;
        this.port = port;
        this.logFile = logFile;
        connected = false;
        connecting = false;
    }
    
    public boolean isConnected(){
        return connected;
    }
    
    public boolean isConnecting(){
        return connecting && !connected;
    }
    
    public void disconnect(){
        socketThread.disconnect();
    }
    
    public void connect(){
        if(!this.isConnected() && !this.isConnecting()){
            connecting = true;
            socketThread = new SocketThread(host, port);
            socketThread.start();
        }
    }
    
    public void send(String msg){
        // noop
    }
    
    /** Listener **/
    
    public void setListener(ISocketHelperListener listener){
        this.listener = listener;
    }
    
    public ISocketHelperListener getListener(){
        return this.listener;
    }
    
    /**
     * this thread will connect to the intersango
     * realtime API https://intersango.com/api.php
     */
    protected class SocketThread extends Thread{
        String host;
        int port;
        protected BufferedReader in;
        protected Socket echoSocket;
        public SocketThread(String host, int port){
            super(host + ":" + port + " Socket Thread");
            this.host = host;
            this.port = port;
        }
        public void disconnect(){
            try{
                if(in !=null) in.close();
            }catch(IOException e){ }
            connected = false;
        }
        public void run(){
            echoSocket = null;
            in = null;
            
            try {
                echoSocket = new Socket(host, port);
                in = new BufferedReader(new InputStreamReader(echoSocket.getInputStream()));
            } catch (Exception e) {
                logFile.log("Couldn't get connection to: " + host);
                connected = false;
                connecting = false;
                RawSocketConnection.this.getListener().onError(RawSocketConnection.this, e.toString() + ": " + Arrays.toString(e.getStackTrace()));
                return;
            }
            
            processInputStream();
        }
        
        protected void processInputStream(){
            String jsonLine;
            
            connected = true;
            connecting = false;
            RawSocketConnection.this.getListener().onOpen(RawSocketConnection.this);
            
            try{
                while ((jsonLine = in.readLine()) != null) {
                    try{
                        // the 5::: is to fake the websocket response
                        // and the json modification is to mimic the
                        // intersango websocket, which i would prefer to use
                        // but is unofficial and not stable
                        JSONArray dataArr = new JSONArray(jsonLine);
                        String name = dataArr.getString(0);
                        JSONObject arg0 = dataArr.getJSONObject(1);
                        JSONArray args = new JSONArray();
                        args.put(arg0);
                        RawSocketConnection.this.getListener().onMessage(RawSocketConnection.this, "5:::{ \"name\": \"" + name + "\", " +
                                                                         "\"args\":" + args.toString() + "}");
                    }catch(JSONException e){
                        RawSocketConnection.this.getListener().onError(RawSocketConnection.this, jsonLine);
                    }
                }
            }catch(IOException e){ }
            try{ in.close(); }catch(IOException e){ }
            try{ echoSocket.close(); }catch(IOException e){ }
            
            connected = false;
            RawSocketConnection.this.getListener().onClose(RawSocketConnection.this, 0, null);
        }
    }
}