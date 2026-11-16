package com.falkordb.impl.api;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.security.SecureRandom;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLContextSpi;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSessionContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;

/**
 * An {@link SSLContext} whose socket factory records every TLS socket it is asked for, as {@code host:port},
 * and then refuses it. Jedis layers TLS over a socket it has already connected, by calling {@link
 * SSLSocketFactory#createSocket(Socket, String, int, boolean)}, so pointing a driver at a plain listening
 * socket shows, without a TLS server, which of the driver's connections went through this context.
 */
final class RecordingSslContext extends SSLContext {

    private final RecordingSpi spi;

    private RecordingSslContext(RecordingSpi spi) {
        super(spi, null, "TLS");
        this.spi = spi;
    }

    static RecordingSslContext create() {
        return new RecordingSslContext(new RecordingSpi());
    }

    /** @return each TLS socket requested so far, as {@code host:port}, in order */
    List<String> requested() {
        return spi.factory.requested;
    }

    /** @return the one socket factory this context hands out */
    SSLSocketFactory factory() {
        return spi.factory;
    }

    private static final class RecordingSpi extends SSLContextSpi {
        final RecordingFactory factory = new RecordingFactory();

        @Override
        protected void engineInit(KeyManager[] km, TrustManager[] tm, SecureRandom sr) {}

        @Override
        protected SSLSocketFactory engineGetSocketFactory() {
            return factory;
        }

        @Override
        protected SSLServerSocketFactory engineGetServerSocketFactory() {
            throw new UnsupportedOperationException();
        }

        @Override
        protected SSLEngine engineCreateSSLEngine() {
            throw new UnsupportedOperationException();
        }

        @Override
        protected SSLEngine engineCreateSSLEngine(String host, int port) {
            throw new UnsupportedOperationException();
        }

        @Override
        protected SSLSessionContext engineGetServerSessionContext() {
            throw new UnsupportedOperationException();
        }

        @Override
        protected SSLSessionContext engineGetClientSessionContext() {
            throw new UnsupportedOperationException();
        }
    }

    private static final class RecordingFactory extends SSLSocketFactory {
        final List<String> requested = new CopyOnWriteArrayList<>();

        @Override
        public Socket createSocket(Socket socket, String host, int port, boolean autoClose) throws IOException {
            requested.add(host + ":" + port);
            if (autoClose) {
                socket.close();
            }
            throw new IOException("TLS socket refused by the test's recording SSLContext");
        }

        @Override
        public String[] getDefaultCipherSuites() {
            return new String[0];
        }

        @Override
        public String[] getSupportedCipherSuites() {
            return new String[0];
        }

        @Override
        public Socket createSocket(String host, int port) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Socket createSocket(String host, int port, InetAddress localHost, int localPort) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Socket createSocket(InetAddress host, int port) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) {
            throw new UnsupportedOperationException();
        }
    }
}
