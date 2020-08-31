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
    private String recordTag;
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

    /**
     * Main loop of processing the XML event stream.
     *
     * @param firstNRecs
     * @return
     * @throws XMLStreamException
     * @throws IOException
     */
    private long flattenXmlDoc(final long firstNRecs) throws XMLStreamException, IOException {

        long recCounter = 0;
        boolean tracking = false,
                inElement = false,
                rootElementVisited = false;

        while (reader.hasNext() && recCounter < firstNRecs) {

            final XMLEvent ev;

            try {
                ev = reader.nextEvent();
            } catch (XMLStreamException ex) {
                throw decorateParseError(ex);
            }

            if (ev.isStartElement()) {                  // Start tag

                // Skip XML document's root element and grab the first element after it as the record tag
                // if user does not specify the primary record tag.
                if (recordTag == null) {
                    // Pick the first start element after encountering the XML root.
                    if (rootElementVisited) {
                        recordTag = ev.asStartElement().getName().getLocalPart();
                    } else {
                        rootElementVisited = true;
                        // User did not specify the primary record tag. Skip root element.
                        continue;
                    }
                }

                if (recordTag.equals(ev.asStartElement().getName().getLocalPart())) {
                    // Start tag of the top-level record. Parsing starts here.
                    tracking = true;
                }

                if (tracking) {
                    tagStack.push(ev);
                    inElement = true;
                }

            } else if (tracking && ev.isEndElement()) { // End tag
                EndElement endElement = ev.asEndElement();
                boolean recordWritten = false;
                if (!inElement) {
                    // If parser is already outside an element and meets end of enclosing element
                    // <c><a>some data</a><a>more data</a>*PARSER HERE*</c>
                    writeRecord(null);
                    recordWritten = true;
                }

                if (recordWritten && tagStack.peek().isStartElement() &&
                        tagStack.peek().asStartElement().getName().equals(endElement.getName())) {
                    // A structural envelope does not contain its own data. Remove it from record.
                    // Avoids empty output files with just the header record from being created.
                    tagStack.pop();
                    inElement = true;
                } else {
                    tagStack.push(ev);
                    inElement = false;
                }

                // Reached the end of the top level record.
                if (tagStack.empty() || endElement.getName().getLocalPart().equals(recordTag)) {
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
        if (tagStack.isEmpty()) {
            return;
        }
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

        if (!stringStack.isEmpty()) {
            out.write(stringStack.pop().getBytes());
            out.write(System.lineSeparator().getBytes());
        }
    }

    private void closeAllFileStreams() throws IOException {
        Stack<String> filesWritten = new Stack<>();
        for (Map.Entry<String, FileOutputStream> entry: fileStreams.entrySet()) {
            try {
                entry.getValue().close();
                filesWritten.push(entry.getKey());
            } catch (IOException ex) {
                filesWritten.push(entry.getKey() + "<err>");
            }
        }
    }

    private XMLStreamException decorateParseError(XMLStreamException ex) throws IOException {
        Stack<XMLEvent> errorRec = new Stack<>();
        writeRecord(errorRec);
        javax.xml.stream.Location loc = ex.getLocation();
        ex.initCause(new XMLStreamException("Excerpt of text before the error location:\n"+
                XmlHelpers.eventsRecordToString(errorRec) +
                String.format(">>error occurred @(line,col,doc-offset)=(%s,%s,%s)<<",
                        loc.getLineNumber(), loc.getColumnNumber(), loc.getCharacterOffset())));
        return ex;
    }

    public String getRecordTag() {
        return this.recordTag;
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
