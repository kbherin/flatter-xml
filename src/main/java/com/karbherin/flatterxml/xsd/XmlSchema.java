package com.karbherin.flatterxml.xsd;

import com.karbherin.flatterxml.model.OpenCan;

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
import java.util.stream.Stream;

import static com.karbherin.flatterxml.helper.XmlHelpers.*;

public class XmlSchema {

    private String targetNamespace;
    private StartElement schema;
    private final Map<Namespace, XmlSchema> importedSchemas = new HashMap<>();
    private final Deque<StartElement> elStack = new ArrayDeque<>();
    private final Map<QName, List<XsdSchemaElement>> complexTypes = new HashMap<>();
    private final Map<QName, XsdSchemaElement> elementTypes = new HashMap<>();
    private XsdSchemaElement currentXsdElement;
    private File xsdFile;
    public static final QName ELEMENT = new QName(XMLConstants.W3C_XML_SCHEMA_NS_URI,"element"),
            NAME = new QName(XMLConstants.W3C_XML_SCHEMA_NS_URI,"name"),
            TYPE = new QName(XMLConstants.W3C_XML_SCHEMA_NS_URI,"type"),
            REF = new QName(XMLConstants.W3C_XML_SCHEMA_NS_URI,"ref"),
            SCHEMA = new QName(XMLConstants.W3C_XML_SCHEMA_NS_URI,"schema"),
            IMPORT = new QName(XMLConstants.W3C_XML_SCHEMA_NS_URI,"import"),
            NAMESPACE = new QName(EMPTY,"namespace"),
            SCHEMA_LOCATION = new QName(EMPTY,"schemaLocation"),
            COMPLEX_TYPE = new QName(XMLConstants.W3C_XML_SCHEMA_NS_URI, "complexType"),
            USE = new QName(XMLConstants.W3C_XML_SCHEMA_NS_URI, "use"),
            SIMPLE_CONTENT = new QName(XMLConstants.W3C_XML_SCHEMA_NS_URI, "simpleContent"),
            COMPLEX_CONTENT = new QName(XMLConstants.W3C_XML_SCHEMA_NS_URI, "complexContent"),
            EXTENSION = new QName(XMLConstants.W3C_XML_SCHEMA_NS_URI, "extension"),
            BASE = new QName(XMLConstants.W3C_XML_SCHEMA_NS_URI, "base"),
            ATTRIBUTE = new QName(XMLConstants.W3C_XML_SCHEMA_NS_URI, "attribute");

    public XmlSchema parse(String xsdFile) throws FileNotFoundException, XMLStreamException {
        this.xsdFile = new File(xsdFile);
        parse(this.xsdFile);
        return this;
    }

    public void parse(File xsdFile) throws FileNotFoundException, XMLStreamException {
        this.xsdFile = xsdFile;
        XMLEventReader reader = XMLInputFactory.newFactory().createXMLEventReader(
                xsdFile.getPath(), new FileInputStream(xsdFile));

        while (reader.hasNext())
            examine(reader.nextEvent());

        resolveReferences();
    }

    public XsdSchemaElement getElementByName(String name) {
        XsdSchemaElement elem = elementTypes.get(parsePrefixTag(name));
        if (elem == null) {
            elem = elementTypes.get(parsePrefixTag(name, schema.getNamespaceContext(), targetNamespace));
        }

        if (elem == null) {
            for (XmlSchema importedSchema : importedSchemas.values()) {
                elem = importedSchema.getElementByName(name);
                if (elem != null)
                    break;
            }
        }
        return elem;
    }

    public XsdSchemaElement getElementByName(QName qName) {
        XsdSchemaElement elem = elementTypes.get(qName);
        if (elem == null) {
            for (XmlSchema importedSchema : importedSchemas.values()) {
                elem = importedSchema.getElementByName(qName);
                if (elem != null)
                    break;
            }
        }

        return elem;
    }


    private void resolveReferences() {
        Map<QName, XsdSchemaElement> allElementTypes = Stream.concat(
                elementTypes.entrySet().stream(),
                importedSchemas.values().stream()
                        .flatMap(xsd -> xsd.elementTypes.entrySet().stream())
        ).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        // Resolve any references. Any element type with ref != null and type=null needs resolution.
        complexTypes.values().forEach(
                children -> children.stream()
                        .filter(child -> child.getRef() != null
                                && child.getType() == null
                                && allElementTypes.containsKey(child.getRef()))
                        .forEach(child -> XsdSchemaElement.copyDefinitionAttrs(
                                allElementTypes.get(child.getRef()), child)));

        complexTypes.remove(null);
        complexTypes.forEach((key, value) -> elementTypes.get(key).setChildElements(value));
    }

