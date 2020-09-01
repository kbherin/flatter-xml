package com.karbherin.flatterxml;

import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;
import java.text.ParseException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Stack;

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
        while (attrs.hasNext()) {
            Attribute attr = attrs.next();

            if (attr.isSpecified()) {
                String prefix = attr.getName().getPrefix();
                attrBuf.append(String.format("%s%s=\"%s\"",
                        prefix.length() == 0 ? "" : prefix + ":",
                        attr.getName().getLocalPart(),
                        attr.getValue()));
            }
            if (attrs.hasNext()) {
                attrBuf.append("  ");
            }
        }

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
}
