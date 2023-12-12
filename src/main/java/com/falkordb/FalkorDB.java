package com.falkordb;

import com.falkordb.impl.api.DriverImpl;

/**
 * FalkorDB driver factory
 */
final public class FalkorDB {

    private FalkorDB() {}   

    /**
     * Creates a new driver instance
     * @return a new driver instance
     */
    public static Driver driver (){
        return new DriverImpl();
    }

    /**
     * Creates a new driver instance
     * @param host host name
     * @param port port number
     * @return a new driver instance
     */
    public static Driver driver (String host, int port){
        return new DriverImpl(host, port);
    }
}
