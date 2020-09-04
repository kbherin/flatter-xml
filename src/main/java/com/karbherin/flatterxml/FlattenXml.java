package com.karbherin.flatterxml;

import com.karbherin.flatterxml.xsd.XmlSchema;
import com.karbherin.flatterxml.xsd.XsdElement;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventFactory;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;
import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Flattens an XML file into a set of tabular files.
 * Useful for flattening a deeply nested XMLs into delimited files or load into tables.
 *
 * @author Kartik Bherin
 */
public class FlattenXml {

    private final byte[] delimiter;
    private final String outDir;
    private QName recordTag;
    private StartElement rootElement;
    private final XMLEventReader reader;
    private final Map<String, FileOutputStream> fileStreams = new HashMap<>();
    private final Stack<XMLEvent> tagStack = new Stack<>();
    private final Stack<String> recordDataPile = new Stack<>();
    private final Stack<String> recordHeaderPile = new Stack<>();
    private final XMLEventFactory eventFactory = XMLEventFactory.newFactory();
    private final List<String[]> filesWritten = new ArrayList<>();
    private final Map<String, String[]> recordCascadesTemplates;
    private final Stack<RecordFieldsCascade> cascadingStack = new Stack<>();
    private final Stack<StartElement> tagPath = new Stack<>();
    private final RecordFieldsCascade.CascadePolicy cascadePolicy;
    private final List<XmlSchema> xsds = new ArrayList<>();

    private int currLevel = 0;
    // List of primary fields for each record that should be cascaded to child records
    private final Stack<RecordFieldsCascade> activeCascades = new Stack<>();

    private FlattenXml(String xmlFilename, QName recordTag, String outDir, String delimiter,
                       RecordFieldsCascade.CascadePolicy cascadePolicy, Map<String, String[]> recordCascadesTemplates,
                       String[] xsdFiles)
            throws FileNotFoundException, XMLStreamException {

        this.delimiter = delimiter.getBytes();
        this.outDir = outDir;
        this.recordTag = recordTag;
        this.reader = XMLInputFactory.newFactory().createXMLEventReader(
                xmlFilename, new FileInputStream(xmlFilename));
        this.recordCascadesTemplates = recordCascadesTemplates;
        this.cascadePolicy = cascadePolicy;

        // Create output directory
        if (!new File(outDir).isDirectory())
            if (new File(outDir).mkdirs())
                throw new FileNotFoundException("Could not create the output directory");

        if (xsdFiles != null)
            for (String xsd: xsdFiles)
                xsds.add(new XmlSchema().parse(xsd));
    }

    /**
     * Flattens an entire XML file into tabular files.
     * @return
     * @throws XMLStreamException
     * @throws IOException
     */
    public long parseFlatten() throws XMLStreamException, IOException {
        long recCounter = 0;
        while (reader.hasNext())
            recCounter += parseFlatten(Long.MAX_VALUE);
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
            if (!reader.hasNext())
                closeAllFileStreams();
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
        RecordFieldsCascade currRecordCascade = null, reuseRecordCascade = null;
        XMLEvent prevEv = null;

        while (reader.hasNext() && recCounter < firstNRecs) {

            final XMLEvent ev;

            try {
                ev = reader.nextEvent();
            } catch (XMLStreamException ex) {
                throw decorateParseError(ex);
            }

            if (ev.isStartElement()) {                  // Start tag

                StartElement el = ev.asStartElement();
                QName tagName = el.getName();

                // Skip XML document's root element and grab the first element after it as the record tag
                // if user does not specify the primary record tag.

                // Pick the first start element after encountering the XML root.
                if (rootElementVisited) {
                    if (recordTag == null) {
                        recordTag = tagName;
                    }
                } else {
                    rootElementVisited = true;
                    rootElement = el;
                    cascadingStack.push(new RecordFieldsCascade(el, new String[0]));
                    tagPath.push(el);
                    prevEv = ev;
                    // User did not specify the primary record tag. Skip root element.
                    continue;
                }

                // Detect nesting boundary
                if (prevEv.isStartElement()) {
                    currRecordCascade = cascadingStack.peek();

                    // Add cascade registry for a newly nested record.
                    if (!currRecordCascade.getRecordName().equals( tagPath.peek().getName() )) {

                        if (reuseRecordCascade != null &&
                                reuseRecordCascade.getRecordName().equals(tagPath.peek().getName())) {
                            // Reuse parent cascades if the record continues to be the same
                            currRecordCascade = reuseRecordCascade.clearCurrentRecordCascades();
                        } else {
                            // Cascade fields and values from parent record to this new record.
                            currRecordCascade = registerCascades(tagPath.peek(), currRecordCascade);
                        }
                        cascadingStack.push(currRecordCascade);
                    }
                }

                if (recordTag.equals(tagName))
                    // Start tag of the top-level record. Parsing starts here.
                    tracking = true;

                if (tracking) {
                    tagStack.push(ev);
                    inElement = true;
                }

                // Increment current level of nesting
                currLevel++;
                tagPath.push(ev.asStartElement());
                prevEv = ev;

            } else if (tracking && ev.isEndElement()) { // End tag
                EndElement endElement = ev.asEndElement();

                // Previous element was data. Add it to the container's cascade list
                if (!tagStack.peek().isStartElement() && !tagStack.peek().isEndElement())
                    currRecordCascade.addCascadingData(endElement.getName(),
                            tagStack.peek().asCharacters().getData(), cascadePolicy);

                if (!inElement) {
                    // If parser is already outside an element and meets end of enclosing element
                    // Example: <c><a>some data</a><a>more data</a>*PARSER HERE*</c>
                    writeRecord(null);

                    // A structural envelope does not contain its own data. Remove it from stack.
                    tagStack.pop();
                    reuseRecordCascade = cascadingStack.pop();
                } else {
                    tagStack.push(ev);
                    inElement = false;
                }

                // Reached the end of the top level record.
                if (tagStack.empty() || endElement.getName().equals(recordTag)) {
                    tracking = false;
                    ++recCounter;
                }

                // Step down everything
                currLevel--;
                tagPath.pop();
                prevEv = ev;

            } else if (ev.isEndElement()) {
                // Step down current level of nesting
                currLevel--;
                tagPath.pop();
                prevEv = ev;

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
                    prevEv = ev;
                }
            }
        }

