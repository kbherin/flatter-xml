package com.karbherin.flatterxml.consumer;

import com.karbherin.flatterxml.feeder.XmlRecordEmitter;

import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.nio.channels.Pipe;
import java.util.concurrent.CountDownLatch;

public class XmlEventWorkerPool {

    public long execute(int numWorkers,
                        XmlRecordEmitter xmlRecordEmitter, XmlEventWorkerFactory xmlEventWorkerFactory)
            throws IOException, XMLStreamException, InterruptedException {

        CountDownLatch workerCounter = new CountDownLatch(numWorkers);

        for (int i = 0; i < numWorkers; i++) {
            Pipe pipe = Pipe.open();
            xmlRecordEmitter.registerChannel(pipe.sink());
            Runnable worker = xmlEventWorkerFactory.newWorker(pipe.source(), workerCounter);
            new Thread(worker).start();
        }

        xmlRecordEmitter.startStream();

        workerCounter.await();
        return xmlRecordEmitter.getRecCounter();
    }
}
