package com.tradebits;

import java.io.*;

public class NullLog extends Log{
    
    
    public NullLog(String name) throws IOException{
        super(name);
    }
    
    public NullLog() throws IOException{
        super("null log");
    }
    
    public void log(String foo){
        // noop!
    }
    
    
}