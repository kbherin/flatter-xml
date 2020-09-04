package com.karbherin.flatterxml;

import org.junit.Test;
import org.xml.sax.SAXException;

import java.io.IOException;

public class XmlHelpersTest {
    @Test
    public void validateXmlTest() throws IOException, SAXException {
        XmlHelpers.validateXml("src/test/resources/emp.xml", new String[]{"src/test/resources/emp.xsd"});
    }

    @Test(expected = SAXException.class)
    public void validateXmlBadTest() throws IOException, SAXException {
        XmlHelpers.validateXml("src/test/resources/emp_bad.xml", new String[]{"src/test/resources/emp.xsd"});
    }
}
