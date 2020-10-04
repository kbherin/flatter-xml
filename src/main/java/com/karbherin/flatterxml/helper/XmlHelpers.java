package com.karbherin.flatterxml.helper;

import com.karbherin.flatterxml.xsd.XmlSchema;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class XmlHelpers {

    public static final String CASCADES_DELIM = ";";
    public static final String PAIR_SEP = "=";
    public static final String PREFIX_SEP = "=";
    public static final String COMMA_DELIM = ",";
    public static final String WHITESPACES = "\\s+";
    public static final String EMPTY = "";

    /**
     * Stringifies of attributes on an XML start tag.
     * @param attrs - iterable of XML element attributes
     * @return
     */
    public static String attributeString(Iterator<Attribute> attrs) {
        StringBuilder attrBuf = new StringBuilder();
        if (attrs.hasNext()) {
            attrBuf.append(attrs.next().toString());
        }
        while (attrs.hasNext()) {
            attrBuf.append(" ").append(attrs.next().toString());
        }
        return attrBuf.toString();
    }

    /** To help locating the errors in an XML document
     *  Extracts text around the error.
     *
     * @param eventsRec - One record of XML element events
     * */
    public static String eventsRecordToString(Deque<XMLEvent> eventsRec) {
        StringBuilder buf = new StringBuilder();
        String indent = EMPTY;
        boolean condenseToEllipsis = false;

        try {
            while (!eventsRec.isEmpty()) {
                XMLEvent ev = eventsRec.pop();

                if (ev.isStartElement()) {
                    StartElement startEl = ev.asStartElement();
                    String attrStr = attributeString(startEl.getAttributes());
                    String prefix = startEl.getName().getPrefix();
                    String tagName = (prefix.length() > 0 ? prefix + PREFIX_SEP : EMPTY)
                            + startEl.getName().getLocalPart();

                    if (!eventsRec.isEmpty() && !eventsRec.peek().isStartElement() &&
                            (eventsRec.peek().isEndElement() ||
                                    eventsRec.peek().asCharacters().getData().length() == 0)) {

                        if (!eventsRec.peek().isEndElement()) {
                            eventsRec.pop(); // Empty data
                        }
                        eventsRec.pop();     // End tag


                        if (!eventsRec.isEmpty() && eventsRec.peek().isStartElement() &&
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

                        if (!eventsRec.isEmpty() && eventsRec.peek().isStartElement()) {
                            buf.append(System.lineSeparator());
                            indent = indent + "  ";
                        }
                    }
                } else if (ev.isEndElement()) {
                    String prefix = ev.asEndElement().getName().getPrefix();
                    String tagName = (prefix.length() > 0 ? prefix + PREFIX_SEP : EMPTY)
                            + ev.asEndElement().getName().getLocalPart();
                    buf.append(String.format("</%s>\n", tagName));
                } else {
                    buf.append(ev.asCharacters().getData());
                }
            }
            return buf.toString();
        } catch (Throwable throwaway) {
            return EMPTY;
        }
    }

    public static QName parsePrefixTag(String input, NamespaceContext nsContext, String targetNamespace) {
        QName qName = QName.valueOf(input);

        String namespaceUri = qName.getNamespaceURI();
        // If namespace URI is empty then use targetNamespace
        if (namespaceUri == null || namespaceUri.isEmpty()) {
            namespaceUri = targetNamespace;
        }

        String[] parts = qName.getLocalPart().split(":");
        if (parts.length > 1) {
            // Handle forms: "{uri}prefix:name" and "prefix:name"
            return new QName(nsContext.getNamespaceURI(parts[0]), parts[1], parts[0]);
        } else {
            // Handle forms: "{uri}name" and "name"
            return new QName(namespaceUri, parts[0]);
        }
    }

    public static QName parsePrefixTag(String input) {
        QName qName = QName.valueOf(input);

        String[] parts = qName.getLocalPart().split(":");
        if (parts.length > 1) {
            String prefix = parts[0];
            String tagName = parts[1];
            return new QName(qName.getNamespaceURI(), tagName, prefix);
        } else {
            String tagName = parts[0];
            return new QName(qName.getNamespaceURI(), tagName);
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

    public static void validateXml(String xmlFile, String xsdFiles[]) throws SAXException, IOException {
        Source[] xsds = new StreamSource[xsdFiles.length];
        Arrays.asList(xsdFiles).stream().map(f -> new StreamSource(f)).collect(Collectors.toList()).toArray(xsds);
        SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
                .newSchema(xsds)
                .newValidator().validate(new StreamSource(new File(xmlFile)));
    }

    public static List<XmlSchema> parseXsds(String[] xsdFiles)
            throws FileNotFoundException, XMLStreamException {

        List<XmlSchema> xsds = new ArrayList<>();
        if (xsdFiles != null) {
            for (String xsd : xsdFiles) {
                xsds.add(new XmlSchema().parse(xsd));
            }
        }

        XmlSchema.resolveReferences(xsds);
        return xsds;
    }

    public static boolean isEmpty(String str) {
        return str == null || str.trim().length() == 0;
    }

    public static <T> T defaultIfNull(T val, T defaultVal) {
        return val == null ? defaultVal : val;
    }

    public static String emptyIfNull(String str) {
        return defaultIfNull(str, EMPTY);
    }

    public static int parseInt(String str, int defaultValue) {
        try {
            return Integer.valueOf(str);
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

}
