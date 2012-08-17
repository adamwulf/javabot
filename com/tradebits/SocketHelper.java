

package com.tradebits;

import java.io.*;
import java.net.*;
import java.util.*;
import javax.net.ssl.*;
import java.security.cert.*;
import com.tradebits.exchange.*;
import java.util.concurrent.*;
import org.eclipse.jetty.websocket.*;
import org.apache.commons.lang3.*;
import org.json.*;

public class SocketHelper{
    
    private String httpURL;
    private String wsURLFragment;
    WebSocket.Connection socketConnection;
    private ISocketHelperListener listener;
    
    /**
     * creates a generic socket that will handshake
     * the input httpURL, and connect to the socket
     * at the wsURL (with its response from the handshake)
     */
    public SocketHelper(String httpURL, String wsURLFragment){
        this.httpURL = httpURL;
        this.wsURLFragment = wsURLFragment;
    }
    
    /**
     * green means go!
     * 
     * lets handshake and connect to the socket
     */
    public void connect(){
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

            String socketInfo = "";
            
            try {
                
                // Construct data
                String data = URLEncoder.encode("t", "UTF-8") + "=" + URLEncoder.encode(time, "UTF-8");
                
                // Send data
                URL url = new URL(httpURL);
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
                return;
            }
            
            
            String urlFragment = socketInfo.substring(0, socketInfo.indexOf(":"));

            Thread.sleep(300);
            
            String wsURL = wsURLFragment + urlFragment;
            
            WebSocketClientFactory factory = new WebSocketClientFactory();
            factory.start();
            
            WebSocketClient client = factory.newWebSocketClient();
            // Configure the client
            
            socketConnection = client.open(new URI(wsURL), new WebSocket.OnTextMessage(){
                
                public void onOpen(Connection aSocketConnection)
                {
                    SocketHelper.this.socketConnection = aSocketConnection;
                    SocketHelper.this.getListener().onOpen(SocketHelper.this);
                }
                
                public void onClose(int closeCode, String message)
                {
                    SocketHelper.this.getListener().onClose(SocketHelper.this, closeCode, message);
                    SocketHelper.this.socketConnection = null;
                }
                
                public void onMessage(String data)
                {
                    SocketHelper.this.getListener().onMessage(SocketHelper.this, data);
                }
            }).get(5, TimeUnit.SECONDS);
            
            socketConnection.setMaxTextMessageSize(2000000);
            
//            connection.sendMessage("Hello World");
            
            Timer foo = new Timer();
            foo.scheduleAtFixedRate(new TimerTask(){
                public boolean cancel(){
                    return false;
                }
                public void run(){
                    if(SocketHelper.this.socketConnection != null){
                        SocketHelper.this.send("2::");
                        SocketHelper.this.getListener().onHeartbeatSent(SocketHelper.this);
                    }
                }
                public long scheduledExecutionTime(){
                    return 0;
                }
            }, 1000, 15000);
            
        }catch(Exception e){
            e.printStackTrace();
        }
    }
    
    /** allow sending data over the socket **/
    
    public void send(String message){
        try{
            if(socketConnection != null){
                socketConnection.sendMessage(message);
            }
        }catch(Exception e){
            e.printStackTrace();
        }
    }
    
    
    /** Listener **/
    
    public void setListener(ISocketHelperListener listener){
        this.listener = listener;
    }
    
    public ISocketHelperListener getListener(){
        return this.listener;
    }
}