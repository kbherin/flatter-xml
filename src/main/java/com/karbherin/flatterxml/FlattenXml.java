package com.karbherin.flatterxml;

import javax.xml.stream.XMLEventFactory;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;
import java.io.*;
import java.math.BigInteger;
import java.util.*;

/**
 * Flattens an XML file into a set of tabular files.
 * Useful for flattening a deeply nested XMLs into delimited files or load into tables.
 *
 * @author Kartik Bherin
 */
public class FlattenXml {

    private final byte[] delimiter;
    private final String outDir;
    private final String recordTag;
    private final XMLEventReader reader;
    private final Map<String, FileOutputStream> fileStreams = new HashMap<>();
    private final Stack<XMLEvent> tagStack = new Stack<>();
    private final Stack<String> recordDataPile = new Stack<>();
    private final Stack<String> recordHeaderPile = new Stack<>();
    private final XMLEventFactory eventFactory = XMLEventFactory.newFactory();

    private FlattenXml(String xmlFilename, String recordTag, String outDir, String delimiter)
        throws FileNotFoundException, XMLStreamException {

        this.delimiter = delimiter.getBytes();
        this.outDir = outDir;
        this.recordTag = recordTag;
        this.reader = XMLInputFactory.newFactory().createXMLEventReader(
                xmlFilename, new FileInputStream(xmlFilename));
    }

    /**
     * Flattens an entire XML file into tabular files.
     * @return
     * @throws XMLStreamException
     * @throws IOException
     */
    public long parseFlatten() throws XMLStreamException, IOException {
        long recCounter = 0;
        while (reader.hasNext()) {
            recCounter += parseFlatten(Long.MAX_VALUE);
        }
        return recCounter;
    }

    /**
     * Flattens the first N records in an XML file into tabular files.
     * Record is defined by the record tag.
     * @param firstNRecords
     * @return
     * @throws XMLStreamException
     * @throws IOException
     */
    public long parseFlatten(long firstNRecords) throws XMLStreamException, IOException {
        try {
            return flattenXmlDoc(firstNRecords);
        } finally {
            if (!reader.hasNext()) {
                closeAllFileStreams();
            }
        }
    }

    private long flattenXmlDoc(final long firstNRecs) throws XMLStreamException, IOException {

        long recCounter = 0;
        boolean tracking = false,
                inElement = false;

        while (reader.hasNext() && recCounter < firstNRecs) { // Start tag

            final XMLEvent ev;

            try {
                ev = reader.nextEvent();
            } catch (XMLStreamException ex) {
                throw decorateParseError(ex);
            }

            if (ev.isStartElement()) {                  // Start tag
                if (recordTag == null || recordTag.equals(ev.asStartElement().getName().getLocalPart())) {
                    // Start tag of the top-level record. Parsing starts here.
                    tracking = true;
                }

                if (tracking) {
                    tagStack.push(ev);
                    inElement = true;
                }

            } else if (tracking && ev.isEndElement()) { // End tag
                if (!inElement) {
                    // If parser is already outside an element and meets end of enclosing element
                    // <c><a>some data</a><a>more data</a>*PARSER HERE*</c>
                    writeRecord(null);
                }

                tagStack.push(ev.asEndElement());
                inElement = false;

                // Reached the end of the top level record.
                if (tagStack.empty()) {
                    tracking = false;
                    ++recCounter;
                }

            } else if (tracking && ev.isCharacters()) { // Character data

                final String data = ev.asCharacters().getData();
                if (data.trim().length() > 0) {
                    tagStack.push(ev);
                }
            }
        }

        return recCounter;
    }


