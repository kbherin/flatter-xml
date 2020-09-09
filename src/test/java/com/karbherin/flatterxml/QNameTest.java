package com.karbherin.flatterxml;

import org.junit.Assert;
import org.junit.Test;

import javax.xml.namespace.QName;

/**
 * To understand the behavior of QName.
 */
public class QNameTest {
    @Test
    public void testValueOf_Local() {
        QName qName = QName.valueOf("employee");
        Assert.assertEquals("employee", qName.getLocalPart());
        Assert.assertEquals("", qName.getNamespaceURI());
        Assert.assertEquals("", qName.getPrefix());
    }

    @Test
    public void testValueOf_Prefixed() {
        QName qName = QName.valueOf("em:employee");
        Assert.assertEquals("em:employee", qName.getLocalPart());
        Assert.assertEquals("", qName.getNamespaceURI());
        Assert.assertEquals("", qName.getPrefix());
    }

    @Test
    public void testValueOf_Uri() {
        QName qName = QName.valueOf("{http://kbps.com/emp}employee");
        Assert.assertEquals("employee", qName.getLocalPart());
        Assert.assertEquals("http://kbps.com/emp", qName.getNamespaceURI());
        Assert.assertEquals("", qName.getPrefix());
    }

    @Test
    public void testQName_StrWithURI() {
        QName qName = new QName("{http://kbps.com/emp}employee");
        Assert.assertEquals("{http://kbps.com/emp}employee", qName.getLocalPart());
        Assert.assertEquals("", qName.getNamespaceURI());
        Assert.assertEquals("", qName.getPrefix());
    }

    @Test
    public void testQName_StrPrefixed() {
        QName qName = new QName("em:employee");
        Assert.assertEquals("em:employee", qName.getLocalPart());
        Assert.assertEquals("", qName.getNamespaceURI());
        Assert.assertEquals("", qName.getPrefix());
    }

    @Test
    public void testQName_AllParts() {
        QName qName = new QName("http://kbps.com/emp", "employee", "em");
        Assert.assertEquals("employee", qName.getLocalPart());
        Assert.assertEquals("http://kbps.com/emp", qName.getNamespaceURI());
        Assert.assertEquals("em", qName.getPrefix());
    }

    @Test
    public void testQName_LocalUri() {
        QName qName = new QName("http://kbps.com/emp", "employee");
        Assert.assertEquals("employee", qName.getLocalPart());
        Assert.assertEquals("http://kbps.com/emp", qName.getNamespaceURI());
        Assert.assertEquals("", qName.getPrefix());
    }
}
