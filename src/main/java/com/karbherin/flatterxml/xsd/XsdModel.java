package com.karbherin.flatterxml.xsd;

import static com.karbherin.flatterxml.XmlHelpers.extractAttrValue;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.*;

public class XsdModel {

    private String targetNamespace;
    private Set<Namespace> namespaces = new HashSet<>();
    private final Stack<XMLEvent> elStack = new Stack<>();
    private Map<String, List<XsdElement>> complexTypes = new HashMap<>();
    private final Map<String, XsdElement> elementTypes = new HashMap<>();
    private static final String ELEMENT = "element", NAME = "name", SCHEMA = "schema", COMPLEX_TYPE = "complexType";

    public void parse(String xsdFile) throws FileNotFoundException, XMLStreamException {
        parse(new File(xsdFile));
    }

    public void parse(File xsdFile) throws FileNotFoundException, XMLStreamException {
        XMLEventReader reader = XMLInputFactory.newFactory().createXMLEventReader(
                xsdFile.getPath(), new FileInputStream(xsdFile));

        while (reader.hasNext())
            examine(reader.nextEvent());

        resolveReferences();
    }

    public XsdElement getElementByName(String name) {
        return elementTypes.get(name);
    }

    private void resolveReferences() {
        complexTypes.values().forEach(
                elements -> elements.stream()
                        .filter(el -> el.getRef() != null && elementTypes.containsKey(el.getRef()))
                        .forEach(el -> {
                            XsdElement.copyDefinitionAttrs(elementTypes.get(el.getRef()), el);
                        }));
        complexTypes.remove(null);
        complexTypes.entrySet()
                .forEach(ent -> elementTypes.get(ent.getKey()).setChildElements(ent.getValue()));
        complexTypes = null;
    }

    private void examine(XMLEvent el) {
        if (el.isStartElement())
            examine(el.asStartElement());
        else if (el.isEndElement())
            examine(el.asEndElement());
    }

    private void examine(StartElement el) {
        if (isXsdSchema(el)) {
            setupNamespaces(el);
            elStack.push(el);
            return;
        }

        if (isXsdComplexType(el))
            elementTypes.get(extractAttrValue(elStack.peek().asStartElement(), "name")).setType(COMPLEX_TYPE);

        if (!isXsdElement(el))
            return;

        if (!elStack.isEmpty()) {

            String parentName = extractAttrValue(elStack.peek().asStartElement(), NAME);
            List<XsdElement> children = complexTypes.get(parentName);
            if (children == null) {
                children = new ArrayList<>();
                complexTypes.put(parentName, children);
            }

            XsdElement xsdEl = new XsdElement(el);
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

    private void setupNamespaces(StartElement el) {
        Attribute attr = el.getAttributeByName(QName.valueOf("targetNamespace"));
        if (attr != null)
            targetNamespace = attr.getValue();
        for (Iterator<Namespace> it = el.getNamespaces(); it.hasNext(); )
            namespaces.add(it.next());
    }

    private static boolean isXsdElement(XMLEvent el) {
        return el.isStartElement() && ELEMENT.equals(el.asStartElement().getName().getLocalPart()) ||
            el.isEndElement() && ELEMENT.equals(el.asEndElement().getName().getLocalPart());
    }

    private static boolean isXsdComplexType(XMLEvent el) {
        return el.isStartElement() && COMPLEX_TYPE.equals(el.asStartElement().getName().getLocalPart()) ||
                el.isEndElement() && COMPLEX_TYPE.equals(el.asEndElement().getName().getLocalPart());
    }

    private static boolean isXsdSchema(XMLEvent el) {
        return el.isStartElement() && SCHEMA.equals(el.asStartElement().getName().getLocalPart()) ||
                el.isEndElement() && SCHEMA.equals(el.asEndElement().getName().getLocalPart());
    }

    public String getTargetNamespace() {
        return targetNamespace;
    }

    public Set<Namespace> getNamespaces() {
        return namespaces;
    }
}
