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


public class MtGoxDepthTest extends TestHelper{
    
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
                        }, 120);
                        // finish the test
                        (new Timer()).schedule(new TimerTask(){
                            public void run(){
                                synchronized(count){
                                    count.increment();
                                    count.notify();
                                }
                            }
                        }, 150);
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
        // wait
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
        
        //
        // wait
        synchronized(count){
            while(count.intValue() < 2){
                try{
                    count.wait();
                }catch(InterruptedException e){}
            }
        }
        
        // confirm it cached the depth data
        // since it hasn't yet loaded full depth
        // from mtgox servers
        assertEquals("the depth data was cached", 2, mtgox.numberOfCachedDepthData());
    }
    
    
    /**
     * This test is for parsing depth data
     * it should also empty any depth messages in
     * the cache
     */
    @Test public void testValidDepthEmptiesCache() {
        
        final MutableInt count = new MutableInt();
        
        //
        // initialize mtgox with a noop socket
        // and null data for the handshake
        mtgox = new MtGox(new ASocketFactory(){
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
                        
                        // send two depth messages
                        (new Timer()).schedule(new TimerTask(){
                            public void run(){
                                socket.getListener().onMessage(socket, "1::/mtgox");
                                socket.getListener().onMessage(socket, "4::/mtgox:{\"channel\":\"24e67e0d-1cad-4cc0-9e7a-f8523ef460fe\","
                                                                   + "\"op\":\"private\",\"origin\":\"broadcast\",\"private\":\"depth\""
                                                                   + ",\"depth\":{\"price\":\"11.88326\",\"type\":2,\"type_str\":\"bid\""
                                                                   + ",\"volume\":\"-0.01014437\",\"price_int\":\"1188326\",\"volume_int\""
                                                                   + ":\"-1014437\",\"item\":\"BTC\",\"currency\":\"USD\",\"now\":"
                                                                   + "\"1345278624831147\",\"total_volume_int\":\"0\"}}");
                            }
                        }, 60);
                        (new Timer()).schedule(new TimerTask(){
                            public void run(){
                                socket.getListener().onMessage(socket, "4::/mtgox:{\"channel\":\"24e67e0d-1cad-4cc0-9e7a-f8523ef460fe\","
                                                                   + "\"op\":\"private\",\"origin\":\"broadcast\",\"private\":\"depth\""
                                                                   + ",\"depth\":{\"price\":\"11.88326\",\"type\":2,\"type_str\":\"bid\""
                                                                   + ",\"volume\":\"-0.01014437\",\"price_int\":\"1188326\",\"volume_int\""
                                                                   + ":\"-1014437\",\"item\":\"BTC\",\"currency\":\"USD\",\"now\":"
                                                                   + "\"1345278624831147\",\"total_volume_int\":\"0\"}}");
                            }
                        }, 90);
                        // notify that we're done with realtime data
                        (new Timer()).schedule(new TimerTask(){
                            public void run(){
                                synchronized(count){
                                    count.increment();
                                    count.notify();
                                }
                            }
                        }, 120);
                        
                        
                        
                        
                        // finish the test
                        (new Timer()).schedule(new TimerTask(){
                            public void run(){
                                synchronized(count){
                                    count.increment();
                                    count.notify();
                                }
                            }
                        }, 18000);
                    }
                    public void send(String message){
                        // noop
                    }
                };
            }
            
            public URLHelper getURLHelper(){
                return new URLHelper(){
                    public String getSynchronousURL(URL foo, String bar) throws IOException{
                        URL url = this.getClass().getResource("/test.depth");
                        File testDepthData = new File(url.getFile());
                        String foo2 = MtGoxDepthTest.this.fileToString(testDepthData);
                        System.out.println(foo2);
                        return foo2;
                    }
                };
            }
        });
        
        // initial connection
        mtgox.connect();
        
        //
        // wait
        synchronized(count){
            while(count.intValue() < 1){
                try{
                    count.wait();
                }catch(InterruptedException e){}
            }
        }
        
        // get the cached realtime data
        assertEquals("the depth data was cached", 2, mtgox.numberOfCachedDepthData());
        
        //
        // wait
        synchronized(count){
            while(count.intValue() < 2){
                try{
                    count.wait();
                }catch(InterruptedException e){}
            }
        }
        
        // confirm it cached the depth data
        // since it hasn't yet loaded full depth
        // from mtgox servers
        assertEquals("the depth data was cached", 0, mtgox.numberOfCachedDepthData());
    }
    
    /**
     * This test is for parsing depth data
     */
    @Test public void testInvalidDepthData() {
        assertTrue("need to implement this test", false);
    }
    
    
}