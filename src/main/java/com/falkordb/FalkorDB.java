package com.falkordb;

import com.falkordb.impl.api.DriverImpl;

final public class FalkorDB {

    private FalkorDB() {}   

    public static Driver driver (){
        return new DriverImpl();
    }

    public static Driver driver (String host, int port){
        return new DriverImpl(host, port);
    }
}
