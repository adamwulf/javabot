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
import org.json.*;
import com.tradebits.exchange.AExchange.CURRENCY;
import org.json.*;

public class MtGoxDepthTest extends TestHelper{
    
    MtGox mtgox;
    
    JSONObject mtgoxTestConfig;
    
    @Before
    protected void setUp() throws JSONException{
        mtgoxTestConfig = new JSONObject("{ \"key\":\"aFakeKey\", \"secret\":\"aFakeSecret\" }");
    }
    
    @After @AfterClass
    protected void tearDown(){
        mtgox.disconnect();
        mtgox = null;
    }
    
    
    
    /**
     * This test makes sure that any realtime data is processed
     * after the depth data loads and adjusts it accordingly.
     * 
     * the realtime data adjusts the bid(0) and ask(0), and we verify
     * the change in this test.
     */
    @Test public void testValidDepthEmptiesCache() throws Exception{
        
        final MutableInt count = new MutableInt();
        
        //
        // initialize mtgox with a noop socket
        // and null data for the handshake
        mtgox = new MtGox(mtgoxTestConfig, new TestSocketFactory(){
            public ISocketHelper getSocketHelperFor(String httpURL, String wsURLFragment){
                return new TestSocketHelper(){
                    final ASocketHelper socket = this;
                    public void connect() throws Exception{
                        super.connect();
                        // open the socket
                        (new Timer()).schedule(new TimerTask(){
                            public void run(){
                                socket.getListener().onOpen(socket);
                            }
                        }, 30);
                        
                        // send two depth messages
                        //
                        // these prices are /before/ the data
                        // from the test file
                        (new Timer()).schedule(new TimerTask(){
                            public void run(){
                                socket.getListener().onMessage(socket, "1::/mtgox");
                                socket.getListener().onMessage(socket, "4::/mtgox:{\"channel\":\"24e67e0d-1cad-4cc0-9e7a-f8523ef460fe\","
                                                                   + "\"op\":\"private\",\"origin\":\"broadcast\",\"private\":\"depth\""
                                                                   + ",\"depth\":{\"price\":\"11.91003\",\"type\":2,\"type_str\":\"ask\""
                                                                   + ",\"volume\":\"-0.01014437\",\"price_int\":\"1188326\",\"volume_int\""
                                                                   + ":\"-1014437\",\"item\":\"BTC\",\"currency\":\"USD\",\"now\":"
                                                                   + "\"1345278624831148\",\"total_volume_int\":\"1200000\"}}");
                            }
                        }, 60);
                        (new Timer()).schedule(new TimerTask(){
                            public void run(){
                                socket.getListener().onMessage(socket, "4::/mtgox:{\"channel\":\"24e67e0d-1cad-4cc0-9e7a-f8523ef460fe\","
                                                                   + "\"op\":\"private\",\"origin\":\"broadcast\",\"private\":\"depth\""
                                                                   + ",\"depth\":{\"price\":\"11.91002\",\"type\":2,\"type_str\":\"bid\""
                                                                   + ",\"volume\":\"-0.01014437\",\"price_int\":\"1191002\",\"volume_int\""
                                                                   + ":\"-1014437\",\"item\":\"BTC\",\"currency\":\"USD\",\"now\":"
                                                                   + "\"1345278624831148\",\"total_volume_int\":\"30540000\"}}");
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
                };
            }
            
            public URLHelper getURLHelper(){
                return new URLHelper(){
                    public String getSynchronousURL(URL url) throws IOException{
                        if(url.toString().indexOf("generic/currency") != -1){
                            // set to our local currency file
                            String currtype = url.toString().substring(url.toString().indexOf("=")+1);
                            url = MtGoxDepthTest.this.getClass().getResource("currency." + currtype);
                        }else if(url.toString().indexOf("/depth") != -1){
                            // set to our local depth file
                            url = MtGoxDepthTest.this.getClass().getResource("test.depth");
                        }else{
                            return "";
                        }
                        File testDepthData = new File(url.getFile());
                        String foo2 = MtGoxDepthTest.this.fileToString(testDepthData);
                        return foo2;
                    }
                };
            }
        }, CURRENCY.USD);
        
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
        
        // now we have the cached realtime data,
        // and the REST depth data has been downloaded
        
        JSONObject highestBid = mtgox.getBid(0);
        JSONObject lowestAsk = mtgox.getAsk(0);
        
        
        
        System.out.println("bid: " + highestBid);
        System.out.println("ask: " + lowestAsk);
        
        
        // confirm it cached the depth data
        // since it hasn't yet loaded full depth
        // from mtgox servers
        assertEquals("ask price is correct", 11.91003, lowestAsk.getDouble("price"));
        assertEquals("ask volume is correct", 0.012, lowestAsk.getDouble("volume"));
        assertEquals("ask date is correct", 1345278624831148L / 1000, ((Date)lowestAsk.get("stamp")).getTime());
        assertEquals("bid price is correct", 11.91002, highestBid.getDouble("price"));
        assertEquals("bid volume is correct", 0.3054, highestBid.getDouble("volume"));
        assertEquals("bid date is correct", 1345278624831148L / 1000, ((Date)highestBid.get("stamp")).getTime());
        assertEquals("the depth data cache was emptied", 0, mtgox.numberOfCachedDepthData());
    }
    
    
    
    
    /**
     * This test is to make sure we load the initial depth data correctly.
     * 
     * the realtime data that is pulled in before the depth data adjusts
     * the bid/ask data that is not the zero'th index. so the bid(0) and ask(0)
     * are the ones from the full depth data.
     * 
     * we then verify that the price/volume is correct for these.
     */
    @Test public void testValidDepthWithRealtimeUpdatesOnNonZeroBidAsk() throws Exception{
        
        final MutableInt count = new MutableInt();
        
        //
        // initialize mtgox with a noop socket
        // and null data for the handshake
        mtgox = new MtGox(mtgoxTestConfig, new TestSocketFactory(){
            public ISocketHelper getSocketHelperFor(String httpURL, String wsURLFragment){
                return new TestSocketHelper(){
                    final ASocketHelper socket = this;
                    public void connect() throws Exception{
                        super.connect();
                        // open the socket
                        (new Timer()).schedule(new TimerTask(){
                            public void run(){
                                socket.getListener().onOpen(socket);
                            }
                        }, 30);
                        
                        // send two depth messages
                        //
                        // these prices are /before/ the data
                        // from the test file
                        (new Timer()).schedule(new TimerTask(){
                            public void run(){
                                socket.getListener().onMessage(socket, "1::/mtgox");
                                socket.getListener().onMessage(socket, "4::/mtgox:{\"channel\":\"24e67e0d-1cad-4cc0-9e7a-f8523ef460fe\","
                                                                   + "\"op\":\"private\",\"origin\":\"broadcast\",\"private\":\"depth\""
                                                                   + ",\"depth\":{\"price\":\"13.91003\",\"type\":2,\"type_str\":\"ask\""
                                                                   + ",\"volume\":\"-0.01014437\",\"price_int\":\"1188326\",\"volume_int\""
                                                                   + ":\"-1014437\",\"item\":\"BTC\",\"currency\":\"USD\",\"now\":"
                                                                   + "\"1345278624831148\",\"total_volume_int\":\"1200000\"}}");
                            }
                        }, 60);
                        (new Timer()).schedule(new TimerTask(){
                            public void run(){
                                socket.getListener().onMessage(socket, "4::/mtgox:{\"channel\":\"24e67e0d-1cad-4cc0-9e7a-f8523ef460fe\","
                                                                   + "\"op\":\"private\",\"origin\":\"broadcast\",\"private\":\"depth\""
                                                                   + ",\"depth\":{\"price\":\"10.91002\",\"type\":2,\"type_str\":\"bid\""
                                                                   + ",\"volume\":\"-0.01014437\",\"price_int\":\"1191002\",\"volume_int\""
                                                                   + ":\"-1014437\",\"item\":\"BTC\",\"currency\":\"USD\",\"now\":"
                                                                   + "\"1345278624831148\",\"total_volume_int\":\"30540000\"}}");
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
                };
            }
            
            public URLHelper getURLHelper(){
                return new URLHelper(){
                    public String getSynchronousURL(URL url) throws IOException{
                        if(url.toString().indexOf("generic/currency") != -1){
                            // set to our local currency file
                            String currtype = url.toString().substring(url.toString().indexOf("=")+1);
                            url = MtGoxDepthTest.this.getClass().getResource("currency." + currtype);
                        }else if(url.toString().indexOf("/depth") != -1){
                            // set to our local depth file
                            url = MtGoxDepthTest.this.getClass().getResource("test.depth");
                        }else{
                            return "";
                        }
                        File testDepthData = new File(url.getFile());
                        String foo2 = MtGoxDepthTest.this.fileToString(testDepthData);
                        return foo2;
                    }
                };
            }
        }, CURRENCY.USD);
        
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
        
        // now we have the cached realtime data,
        // and the REST depth data has been downloaded
        
        JSONObject highestBid = mtgox.getBid(0);
        JSONObject lowestAsk = mtgox.getAsk(0);
        
        
        
        System.out.println("bid: " + highestBid);
        System.out.println("ask: " + lowestAsk);
        
        
        // confirm it cached the depth data
        // since it hasn't yet loaded full depth
        // from mtgox servers
        assertEquals("ask price is correct", 11.91003, lowestAsk.getDouble("price"));
        assertEquals("ask volume is correct", 0.01, lowestAsk.getDouble("volume"));
        assertEquals("ask date is correct", 1345278624831148L / 1000, ((Date)lowestAsk.get("stamp")).getTime());
        assertEquals("bid price is correct", 11.91002, highestBid.getDouble("price"));
        assertEquals("bid volume is correct", 0.2054, highestBid.getDouble("volume"));
        assertEquals("bid date is correct", 1345278624831148L / 1000, ((Date)highestBid.get("stamp")).getTime());
        assertEquals("the depth data cache was emptied", 0, mtgox.numberOfCachedDepthData());
    }
    
    
    
}