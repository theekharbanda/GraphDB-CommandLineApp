package com.graphdb.modal;

import java.io.Serializable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Relationship implements Serializable {
    private final Map<String, String> relation;


    public Relationship() {
        this.relation = new ConcurrentHashMap<>();
    }
    public void put (String key, String value){
        relation.put(key,value);
    }
    public Map<String,String> getRelation(){
        return relation;
    }


}
