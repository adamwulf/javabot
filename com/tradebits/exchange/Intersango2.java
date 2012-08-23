package com.tradebits.exchange;

import java.io.*;
import java.net.*;


public class Intersango2 {
    public static void main() throws IOException {

        Socket echoSocket = null;
        BufferedReader in = null;

        try {
            echoSocket = new Socket("db.intersango.com", 1337);
            in = new BufferedReader(new InputStreamReader(
                                        echoSocket.getInputStream()));
        } catch (UnknownHostException e) {
            System.err.println("Don't know about host: taranis.");
            System.exit(1);
        } catch (IOException e) {
            System.err.println("Couldn't get I/O for "
                               + "the connection to: taranis.");
            System.exit(1);
        }

 BufferedReader stdIn = new BufferedReader(
                                   new InputStreamReader(System.in));
 String jsonLine;

 while ((jsonLine = in.readLine()) != null) {
     System.out.println(jsonLine);
 }

 in.close();
 stdIn.close();
 echoSocket.close();
    }
}