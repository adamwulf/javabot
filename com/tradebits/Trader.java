package com.tradebits;

import java.io.*;
import java.net.*;
import java.util.*;
import javax.net.ssl.*;
import java.security.cert.*;
import com.tradebits.exchange.*;
import com.tradebits.exchange.AExchange.CURRENCY;
import com.tradebits.trade.*;
import com.tradebits.socket.*;
import org.json.*;
import org.apache.commons.lang3.mutable.*;

public class Trader{

    private static JSONObject config;
    
    public static void main(String[] args) throws Exception {
        
        //
        // load config
        File configPath = new File(System.getProperty("configPath"));
        String configJSON = URLHelper.fileToString(configPath);
        config = new JSONObject(configJSON);
        
        
        
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
        
        final Log tradeLog = new Log("Trade Log");
        final Log exchangeRatesFromUSDOverTimeLog = new Log("Exchange Rates USD => EXD");
        final Log exchangeRatesToUSDOverTimeLog = new Log("Exchange Rates USD <= EXD");
        
        File logDir = new File(System.getProperty("logPath"));
        
        final OpenExchangeRates exchangeRates = new OpenExchangeRates(socketFactory.getURLHelper());
        
        final LinkedList<AExchange> exchanges = new LinkedList<AExchange>();
        
        final MtGox mtGoxUSD = new MtGox(config.getJSONObject("MtGox"), socketFactory, CURRENCY.USD);
        exchanges.add(mtGoxUSD);
        
        exchanges.add(new Intersango(socketFactory, CURRENCY.USD));
//        exchanges.add(new MtGox(socketFactory, CURRENCY.EUR));
//        exchanges.add(new MtGox(socketFactory, CURRENCY.AUD));
//        exchanges.add(new MtGox(socketFactory, CURRENCY.CAD));
//        exchanges.add(new MtGox(socketFactory, CURRENCY.GBP));
//        exchanges.add(new MtGox(socketFactory, CURRENCY.CHF));
//        exchanges.add(new MtGox(socketFactory, CURRENCY.CNY));
//        exchanges.add(new MtGox(socketFactory, CURRENCY.DKK));
//        exchanges.add(new MtGox(socketFactory, CURRENCY.HKD));
//        exchanges.add(new MtGox(socketFactory, CURRENCY.JPY));
//        exchanges.add(new MtGox(socketFactory, CURRENCY.NZD));
//        exchanges.add(new MtGox(socketFactory, CURRENCY.PLN));
//        exchanges.add(new MtGox(socketFactory, CURRENCY.RUB));
//        exchanges.add(new MtGox(socketFactory, CURRENCY.SEK));
//        exchanges.add(new MtGox(socketFactory, CURRENCY.SGD));
//        exchanges.add(new MtGox(socketFactory, CURRENCY.THB));
        
        for(AExchange ex : exchanges){
            ex.connect();
            Thread.sleep(5000);
        }
        
        
        final MutableInt ordersSent = new MutableInt();
        
        final Date lastRun = new Date();
        Timer foo = new Timer();
        foo.scheduleAtFixedRate(new TimerTask(){
            JSONObject rates;
            
            public void run(){
                boolean didLog = false;
                rates = exchangeRates.getUSDRates();

                for(AExchange ex : exchanges){
                    if(ex != mtGoxUSD && ex.isConnected() && mtGoxUSD.isConnected()){
                        
                        if(ordersSent.intValue() == 0){
                            try{
                                //
                                // only send 1 trade
                                Log transactionLog = new Log("Transactions");
                                String key = config.getJSONObject("MtGox").getString("key");
                                String secret = config.getJSONObject("MtGox").getString("secret");
                                MtGoxRESTClient client = new MtGoxRESTClient(key, secret, transactionLog);
                                
                                String queryURL = "1/generic/private/info";
                                HashMap<String, String> args = new HashMap<String, String>();
                                String response = client.query(queryURL, args);
                                
                                transactionLog.log(queryURL + " with args " + args);
                                transactionLog.log(response);
                                
                                if(response != null){
                                    JSONObject ret = new JSONObject(response);
                                    System.out.println(ret);
                                }
                                
                                ordersSent.increment();
                            }catch(Exception e){ }
                        }
                        
                        //
                        // ok, lets figure out the "exchange rate" 
                        // of this currency compared to USD
                        AExchange fromEx = mtGoxUSD;
                        AExchange toEx = ex;
                        CurrencyTrade possibleTrade1 = new CurrencyTrade(fromEx, toEx, exchangeRates, exchangeRatesFromUSDOverTimeLog);
                        didLog = possibleTrade1.prepTradeInformation() || didLog;
                        CurrencyTrade possibleTrade2 = new CurrencyTrade(toEx, fromEx, exchangeRates, exchangeRatesToUSDOverTimeLog);
                        didLog = possibleTrade2.prepTradeInformation() || didLog;
                        
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
                if(didLog){
                    lastRun.setTime(new Date().getTime());
                }
            }
        }, 10000, 1000);
    }
}