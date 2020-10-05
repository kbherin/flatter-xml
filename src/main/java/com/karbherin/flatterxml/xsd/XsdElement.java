package com.karbherin.flatterxml.xsd;

import com.karbherin.flatterxml.model.ElementWithAttributes;

import static com.karbherin.flatterxml.helper.XmlHelpers.defaultIfNull;
import static com.karbherin.flatterxml.helper.XmlHelpers.parsePrefixTag;
import static com.karbherin.flatterxml.xsd.XmlSchema.*;

import javax.xml.namespace.QName;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.StartElement;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.Collections.unmodifiableList;

public class XsdElement implements ElementWithAttributes {

    private final String targetNamespace;
    private QName name;
    private int minOccurs;
    private String maxOccurs;
    private QName ref;
    private QName type;
    private QName content;
    private List<XsdElement> childElements = new ArrayList<>();
    private List<XsdAttribute> attributes = new ArrayList<>();

    private static final String UNBOUNDED = "unbounded";
    private static final String NAME = "name", REF = "ref", TYPE = "type",
            MIN_OCCURS = "minoccurs", MAX_OCCURS = "maxoccurs";

    public XsdElement(StartElement el, String targetNamespace) {
        this.targetNamespace = targetNamespace;

        for (Iterator<Attribute> it = el.getAttributes(); it.hasNext(); ) {
            Attribute attr = it.next();
            switch(attr.getName().getLocalPart().toLowerCase()) {
                case NAME : name = parsePrefixTag(attr.getValue(), el.getNamespaceContext(), targetNamespace);
                    break;
                case MIN_OCCURS: minOccurs = Integer.parseInt(attr.getValue());
                    break;
                case REF : ref = parsePrefixTag(attr.getValue(), el.getNamespaceContext(), targetNamespace);
                    break;
                case TYPE : type = parsePrefixTag(attr.getValue(), el.getNamespaceContext(), targetNamespace);
                    content = SIMPLE_CONTENT;
                    break;
                case MAX_OCCURS: maxOccurs = attr.getValue().toLowerCase();
            }
        }
    }

    public static XsdElement copyDefinitionAttrs(XsdElement fromEl, XsdElement toEl) {
        toEl.name = fromEl.getName();
        toEl.type = fromEl.getType();
        toEl.content = fromEl.getContent();

        if (toEl.type == null && COMPLEX_TYPE.equals(fromEl.getType())) {
            if (SIMPLE_CONTENT.equals(fromEl.getContent())) {
                // Basic type specified as a ref
                toEl.type = fromEl.getRef();
            } else {
                // Extension of another complex type
                toEl.type = COMPLEX_TYPE;
                toEl.prependChildElements(fromEl.getChildElements());
            }
        }

        // Copy element attributes
        toEl.prependAttributes(fromEl.getElementAttributes());

        return toEl;
    }

    public boolean isRequired() {
        return minOccurs > 0;
    }

    public boolean isList() {
        return UNBOUNDED.equals(maxOccurs)
                || Integer.parseInt(defaultIfNull(maxOccurs, "1")) > 1;
    }

    @Override
    public QName getName() {
        return name;
    }

    public int getMinOccurs() {
        return minOccurs;
    }

    public String getMaxOccurs() {
        return maxOccurs;
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
        if (COMPLEX_TYPE.equals(type)) {
            this.content = COMPLEX_CONTENT;
        }
    }

    public List<XsdElement> getChildElements() {
        return unmodifiableList(Optional.of(childElements).orElse(Collections.emptyList()));
    }

    public void setChildElements(List<XsdElement> childElements) {
        this.childElements = childElements;
    }

    public void prependChildElements(List<XsdElement> childElements) {
        this.childElements.addAll(0, childElements);
    }

    public void addAttribute(XsdAttribute attr) {
        attributes.add(attr);
    }

    public void prependAttributes(List<XsdAttribute> attrs) {
        attributes.addAll(0, attrs);
    }

    public List<XsdAttribute> getElementAttributes() {
        return unmodifiableList(attributes);
    }

    @Override
    public List<QName> getAttributes() {
        return attributes.stream()
                .map(attr -> attr.getName()).collect(Collectors.toList());
    }

    public void setContent(QName content) {
        this.content = content;
    }

    public QName getContent() {
        return content;
    }

    public String getTargetNamespace() {
        return targetNamespace;
    }
}
