package com.karbherin.flatterxml.consumer;

import java.nio.channels.Pipe;
import java.util.concurrent.CountDownLatch;

public interface XmlEventWorkerFactory {

    Runnable newWorker(Pipe.SourceChannel channel, CountDownLatch workerCounter);

}
