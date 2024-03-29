package com.tradebits;



import java.io.*;
import java.net.*;
import java.util.*;



public class Log{
    
    protected String name;
    
    LinkedList<String> logs = new LinkedList<String>();
    
    public Log(String name) throws IOException{
        this.name = name;
        File logDir = new File(System.getProperty("logPath"));
        String logDirectory = logDir.getAbsolutePath();
        String logFilePath = logDirectory + File.separator + name + ".log";
        final File logFile = new File(logFilePath);
        
        this.log("====================================================================");
        this.log("Begin New Log");
        
        (new Thread("Log " + name){
            public void run(){
                synchronized(logs){
                    while(true){
                        try{
                            if(!logFile.exists()){
                                logFile.createNewFile();
                            }
                            while(logs.size() == 0){
                                try{
                                    logs.wait();
                                }catch(InterruptedException e){}
                            }
                            FileWriter fileWritter = new FileWriter(logFile,true);
                            BufferedWriter bufferWritter = new BufferedWriter(fileWritter);
                            while(logs.size() > 0){
                                bufferWritter.write(logs.removeFirst() + "\n");
                            }
                            bufferWritter.close();
                        }catch(IOException e){ }
                    }
                }
            }
        }).start();
    }
    
    public void log(String msg){
        synchronized(logs){
            logs.add((new Date()) + ": " + msg);
            logs.notify();
        }
    }
 
    public String getName(){
        return name;
    }
    
}