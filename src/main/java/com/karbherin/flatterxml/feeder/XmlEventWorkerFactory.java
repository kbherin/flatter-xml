package com.karbherin.flatterxml.feeder;

import java.io.PipedInputStream;
import java.util.concurrent.CountDownLatch;

public interface XmlEventWorkerFactory {

    Thread newWorker(PipedInputStream channel, int channelNum, CountDownLatch workerCounter);

}
