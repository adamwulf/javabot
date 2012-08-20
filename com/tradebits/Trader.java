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
        
//        final MtGox mtGoxUSD = new MtGox(socketFactory, CURRENCY.USD);
//        mtGoxUSD.connect();
        
        final MtGox mtGoxEUR = new MtGox(socketFactory, CURRENCY.EUR);
        mtGoxEUR.connect();
        
        final MtGox mtGoxAUD = new MtGox(socketFactory, CURRENCY.AUD);
        mtGoxAUD.connect();
        
        final MtGox mtGoxCAD = new MtGox(socketFactory, CURRENCY.CAD);
        mtGoxCAD.connect();
        
        final MtGox mtGoxGBP = new MtGox(socketFactory, CURRENCY.GBP);
        mtGoxGBP.connect();
        
        
        Timer foo = new Timer();
        foo.scheduleAtFixedRate(new TimerTask(){
            public void run(){
//                mtGoxUSD.log("isConnected: " + mtGoxUSD.isConnected());
//                mtGoxUSD.log("bid: " + mtGoxUSD.getBid(0));
//                mtGoxUSD.log("ask: " + mtGoxUSD.getAsk(0));

                mtGoxEUR.log("isConnected: " + mtGoxEUR.isConnected());
                mtGoxEUR.log("bid: " + mtGoxEUR.getBid(0));
                mtGoxEUR.log("ask: " + mtGoxEUR.getAsk(0));

                mtGoxAUD.log("isConnected: " + mtGoxAUD.isConnected());
                mtGoxAUD.log("bid: " + mtGoxAUD.getBid(0));
                mtGoxAUD.log("ask: " + mtGoxAUD.getAsk(0));

                mtGoxCAD.log("isConnected: " + mtGoxCAD.isConnected());
                mtGoxCAD.log("bid: " + mtGoxCAD.getBid(0));
                mtGoxCAD.log("ask: " + mtGoxCAD.getAsk(0));

                mtGoxGBP.log("isConnected: " + mtGoxGBP.isConnected());
                mtGoxGBP.log("bid: " + mtGoxGBP.getBid(0));
                mtGoxGBP.log("ask: " + mtGoxGBP.getAsk(0));
            }
        }, 10000, 10000);
    }
}