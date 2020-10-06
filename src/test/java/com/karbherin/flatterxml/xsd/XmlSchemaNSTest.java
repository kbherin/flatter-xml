package com.karbherin.flatterxml.xsd;

import com.karbherin.flatterxml.helper.XmlHelpers;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.xml.XMLConstants;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Namespace;
import java.io.FileNotFoundException;
import java.util.Iterator;
import java.util.Set;
import java.util.stream.Collectors;

import static com.karbherin.flatterxml.helper.XmlHelpers.iteratorStream;
import static org.junit.Assert.*;

public class XmlSchemaNSTest {

    private static XmlSchema xsdModel;
    private static Set<Namespace> namespaces;
    private static Set<String> nsUris;
    private static Set<String> nsPrefixes;
    private static Set<QName> nsNames;
    private static XMLEventFactory factory = XMLEventFactory.newInstance();

    private static QName STRING_QNAME, INTEGER_QNAME;

    @BeforeClass
    public static void setUp() throws FileNotFoundException, XMLStreamException {
        xsdModel = new XmlSchema();
        xsdModel.parse("src/test/resources/emp_ns.xsd");
        namespaces = iteratorStream((Iterator<Namespace>) xsdModel.getSchema().getNamespaces())
                .collect(Collectors.toSet());
        nsUris = namespaces.stream().map(Namespace::getNamespaceURI).collect(Collectors.toSet());
        nsPrefixes = namespaces.stream().map(Namespace::getPrefix).collect(Collectors.toSet());
        nsNames = namespaces.stream().map(Namespace::getName).collect(Collectors.toSet());
        STRING_QNAME = XmlHelpers.parsePrefixTag("xs:string", xsdModel.getSchema().getNamespaceContext(), xsdModel.getTargetNamespace());
        INTEGER_QNAME = XmlHelpers.parsePrefixTag("xs:integer", xsdModel.getSchema().getNamespaceContext(), xsdModel.getTargetNamespace());
    }

    @Test
    public void test_namespaces() {
        assertEquals("http://kbps.com/emp", xsdModel.getTargetNamespace());
        assertEquals(3, ((Long) iteratorStream(xsdModel.getSchema().getNamespaces()).count()).longValue());

        assertTrue(nsUris.contains(XMLConstants.W3C_XML_SCHEMA_NS_URI));
        assertTrue(nsPrefixes.contains("xs"));
        assertTrue(nsNames.contains(
                factory.createNamespace("xs", XMLConstants.W3C_XML_SCHEMA_NS_URI).getName()));

        assertTrue(nsUris.contains("http://kbps.com/phone"));
        assertTrue(nsPrefixes.contains("ph"));
        assertTrue(nsNames.contains(
                factory.createNamespace("ph", "http://kbps.com/phone").getName()));

        assertTrue(nsUris.contains("http://kbps.com/emp"));
        assertTrue(nsPrefixes.contains(""));
        assertTrue(nsNames.contains(
                factory.createNamespace("", "http://kbps.com/emp").getName()));
    }

    @Test
    public void test_complexType()  {
        XsdElement employee = xsdModel.getElementByName("employee");
        assertEquals(XmlSchema.COMPLEX_TYPE, employee.getType());
        assertNotNull(employee.getChildElements());
        assertFalse(employee.getChildElements().isEmpty());
        assertNull(employee.getMaxOccurs());
        assertEquals(0, employee.getMinOccurs());
        assertNull(employee.getRef());
        assertFalse(employee.isList());
    }

    @Test
    public void test_employee()  {
        XsdElement employee = xsdModel.getElementByName("employee");
        assertEquals(XmlSchema.COMPLEX_TYPE, employee.getType());
        assertEquals(XmlSchema.COMPLEX_CONTENT, employee.getContent());
        assertEquals(6,         employee.getChildElements().size());

        assertEquals(new QName(xsdModel.getTargetNamespace(),"identifiers"),
                employee.getChildElements().get(0).getName());
        assertTrue(employee.getChildElements().get(0).isList());
        assertFalse(employee.getChildElements().get(0).isRequired());
        assertEquals(XmlSchema.SIMPLE_CONTENT, employee.getChildElements().get(0).getContent());

        assertEquals(new QName(xsdModel.getTargetNamespace(),"employee-no"),
                employee.getChildElements().get(1).getName());
        assertEquals(new QName(XMLConstants.W3C_XML_SCHEMA_NS_URI, "integer"),
                employee.getChildElements().get(1).getType());
        assertFalse(employee.getChildElements().get(1).isList());
        assertTrue(employee.getChildElements().get(1).isRequired());
        assertEquals(XmlSchema.SIMPLE_CONTENT, employee.getChildElements().get(1).getContent());

        assertEquals(new QName(xsdModel.getTargetNamespace(),"employee-name"),
                employee.getChildElements().get(2).getName());
        assertFalse(employee.getChildElements().get(2).isList());
        assertTrue(employee.getChildElements().get(2).isRequired());
        assertEquals(XmlSchema.SIMPLE_CONTENT, employee.getChildElements().get(2).getContent());

        assertEquals(new QName(xsdModel.getTargetNamespace(),"department"),
                employee.getChildElements().get(3).getName());
        assertFalse(employee.getChildElements().get(3).isList());
        assertTrue(employee.getChildElements().get(3).isRequired());
        assertEquals(XmlSchema.SIMPLE_CONTENT, employee.getChildElements().get(3).getContent());

        assertEquals(new QName(xsdModel.getTargetNamespace(),"salary"),
                employee.getChildElements().get(4).getName());
        assertFalse(employee.getChildElements().get(4).isList());
        assertFalse(employee.getChildElements().get(4).isRequired());
        assertEquals(XmlSchema.SIMPLE_CONTENT, employee.getChildElements().get(4).getContent());

        assertEquals(new QName(xsdModel.getTargetNamespace(),"contact"),
                employee.getChildElements().get(5).getName());
        assertFalse(employee.getChildElements().get(5).isList());
        assertTrue(employee.getChildElements().get(5).isRequired());
        assertEquals(XmlSchema.COMPLEX_CONTENT, employee.getChildElements().get(5).getContent());
    }

