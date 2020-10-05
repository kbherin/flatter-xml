package com.karbherin.flatterxml.feeder;

import org.junit.Assert;
import org.junit.Test;

import javax.xml.stream.*;
import java.io.*;
import java.nio.channels.Channels;
import java.nio.channels.Pipe;
import java.util.concurrent.CountDownLatch;

public class XmlEventEmitterTest {

    @Test
    public void test() throws IOException, XMLStreamException, InterruptedException {
        String xmlFile = "src/test/resources/emp.xml";
        Assert.assertEquals("Entire file", 21, run(
                new XmlEventEmitter.XmlEventEmitterBuilder()
                .setXmlFile(xmlFile).create()));
        Assert.assertEquals("Skip 5 records", 16, run(
                new XmlEventEmitter.XmlEventEmitterBuilder()
                .setXmlFile(xmlFile).setSkipRecs(5).create()));
        Assert.assertEquals("Skip 5 and pick first 4", 4, run(
                new XmlEventEmitter.XmlEventEmitterBuilder()
                .setXmlFile(xmlFile).setSkipRecs(5).setFirstNRecs(4).create()));
        Assert.assertEquals("First 4 records", 4, run(
                new XmlEventEmitter.XmlEventEmitterBuilder()
                .setXmlFile(xmlFile).setSkipRecs(0).setFirstNRecs(4).create()));
        Assert.assertEquals("Overshoot the end", 3, run(
                new XmlEventEmitter.XmlEventEmitterBuilder()
                .setXmlFile(xmlFile).setSkipRecs(18).setFirstNRecs(10).create()));
    }
    private long run(XmlEventEmitter emitter)
            throws IOException, XMLStreamException, InterruptedException {

        int numWorkers = 3;
        String outDir = "target/test/results/emp_eventstream_splits";
        new File(outDir).mkdirs();
        CountDownLatch workerCounter = new CountDownLatch(numWorkers);
        Thread[] workers = new Thread[numWorkers];
        for (int i = numWorkers; i > 0; i--) {
            Pipe pipe = Pipe.open();
            emitter.registerChannel(pipe.sink());
            Thread worker = newWorkerThread(pipe.source(), numWorkers-i+1,
                    outDir, workerCounter);
            workers[i-1] = worker;
            worker.start();
        }

        emitter.startStream();
        workerCounter.await();
        return emitter.getRecCounter();
    }

    private Thread newWorkerThread(final Pipe.SourceChannel channel, int channelNum, String outDir,
                                   CountDownLatch workerCounter) {
        return new Thread(() -> {
            XMLEventReader reader;
            try {
                reader = XMLInputFactory.newFactory().createXMLEventReader(Channels.newInputStream(channel));
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
