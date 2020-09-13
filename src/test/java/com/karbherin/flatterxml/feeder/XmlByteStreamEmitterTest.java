package com.karbherin.flatterxml.feeder;

import static org.junit.Assert.assertEquals;

import com.karbherin.flatterxml.consumer.XmlEventWorkerFactory;
import com.karbherin.flatterxml.consumer.XmlEventWorkerPool;
import com.karbherin.flatterxml.consumer.XmlFileSplitterFactory;
import static com.karbherin.flatterxml.feeder.XmlByteStreamEmitter.XmlByteStreamEmitterBuilder;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import javax.xml.stream.XMLStreamException;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.Pipe;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.CountDownLatch;

public class XmlByteStreamEmitterTest {
    /*@Test
    public void test() throws IOException, XMLStreamException {
        XmlByteStreamEmitter emitter = new XmlByteStreamEmitterBuilder()
                .setXmlFile("src/test/resources/emp_ns.xml")
                .setSkipRecs(0)
                .setFirstNRecs(0)
                .create();
        Path inputFile = Paths.get("src/test/resources/emp_ns.xml");
        Path outputFile = Paths.get("target/test/resources/out_emp_ns.xml");

        WritableByteChannel writer = Files.newByteChannel(
                Paths.get("target/test/resources/out_emp_ns.xml"),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
        emitter.registerChannel(writer);

        emitter.startStream();
        assertEquals(inputFile.toFile().length(), outputFile.toFile().length());
        assertEquals(3, emitter.getRecCounter());
    }*/

    @Test
    public void splitter_test() throws IOException, XMLStreamException, InterruptedException {
        String xmlFilePath = "src/test/resources/emp.xml";
        String outDir = "target/test/resources/emp_tables/splits";
        XmlEventWorkerPool workerPool = new XmlEventWorkerPool();
        XmlFileSplitterFactory workerFactory = XmlFileSplitterFactory.newInstance(outDir, xmlFilePath);

        Assert.assertEquals("Entire file", 21,
                workerPool.execute(1,
                        new XmlByteStreamEmitterBuilder().setXmlFile(xmlFilePath).create(), new Abc()));
        /*Assert.assertEquals("Skip 5 records", 16,
                workerPool.execute(3, new XmlByteStreamEmitter(xmlFilePath, 5), workerFactory));
        Assert.assertEquals("Skip 5 and pick first 4", 4,
                workerPool.execute(3, new XmlByteStreamEmitter(xmlFilePath, 5, 4), workerFactory));
        Assert.assertEquals("First 4 records", 4,
                workerPool.execute(3, new XmlByteStreamEmitter(xmlFilePath, 0, 4), workerFactory));
        Assert.assertEquals("Overshoot the end", 3,
                workerPool.execute(3, new XmlByteStreamEmitter(xmlFilePath, 18, 10), workerFactory));*/
    }

    private static class Abc implements XmlEventWorkerFactory {

        @Override
        public Runnable newWorker(Pipe.SourceChannel channel, CountDownLatch workerCounter) {
            return () -> {
                try {
                    OutputStream out = Files.newOutputStream(Paths.get("target/test/resources/emp_out.xml"));
                    InputStream ist = Channels.newInputStream(channel);
                    byte[] bytes = new byte[1024];

                    while (ist.read(bytes) > 0) {
                        out.write(bytes);
                    }

                } catch (IOException e) {
                    e.printStackTrace();
                    throw new RuntimeException(e);
                } finally {
                    workerCounter.countDown();
                }
            };
        }
    }
}
