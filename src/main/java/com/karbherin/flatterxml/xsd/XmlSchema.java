package com.karbherin.flatterxml.xsd;

import static com.karbherin.flatterxml.helper.XmlHelpers.extractAttrValue;
import static com.karbherin.flatterxml.helper.XmlHelpers.parsePrefixTag;

import javax.xml.XMLConstants;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.*;
import java.util.stream.Collectors;

public class XmlSchema {

    private String targetNamespace;
    private StartElement schema;
    private final Stack<XMLEvent> elStack = new Stack<>();
    private Map<QName, List<XsdElement>> complexTypes = new HashMap<>();
    private final Map<QName, XsdElement> elementTypes = new HashMap<>();
    public static final QName ELEMENT = new QName(XMLConstants.W3C_XML_SCHEMA_NS_URI,"element"),
            NAME = new QName(XMLConstants.W3C_XML_SCHEMA_NS_URI,"name"),
            TYPE = new QName(XMLConstants.W3C_XML_SCHEMA_NS_URI,"type"),
            REF = new QName(XMLConstants.W3C_XML_SCHEMA_NS_URI,"ref"),
            SCHEMA = new QName(XMLConstants.W3C_XML_SCHEMA_NS_URI,"schema"),
            COMPLEX_TYPE = new QName(XMLConstants.W3C_XML_SCHEMA_NS_URI, "complexType");

    public XmlSchema parse(String xsdFile) throws FileNotFoundException, XMLStreamException {
        parse(new File(xsdFile));
        return this;
    }

    public void parse(File xsdFile) throws FileNotFoundException, XMLStreamException {
        XMLEventReader reader = XMLInputFactory.newFactory().createXMLEventReader(
                xsdFile.getPath(), new FileInputStream(xsdFile));

        while (reader.hasNext())
            examine(reader.nextEvent());

        resolveReferences();
    }

    public XsdElement getElementByName(String name) {
        return elementTypes.get(parsePrefixTag(name, schema.getNamespaceContext(), targetNamespace));
    }

    public XsdElement getElementByName(QName qName) {
        return elementTypes.get(qName);
    }

    private void resolveReferences() {
        // Resolve any references. Any element type with ref != null and type=null needs resolution.
        complexTypes.values().forEach(
                children -> children.stream()
                        .filter(child -> child.getRef() != null
                                && child.getType() == null
                                && elementTypes.containsKey(child.getRef()))
                        .forEach(child -> XsdElement.copyDefinitionAttrs(
                                elementTypes.get(child.getRef()), child)));
        complexTypes.remove(null);
        complexTypes.entrySet()
                .forEach(ent -> elementTypes.get(ent.getKey()).setChildElements(ent.getValue()));
    }

    public static void resolveReferences(List<XmlSchema> xsds) {
        // All elements from all XSDs
        Map<QName, XsdElement> allElementTypes = xsds.stream()
                .flatMap(xsd -> xsd.elementTypes.entrySet().stream())
                .collect(Collectors.toMap(ent -> ent.getKey(), ent -> ent.getValue()));

        // Resolve any references. Any element type with ref != null and type=null needs resolution.
        xsds.stream().forEach(xsd ->
            xsd.complexTypes.values().forEach(
                    children -> children.stream()
                            .filter(child -> child.getRef() != null
                                    && child.getType() == null
                                    && allElementTypes.containsKey(child.getRef()))
                            .forEach(child -> XsdElement.copyDefinitionAttrs(
                                    allElementTypes.get(child.getRef()), child))));

        // Clear the memory for complex types
        xsds.stream().forEach(xsd -> xsd.complexTypes = null);
    }

    private void examine(XMLEvent el) {
        if (el.isStartElement())
            examine(el.asStartElement());
        else if (el.isEndElement())
            examine(el.asEndElement());
    }

    private void examine(StartElement el) {
        if (isXsdSchema(el)) {
            setupSchema(el);
            elStack.push(el);
            return;
        }

        if (isXsdComplexType(el))
            elementTypes.get(extractAttrValue(elStack.peek().asStartElement(), NAME, targetNamespace))
                    .setType(COMPLEX_TYPE);

        if (!isXsdElement(el))
            return;

        if (!elStack.isEmpty()) {

            QName parentName = extractAttrValue(elStack.peek().asStartElement(), NAME, targetNamespace);
            List<XsdElement> children = complexTypes.get(parentName);
            if (children == null) {
                children = new ArrayList<>();
                complexTypes.put(parentName, children);
            }

            XsdElement xsdEl = new XsdElement(el, targetNamespace);
            if (xsdEl.getName() != null)
                elementTypes.put(xsdEl.getName(), xsdEl);

            children.add(xsdEl);
        }

        elStack.push(el);
    }

    private void examine(EndElement el) {
        if (isXsdElement(el) || isXsdSchema(el))
            elStack.pop();
    }

    private void setupSchema(StartElement el) {
        Attribute attr = el.getAttributeByName(QName.valueOf("targetNamespace"));
        if (attr != null)
            targetNamespace = attr.getValue();
        schema = el;
    }

    private static boolean isXsdElement(XMLEvent el) {
        return el.isStartElement() && ELEMENT.equals(el.asStartElement().getName()) ||
            el.isEndElement() && ELEMENT.equals(el.asEndElement().getName());
    }

    private static boolean isXsdComplexType(XMLEvent el) {
        if (el.isStartElement() && COMPLEX_TYPE.equals(el.asStartElement().getName()) ||
                el.isEndElement() && COMPLEX_TYPE.equals(el.asEndElement().getName())) {
            return true;
        }
        return false;
    }

    private static boolean isXsdSchema(XMLEvent el) {
        return el.isStartElement() && SCHEMA.equals(el.asStartElement().getName()) ||
                el.isEndElement() && SCHEMA.equals(el.asEndElement().getName());
    }

    public String getTargetNamespace() {
        return targetNamespace;
    }

    public StartElement getSchema() {
        return schema;
    }
}
