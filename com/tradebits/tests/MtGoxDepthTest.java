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
                        //
                        // these prices are /before/ the data
                        // from the test file
                        (new Timer()).schedule(new TimerTask(){
                            public void run(){
                                socket.getListener().onMessage(socket, "1::/mtgox");
                                socket.getListener().onMessage(socket, "4::/mtgox:{\"channel\":\"24e67e0d-1cad-4cc0-9e7a-f8523ef460fe\","
                                                                   + "\"op\":\"private\",\"origin\":\"broadcast\",\"private\":\"depth\""
                                                                   + ",\"depth\":{\"price\":\"11.88326\",\"type\":2,\"type_str\":\"bid\""
                                                                   + ",\"volume\":\"-0.01014437\",\"price_int\":\"1188326\",\"volume_int\""
                                                                   + ":\"-1014437\",\"item\":\"BTC\",\"currency\":\"USD\",\"now\":"
                                                                   + "\"1345278624831146\",\"total_volume_int\":\"0\"}}");
                            }
                        }, 60);
                        (new Timer()).schedule(new TimerTask(){
                            public void run(){
                                socket.getListener().onMessage(socket, "4::/mtgox:{\"channel\":\"24e67e0d-1cad-4cc0-9e7a-f8523ef460fe\","
                                                                   + "\"op\":\"private\",\"origin\":\"broadcast\",\"private\":\"depth\""
                                                                   + ",\"depth\":{\"price\":\"11.88326\",\"type\":2,\"type_str\":\"bid\""
                                                                   + ",\"volume\":\"-0.01014437\",\"price_int\":\"1188326\",\"volume_int\""
                                                                   + ":\"-1014437\",\"item\":\"BTC\",\"currency\":\"USD\",\"now\":"
                                                                   + "\"1345278624831146\",\"total_volume_int\":\"0\"}}");
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
        
        // now we have the cached realtime data,
        // and the REST depth data has been downloaded
        
        JSONObject highestBid = mtgox.getBid(CURRENCY.USD, 0);
        JSONObject lowestAsk = mtgox.getAsk(CURRENCY.USD, 0);
        
        
        
        // confirm it cached the depth data
        // since it hasn't yet loaded full depth
        // from mtgox servers
        assertEquals("the depth data cache was emptied", 0, mtgox.numberOfCachedDepthData());
        
        
        //
        // TODO
        //
        // run this test with timestamps before the test.depth data, and verify
        // that my code shows the correct volume for each price
        //
        // then run this test with timestamps after the test.depth data and
        // verify again
    }
    
    
    
}