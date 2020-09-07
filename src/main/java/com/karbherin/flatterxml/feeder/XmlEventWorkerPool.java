package com.karbherin.flatterxml.feeder;

import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.io.PipedInputStream;
import java.util.concurrent.CountDownLatch;

public class XmlEventWorkerPool {

    public long execute(int numWorkers,
                        XmlEventEmitter xmlEventEmitter, XmlEventWorkerFactory xmlEventWorkerFactory)
            throws IOException, XMLStreamException, InterruptedException {

        CountDownLatch workerCounter = new CountDownLatch(numWorkers);
        Thread[] workers = new Thread[numWorkers];

        for (int i = 1; i <= numWorkers; i++) {
            PipedInputStream channel = new PipedInputStream();
            xmlEventEmitter.registerChannel(channel);
            Thread worker = xmlEventWorkerFactory.newWorker(channel, i, workerCounter);
            workers[i-1] = worker;
            worker.start();
        }

        xmlEventEmitter.startStream();

        workerCounter.await();
        return xmlEventEmitter.getRecCounter();
    }
}
