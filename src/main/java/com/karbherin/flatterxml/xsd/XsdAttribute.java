package com.karbherin.flatterxml.xsd;

import javax.xml.namespace.QName;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.StartElement;
import java.util.Iterator;
import java.util.Optional;

import static com.karbherin.flatterxml.helper.XmlHelpers.extractAttrValue;
import static com.karbherin.flatterxml.helper.XmlHelpers.parsePrefixTag;

public class XsdAttribute {

    private QName name;
    private QName ref;
    private QName type;
    private String use;

    private static final String NAME = "name", REF = "ref", TYPE = "type", USE = "use";

    public XsdAttribute(StartElement el, String targetNamespace) {
        for (Iterator<Attribute> it = el.getAttributes(); it.hasNext(); ) {
            Attribute attr = it.next();
            switch(attr.getName().getLocalPart().toLowerCase()) {
                case NAME : name = parsePrefixTag(attr.getValue(), el.getNamespaceContext(), targetNamespace);
                    break;
                case REF : ref = parsePrefixTag(attr.getValue(), el.getNamespaceContext(), targetNamespace);
                    break;
                case TYPE : type = parsePrefixTag(attr.getValue(), el.getNamespaceContext(), targetNamespace);
                    break;
                case USE : use = Optional.ofNullable(el.getAttributeByName(XmlSchema.USE))
                        .map(atr -> atr.getValue()).orElse("optional");
            }
        }
    }

    public boolean isRequired() {
        return use == "required";
    }

    public QName getName() {
        return name;
    }

    public QName getRef() {
        return ref;
    }

    public QName getType() {
        return type;
    }

    public String getUse() {
        return use;
    }
}
