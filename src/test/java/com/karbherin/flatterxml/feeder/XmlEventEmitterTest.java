package com.karbherin.flatterxml.feeder;

import org.junit.Assert;
import org.junit.Test;

import javax.xml.stream.*;
import java.io.*;
import java.util.concurrent.CountDownLatch;

public class XmlEventEmitterTest {

    @Test
    public void test() throws IOException, XMLStreamException, InterruptedException {
        String xmlFile = "src/test/resources/emp.xml";
        Assert.assertEquals("Entire file", 21, run(new XmlEventEmitter(xmlFile)));
        Assert.assertEquals("Skip 5 records", 16, run(new XmlEventEmitter(xmlFile, 5)));
        Assert.assertEquals("Skip 5 and pick first 4", 4, run(new XmlEventEmitter(xmlFile, 5, 4)));
        Assert.assertEquals("First 4 records", 4, run(new XmlEventEmitter(xmlFile, 0, 4)));
        Assert.assertEquals("Overshoot the end", 3, run(new XmlEventEmitter(xmlFile, 18, 10)));
    }
    private long run(XmlEventEmitter emitter)
            throws IOException, XMLStreamException, InterruptedException {

        int numWorkers = 3;
        String outDir = "target/test/resources/emp_tables";
        new File(outDir).mkdirs();
        CountDownLatch workerCounter = new CountDownLatch(numWorkers);
        Thread[] workers = new Thread[numWorkers];
        for (int i = numWorkers; i > 0; i--) {
            PipedInputStream channel = new PipedInputStream();
            emitter.registerChannel(channel);
            Thread worker = newWorkerThread(channel, numWorkers-i+1,
                    outDir, workerCounter);
            workers[i-1] = worker;
            worker.start();
        }

        emitter.startStream();
        workerCounter.await();
        return emitter.getRecCounter();
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
