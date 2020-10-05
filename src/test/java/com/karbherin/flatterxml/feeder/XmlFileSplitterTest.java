package com.karbherin.flatterxml.feeder;

import com.karbherin.flatterxml.consumer.XmlEventWorkerPool;
import com.karbherin.flatterxml.consumer.XmlFileSplitterFactory;
import org.junit.Assert;
import org.junit.Test;

import javax.xml.stream.XMLStreamException;
import java.io.IOException;

public class XmlFileSplitterTest {

    @Test
    public void splitter_test() throws IOException, XMLStreamException, InterruptedException {
        String xmlFilePath = "src/test/resources/emp.xml";
        String outDir = "target/test/results/emp_textfile_splits";;
        XmlEventWorkerPool workerPool = new XmlEventWorkerPool();
        XmlFileSplitterFactory workerFactory = XmlFileSplitterFactory.newInstance(outDir, xmlFilePath);

        Assert.assertEquals("Entire file", 21,
                workerPool.execute(3, new XmlEventEmitter.XmlEventEmitterBuilder()
                        .setXmlFile(xmlFilePath).create(), workerFactory));
        Assert.assertEquals("Skip 5 records", 16,
                workerPool.execute(3, new XmlEventEmitter.XmlEventEmitterBuilder()
                        .setXmlFile(xmlFilePath).setSkipRecs(5).create(), workerFactory));
        Assert.assertEquals("Skip 5 and pick first 4", 4,
                workerPool.execute(3, new XmlEventEmitter.XmlEventEmitterBuilder()
                        .setXmlFile(xmlFilePath).setSkipRecs(5).setFirstNRecs(4).create(), workerFactory));
        Assert.assertEquals("First 4 records", 4,
                workerPool.execute(3, new XmlEventEmitter.XmlEventEmitterBuilder()
                        .setXmlFile(xmlFilePath).setSkipRecs(0).setFirstNRecs(4).create(), workerFactory));
        Assert.assertEquals("Overshoot the end", 3,
                workerPool.execute(3, new XmlEventEmitter.XmlEventEmitterBuilder()
                        .setXmlFile(xmlFilePath).setSkipRecs(18).setFirstNRecs(10).create(), workerFactory));
    }

    @Test
    public void streamSplitter_test() throws IOException, XMLStreamException, InterruptedException {
        String xmlFilePath = "src/test/resources/emp.xml";
        String outDir = "target/test/results/emp_textstream_splits";
        XmlEventWorkerPool workerPool = new XmlEventWorkerPool();
        XmlFileSplitterFactory workerFactory = XmlFileSplitterFactory.newInstance(outDir, xmlFilePath);

        Assert.assertEquals("Entire file", 21,
                workerPool.execute(3, new XmlByteStreamEmitter.XmlByteStreamEmitterBuilder()
                        .setXmlFile(xmlFilePath).create(), workerFactory));
        Assert.assertEquals("Skip 5 records", 16,
                workerPool.execute(3, new XmlByteStreamEmitter.XmlByteStreamEmitterBuilder()
                        .setXmlFile(xmlFilePath).setSkipRecs(5).create(), workerFactory));
        Assert.assertEquals("Skip 5 and pick first 4", 4,
                workerPool.execute(3, new XmlByteStreamEmitter.XmlByteStreamEmitterBuilder()
                        .setXmlFile(xmlFilePath).setSkipRecs(5).setFirstNRecs(4).create(), workerFactory));
        Assert.assertEquals("First 4 records", 4,
                workerPool.execute(3, new XmlByteStreamEmitter.XmlByteStreamEmitterBuilder()
                        .setXmlFile(xmlFilePath).setSkipRecs(0).setFirstNRecs(4).create(), workerFactory));
        Assert.assertEquals("Overshoot the end", 3,
                workerPool.execute(3, new XmlByteStreamEmitter.XmlByteStreamEmitterBuilder()
                        .setXmlFile(xmlFilePath).setSkipRecs(18).setFirstNRecs(10).create(), workerFactory));
    }
}
