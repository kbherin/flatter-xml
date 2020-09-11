package com.karbherin.flatterxml.feeder;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class XmlRecordScannerTest {
    @Test
    public void test() throws IOException, XMLStreamException {
        XmlRecordScanner emitter = new XmlRecordScanner("src/test/resources/emp_ns.xml", 0, 0);
        emitter.startStream();
        Path inputFile = Paths.get("src/test/resources/emp_ns.xml");
        Path outputFile = Paths.get("target/test/resources/out_emp_ns.xml");
        assertEquals(inputFile.toFile().length(), outputFile.toFile().length());
    }
}
