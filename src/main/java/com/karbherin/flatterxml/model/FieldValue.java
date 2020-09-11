package com.karbherin.flatterxml.model;

public final class FieldValue<K, V> {
    private final K field;
    private V value;

    public FieldValue(K fld, V val) {
        field = fld;
        value = val;
    }

    public K getField() {
        return field;
    }

    public V getValue() {
        return value;
    }

    public void setValue(V value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return String.format("%s => %s", field.toString(), value.toString());
    }
}
