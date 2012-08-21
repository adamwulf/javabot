package com.tradebits;

import java.io.*;
import java.net.*;
import java.util.*;
import javax.net.ssl.*;
import java.security.cert.*;
import com.tradebits.exchange.*;
import com.tradebits.exchange.AExchange.CURRENCY;
import com.tradebits.socket.*;
import org.json.*;

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
        
        final Log exchangeRatesOverTimeLog = new Log("Exchange Rates");
        
        File logDir = new File(System.getProperty("logPath"));
        
        final OpenExchangeRates exchangeRates = new OpenExchangeRates(socketFactory.getURLHelper());
        
        final LinkedList<AExchange> exchanges = new LinkedList<AExchange>();
        
        final MtGox mtGoxUSD = new MtGox(socketFactory, CURRENCY.USD);
        exchanges.add(mtGoxUSD);
        
        MtGox mtGoxEUR = new MtGox(socketFactory, CURRENCY.EUR);
//        exchanges.add(mtGoxEUR);
        
        MtGox mtGoxAUD = new MtGox(socketFactory, CURRENCY.AUD);
//        exchanges.add(mtGoxAUD);
        
        MtGox mtGoxCAD = new MtGox(socketFactory, CURRENCY.CAD);
        exchanges.add(mtGoxCAD);
        
        MtGox mtGoxGBP = new MtGox(socketFactory, CURRENCY.GBP);
        exchanges.add(mtGoxGBP);
        
        exchanges.add(new MtGox(socketFactory, CURRENCY.CHF));
        exchanges.add(new MtGox(socketFactory, CURRENCY.CNY));
        exchanges.add(new MtGox(socketFactory, CURRENCY.DKK));
        exchanges.add(new MtGox(socketFactory, CURRENCY.HKD));
        exchanges.add(new MtGox(socketFactory, CURRENCY.JPY));
        exchanges.add(new MtGox(socketFactory, CURRENCY.NZD));
        exchanges.add(new MtGox(socketFactory, CURRENCY.PLN));
        exchanges.add(new MtGox(socketFactory, CURRENCY.RUB));
        exchanges.add(new MtGox(socketFactory, CURRENCY.SEK));
        exchanges.add(new MtGox(socketFactory, CURRENCY.SGD));
        exchanges.add(new MtGox(socketFactory, CURRENCY.THB));
        
        for(AExchange ex : exchanges){
            ex.connect();
            Thread.sleep(5000);
        }
        

        Timer foo = new Timer();
        foo.scheduleAtFixedRate(new TimerTask(){
            public void run(){
                JSONObject rates = exchangeRates.getUSDRates();
                System.out.println("****************************************");
                for(AExchange ex : exchanges){
                    if(ex != mtGoxUSD && ex.isConnected() && mtGoxUSD.isConnected()){
                        //
                        // ok, lets figure out the "exchange rate" 
                        // of this currency compared to USD
                        try{
                            JSONObject usdAsk = mtGoxUSD.getAsk(0);
                            double usdToBTC = usdAsk.getDouble("price");
                            JSONObject exBid = ex.getBid(0);
                            double exToBTC = exBid.getDouble("price");
                            double exdusd = exToBTC / usdToBTC;
                            double actualRate = rates.getDouble(ex.getCurrency().toString());
                            // "how many EXD do we get per USD?"
                            exchangeRatesOverTimeLog.log("exchange rate: " + ex.getCurrency() + mtGoxUSD.getCurrency() + ": " + exdusd + " vs " + actualRate + " diff= " + (exdusd/actualRate));
                            ex.log("bid: " + ex.getBid(0));
                            ex.log("ask: " + ex.getAsk(0));
                        }catch(JSONException e){
                            exchangeRatesOverTimeLog.log("error calculating currency exchange rates: ");
                            e.printStackTrace();
                        }
                    }else if(mtGoxUSD.isOffline()){
                        mtGoxUSD.log("is offline");
                    }else if(ex.isOffline()){
                        ex.log("is offline");
                    }else if(ex.isConnecting()){
                        ex.log("is connecting");
                    }
                    if(ex.isOffline()){
                        ex.connect();
                    }
                }
                System.out.println("****************************************");
            }
        }, 10000, 10000);
    }
}