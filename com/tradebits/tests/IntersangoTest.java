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
    
    JSONObject intersangoTestConfig;
    
    @After @AfterClass
    protected void tearDown(){
        intersango.disconnect();
        intersango = null;
    }
    
    
    @Before
    protected void setUp() throws JSONException{
        intersangoTestConfig = new JSONObject("{ \"host\":\"fakeHost\", \"port\":1337 }");
    }
    
    
    /**
     * This tests that the socket connect()
     * method is called when connecting to mtgox
     */
    @Test public void testConnect() throws ExchangeException{
        
        final MutableInt count = new MutableInt();
        
        intersango = new Intersango(intersangoTestConfig, new TestSocketFactory(){
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
        
        intersango = new Intersango(intersangoTestConfig, new TestSocketFactory(){
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
        
        intersango = new Intersango(intersangoTestConfig, new TestSocketFactory(){
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
        final ISocketHelper noopSocket = new TestSocketHelper(){
            public void connect() throws Exception{
                super.connect();
                count.increment();
            }
        };
        
        //
        // initialize mtgox with a noop socket
        intersango = new Intersango(intersangoTestConfig, new TestSocketFactory(){
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
        final ISocketHelper noopSocket = new TestSocketHelper(){
            public void connect() throws Exception{
                super.connect();
                count.increment();
            }
        };
        
        //
        // initialize mtgox with a noop socket
        intersango = new Intersango(intersangoTestConfig, new TestSocketFactory(){
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
        final ISocketHelper noopSocket = new TestSocketHelper(){
            public void connect() throws Exception{
                super.connect();
                count.increment();
            }
        };
        
        //
        // initialize mtgox with a noop socket
        intersango = new Intersango(intersangoTestConfig, new TestSocketFactory(){
            public ISocketHelper getRawSocketTo(String host, int port, Log logFile){
                return noopSocket;
            }
        }, CURRENCY.USD);
        
        // initial connection
        intersango.connect();
        
        // send close message
        noopSocket.getListener().onMessage(noopSocket, "5:::{\"mumble\":\"foobar\"}");
        
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
        intersango = new Intersango(intersangoTestConfig, new StandardSocketFactory(){
            public ISocketHelper getRawSocketTo(String host, int port, Log logFile){
                return new RawSocketConnection(host, port, logFile){
                    public void connect(){
                        count.increment();
                        super.connect();
                    }
                };
            }
        }, CURRENCY.USD);
        
        // initial connection
        intersango.connect();
        
        // confirm it goes offline
        assertEquals("websocket connects once", 1, count.intValue());
        assertTrue("mtgox is offline", intersango.isOffline());
    }
    
    
    
    
    
    /**
     * This test is for parsing depth data
     */
    
    
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
        
        final ServerSocket testSocketServer = new ServerSocket(2338);
        
        JSONObject testConfig = new JSONObject();
        testConfig.put("port", 2338);
        testConfig.put("host", testSocketServer.getInetAddress().getHostName());
        System.out.println(testConfig);
        
        (new Thread(){
            public void run(){
                Socket clientSocket = null;
                try {
                    clientSocket = testSocketServer.accept();
                    System.out.println("Accept wins");
                    PrintWriter os = new PrintWriter(clientSocket.getOutputStream());
                    
                    String orderbook = TestHelper.loadTestResource("intersango.orderbook");
                    os.println(orderbook);
                    os.flush();
                    os.close();
                } 
                catch (IOException e) {
                    System.out.println("Accept failed: 4444");
                    System.exit(-1);
                }
            }
        }).start();

        //
        // initialize mtgox with a noop socket
        // and null data for the handshake
        intersango = new Intersango(testConfig, new StandardSocketFactory(), CURRENCY.USD);
        intersango.addListener(new AExchangeListener(){
            public void didInitializeDepth(AExchange exchange){
                System.out.println("depth initialized");
                synchronized(count){
                    count.increment();
                    count.notify();
                }
            }
            
            public void didProcessDepth(AExchange exchange){
                System.out.println("depth message processed");
            }
        });
        // initial connection
        intersango.connect();
        
        //
        // wait
        synchronized(count){
            while(count.intValue() < 1){
                try{
                    count.wait();
                }catch(InterruptedException e){}
            }
        }
        
        // now we've loaded the default orderbook,
        // so check our low/high bid/ask are correct
        
        JSONObject highestBid = intersango.getBid(0);
        JSONObject lowestAsk = intersango.getAsk(0);
        
        System.out.println("bid: " + highestBid);
        System.out.println("ask: " + lowestAsk);
        
        // confirm it cached the depth data
        // since it hasn't yet loaded full depth
        // from mtgox servers
        assertEquals("ask price is correct", 11.07182, lowestAsk.getDouble("price"));
        assertEquals("ask volume is correct", 0.18240, lowestAsk.getDouble("volume"));
        assertEquals("bid price is correct", 10.18748, highestBid.getDouble("price"));
        assertEquals("bid volume is correct", 0.02803, highestBid.getDouble("volume"));
//        
//        //
//        // now lets wait for the realtime updates
//        synchronized(count){
//            while(count.intValue() < 2){
//                try{
//                    count.wait();
//                }catch(InterruptedException e){}
//            }
//        }
//        
//        
//        highestBid = intersango.getBid(0);
//        lowestAsk = intersango.getAsk(0);
//        
//        System.out.println("bid: " + highestBid);
//        System.out.println("ask: " + lowestAsk);
//        
//        // confirm the low/high bid/ask updated correctly
//        assertEquals("ask price is correct", 11.91003, lowestAsk.getDouble("price"));
//        assertEquals("ask volume is correct", 0.01, lowestAsk.getDouble("volume"));
//        assertEquals("ask date is correct", 1345278624831148L / 1000, ((Date)lowestAsk.get("stamp")).getTime());
//        assertEquals("bid price is correct", 11.91002, highestBid.getDouble("price"));
//        assertEquals("bid volume is correct", 0.2054, highestBid.getDouble("volume"));
//        assertEquals("bid date is correct", 1345278624831148L / 1000, ((Date)highestBid.get("stamp")).getTime());
        
    }
    
    
    
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