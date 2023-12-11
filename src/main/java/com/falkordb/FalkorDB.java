package com.falkordb;

final public class FalkorDB {

    private FalkorDB() {}   

    public static Driver driver (){
        return new com.falkordb.impl.api.Driver();
    }

    public static Driver driver (String host, int port){
        return new com.falkordb.impl.api.Driver(host, port);
    }
}
