package com.karbherin.flatterxml.model;

public final class Pair<K, V> {
    private final K key;
    private V val;

    public Pair(K key, V val) {
        this.key = key;
        this.val = val;
    }

    public K getKey() {
        return key;
    }

    public V getVal() {
        return val;
    }

    public void setVal(V val) {
        this.val = val;
    }

    @Override
    public String toString() {
        return String.format("\"%s\" : \"%s\"", key.toString(), val.toString());
    }
}