    private void writeRecord(Stack<XMLEvent> captureRecordOnError) throws IOException {
        XMLEvent ev;

        // Read one part of an XML element at a time.
        while (!(ev = tagStack.pop()).isStartElement()) {

            final EndElement endElement = ev.asEndElement();
            String data = "";
            final StartElement startElement;

            ev = tagStack.pop();
            if (ev.isCharacters()) {
                // If top of the stack is data then pop its start tag as well.
                data = ev.asCharacters().getData();

                // Combine all data element between start and end tags.
                // Multiple lines in a tag's data is seen as multiple data elements by StAX.
                // Appearance of &amp; in character data also breaks it in multiple data elements.
                while (tagStack.peek().isCharacters()) {
                    data = tagStack.pop().asCharacters().getData() + data;
                }

                // Now the start tag
                startElement = tagStack.pop().asStartElement();
            } else {
                // Empty data. Is of the form <someTag/>
                startElement = ev.asStartElement();
            }

            recordDataPile.push(data);
            recordHeaderPile.push(startElement.getName().getLocalPart());

            // Capture the entire record if a parsing error has occurred in the record.
            if (captureRecordOnError != null) {
                captureRecordOnError.push(endElement);
                captureRecordOnError.push(eventFactory.createCharacters(data));
                captureRecordOnError.push(startElement);
            }
        }

        // The tabular file to write the record to.
        String fileName = ev.asStartElement().getName().getLocalPart();
        tagStack.push(ev);     // Add it back on to tag stack.

        if (captureRecordOnError != null) {
            // Capture the table name which has an error in the record.
            captureRecordOnError.push(ev);
        } else {
            // Write record to file only if there are no errors.
            writeToFile(fileName);
        }
    }

    private void writeToFile(String fileName) throws IOException {

        FileOutputStream out = fileStreams.get(fileName);
        if (out == null) {
            out = new FileOutputStream(String.format("%s/%s.csv", outDir, fileName));
            // Register the new file stream.
            fileStreams.put(fileName, out);

            // Writer header record into a newly opened file.
            writeDelimited(recordHeaderPile, out);
        } else {
            recordHeaderPile.removeAllElements();
        }

        writeDelimited(recordDataPile, out);
    }

    private void writeDelimited(Stack<String> stringStack, OutputStream out)
        throws IOException {

        for (int i = stringStack.size(); i > 1; i--) {
            String data = stringStack.pop();
            out.write(data.getBytes());
            out.write(delimiter);
        }

        out.write(stringStack.pop().getBytes());
        out.write(System.lineSeparator().getBytes());
    }

    private void closeAllFileStreams() {
        for (Map.Entry<String, FileOutputStream> entry: fileStreams.entrySet()) {
            System.out.println("Closing file " + entry.getKey());
            try {
                entry.getValue().close();
            } catch (IOException ex) {
                System.err.println("Could not close file " + entry.getKey());
            }
        }
    }

    /** To help locating the errors in an XML document */
    private String attributeString(Iterator<Attribute> attrs) {
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

    private String eventsRecordToString(Stack<XMLEvent> eventsRec) {
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

    private XMLStreamException decorateParseError(XMLStreamException ex) throws IOException {
        Stack<XMLEvent> errorRec = new Stack<>();
        writeRecord(errorRec);
        javax.xml.stream.Location loc = ex.getLocation();
        ex.initCause(new XMLStreamException("Excerpt of text before the error location:\n"+
                eventsRecordToString(errorRec) +
                String.format(">>error occurred @(line,col,doc-offset)=(%s,%s,%s)<<",
                        loc.getLineNumber(), loc.getColumnNumber(), loc.getCharacterOffset())));
        return ex;
    }

    public static class FlattenXmlBuilder {
        private String xmlFilename;
        private String recordTag = null;
        private String outDir = ".";
        private String delimiter = ",";

        public FlattenXmlBuilder setXmlFilename(String xmlFilename) {
            this.xmlFilename = xmlFilename;
            return this;
        }

        public FlattenXmlBuilder setRecordTag(String recordTag) {
            this.recordTag = recordTag;
            return this;
        }

        public FlattenXmlBuilder setOutDir(String outDir) {
            this.outDir = outDir;
            return this;
        }

        public FlattenXmlBuilder setDelimiter(String delimiter) {
            this.delimiter = delimiter;
            return this;
        }

        public FlattenXml createFlattenXml()
                throws FileNotFoundException, XMLStreamException {
            return new FlattenXml(xmlFilename, recordTag, outDir, delimiter);
        }
    }
}
