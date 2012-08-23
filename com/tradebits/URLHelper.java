package com.tradebits;

import java.net.*;
import java.io.*;
import org.json.*;


public class URLHelper{
    
    
    public String getSynchronousURL(URL url) throws IOException{
        String depthData = "";
        String depthString = "";
        // Send data
        URLConnection conn = url.openConnection();
        conn.setRequestProperty("User-Agent", "Brokerbot Java Trading Bot");
        
        // Get the response
        BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        String line;
        while ((line = rd.readLine()) != null) {
            // Process line...
            depthString += line + "\n";
        }
        rd.close();
        
        if(depthString.length() > 0){
            return depthString;
        }else{
            return null;
        }
    }
    
    
    
    public String postSynchronousURL(URL url, String data) throws IOException{
        String socketInfo = "";
        // Send data
        URLConnection conn = url.openConnection();
        conn.setRequestProperty("User-Agent", "Brokerbot Java Trading Bot");
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
        
        if(socketInfo.length() > 0){
            return socketInfo;
        }else{
            return null;
        }
    }
    
    
    public static String fileToString(File file) {
        String result = null;
        DataInputStream in = null;

        try {
            byte[] buffer = new byte[(int) file.length()];
            in = new DataInputStream(new FileInputStream(file));
            in.readFully(buffer);
            result = new String(buffer);
        } catch (IOException e) {
            throw new RuntimeException("IO problem in fileToString", e);
        } finally {
            try {
                in.close();
            } catch (IOException e) { /* ignore it */
            }
        }
        return result;
    }
}