package com.tradebits;

import java.net.*;
import java.util.*;
import javax.net.ssl.*;

public class Trader{
    
     public static void main(String[] args) throws Exception {
         // Create a trust manager that does not validate certificate chains
         TrustManager[] trustAllCerts = new TrustManager[]{
             new X509TrustManager() {
                 public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                     return null;
                 }
                 public void checkClientTrusted(
                                                java.security.cert.X509Certificate[] certs, String authType) {
                 }
                 public void checkServerTrusted(
                                                java.security.cert.X509Certificate[] certs, String authType) {
                 }
             }
         };
         
// Install the all-trusting trust manager
         try {
             SSLContext sc = SSLContext.getInstance("SSL");
             sc.init(null, trustAllCerts, new java.security.SecureRandom());
             HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
         } catch (Exception e) {
         }
         
         
         

         MtGox mtgox = new MtGox();
         mtgox.connect();
         
         BlockChain block = new BlockChain();
         block.connect();
         
//         BitFloor bitfloor = new BitFloor();
//         bitfloor.connect();
         
         ICBit icbit = new ICBit();
         icbit.connect();
     }
}