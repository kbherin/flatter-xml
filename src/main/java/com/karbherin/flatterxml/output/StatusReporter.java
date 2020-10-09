package com.karbherin.flatterxml.output;

import static com.karbherin.flatterxml.output.RecordHandler.GeneratedResult;

import java.io.OutputStream;
import java.io.PrintStream;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

public class StatusReporter {
    long start = System.currentTimeMillis();

    private AtomicLong recordCounter = new AtomicLong(0L);
    private Map<String, GeneratedResult> filesGenerated = new HashMap<>();
    private final PrintStream out;
    private final PrintStream err;

    public StatusReporter() {
        out = System.out;
        err = System.err;
    }

    public StatusReporter(PrintStream outputStream, PrintStream errorStream) {
        out = outputStream;
        err = errorStream;
    }

    public synchronized void logError(Throwable exception, int workerNum) {
        err.println(String.format("\nWorker %d: %s", workerNum, exception.getMessage()));
        exception.printStackTrace(err);
    }

    public synchronized void logInfo(String message) {
        out.print(message);
    }

    public long incrementRecordCounter(long increment) {
        return recordCounter.addAndGet(increment);
    }

    public synchronized void addFilesGenerated(List<GeneratedResult> filesGen) {
        filesGen.stream().forEach(f -> this.filesGenerated.put(f.recordType, f));
    }

    /**
     * Show progress and time elapsed
     */
    public void showProgress() {

        long end = System.currentTimeMillis();
        long durSec = (end - start) / 1000;
        long totalRecs = recordCounter.get();

        if (durSec < 600) {
            logInfo(String.format("\rProcessed %d XML records in %d seconds",
                    totalRecs, durSec));
        } else if (durSec < 3600) {
            logInfo(String.format("\rProcessed %d XML records in %d minutes %d seconds",
                    totalRecs, durSec / 60, durSec % 60));
        } else {
            logInfo(String.format("\rProcessed %d XML records in %d hours %d minutes %d seconds",
                    totalRecs, durSec / 3600, durSec / 60, durSec % 60));
        }
    }

    public long getTotalRecordCount() {
        return recordCounter.get();
    }

    public Collection<GeneratedResult> getFilesGenerated() {
        return filesGenerated.values();
    }

}
