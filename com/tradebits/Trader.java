package com.tradebits;

import java.io.*;
import java.net.*;
import java.util.*;
import javax.net.ssl.*;
import java.security.cert.*;
import com.tradebits.exchange.*;
import com.tradebits.exchange.AExchange.CURRENCY;
import com.tradebits.socket.*;

public class Trader{
    
    public static void main(String[] args) throws Exception {
        
        // Create a trust manager that does not validate certificate chains
        TrustManager[] trustAllCerts = new TrustManager[] {new X509TrustManager() {
            public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                return null;
            }
            public void checkClientTrusted(X509Certificate[] certs, String authType) {
            }
            public void checkServerTrusted(X509Certificate[] certs, String authType) {
            }
        }
        };
        
        // Install the all-trusting trust manager
        SSLContext sc = SSLContext.getInstance("SSL");
        sc.init(null, trustAllCerts, new java.security.SecureRandom());
        HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
        
        // Create all-trusting host name verifier
        HostnameVerifier allHostsValid = new HostnameVerifier() {
            public boolean verify(String hostname, SSLSession session) {
                return true;
            }
        };
        
        // Install the all-trusting host verifier
        HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);
        
        
        
        StandardSocketFactory socketFactory = new StandardSocketFactory();
        
        
//         BitFloor bitfloor = new BitFloor();
//         bitfloor.connect();
        
//         BlockChain block = new BlockChain();
//         block.connect();
        
//         ICBit icbit = new ICBit();
//         icbit.connect();
        
//        Intersango intersango = new Intersango();
//        intersango.connect();
        
        final LinkedList<AExchange> exchanges = new LinkedList<AExchange>();
        
        MtGox mtGoxUSD = new MtGox(socketFactory, CURRENCY.USD);
        exchanges.add(mtGoxUSD);
        
        MtGox mtGoxEUR = new MtGox(socketFactory, CURRENCY.EUR);
//        exchanges.add(mtGoxEUR);
        
        MtGox mtGoxAUD = new MtGox(socketFactory, CURRENCY.AUD);
//        exchanges.add(mtGoxAUD);
        
        MtGox mtGoxCAD = new MtGox(socketFactory, CURRENCY.CAD);
//        exchanges.add(mtGoxCAD);
        
        MtGox mtGoxGBP = new MtGox(socketFactory, CURRENCY.GBP);
//        exchanges.add(mtGoxGBP);
        
        
        for(AExchange ex : exchanges){
            ex.connect();
        }
        

        Timer foo = new Timer();
        foo.scheduleAtFixedRate(new TimerTask(){
            public void run(){
                for(AExchange ex : exchanges){
                    ex.log("isOffline: " + ex.isOffline());
                    ex.log("isConnected: " + ex.isConnected());
                    ex.log("bid: " + ex.getBid(0));
                    ex.log("ask: " + ex.getAsk(0));
                    if(ex.isOffline()){
                        ex.connect();
                    }
                }
            }
        }, 10000, 10000);
    }
}