        return recCounter;
    }


    private void writeRecord(Stack<XMLEvent> captureRecordOnError) throws IOException {
        if (tagStack.isEmpty())
            return;

        XMLEvent ev;

        // Read one part of an XML element at a time.
        while (!(ev = tagStack.pop()).isStartElement()) {

            final EndElement endElement = ev.asEndElement();
            String data = XmlHelpers.EMPTY;
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

        // Lookup schema for a list of fields a record can legitimately have
        XsdElement schemaEl = xsds.stream()
                .map(xsd -> xsd.getElementByName(envelope.getName()))
                .filter(xsd -> xsd != null)
                .findFirst().orElse(null);

        // Collect the list of fields a record should have
        Stack<QName> recordSchemaFields = new Stack<>();
        if (schemaEl != null) {
            schemaEl.getChildElements().stream()
                    .map(ch -> ch.getName()).forEach(recordSchemaFields::push);
            alignFieldsToSchema(recordDataPile, recordHeaderPile, recordSchemaFields);
        }

        if (captureRecordOnError != null)
            // Capture the table name which has an error in the record.
            captureRecordOnError.push(ev);
        else
            // Write record to file if there are no errors.
            writeToFile(recordElementName, recordDataPile, recordHeaderPile,
                    cascadingStack.peek());
    }

    private void alignFieldsToSchema(Stack<String> recordDataPile, Stack<String> recordHeaderPile,
                                     Stack<QName> schemaFields) {

        if (schemaFields == null || schemaFields.isEmpty())
            return;

        Map<String, String> fv = new HashMap<>();
        while (!recordHeaderPile.isEmpty())
            fv.put(recordHeaderPile.pop(), recordDataPile.pop());

        Stack<String[]> aligned = new Stack<>();
        while (!schemaFields.isEmpty()) {
            String xsdFld = XmlHelpers.toPrefixedTag(schemaFields.pop());
            recordHeaderPile.push(xsdFld);
            recordDataPile.push(Optional.ofNullable(fv.get(xsdFld)).orElse(XmlHelpers.EMPTY));
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
            filesWritten.add(new String[]{ String.valueOf(currLevel), fileName, previousFile() });

            // Writer header record into a newly opened file.
            writeDelimited(recordHeaderStack, out, cascadedData.getParentCascadedNames());
        } else {
            recordHeaderStack.removeAllElements();
        }

        writeDelimited(recordDataStack, out, cascadedData.getParentCascadedValues());
    }

    private String previousFile() {
        StartElement prevFile = tagPath.pop();
        String prevFileName = "ROOT";
        if (cascadingStack.size() > 1)
            prevFileName = tagPath.peek().getName().getLocalPart();
        tagPath.push(prevFile);
        return prevFileName;
    }

    private void writeDelimited(Stack<String> stringStack, OutputStream out, Iterable<String> appendList)
        throws IOException {

        if (stringStack.isEmpty())
            return;

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

    private RecordFieldsCascade registerCascades(StartElement tag, RecordFieldsCascade parentRecCascade) {
        RecordFieldsCascade recordFieldsCascade = new RecordFieldsCascade(tag, recordCascadesTemplates.get(tag.getName()));
        return recordFieldsCascade.cascadeFromParent(parentRecCascade);
    }

    private void closeAllFileStreams() throws IOException {
        for (Map.Entry<String, FileOutputStream> entry: fileStreams.entrySet()) {
            try {
                entry.getValue().close();
            } catch (IOException ex) {
                ;
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

    public QName getRecordTag() {
        return this.recordTag;
    }

    public List<String[]> getFilesWritten() {
        return filesWritten;
    }

    public StartElement getRootElement() {
        return rootElement;
    }

    public static class FlattenXmlBuilder {
        private String xmlFilename;
        private QName recordTag = null;
        private String outDir = ".";
        private String delimiter = ",";
        private RecordFieldsCascade.CascadePolicy cascadePolicy = RecordFieldsCascade.CascadePolicy.NONE;
        private Map<String, String[]> recordCascadesTemplates = Collections.emptyMap();
        private String[] xsdFiles;

        public FlattenXmlBuilder setXmlFilename(String xmlFilename) {
            this.xmlFilename = xmlFilename;
            return this;
        }

        public FlattenXmlBuilder setRecordTag(QName recordTag) {
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

        public FlattenXmlBuilder setXsdFiles(String[] xsds) {
            this.xsdFiles = xsds;
            return this;
        }

        public FlattenXml createFlattenXml()
                throws FileNotFoundException, XMLStreamException {
            return new FlattenXml(xmlFilename, recordTag, outDir, delimiter, cascadePolicy, recordCascadesTemplates, xsdFiles);
        }
    }
}
