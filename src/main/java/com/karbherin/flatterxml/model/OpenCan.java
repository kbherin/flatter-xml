package com.karbherin.flatterxml.model;

public final class OpenCan<T> {
    public T val;

    public<V extends T> OpenCan(V val) {
        this.val = val;
    }

    public OpenCan() {
        this.val = null;
    }

    public <V extends T> OpenCan<T> setVal(V val) {
        this.val = val;
        return this;
    }
}
