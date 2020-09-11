package com.karbherin.flatterxml.consumer;

import com.karbherin.flatterxml.feeder.XmlEventEmitter;

import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.nio.channels.Pipe;
import java.util.concurrent.CountDownLatch;

public class XmlEventWorkerPool {

    public long execute(int numWorkers,
                        XmlEventEmitter xmlEventEmitter, XmlEventWorkerFactory xmlEventWorkerFactory)
            throws IOException, XMLStreamException, InterruptedException {

        CountDownLatch workerCounter = new CountDownLatch(numWorkers);

        for (int i = 0; i < numWorkers; i++) {
            Pipe pipe = Pipe.open();
            xmlEventEmitter.registerChannel(pipe.sink());
            Runnable worker = xmlEventWorkerFactory.newWorker(pipe.source(), workerCounter);
            new Thread(worker).start();
        }

        xmlEventEmitter.startStream();

        workerCounter.await();
        return xmlEventEmitter.getRecCounter();
    }
}
