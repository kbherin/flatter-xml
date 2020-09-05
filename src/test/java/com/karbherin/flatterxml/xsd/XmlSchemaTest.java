package com.karbherin.flatterxml.xsd;

import static org.junit.Assert.*;

import static com.karbherin.flatterxml.XmlHelpers.iteratorStream;

import com.karbherin.flatterxml.XmlHelpers;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.xml.XMLConstants;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Namespace;
import java.io.FileNotFoundException;
import java.util.Iterator;
import java.util.stream.Collectors;

public class XmlSchemaTest {

    private static XmlSchema xsdModel;

    private static QName STRING_QNAME, INTEGER_QNAME;

    @BeforeClass
    public static void setUp() throws FileNotFoundException, XMLStreamException {
        xsdModel = new XmlSchema();
        xsdModel.parse("src/test/resources/contact.xsd");
        STRING_QNAME = XmlHelpers.parsePrefixTag("xs:string", xsdModel.getSchema().getNamespaceContext(), xsdModel.getTargetNamespace());
        INTEGER_QNAME = XmlHelpers.parsePrefixTag("xs:integer", xsdModel.getSchema().getNamespaceContext(), xsdModel.getTargetNamespace());
    }

    @Test
    public void test_namespaces() {
        assertEquals("http://kbps.com", xsdModel.getTargetNamespace());
        assertEquals(1, ((Long) iteratorStream(xsdModel.getSchema().getNamespaces()).count()).longValue());
        XMLEventFactory factory = XMLEventFactory.newInstance();
        for (Iterator<Namespace> it = xsdModel.getSchema().getNamespaces(); it.hasNext(); ) {
            Namespace ns = it.next();
            assertEquals(XMLConstants.W3C_XML_SCHEMA_NS_URI, ns.getNamespaceURI());
            assertEquals("xs", ns.getPrefix());
            System.out.println(ns + " == " + factory.createNamespace("xs", XMLConstants.W3C_XML_SCHEMA_NS_URI));
            System.out.println("prefix,namespaceURI=" + ns.getPrefix()+","+ns.getNamespaceURI());
        }

        assertTrue(iteratorStream((Iterator<Namespace>) xsdModel.getSchema().getNamespaces())
                .map(n -> n.getName()).collect(Collectors.toSet())
                .contains(factory.createNamespace("xs", XMLConstants.W3C_XML_SCHEMA_NS_URI).getName()));
    }

    @Test
    public void test_complexType()  {
        XsdElement contact = xsdModel.getElementByName("contact");
        assertEquals(XmlSchema.COMPLEX_TYPE, contact.getType());
        assertNotNull(contact.getChildElements());
        assertFalse(contact.getChildElements().isEmpty());
        assertNull(contact.getMaxOccurs());
        assertEquals(0, contact.getMinOccurs());
        assertNull(contact.getRef());
        assertFalse(contact.isList());
    }

    @Test
    public void test_contact()  {
        XsdElement contact = xsdModel.getElementByName("contact");
        assertEquals(XmlSchema.COMPLEX_TYPE, contact.getType());
        assertEquals(1,         contact.getChildElements().size());
        assertEquals(new QName(xsdModel.getTargetNamespace(),"address"), contact.getChildElements().get(0).getName());
        assertTrue(contact.getChildElements().get(0).isList());
    }

    @Test
    public void test_address()  {
        XsdElement address = xsdModel.getElementByName("address");
        assertEquals(XmlSchema.COMPLEX_TYPE,    address.getType());
        assertEquals(3,        address.getChildElements().size());
        assertEquals(new QName(xsdModel.getTargetNamespace(), "name"),   address.getChildElements().get(0).getName());
        assertFalse(address.getChildElements().get(0).isList());
        assertEquals(new QName(xsdModel.getTargetNamespace(),"street"), address.getChildElements().get(1).getName());
        assertEquals(new QName(xsdModel.getTargetNamespace(),"phone"),  address.getChildElements().get(2).getName());
    }

    @Test
    public void test_phone()  {
        XsdElement phone = xsdModel.getElementByName("phone");
        assertEquals(XmlSchema.COMPLEX_TYPE, phone.getType());
        assertEquals(2,             phone.getChildElements().size());
        assertEquals(new QName(xsdModel.getTargetNamespace(),"phoneNumber"), phone.getChildElements().get(0).getName());
        assertEquals(new QName(xsdModel.getTargetNamespace(),"phoneType"),   phone.getChildElements().get(1).getName());
    }

    @Test
    public void test_simpleType() {
        XsdElement name = xsdModel.getElementByName("name");
        assertNull(name.getRef());
        assertNull(name.getMaxOccurs());
        assertNotNull(name.getChildElements());
        assertTrue(name.getChildElements().isEmpty());
        assertEquals(STRING_QNAME, name.getType());
        assertEquals(0, name.getMinOccurs());
    }

    @Test
    public void test_name() {
        XsdElement name = xsdModel.getElementByName("name");
        assertEquals(STRING_QNAME, name.getType());
    }

    @Test
    public void test_street() {
        XsdElement street = xsdModel.getElementByName("street");
        assertEquals(STRING_QNAME, street.getType());
    }

    @Test
    public void test_phoneNumber() {
        XsdElement phoneNumber = xsdModel.getElementByName("phoneNumber");
        assertEquals(INTEGER_QNAME, phoneNumber.getType());
    }

    @Test
    public void test_phoneType() {
        XsdElement phoneType = xsdModel.getElementByName("phoneType");
        assertEquals(STRING_QNAME, phoneType.getType());
    }

}
