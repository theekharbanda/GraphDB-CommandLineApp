package com.graphdb.modal;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Node implements Serializable  {
    @Serial
    private static final long serialVersionUID = 1L;
    private final Map<String, String> data;



    public Node() {
        this.data = new ConcurrentHashMap<>();
    }

    public void put(String key, String value) {
        data.put(key, value);
    }

    public String get(String key) {
        return data.get(key);
    }

    public Map<String, String> getData() {
        return new ConcurrentHashMap<>(data);
    }

    @Override
    public String toString() {
        return "Node{" +
                "data=" + data +
                '}';
    }
}
