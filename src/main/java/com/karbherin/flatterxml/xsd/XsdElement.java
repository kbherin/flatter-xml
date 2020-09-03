package com.karbherin.flatterxml.xsd;

import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.StartElement;
import java.util.Iterator;
import java.util.List;

public class XsdElement {
    private String name;
    private int minOccurs;
    private String maxOccurs;
    private String ref;
    private String type;
    private List<XsdElement> childElements;

    private static final String UNBOUNDED = "unbounded";

    public XsdElement(StartElement el) {
        for (Iterator<Attribute> it = el.getAttributes(); it.hasNext(); ) {
            Attribute attr = it.next();
            switch(attr.getName().getLocalPart().toLowerCase()) {
                case "name" : name = attr.getValue();
                    break;
                case "minoccurs" : minOccurs = Integer.parseInt(attr.getValue());
                    break;
                case "ref" : ref = attr.getValue();
                    break;
                case "type" : type = attr.getValue();
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

    public String getName() {
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

    public String getRef() {
        return ref;
    }

    public void setRef(String ref) {
        this.ref = ref;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public List<XsdElement> getChildElements() {
        return childElements;
    }

    public void setChildElements(List<XsdElement> childElements) {
        this.childElements = childElements;
    }
}
