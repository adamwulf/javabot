package com.tradebits.exchange;

import java.net.*;
import java.util.*;
import com.kaazing.gateway.client.html5.WebSocket;
import com.kaazing.gateway.client.html5.WebSocketAdapter;
import com.kaazing.gateway.client.html5.WebSocketEvent;
import org.java_websocket.client.*;
import org.java_websocket.drafts.*;
import org.java_websocket.handshake.*;
import java.nio.ByteBuffer;


public class MtGox extends AExchange {
    
    WebSocketClient socket;
    
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
                    MtGox.this.log(message);
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
        }
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