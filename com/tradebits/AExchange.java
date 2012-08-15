/**
 * a generic bitcoin exchange
 */
package com.tradebits;


public abstract class AExchange{
    
    private String name;
    
    public AExchange(String name){
        this.name = name;
    }
    
    public String getName(){
        return name;
    }
    
    protected void log(String log){
        System.out.println(this.getName() + ": " + log);
    }
    
}