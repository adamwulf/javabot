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
import com.tradebits.exchange.AExchange.CURRENCY;
import org.json.*;


public class IntersangoTest extends TestHelper{
    
    Intersango intersango;
    
    @After @AfterClass
    protected void tearDown(){
        intersango.disconnect();
        intersango = null;
    }
    
    
    
    
    /**
     * This tests that the socket connect()
     * method is called when connecting to mtgox
         */
    @Test public void testConnect() throws ExchangeException{
        
        final MutableInt count = new MutableInt();
        
        intersango = new Intersango(new TestSocketFactory(){
            public ISocketHelper getRawSocketTo(String host, int port, Log logFile){
                return new TestSocketHelper(){
                    public void connect() throws Exception{
                        super.connect();
                        count.increment();
                    }
                    public void send(String message){
                        assertTrue(false);
                    }
                };
            }
        }, CURRENCY.USD);
        
        intersango.connect();
        
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

        assertEquals(1, count.intValue());
        assertTrue("should be connecting since socket is ok but no messages yet", intersango.isConnecting());
        System.out.println("checking");
    }

    
    
    /**
     * This tests that mtgox retries the connection
     * if the connect() method throws an exception
     */
    @Test public void testConnectException() throws ExchangeException{
        
        final MutableInt count = new MutableInt();
        
        intersango = new Intersango(new TestSocketFactory(){
            public ISocketHelper getRawSocketTo(String host, int port, Log logFile){
                return new TestSocketHelper(){
                    public void connect() throws Exception{
                        super.connect();
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
        }, CURRENCY.USD);
        
        intersango.connect();
        
        assertEquals("connect only once, but it fails", 1, count.intValue());
        assertTrue("mtgox is offline if there is an exception thrown during connect", intersango.isOffline());
    }
    
    
    
    /**
     * This tests that mtgox retries the connection
     * if the connect() method throws an exception
     */
    @Test public void testConnectTimeoutException() throws ExchangeException{
        
        final MutableInt count = new MutableInt();
        
        intersango = new Intersango(new TestSocketFactory(){
            public ISocketHelper getRawSocketTo(String host, int port, Log logFile){
                return new TestSocketHelper(){
                    public void connect() throws Exception{
                        super.connect();
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
        }, CURRENCY.USD);
        
        intersango.connect();
        
        assertEquals(1, count.intValue());
        assertTrue("mtgox is offline after failed connection", intersango.isOffline());
    }
    
    
    /**
     * This tests that mtgox tries to connec to 1::/mtgox
     * after the socket is connected
     */
    @Test public void testClosesOnDisconnect() throws ExchangeException{
        
        final MutableInt count = new MutableInt();
        
        //
        // record how often mtgox tries to connect
        final ASocketHelper noopSocket = new TestSocketHelper(){
            public void connect() throws Exception{
                super.connect();
                count.increment();
            }
        };
        
        //
        // initialize mtgox with a noop socket
        intersango = new Intersango(new TestSocketFactory(){
            public ISocketHelper getRawSocketTo(String host, int port, Log logFile){
                return noopSocket;
            }
        }, CURRENCY.USD);
        
        // initial connection
        intersango.connect();
        
        // send close message
        noopSocket.getListener().onClose(noopSocket, 1, null);
        
        // confirm mtgox is offline after closing
        assertEquals(1, count.intValue());
        assertTrue("mtgox is offline", intersango.isOffline());
    }
    
    
    /**
     * This tests that mtgox tries to reconnect
     * after recieving a null message
     */
    @Test public void testReconnectOnNullData() throws ExchangeException{
        
        final MutableInt count = new MutableInt();
        
        //
        // record how often mtgox tries to connect
        final ASocketHelper noopSocket = new TestSocketHelper(){
            public void connect() throws Exception{
                super.connect();
                count.increment();
            }
        };
        
        //
        // initialize mtgox with a noop socket
        intersango = new Intersango(new TestSocketFactory(){
            public ISocketHelper getRawSocketTo(String host, int port, Log logFile){
                return noopSocket;
            }
        }, CURRENCY.USD);
        
        // initial connection
        intersango.connect();
        
        // send close message
        noopSocket.getListener().onMessage(noopSocket, null);
        
        // confirm it goes offline
        assertEquals(1, count.intValue());
        assertTrue("mtgox is offline", intersango.isOffline());
    }
    
    /**
     * This tests that mtgox tries to reconnect
     * after recieving a json message with incorrect data
     */
    @Test public void testReconnectOnNonDataJSON() throws ExchangeException{
        
        final MutableInt count = new MutableInt();
        
        //
        // record how often mtgox tries to connect
        final ASocketHelper noopSocket = new TestSocketHelper(){
            public void connect() throws Exception{
                super.connect();
                count.increment();
            }
        };
        
        //
        // initialize mtgox with a noop socket
        intersango = new Intersango(new TestSocketFactory(){
            public ISocketHelper getRawSocketTo(String host, int port, Log logFile){
                return noopSocket;
            }
        }, CURRENCY.USD);
        
        // initial connection
        intersango.connect();
        
        // send close message
        noopSocket.getListener().onMessage(noopSocket, "5:::{\"mumble\"}");
        
        // confirm it goes offline
        assertEquals(1, count.intValue());
        assertTrue("intersango is offline", intersango.isOffline());
    }
    
    
    
    
    /**
     * This tests that mtgox tries to reconnect
     * after receiving a null response during the handshake
     */
    @Test public void testNullWebSocketHandshake() throws ExchangeException{
        
        final MutableInt count = new MutableInt();
        
        //
        // initialize mtgox with a noop socket
        // and null data for the handshake
        mtgox = new MtGox(mtgoxTestConfig, new StandardSocketFactory(){
            public ISocketHelper getSocketHelperFor(String httpURL, String wsURLFragment){
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
        }, CURRENCY.USD);
        
        // initial connection
        mtgox.connect();
        
        // confirm it goes offline
        assertEquals("websocket connects once", 1, count.intValue());
        assertTrue("mtgox is offline", mtgox.isOffline());
    }
    
    
    
    /**
     * This tests that mtgox tries to reconnect
     * after receiving a null response during the handshake
    @Test public void testInvalidWebSocketHandshake() throws ExchangeException{
        
        final MutableInt count = new MutableInt();

        //
        // initialize mtgox with a noop socket
        // and null data for the handshake
        mtgox = new MtGox(mtgoxTestConfig, new StandardSocketFactory(){
            public ISocketHelper getSocketHelperFor(String httpURL, String wsURLFragment){
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
        }, CURRENCY.USD);
        
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
        
        
        // confirm it tried to reconnect
        assertEquals("make sure to reconnect if failed websocket handshake", 1, count.intValue());
        
    }
     */
    
    
    
    /**
     * This test is for parsing depth data
    @Test public void testValidDepthDataToCache() throws ExchangeException{
        
        final MutableInt count = new MutableInt();
        
        
        //
        // initialize mtgox with a noop socket
        // and null data for the handshake
        mtgox = new MtGox(mtgoxTestConfig, new StandardSocketFactory(){
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
     */
    
    
    /**
     * This test is for parsing depth data
    @Test public void testInvalidDepthData() {
        assertTrue("need to implement this test", false);
    }
     */
    
    
    
    /**
     * This test is for parsing depth data
    @Test public void testMovingDepthData() {
        //
        // load depth data
        //
        // then set the highest bid / lowest ask to zero volume
        // and it should remove it from the cache
            
        assertTrue("need to implement this test", false);
    }
     */
}