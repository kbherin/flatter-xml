package com.karbherin.flatterxml.xsd;

import static org.junit.Assert.*;

import org.junit.BeforeClass;
import org.junit.Test;

import javax.xml.XMLConstants;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Namespace;
import java.io.FileNotFoundException;
import java.util.stream.Collectors;

public class XsdModelTest {

    private static XsdModel xsdModel;

    @BeforeClass
    public static void setUp() throws FileNotFoundException, XMLStreamException {
        xsdModel = new XsdModel();
        xsdModel.parse("src/test/resources/contact.xsd");
    }

    @Test
    public void test_namespaces() {
        assertEquals("bk", xsdModel.getTargetNamespace());
        assertEquals(1, xsdModel.getNamespaces().size());
        XMLEventFactory factory = XMLEventFactory.newInstance();
        for (Namespace ns: xsdModel.getNamespaces()) {
            assertEquals(XMLConstants.W3C_XML_SCHEMA_NS_URI, ns.getNamespaceURI());
            assertEquals("xs", ns.getPrefix());
            System.out.println(ns + " == " + factory.createNamespace("xs", XMLConstants.W3C_XML_SCHEMA_NS_URI));
            System.out.println("prefix,namespaceURI=" + ns.getPrefix()+","+ns.getNamespaceURI());
        }
        assertTrue(xsdModel.getNamespaces().stream()
                .map(n -> n.getName()).collect(Collectors.toSet()).contains(
                factory.createNamespace("xs", XMLConstants.W3C_XML_SCHEMA_NS_URI).getName()));
    }

    @Test
    public void test_complexType()  {
        XsdElement contact = xsdModel.getElementByName("contact");
        assertEquals("complexType", contact.getType());
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
        assertEquals("complexType", contact.getType());
        assertEquals(1,         contact.getChildElements().size());
        assertEquals("address", contact.getChildElements().get(0).getName());
        assertTrue(contact.getChildElements().get(0).isList());
    }

    @Test
    public void test_address()  {
        XsdElement address = xsdModel.getElementByName("address");
        assertEquals("complexType",    address.getType());
        assertEquals(3,        address.getChildElements().size());
        assertEquals("name",   address.getChildElements().get(0).getName());
        assertFalse(address.getChildElements().get(0).isList());
        assertEquals("street", address.getChildElements().get(1).getName());
        assertEquals("phone",  address.getChildElements().get(2).getName());
    }

    @Test
    public void test_phone()  {
        XsdElement phone = xsdModel.getElementByName("phone");
        assertEquals("complexType", phone.getType());
        assertEquals(2,             phone.getChildElements().size());
        assertEquals("phoneNumber", phone.getChildElements().get(0).getName());
        assertEquals("phoneType",   phone.getChildElements().get(1).getName());
    }

    @Test
    public void test_simpleType() {
        XsdElement name = xsdModel.getElementByName("name");
        assertNull(name.getRef());
        assertNull(name.getMaxOccurs());
        assertNull(name.getChildElements());
        assertEquals("xs:string", name.getType());
        assertEquals(0, name.getMinOccurs());
    }

    @Test
    public void test_name() {
        XsdElement name = xsdModel.getElementByName("name");
        assertEquals("xs:string", name.getType());
    }

    @Test
    public void test_street() {
        XsdElement street = xsdModel.getElementByName("street");
        assertEquals("xs:string", street.getType());
    }

    @Test
    public void test_phoneNumber() {
        XsdElement phoneNumber = xsdModel.getElementByName("phoneNumber");
        assertEquals("xs:integer", phoneNumber.getType());
    }

    @Test
    public void test_phoneType() {
        XsdElement phoneType = xsdModel.getElementByName("phoneType");
        assertEquals("xs:string", phoneType.getType());
    }

}
