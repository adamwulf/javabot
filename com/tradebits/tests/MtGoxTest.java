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


public class MtGoxTest extends TestCase{
    
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
     * This tests that the socket connect()
     * method is called when connecting to mtgox
     */
    @Test public void testConnect() {
        
        final MutableInt count = new MutableInt();
        
        mtgox = new MtGox(new ASocketFactory(){
            public ASocketHelper getSocketHelperFor(String httpURL, String wsURLFragment){
                return new TestSocketHelper(){
                    public void connect(){
                        count.increment();
                    }
                    public void send(String message){
                        assertTrue(false);
                    }
                };
            }
        });
        
        mtgox.connect();
        
        
        assertEquals(1, count.intValue());
    }
    
    
    
    /**
     * This tests that mtgox retries the connection
     * if the connect() method throws an exception
     */
    @Test public void testConnectException() {
        
        final MutableInt count = new MutableInt();
        
        mtgox = new MtGox(new ASocketFactory(){
            public ASocketHelper getSocketHelperFor(String httpURL, String wsURLFragment){
                return new TestSocketHelper(){
                    public void connect(){
                        count.increment();
                        if(count.intValue() == 1){
                            // this is called first
                            throw new RuntimeException("oh no!");
                        }
                        // then this is called a second time after disconnect()
                    }
                    
                    public void send(String message){
                        // never called
                        assertTrue(false);
                    }
                };
            }
        });
        
        mtgox.connect();
        
        assertEquals("connect twice and disconnect twice (including tearDown)", 2, count.intValue());
    }
    
    
    
    
    /**
     * This tests that mtgox retries the connection
     * if the connect() method throws an exception
     */
    @Test public void testConnectTimeoutException() {
        
        final MutableInt count = new MutableInt();
        
        mtgox = new MtGox(new ASocketFactory(){
            public ASocketHelper getSocketHelperFor(String httpURL, String wsURLFragment){
                return new TestSocketHelper(){
                    
                    public void connect() throws Exception{
                        count.increment();
                        if(count.intValue() == 1){
                            // this is called first
                            throw new java.util.concurrent.TimeoutException("oh no!");
                        }
                        // then this is called a second time after disconnect()
                    }
                    
                    public void send(String message){
                        // never called
                        assertTrue(false);
                    }
                };
            }
        });
        
        mtgox.connect();
        
        assertEquals(2, count.intValue());
    }
    
    
    /**
     * This tests that mtgox tries to connec to 1::/mtgox
     * after the socket is connected
     */
    @Test public void testReconnectOnDisconnect() {
        
        final MutableInt count = new MutableInt();
        
        //
        // record how often mtgox tries to connect
        final ASocketHelper noopSocket = new TestSocketHelper(){
            public void connect() throws Exception{
                count.increment();
            }
        };
        
        //
        // initialize mtgox with a noop socket
        mtgox = new MtGox(new ASocketFactory(){
            public ASocketHelper getSocketHelperFor(String httpURL, String wsURLFragment){
                return noopSocket;
            }
        });
        
        // initial connection
        mtgox.connect();
        
        // send close message
        noopSocket.getListener().onClose(noopSocket, 1, null);
        
        // confirm it tried to reconnect
        assertEquals(2, count.intValue());
    }
    
    
    /**
     * This tests that mtgox tries to reconnect
     * after recieving a null message
     */
    @Test public void testReconnectOnNullData() {
        
        final MutableInt count = new MutableInt();
        
        //
        // record how often mtgox tries to connect
        final ASocketHelper noopSocket = new TestSocketHelper(){
            public void connect() throws Exception{
                count.increment();
            }
        };
        
        //
        // initialize mtgox with a noop socket
        mtgox = new MtGox(new ASocketFactory(){
            public ASocketHelper getSocketHelperFor(String httpURL, String wsURLFragment){
                return noopSocket;
            }
        });
        
        // initial connection
        mtgox.connect();
        
        // send close message
        noopSocket.getListener().onMessage(noopSocket, null);
        
        // confirm it tried to reconnect
        assertEquals(2, count.intValue());
    }
    
    /**
     * This tests that mtgox tries to reconnect
     * after recieving a json message with incorrect data
     */
    @Test public void testReconnectOnNonDataJSON() {
        
        final MutableInt count = new MutableInt();
        
        //
        // record how often mtgox tries to connect
        final ASocketHelper noopSocket = new TestSocketHelper(){
            public void connect() throws Exception{
                count.increment();
            }
        };
        
        //
        // initialize mtgox with a noop socket
        mtgox = new MtGox(new ASocketFactory(){
            public ASocketHelper getSocketHelperFor(String httpURL, String wsURLFragment){
                return noopSocket;
            }
        });
        
        // initial connection
        mtgox.connect();
        
        // send close message
        noopSocket.getListener().onMessage(noopSocket, "4::/mtgox:{\"mumble\"}");
        
        // confirm it tried to reconnect
        assertEquals(2, count.intValue());
    }
    
    
    
    
    /**
     * This tests that mtgox tries to reconnect
     * after receiving a null response during the handshake
     */
    @Test public void testNullWebSocketHandshake() {
        
        final MutableInt count = new MutableInt();
        
        //
        // initialize mtgox with a noop socket
        // and null data for the handshake
        mtgox = new MtGox(new StandardSocketFactory(){
            public ASocketHelper getSocketHelperFor(String httpURL, String wsURLFragment){
                return new SocketHelper(this, httpURL, wsURLFragment){
                    public void connect() throws Exception{
                        count.increment();
                        super.connect();
                    }
                };
            }
            public URLHelper getURLHelper(){
                return new URLHelper(){
                    public String postSynchronousURL(URL foo, String bar) throws IOException{
                        if(count.intValue() == 1){
                            return null;
                        }else{
                            return super.postSynchronousURL(foo, bar);
                        }
                    }
                };
            }
        });
        
        // initial connection
        mtgox.connect();
        
        // confirm it tried to reconnect
        assertEquals("make sure to reconnect if failed websocket handshake", 2, count.intValue());
    }
    
    
    
    /**
     * This tests that mtgox tries to reconnect
     * after receiving a null response during the handshake
     */
    @Test public void testInvalidWebSocketHandshake() {
        
        final MutableInt count = new MutableInt();

        //
        // initialize mtgox with a noop socket
        // and null data for the handshake
        mtgox = new MtGox(new StandardSocketFactory(){
            public ASocketHelper getSocketHelperFor(String httpURL, String wsURLFragment){
                return new SocketHelper(this, httpURL, wsURLFragment){
                    public void connect() throws Exception{
                        count.increment();
                        super.connect();
                    }
                };
            }
            public URLHelper getURLHelper(){
                return new URLHelper(){
                    public String postSynchronousURL(URL foo, String bar) throws IOException{
                        if(count.intValue() == 1){
                            return "gibberish:";
                        }else{
                            synchronized(count){
                                count.notify();
                            }
                            return super.postSynchronousURL(foo, bar);
                        }
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
            while(count.intValue() < 2){
                try{
                    count.wait();
                }catch(InterruptedException e){}
            }
        }
        
        
        // confirm it tried to reconnect
        assertEquals("make sure to reconnect if failed websocket handshake", 2, count.intValue());
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
     */
    @Test public void testInvalidDepthData() {
        assertTrue("need to implement this test", false);
    }
}