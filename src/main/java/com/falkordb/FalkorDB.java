package com.falkordb;

import java.net.URI;

import com.falkordb.impl.api.DriverImpl;

/**
 * FalkorDB driver factory
 */
final public class FalkorDB {

    private FalkorDB() {
    }

    /**
     * Creates a new driver instance
     * 
     * @return a new driver instance
     */
    public static Driver driver() {
        return driver("localhost", 6379);
    }

    /**
     * Creates a new driver instance
     * 
     * @param host host name
     * @param port port number
     * @return a new driver instance
     */
    public static Driver driver(String host, int port) {
        return new DriverImpl(host, port);
    }

    /**
     * Creates a new driver instance
     * 
     * @param host     host name
     * @param port     port number
     * @param user     username
     * @param password password
     * @return a new driver instance
     */
    public static Driver driver(String host, int port, String user, final String password) {
        return new DriverImpl(host, port, user, password);
    }

    /**
     * Creates a new driver instance
     * 
     * @param uri server uri
     * @return a new driver instance
     */
    public static Driver driver(URI uri) {
        return new DriverImpl(uri);
    }
}
