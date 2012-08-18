package com.tradebits.exchange;

import org.junit.*;
import junit.framework.TestCase;
import static org.junit.Assert.*;
import com.tradebits.socket.*;
import org.apache.commons.lang3.mutable.*;

public class MtGoxTest extends TestCase{
    
    MtGox mtgox;
    
    @Before
    protected void setUp(){
        
    }
    
    @After
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
        
        mtgox = new MtGox(new ISocketFactory(){
            public ASocketHelper getSocketHelperFor(String httpURL, String wsURLFragment){
                return new ASocketHelper(){
                    
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
        
        mtgox = new MtGox(new ISocketFactory(){
            public ASocketHelper getSocketHelperFor(String httpURL, String wsURLFragment){
                return new ASocketHelper(){
                    
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
        
        mtgox = new MtGox(new ISocketFactory(){
            public ASocketHelper getSocketHelperFor(String httpURL, String wsURLFragment){
                return new ASocketHelper(){
                    
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
        final ASocketHelper noopSocket = new ASocketHelper(){
            public void disconnect(){
                // noop
            }
            public void connect() throws Exception{
                count.increment();
            }
            public void send(String message){
                // noop
            }
        };
        
        //
        // initialize mtgox with a noop socket
        mtgox = new MtGox(new ISocketFactory(){
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
}