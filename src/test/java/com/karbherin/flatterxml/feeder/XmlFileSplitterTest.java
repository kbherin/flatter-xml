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
        String outDir = "target/test/resources/emp_tables/splits";
        XmlEventWorkerPool workerPool = new XmlEventWorkerPool();
        XmlFileSplitterFactory workerFactory = XmlFileSplitterFactory.newInstance(outDir, xmlFilePath);

        Assert.assertEquals("Entire file", 21,
                workerPool.execute(3, new XmlEventEmitter(xmlFilePath), workerFactory));
        Assert.assertEquals("Skip 5 records", 16,
                workerPool.execute(3, new XmlEventEmitter(xmlFilePath, 5), workerFactory));
        Assert.assertEquals("Skip 5 and pick first 4", 4,
                workerPool.execute(3, new XmlEventEmitter(xmlFilePath, 5, 4), workerFactory));
        Assert.assertEquals("First 4 records", 4,
                workerPool.execute(3, new XmlEventEmitter(xmlFilePath, 0, 4), workerFactory));
        Assert.assertEquals("Overshoot the end", 3,
                workerPool.execute(3, new XmlEventEmitter(xmlFilePath, 18, 10), workerFactory));
    }

    @Test
    public void streamSplitter_test() throws IOException, XMLStreamException, InterruptedException {
        String xmlFilePath = "src/test/resources/emp.xml";
        String outDir = "target/test/resources/emp_tables/splits";
        XmlEventWorkerPool workerPool = new XmlEventWorkerPool();
        XmlFileSplitterFactory workerFactory = XmlFileSplitterFactory.newInstance(outDir, xmlFilePath);

        Assert.assertEquals("Entire file", 21,
                workerPool.execute(3, new XmlByteStreamEmitter(xmlFilePath), workerFactory));
        Assert.assertEquals("Skip 5 records", 16,
                workerPool.execute(3, new XmlByteStreamEmitter(xmlFilePath, 5), workerFactory));
        Assert.assertEquals("Skip 5 and pick first 4", 4,
                workerPool.execute(3, new XmlByteStreamEmitter(xmlFilePath, 5, 4), workerFactory));
        Assert.assertEquals("First 4 records", 4,
                workerPool.execute(3, new XmlByteStreamEmitter(xmlFilePath, 0, 4), workerFactory));
        Assert.assertEquals("Overshoot the end", 3,
                workerPool.execute(3, new XmlByteStreamEmitter(xmlFilePath, 18, 10), workerFactory));
    }
}
