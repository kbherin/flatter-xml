package com.karbherin.flatterxml.model;

import javax.xml.namespace.QName;
import javax.xml.stream.events.StartElement;
import java.util.List;

public interface ElementWithAttributes {
    List<QName> getAttributes();
    QName getName();
}
