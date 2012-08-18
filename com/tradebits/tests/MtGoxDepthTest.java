package com.tradebits.tests;

import com.tradebits.*;
import com.tradebits.exchange.*;
import com.tradebits.socket.*;
import com.tradebits.tests.*;
import org.junit.*;
import junit.framework.TestCase;
import static org.junit.Assert.*;
import org.apache.commons.lang3.mutable.*;
import java.net.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.locks.*;


public class MtGoxDepthTest extends TestCase{
    
    MtGox mtgox;
    
    @Before
    protected void setUp(){
        
    }
    
    @After @AfterClass
    protected void tearDown(){
        mtgox.disconnect();
        mtgox = null;
    }
    
    
    
    /**
     * This test is for parsing depth data
     */
    @Test public void testInvalidDepthData() {
        assertTrue("need to implement this test", false);
    }
    
    
    
    /**
     * This test is for parsing depth data
     */
    @Test public void testValidDepthDataToCache() {
        
        final MutableInt count = new MutableInt();
        
        //
        // initialize mtgox with a noop socket
        // and null data for the handshake
        mtgox = new MtGox(new StandardSocketFactory(){
            public ASocketHelper getSocketHelperFor(String httpURL, String wsURLFragment){
                return new TestSocketHelper(){
                    final ASocketHelper socket = this;
                    public void disconnect(){
                        // noop
                    }
                    public void connect() throws Exception{
                        // open the socket
                        (new Timer()).schedule(new TimerTask(){
                            public void run(){
                                socket.getListener().onOpen(socket);
                            }
                        }, 30);
                        // send the depth data
                        (new Timer()).schedule(new TimerTask(){
                            public void run(){
                                socket.getListener().onMessage(socket, "4::/mtgox:{\"channel\":\"24e67e0d-1cad-4cc0-9e7a-f8523ef460fe\","
                                                                   + "\"op\":\"private\",\"origin\":\"broadcast\",\"private\":\"depth\""
                                                                   + ",\"depth\":{\"price\":\"11.88326\",\"type\":2,\"type_str\":\"bid\""
                                                                   + ",\"volume\":\"-0.01014437\",\"price_int\":\"1188326\",\"volume_int\""
                                                                   + ":\"-1014437\",\"item\":\"BTC\",\"currency\":\"USD\",\"now\":"
                                                                   + "\"1345278624831147\",\"total_volume_int\":\"0\"}}");
                            }
                        }, 60);
                        // finish the test
                        (new Timer()).schedule(new TimerTask(){
                            public void run(){
                                synchronized(count){
                                    count.increment();
                                    count.notify();
                                }
                            }
                        }, 90);
                    }
                    public void send(String message){
                        // noop
                    }
                };
            }
        });
        
        // initial connection
        mtgox.connect();
        
        //
        // wait till we skip the gibberish URL
        // and load the real URL
        synchronized(count){
            while(count.intValue() < 1){
                try{
                    count.wait();
                }catch(InterruptedException e){}
            }
        }
        
        
        // confirm it cached the depth data
        // since it hasn't yet loaded full depth
        // from mtgox servers
        assertEquals("the depth data was cached", 1, mtgox.numberOfCachedDepthData());
    }
    
}