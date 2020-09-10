package com.karbherin.flatterxml.feeder;

import java.nio.channels.Pipe;
import java.util.concurrent.CountDownLatch;

public interface XmlEventWorkerFactory {

    Runnable newWorker(Pipe.SourceChannel channel, CountDownLatch workerCounter);

}
