package com.karbherin.flatterxml.model;

import javax.xml.namespace.QName;
import javax.xml.stream.events.StartElement;
import java.util.List;

public interface SchemaElementWithAttributes {
    List<QName> getAttributes();
    QName getName();
}
