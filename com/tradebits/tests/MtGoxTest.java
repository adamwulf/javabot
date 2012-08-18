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

public class MtGoxTest extends TestCase{
    
    MtGox mtgox;
    
    @Before
    protected void setUp(){
        
    }
    
    @After @AfterClass
    protected void teardown(){
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
                    
                    public void disconnect(){
                        assertTrue(false);
                    }
                    
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
                    
                    public void disconnect(){
                        // when the exception is thrown,
                        // disconnect is called to cleanup
                        assertEquals(count.intValue(), 1);
                    }
                    
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
        
        assertEquals(2, count.intValue());
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
                    
                    public void disconnect(){
                        // when the exception is thrown,
                        // disconnect is called to cleanup
                        assertEquals(count.intValue(), 1);
                    }
                    
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
    @Test public void testNullDepthDataRetries() {
        
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
        assertEquals(2, count.intValue());
    }
    
    
    
}