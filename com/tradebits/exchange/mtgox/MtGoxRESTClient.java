package com.tradebits.exchange.mtgox;


import com.tradebits.exchange.*;
import com.tradebits.*;
import java.util.*;
import java.net.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import sun.misc.BASE64Decoder;
import sun.misc.BASE64Encoder;



public class MtGoxRESTClient {
    protected Log logFile;
    protected String key;
    protected String secret;
    
    /**
     * @param args the command line arguments
     public static void main(String[] args) {
     MtGoxRESTClient client = new MtGoxRESTClient(
     "your key here",
     "your secret here"
     );
     HashMap<String, String> query_args = new HashMap<String, String>();
     query_args.put("currency", "BTC");
     query_args.put("amount", "5.0");
     query_args.put("return_success", "https://mtgox.com/success");
     query_args.put("return_failure", "https://mtgox.com/failure");
     
     client.query("1/generic/private/merchant/order/create", query_args);
     }
     */
    
    public MtGoxRESTClient(String key, String secret, Log logFile) {
        this.key = key;
        this.secret = secret;
        this.logFile = logFile;
    }
    
    public String query(String path, HashMap<String, String> args) {
        try {
            // add nonce and build arg list
            args.put("nonce", String.valueOf(System.currentTimeMillis()));
            String post_data = this.buildQueryString(args);
            
            // args signature
            Mac mac = Mac.getInstance("HmacSHA512");
            SecretKeySpec secret_spec = new SecretKeySpec((new BASE64Decoder()).decodeBuffer(this.secret), "HmacSHA512");
            mac.init(secret_spec);
            String signature = (new BASE64Encoder()).encode(mac.doFinal(post_data.getBytes()));
            
            
            // build URL
            URL queryUrl = new URL("https://mtgox.com/api/" + path);
            
            // create connection
            HttpURLConnection connection = (HttpURLConnection)queryUrl.openConnection();
            connection.setDoOutput(true);
            // set signature
            connection.setRequestProperty("User-Agent", "Mozilla/4.0 (compatible; Java Test client)");
            connection.setRequestProperty("Rest-Key", this.key);
            connection.setRequestProperty("Rest-Sign", signature.replaceAll("\n", ""));
            
            // write post
            connection.getOutputStream().write(post_data.getBytes());
            
            // read info
            byte buffer[] = new byte[16384];
            int len = connection.getInputStream().read(buffer, 0, 16384);
            return new String(buffer, 0, len, "UTF-8");
        } catch (Exception ex) {
            logFile.log(ex.toString());
        }
        return null;
    }
    
    protected String buildQueryString(HashMap<String, String> args) {
        String result = new String();
        for (String hashkey : args.keySet()) {
            if (result.length() > 0) result += '&';
            try {
                result += URLEncoder.encode(hashkey, "UTF-8") + "="
                    + URLEncoder.encode(args.get(hashkey), "UTF-8");
            } catch (Exception ex) {
                logFile.log(Arrays.toString(ex.getStackTrace()));
            }
        }
        return result;
    }
}