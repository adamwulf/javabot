package com.tradebits;

import java.io.*;

public class SysOutLog extends Log{
    
    
    public SysOutLog(String name) throws IOException{
        super(name);
    }
    
    public SysOutLog() throws IOException{
        super("null log");
    }
    
    public void log(String foo){
        System.out.println(this.getName() + ": " + foo);
    }
    
    
}