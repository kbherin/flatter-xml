package com.karbherin.flatterxml.feeder;

import org.junit.Test;

import javax.xml.stream.*;
import java.io.*;
import java.util.concurrent.CountDownLatch;

public class XmlEventEmitterTest {

    @Test
    public void test() throws IOException, XMLStreamException, InterruptedException {
        XmlEventEmitter emitter = new XmlEventEmitter("src/test/resources/emp.xml");

        int numWorkers = 3;
        CountDownLatch workerCounter = new CountDownLatch(numWorkers);
        Thread[] workers = new Thread[numWorkers];
        for (int i = numWorkers; i > 0; i--) {
            PipedInputStream channel = new PipedInputStream();
            emitter.registerChannel(channel);
            Thread worker = newWorkerThread(channel, numWorkers-i+1,
                    "target/test/resources/emp_tables", workerCounter);
            workers[i-1] = worker;
            worker.start();
        }

        emitter.startStream(1);

        workerCounter.await();
        System.out.println("Done");
    }

    private Thread newWorkerThread(final PipedInputStream channel, int channelNum, String outDir,
                                   CountDownLatch workerCounter) {
        return new Thread(() -> {
            XMLEventReader reader;
            try {
                reader = XMLInputFactory.newFactory().createXMLEventReader(channel);
                XMLEventWriter os = XMLOutputFactory.newFactory().createXMLEventWriter(
                        new FileOutputStream(String.format("%s/part%d.xml", outDir, channelNum)));

                while(reader.hasNext()) {
                    os.add(reader.nextEvent());
                }

            } catch (XMLStreamException | IOException e) {
                e.printStackTrace();

            } finally {
                workerCounter.countDown();
                try {
                    channel.close();
                } catch (IOException ioException) {
                    ioException.printStackTrace();
                }
            }
        });
    }
}
