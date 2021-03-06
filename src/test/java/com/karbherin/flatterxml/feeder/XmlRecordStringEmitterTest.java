package com.karbherin.flatterxml.feeder;

import static org.junit.Assert.assertEquals;

import com.karbherin.flatterxml.consumer.XmlEventWorkerFactory;
import com.karbherin.flatterxml.consumer.XmlEventWorkerPool;
import com.karbherin.flatterxml.consumer.XmlFileSplitterFactory;
import static com.karbherin.flatterxml.feeder.XmlRecordStringEmitter.XmlByteStreamEmitterBuilder;

import com.karbherin.flatterxml.helper.XmlHelpers;
import org.junit.Test;

import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.Pipe;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CountDownLatch;

public class XmlRecordStringEmitterTest {
    /*@Test
    public void test() throws IOException, XMLStreamException {
        XmlRecordStringEmitter emitter = new XmlByteStreamEmitterBuilder()
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
        String outDir = "target/test/results/emp_bytestream_splits";
        XmlEventWorkerPool workerPool = new XmlEventWorkerPool();
        XmlFileSplitterFactory workerFactory = XmlFileSplitterFactory.newInstance(outDir, xmlFilePath);

        assertEquals("Entire file single threaded", 21,
                workerPool.execute(1, new XmlByteStreamEmitterBuilder().setXmlFile(xmlFilePath).create(),
                        new XmlPipeToFileWriter(outDir, "emp_bytestream.xml")));
        assertEquals("Skip 5 records", 16,
                workerPool.execute(3, new XmlRecordStringEmitter.XmlByteStreamEmitterBuilder().setXmlFile(xmlFilePath)
                        .setSkipRecs(5).create(), workerFactory));
        assertEquals("Skip 5 records", 16,
                workerPool.execute(3, new XmlRecordStringEmitter.XmlByteStreamEmitterBuilder().setXmlFile(xmlFilePath)
                        .setSkipRecs(5).create(), workerFactory));
        assertEquals("Skip 5 and pick first 4", 4,
                workerPool.execute(3, new XmlRecordStringEmitter.XmlByteStreamEmitterBuilder().setXmlFile(xmlFilePath)
                        .setSkipRecs(5).setFirstNRecs(4).create(), workerFactory));
        assertEquals("First 4 records", 4,
                workerPool.execute(3, new XmlRecordStringEmitter.XmlByteStreamEmitterBuilder().setXmlFile(xmlFilePath)
                        .setSkipRecs(0).setFirstNRecs(4).create(), workerFactory));
        XmlRecordEmitter emitter = new XmlRecordStringEmitter.XmlByteStreamEmitterBuilder().setXmlFile(xmlFilePath)
                .setSkipRecs(18).setFirstNRecs(10).create();
        assertEquals("Overshoot the end", 3,
                workerPool.execute(3, emitter, workerFactory));

        assertEquals("employees", XmlHelpers.toPrefixedTag(emitter.getRootTag()));
        assertEquals("employee", XmlHelpers.toPrefixedTag(emitter.getRecordTag()));
    }

    @Test
    public void splitterNSXml_test() throws IOException, XMLStreamException, InterruptedException {
        String xmlFilePath = "src/test/resources/emp.xml";
        String outDir = "target/test/results/emp_bytestream_splits";
        XmlRecordEmitter emitter = new XmlRecordStringEmitter.XmlByteStreamEmitterBuilder()
                .setXmlFile("src/test/resources/emp_ns.xml").setSkipRecs(1).setFirstNRecs(10).create();
        XmlFileSplitterFactory workerFactory = XmlFileSplitterFactory.newInstance(outDir, xmlFilePath);
        XmlEventWorkerPool workerPool = new XmlEventWorkerPool();
        assertEquals("Overshoot the end", 2,
                workerPool.execute(3, emitter, workerFactory));

        assertEquals("emp:employees", XmlHelpers.toPrefixedTag(emitter.getRootTag()));
        assertEquals("emp:employee", XmlHelpers.toPrefixedTag(emitter.getRecordTag()));
        assertEquals("employees", emitter.getRootTag().getLocalPart());
        assertEquals("employee", emitter.getRecordTag().getLocalPart());
    }

    private static class XmlPipeToFileWriter implements XmlEventWorkerFactory {

        private Path outputFilePath;
        public XmlPipeToFileWriter(String outDir, String outputFile) {
            this.outputFilePath = Paths.get(outDir + "/" + outputFile);
        }

        @Override
        public Runnable newWorker(Pipe.SourceChannel channel, CountDownLatch workerCounter) {

            return () -> {
                try {
                    OutputStream out = Files.newOutputStream(outputFilePath);
                    InputStream ist = Channels.newInputStream(channel);
                    byte[] bytes = new byte[1024];

                    int count;
                    while ((count = ist.read(bytes)) > -1) {
                        out.write(bytes, 0, count);
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
