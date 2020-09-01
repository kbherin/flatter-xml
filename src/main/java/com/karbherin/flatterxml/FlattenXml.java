package com.karbherin.flatterxml;

import javax.xml.stream.XMLEventFactory;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;
import java.io.*;
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
    private final List<String> filesWritten = new ArrayList<>();
    private final Map<String, String[]> recordCascadesTemplates;
    private final Map<String, RecordFieldsCascade> cascades = new HashMap<>();
    private final Stack<RecordFieldsCascade> cascadingStack = new Stack<>();
    private final RecordFieldsCascade.CascadePolicy cascadePolicy;

    private int currLevel = 0;
    // List of primary fields for each record that should be cascaded to child records
    private final Stack<RecordFieldsCascade> activeCascades = new Stack<>();

    private FlattenXml(String xmlFilename, String recordTag, String outDir, String delimiter,
                       RecordFieldsCascade.CascadePolicy cascadePolicy, Map<String, String[]> recordCascadesTemplates)
            throws FileNotFoundException, XMLStreamException {

        this.delimiter = delimiter.getBytes();
        this.outDir = outDir;
        this.recordTag = recordTag;
        this.reader = XMLInputFactory.newFactory().createXMLEventReader(
                xmlFilename, new FileInputStream(xmlFilename));
        this.recordCascadesTemplates = recordCascadesTemplates;
        this.cascadePolicy = cascadePolicy;
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
        Stack<StartElement> tagPath = new Stack<>();
        RecordFieldsCascade parentRecCascade = null;
        String parentTag = null;

        while (reader.hasNext() && recCounter < firstNRecs) {

            final XMLEvent ev;

            try {
                ev = reader.nextEvent();
            } catch (XMLStreamException ex) {
                throw decorateParseError(ex);
            }

            if (ev.isStartElement()) {                  // Start tag

                String tagName = ev.asStartElement().getName().getLocalPart();

                // Skip XML document's root element and grab the first element after it as the record tag
                // if user does not specify the primary record tag.

                // Pick the first start element after encountering the XML root.
                if (rootElementVisited) {
                    if (recordTag == null) {
                        recordTag = tagName;
                    }
                } else {
                    rootElementVisited = true;
                    cascadingStack.push(new RecordFieldsCascade(tagName, new String[0]));
                    // User did not specify the primary record tag. Skip root element.
                    continue;
                }

                // Detect nesting boundary
                if (!tagStack.isEmpty() && tagStack.peek().isStartElement()) {
                    parentRecCascade = cascadingStack.peek();
                    parentTag = tagStack.peek().asStartElement().getName().getLocalPart();
                }

                // Child element can either be a container or a data element.
                // Cascade fields and values from parent to child record.
                // If a cascade is not setup for it then use parent container's cascade settings.
                // If cascade policy is ALL then use a clone of parent's cascades to append additional fields.
                cascadingStack.push(registerCascades(tagName, parentRecCascade));

                if (recordTag.equals(tagName)) {
                    // Start tag of the top-level record. Parsing starts here.
                    tracking = true;
                }

                if (tracking) {
                    tagStack.push(ev);
                    inElement = true;
                }

                // Increment current level of nesting
                currLevel++;
                tagPath.push(ev.asStartElement());

            } else if (tracking && ev.isEndElement()) { // End tag
                EndElement endElement = ev.asEndElement();
                if (!tagStack.peek().isStartElement() && !tagStack.peek().isEndElement()) {
                    parentRecCascade.addCascadingData(endElement.getName().getLocalPart(),
                            tagStack.peek().asCharacters().getData(), cascadePolicy);
                }

                if (!inElement) {
                    // If parser is already outside an element and meets end of enclosing element
                    // <c><a>some data</a><a>more data</a>*PARSER HERE*</c>
                    writeRecord(null);

                    // A structural envelope does not contain its own data. Remove it from stack.
                    // Avoids empty output files with just the header record from being created.
                    tagStack.pop();
                } else {
                    tagStack.push(ev);
                    inElement = false;
                }

                // Step down current level of nesting
                currLevel--;

                // Reached the end of the top level record.
                if (tagStack.empty() || endElement.getName().getLocalPart().equals(recordTag)) {
                    tracking = false;
                    ++recCounter;
                }

                tagPath.pop();
                cascadingStack.pop();

            } else if (ev.isEndElement()) {
                // Step down current level of nesting
                currLevel--;
                cascadingStack.pop();

            } else if (tracking && ev.isCharacters()) { // Character data

                final String data = ev.asCharacters().getData();
                if (data.trim().length() > 0) {
                    if (!tagStack.peek().isStartElement() && !tagStack.peek().isEndElement()) {
                        // StAX can fragment character data into multiple elements. Combine them.
                        tagStack.push(eventFactory.createCharacters(
                                tagStack.pop().asCharacters().getData() + ev.asCharacters().getData()));
                    } else {
                        tagStack.push(ev);
                    }
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
        StartElement envelope = ev.asStartElement();
        String recordElementName = envelope.getName().getLocalPart();
        tagStack.push(ev);     // Add it back on to tag stack.

        if (captureRecordOnError != null) {
            // Capture the table name which has an error in the record.
            captureRecordOnError.push(ev);
        } else {
            // Write record to file if there are no errors.
            writeToFile(recordElementName, recordDataPile, recordHeaderPile,
                    cascades.get(recordElementName));
        }
    }

    private void writeToFile(String fileName, Stack<String> recordDataStack, Stack<String> recordHeaderStack,
                             RecordFieldsCascade cascadedData)
            throws IOException {

        FileOutputStream out = fileStreams.get(fileName);
        if (out == null) {
            out = new FileOutputStream(String.format("%s/%s.csv", outDir, fileName));
            // Register the new file stream.
            fileStreams.put(fileName, out);

            // Writer header record into a newly opened file.
            writeDelimited(recordHeaderStack, out, cascadedData.getParentCascadedNames());
        } else {
            recordHeaderStack.removeAllElements();
        }

        writeDelimited(recordDataStack, out, cascadedData.getParentCascadedValues());
    }

    private void writeDelimited(Stack<String> stringStack, OutputStream out, Iterable<String> appendList)
        throws IOException {

        if (stringStack.isEmpty()) {
            return;
        }

        out.write(stringStack.pop().getBytes());
        while (!stringStack.isEmpty()) {
            out.write(delimiter);
            out.write(stringStack.pop().getBytes());
        }
        for (String append: appendList) {
            out.write(delimiter);
            out.write(append.getBytes());
        }

        out.write(System.lineSeparator().getBytes());
    }

    private RecordFieldsCascade registerCascades(String tagName, RecordFieldsCascade parentRecCascade) {
        RecordFieldsCascade recordFieldsCascade = cascades.get(tagName);
        if (recordFieldsCascade == null) {
            // Register the cascades for the tag
            recordFieldsCascade = new RecordFieldsCascade(tagName, recordCascadesTemplates.get(tagName));
            recordFieldsCascade.cascadeFromParent(parentRecCascade);
            cascades.put(tagName, recordFieldsCascade);
        }
        return recordFieldsCascade;
    }

    private void closeAllFileStreams() throws IOException {
        for (Map.Entry<String, FileOutputStream> entry: fileStreams.entrySet()) {
            try {
                entry.getValue().close();
                filesWritten.add(entry.getKey());
            } catch (IOException ex) {
                filesWritten.add(entry.getKey() + "<err>");
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

    public Iterable<String> getFilesWritten() {
        return this.filesWritten;
    }

    public static class FlattenXmlBuilder {
        private String xmlFilename;
        private String recordTag = null;
        private String outDir = ".";
        private String delimiter = ",";
        private RecordFieldsCascade.CascadePolicy cascadePolicy = RecordFieldsCascade.CascadePolicy.NONE;
        private Map<String, String[]> recordCascadesTemplates = Collections.emptyMap();

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

        public FlattenXmlBuilder setCascadePolicy(RecordFieldsCascade.CascadePolicy cascadePolicy) {
            this.cascadePolicy = cascadePolicy;
            return this;
        }

        public FlattenXmlBuilder setRecordCascadesTemplates(Map<String, String[]> recordCascadesTemplates) {
            this.recordCascadesTemplates = recordCascadesTemplates;
            return this;
        }

        public FlattenXml createFlattenXml()
                throws FileNotFoundException, XMLStreamException {
            return new FlattenXml(xmlFilename, recordTag, outDir, delimiter, cascadePolicy, recordCascadesTemplates);
        }
    }
}
