package com.karbherin.flatterxml.output;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class StatusReporter {
    private List<Throwable> exceptions = new LinkedList<>();
    private AtomicLong recordCounter = new AtomicLong(0L);

    public void reportException(Throwable exception) {
       exceptions.add(exception);
    }

    public long incrementRecordCounter(long increment) {
        return recordCounter.addAndGet(increment);
    }

    public List<Throwable> getExceptions() {
        return exceptions;
    }

    public AtomicLong getRecordCounter() {
        return recordCounter;
    }
}