    public static void resolveReferences(List<XmlSchema> xsds) {
        // All elements from all XSDs
        Map<QName, XsdSchemaElement> allElementTypes = xsds.stream()
                .flatMap(xsd -> xsd.elementTypes.entrySet().stream())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        // Resolve any references. Any element type with ref != null and type=null needs resolution.
        xsds.forEach(xsd ->
            xsd.complexTypes.values().forEach(
                    children -> children.stream()
                            .filter(child -> child.getRef() != null
                                    && child.getType() == null
                                    && allElementTypes.containsKey(child.getRef()))
                            .forEach(child -> XsdSchemaElement.copyDefinitionAttrs(
                                    allElementTypes.get(child.getRef()), child))));

        // Clear the memory for complex types
        xsds.forEach(xsd -> xsd.complexTypes.clear());
    }

    private void examine(XMLEvent el) {
        if (el.isStartElement())
            examine(el.asStartElement());
        else if (el.isEndElement())
            examine(el.asEndElement());
    }

    private void examine(StartElement el) throws RuntimeException {
        if (isXsdSchema(el)) {
            setupSchema(el);
            elStack.push(el);
            return;
        }

        if (isXsdImport(el)) {
            String importedNamespace = el.getAttributeByName(NAMESPACE).getValue();
            String importedSchemaLocation = el.getAttributeByName(SCHEMA_LOCATION).getValue();
            final OpenCan<Throwable> exception = new OpenCan<>();

            iteratorStream((Iterator<Namespace>) schema.getNamespaces())
                    .filter(ns -> ns.getNamespaceURI().equals(importedNamespace))
                    .findFirst().ifPresent(ns -> {
                try {
                    XmlSchema importedSchema = new XmlSchema().parse(
                            xsdFile.getParentFile() + File.separator + importedSchemaLocation);
                    importedSchemas.put(ns, importedSchema);
                } catch (FileNotFoundException | XMLStreamException ex) {
                    exception.val = ex;
                }
            });

            if (exception.val != null)
                throw new RuntimeException(exception.val);
        }

        if (isXsdComplexType(el))
            elementTypes.get(extractAttrValue(elStack.peek().asStartElement(), NAME, targetNamespace))
                    .setType(COMPLEX_TYPE);

        if (isXsdExtension(el)) {
            currentXsdElement.setRef(extractAttrValue(el, BASE, targetNamespace));
            if (SIMPLE_CONTENT.equals(currentXsdElement.getContent()))
                currentXsdElement.setType(currentXsdElement.getRef());
        }

        if (isXsdAttribute(el))
            currentXsdElement.addAttribute(new XsdAttribute(el, targetNamespace));

        if (isXsdSimpleContent(el) || isXsdComplexContent(el))
            currentXsdElement.setContent(el.getName());

        if (!isXsdElement(el))
            return;

        if (!elStack.isEmpty()) {

            QName parentName = extractAttrValue(elStack.peek().asStartElement(), NAME, targetNamespace);
            List<XsdSchemaElement> children = Optional.ofNullable(complexTypes.get(parentName)).orElse(new ArrayList<>());
            complexTypes.put(parentName, children);

            XsdSchemaElement xsdEl = new XsdSchemaElement(el, targetNamespace);
            currentXsdElement = xsdEl;
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

    private static boolean isXsdSchema(XMLEvent el) {
        return el.isStartElement() && SCHEMA.equals(el.asStartElement().getName()) ||
                el.isEndElement() && SCHEMA.equals(el.asEndElement().getName());
    }

    private static boolean isXsdImport(XMLEvent el) {
        return el.isStartElement() && IMPORT.equals(el.asStartElement().getName()) ||
                el.isEndElement() && IMPORT.equals(el.asEndElement().getName());
    }

    private static boolean isXsdElement(XMLEvent el) {
        return el.isStartElement() && ELEMENT.equals(el.asStartElement().getName()) ||
            el.isEndElement() && ELEMENT.equals(el.asEndElement().getName());
    }

    private static boolean isXsdComplexType(XMLEvent el) {
        return (el.isStartElement() && COMPLEX_TYPE.equals(el.asStartElement().getName()) ||
                el.isEndElement() && COMPLEX_TYPE.equals(el.asEndElement().getName()));
    }

    private static boolean isXsdAttribute(XMLEvent el) {
        return (el.isStartElement() && ATTRIBUTE.equals(el.asStartElement().getName()) ||
                el.isEndElement() && ATTRIBUTE.equals(el.asEndElement().getName()));
    }

    private static boolean isXsdExtension(XMLEvent el) {
        return (el.isStartElement() && EXTENSION.equals(el.asStartElement().getName()) ||
                el.isEndElement() && EXTENSION.equals(el.asEndElement().getName()));
    }

    private static boolean isXsdSimpleContent(XMLEvent el) {
        return (el.isStartElement() && SIMPLE_CONTENT.equals(el.asStartElement().getName()) ||
                el.isEndElement() && SIMPLE_CONTENT.equals(el.asEndElement().getName()));
    }

    private static boolean isXsdComplexContent(XMLEvent el) {
        return (el.isStartElement() && COMPLEX_CONTENT.equals(el.asStartElement().getName()) ||
                el.isEndElement() && COMPLEX_CONTENT.equals(el.asEndElement().getName()));
    }

    public String getTargetNamespace() {
        return targetNamespace;
    }

    public StartElement getSchema() {
        return schema;
    }
}
