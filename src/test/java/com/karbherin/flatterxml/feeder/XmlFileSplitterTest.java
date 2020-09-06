package com.karbherin.flatterxml.feeder;

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

        Assert.assertEquals(21,
                workerPool.execute(3, new XmlEventEmitter(xmlFilePath), workerFactory));
    }
}