    @Test
    public void test_address()  {
        XsdElement address = xsdModel.getElementByName("address");
        assertEquals(XmlSchema.COMPLEX_TYPE,    address.getType());
        assertEquals(6,        address.getChildElements().size());
        assertEquals(new QName(xsdModel.getTargetNamespace(), "address-type"),
                address.getChildElements().get(0).getName());
        assertFalse(address.getChildElements().get(0).isList());
    }

    @Test
    public void test_importedPhone()  {
        String phoneNsUri = "http://kbps.com/phone";
        XsdElement phone = xsdModel.getElementByName(String.format("phone", phoneNsUri));
        assertEquals(XmlSchema.COMPLEX_TYPE, phone.getType());
        assertEquals(2,             phone.getChildElements().size());
        assertEquals(new QName(phoneNsUri,"phone-num"), phone.getChildElements().get(0).getName());
        assertEquals(new QName(phoneNsUri,"phone-type"),   phone.getChildElements().get(1).getName());
    }

    @Test
    public void test_importedPhone2()  {
        String phoneNsUri = "http://kbps.com/phone";
        XsdElement phone = xsdModel.getElementByName(String.format("{%s}phone", phoneNsUri));
        assertEquals(XmlSchema.COMPLEX_TYPE, phone.getType());
        assertEquals(2,             phone.getChildElements().size());
        assertEquals(new QName(phoneNsUri,"phone-num"), phone.getChildElements().get(0).getName());
        assertEquals(new QName(phoneNsUri,"phone-type"),   phone.getChildElements().get(1).getName());
    }

    @Test
    public void test_simpleType() {
        XsdElement name = xsdModel.getElementByName("employee-name");
        assertNull(name.getRef());
        assertNull(name.getMaxOccurs());
        assertNotNull(name.getChildElements());
        assertTrue(name.getChildElements().isEmpty());
        assertEquals(STRING_QNAME, name.getType());
        assertEquals(0, name.getMinOccurs());
    }

    @Test
    public void test_name() {
        XsdElement name = xsdModel.getElementByName("employee-name");
        assertEquals(STRING_QNAME, name.getType());
    }

    @Test
    public void test_street() {
        XsdElement street = xsdModel.getElementByName("line1");
        assertEquals(STRING_QNAME, street.getType());
    }

    @Test
    public void test_phoneNum() {
        XsdElement phoneNum = xsdModel.getElementByName("phone-num");
        assertEquals(INTEGER_QNAME, phoneNum.getType());
        assertEquals(XmlSchema.SIMPLE_CONTENT, phoneNum.getContent());
        assertEquals(1, phoneNum.getElementAttributes().size());
        assertEquals(new QName(phoneNum.getTargetNamespace(), "contact-type"),
                phoneNum.getElementAttributes().get(0).getName());
    }

    @Test
    public void test_phoneType() {
        XsdElement phoneType = xsdModel.getElementByName("phone-type");
        assertEquals(STRING_QNAME, phoneType.getType());
        assertEquals(XmlSchema.SIMPLE_CONTENT, phoneType.getContent());
        assertTrue(phoneType.getElementAttributes().isEmpty());
    }

    @Test
    public void test_contact() {
        XsdElement contact = xsdModel.getElementByName("contact");
        assertEquals(XmlSchema.COMPLEX_TYPE, contact.getType());
        assertEquals(XmlSchema.COMPLEX_CONTENT, contact.getContent());
        assertEquals(2, contact.getChildElements().size());
        assertEquals(0, contact.getElementAttributes().size());
        assertEquals("Reference type exists in same XSD",
                new QName(contact.getTargetNamespace(), "addresses"),
                contact.getChildElements().get(0).getName());

        assertEquals("Reference type exists in an imported XSD",
                new QName("http://kbps.com/phone", "phones"),
                contact.getChildElements().get(1).getName());
    }

}
