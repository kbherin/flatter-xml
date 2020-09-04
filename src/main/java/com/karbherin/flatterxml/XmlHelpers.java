package com.karbherin.flatterxml;

import com.karbherin.flatterxml.xsd.XmlSchema;
import com.karbherin.flatterxml.xsd.XsdElement;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;
import javax.xml.namespace.QName;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.Namespace;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;
import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.*;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class XmlHelpers {

    public static String CASCADES_DELIM = ";";
    public static String PAIR_SEP = "=";
    public static String COMMA_DELIM = ",";
    public static String WHITESPACES = "\\s+";

    /**
     * Stringifies of attributes on an XML start tag.
     * @param attrs - iterable of XML element attributes
     * @return
     */
    public static String attributeString(Iterator<Attribute> attrs) {
        StringBuffer attrBuf = new StringBuffer();
        if (attrs.hasNext())
            attrBuf.append(attrs.next().toString());
        while (attrs.hasNext())
            attrBuf.append(" ").append(attrs.next().toString());
        return attrBuf.toString();
    }

    /** To help locating the errors in an XML document
     *  Extracts text around the error.
     *
     * @param eventsRec - One record of XML element events
     * */
    public static String eventsRecordToString(Stack<XMLEvent> eventsRec) {
        StringBuffer buf = new StringBuffer();
        String indent = "";
        boolean condenseToEllipsis = false;

        try {
            while (!eventsRec.empty()) {
                XMLEvent ev = eventsRec.pop();

                if (ev.isStartElement()) {
                    StartElement startEl = ev.asStartElement();
                    String attrStr = attributeString(startEl.getAttributes());
                    String prefix = startEl.getName().getPrefix();
                    String tagName = (prefix.length() > 0 ? prefix + ":" : "")
                            + startEl.getName().getLocalPart();

                    if (!eventsRec.empty() && !eventsRec.peek().isStartElement() &&
                            (eventsRec.peek().isEndElement() ||
                                    eventsRec.peek().asCharacters().getData().length() == 0)) {

                        if (!eventsRec.peek().isEndElement()) {
                            eventsRec.pop(); // Empty data
                        }
                        eventsRec.pop();     // End tag


                        if (!eventsRec.empty() && eventsRec.peek().isStartElement() &&
                                eventsRec.peek().asStartElement().getName().equals(startEl.getName())) {
                            condenseToEllipsis = true;
                        }

                        if (condenseToEllipsis) {
                            buf.append(String.format("%s<%s %s>...<%s>\n", indent, tagName, attrStr, tagName));
                        } else {
                            buf.append(String.format("%s<%s %s/>\n", indent, tagName, attrStr));
                        }
                    } else {
                        buf.append(String.format("%s<%s %s>", indent, tagName, attrStr));

                        if (!eventsRec.empty() && eventsRec.peek().isStartElement()) {
                            buf.append(System.lineSeparator());
                            indent = indent + "  ";
                        }
                    }
                } else if (ev.isEndElement()) {
                    String prefix = ev.asEndElement().getName().getPrefix();
                    String tagName = (prefix.length() > 0 ? prefix + ":" : "")
                            + ev.asEndElement().getName().getLocalPart();
                    buf.append(String.format("</%s>\n", tagName));
                } else {
                    buf.append(ev.asCharacters().getData());
                }
            }
            return buf.toString();
        } catch (Throwable throwaway) {
            return "";
        }
    }

    public static Map<String, String[]> parseTagValueCascades(String cascades) {
        Map<String, String[]> cascadeMap = new HashMap<>();
        int c = 1;
        for (String cascade: cascades.replaceAll(WHITESPACES, "").split(CASCADES_DELIM)) {
            try {
                String pair[] = cascade.split(PAIR_SEP);
                String elem = pair[0];
                String primaries = pair[1];
                String[] primaryTags = primaries.split(COMMA_DELIM);

                cascadeMap.put(elem, primaryTags);
            } catch (Exception ex) {
                throw new RuntimeException(new ParseException("Could not parse the tag value cascades", c));
            }
            c++;
        }

        return cascadeMap;
    }

    public static QName parsePrefixTag(String input, NamespaceContext nsContext, String targetNamespace) {
        String[] parts = input.split(":");
        if (parts.length > 1) {
            return new QName(nsContext.getNamespaceURI(parts[0]), parts[1], parts[0]);
        } else {
            QName qname = new QName(parts[0]);
            if (qname.getNamespaceURI() == null || qname.getNamespaceURI().length() == 0)
                return new QName(targetNamespace, parts[0]);
            return qname;
        }
    }

    public static String toPrefixedTag(QName qname) {
        if (qname.getPrefix() != null && qname.getPrefix().length() > 0) {
            return String.format("%s:%s", qname.getPrefix(), qname.getLocalPart());
        }
        return qname.getLocalPart();
    }

    public static QName extractAttrValue(StartElement el, QName attrName, String targetNamespace) {
        for (Iterator<Attribute> it = el.getAttributes(); it.hasNext(); ) {
            Attribute attr = it.next();
            QName attrQName = attr.getName();
            String nsUri = attrQName.getNamespaceURI();
            if (nsUri == null || nsUri.length() == 0)
                    nsUri = el.getName().getNamespaceURI();
            if (attrName.getLocalPart().equals(attrQName.getLocalPart()) &&
                    attrName.getNamespaceURI().equals(nsUri))
                return parsePrefixTag(attr.getValue(), el.getNamespaceContext(), targetNamespace);
        }
        return null;
    }

    public static<T> Stream<T> iteratorStream(Iterator<T> iterator) {
        return StreamSupport.stream(
                Spliterators.spliteratorUnknownSize(iterator, Spliterator.ORDERED), false);
    }

    public static void validateXml(String xmlFile, String xsdFile) throws SAXException, IOException {
        SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
                .newSchema(new File(xsdFile))
                .newValidator().validate(new StreamSource(new File(xmlFile)));
    }
}
