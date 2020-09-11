package com.karbherin.flatterxml.xsd;

import com.karbherin.flatterxml.helper.XmlHelpers;

import javax.xml.namespace.QName;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.StartElement;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class XsdElement {
    private QName name;
    private int minOccurs;
    private String maxOccurs;
    private QName ref;
    private QName type;
    private List<XsdElement> childElements;

    private static final String UNBOUNDED = "unbounded";

    public XsdElement(StartElement el, String targetNamespace) {
        for (Iterator<Attribute> it = el.getAttributes(); it.hasNext(); ) {
            Attribute attr = it.next();
            switch(attr.getName().getLocalPart().toLowerCase()) {
                case "name" : name = XmlHelpers.parsePrefixTag(attr.getValue(), el.getNamespaceContext(), targetNamespace);
                    break;
                case "minoccurs" : minOccurs = Integer.parseInt(attr.getValue());
                    break;
                case "ref" : ref = XmlHelpers.parsePrefixTag(attr.getValue(), el.getNamespaceContext(), targetNamespace);
                    break;
                case "type" : type = XmlHelpers.parsePrefixTag(attr.getValue(), el.getNamespaceContext(), targetNamespace);
                    break;
                case "maxoccurs" : maxOccurs = attr.getValue().toLowerCase();
            }
        }
    }

    public static XsdElement copyDefinitionAttrs(XsdElement fromEl, XsdElement toEl) {
        toEl.name = fromEl.name;
        toEl.type = fromEl.type;
        return toEl;
    }

    public boolean isRequired() {
        return minOccurs > 0;
    }

    public boolean isList() {
        return maxOccurs != null &&
                (UNBOUNDED.equals(maxOccurs) || Integer.parseInt(maxOccurs) > 1);
    }

    public QName getName() {
        return name;
    }

    public int getMinOccurs() {
        return minOccurs;
    }

    public void setMinOccurs(int minOccurs) {
        this.minOccurs = minOccurs;
    }

    public String getMaxOccurs() {
        return maxOccurs;
    }

    public void setMaxOccurs(String maxOccurs) {
        this.maxOccurs = maxOccurs;
    }

    public QName getRef() {
        return ref;
    }

    public void setRef(QName ref) {
        this.ref = ref;
    }

    public QName getType() {
        return type;
    }

    public void setType(QName type) {
        this.type = type;
    }

    public List<XsdElement> getChildElements() {
        if (childElements != null)
            return childElements;
        else
            return Collections.emptyList();
    }

    public void setChildElements(List<XsdElement> childElements) {
        this.childElements = childElements;
    }
}
