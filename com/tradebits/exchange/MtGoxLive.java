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
    boolean depthDataIsInitialized = false;
    
    public MtGoxLive(){
        super("MtGoxLive");
        
        socket = new SocketHelper("http://mtgoxlive.com:3457/socket.io/1/", "ws://mtgoxlive.com:3457/socket.io/1/websocket/");
    }
    
    public void connect(){
        socket.connect();
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