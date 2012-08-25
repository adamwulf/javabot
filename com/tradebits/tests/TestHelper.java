package com.tradebits.tests;


import junit.framework.TestCase;
import java.io.*;
import java.net.*;

public abstract class TestHelper extends TestCase{
    
    
    public static String loadTestResource(String resourceName){
        URL url = TestHelper.class.getResource(resourceName);
        return TestHelper.fileToString(new File(url.getFile()));
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




