package com.karbherin.flatterxml;

import com.karbherin.flatterxml.output.RecordHandler;
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

    // Inputs supplied by the caller
    private String recordTagGiven;
    private QName recordTag = null;  // Will be populated from recordTagGiven
    private final XMLEventReader reader;
    private final List<XmlSchema> xsds = new ArrayList<>();
    private final RecordFieldsCascade.CascadePolicy cascadePolicy;
    private StartElement rootElement;
    private final Map<String, String[]> recordCascadesTemplates;

    // Parsing state
    private final Stack<XMLEvent> tagStack = new Stack<>();
    private final Stack<RecordFieldsCascade> cascadingStack = new Stack<>();
    private final Stack<StartElement> tagPath = new Stack<>();
    private int currLevel = 0;
    private boolean rootElementVisited = false;
    private XMLEvent prevEv = null;
    private boolean tracking = false;
    private boolean inElement = false;
    private RecordFieldsCascade currRecordCascade = null;
    private RecordFieldsCascade reuseRecordCascade = null;

    // Registries
    private Map<QName, RecordFieldsCascade> cascadeRegistry = new HashMap<>();

    // Output
    private final RecordHandler recordHandler;

    // Helpers
    private final XMLEventFactory eventFactory = XMLEventFactory.newFactory();

    private FlattenXml(String xmlFilename, String recordTag,
                       RecordFieldsCascade.CascadePolicy cascadePolicy, Map<String, String[]> recordCascadesTemplates,
                       String[] xsdFiles, RecordHandler recordHandler)
            throws FileNotFoundException, XMLStreamException {

        this.recordTagGiven = recordTag;
        this.reader = XMLInputFactory.newFactory().createXMLEventReader(
                xmlFilename, new FileInputStream(xmlFilename));
        this.recordCascadesTemplates = recordCascadesTemplates;
        this.cascadePolicy = cascadePolicy;
        this.recordHandler = recordHandler;

        if (xsdFiles != null) {
            for (String xsd : xsdFiles) {
                xsds.add(new XmlSchema().parse(xsd));
            }
        }
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
            if (!reader.hasNext())
                recordHandler.closeAllFileStreams();
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
                    // The actual record tag string is parsed here as we now have the namespace context
                    if (recordTag != null) {
                        recordTag = XmlHelpers.parsePrefixTag(recordTagGiven,
                                el.getNamespaceContext(), rootElement.getName().getNamespaceURI());
                    }
                    cascadingStack.push(new RecordFieldsCascade(el, new String[0], xsds));
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

                if (tagName.equals(recordTag)) {
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
                prevEv = ev;

            } else if (tracking && ev.isEndElement()) { // End tag
                EndElement endElement = ev.asEndElement();

                // Previous element was data. Add it to the container's cascade list
                if (!tagStack.peek().isStartElement() && !tagStack.peek().isEndElement()) {
                    currRecordCascade.addCascadingData(
                            endElement.getName(),
                            tagStack.peek().asCharacters().getData(),
                            cascadePolicy);
                }

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
        if (tagStack.isEmpty()) {
            return;
        }
        XMLEvent ev;
        Stack<XmlHelpers.FieldValue<String, String>> fieldValueStack = new Stack<>();

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

            fieldValueStack.push(new XmlHelpers.FieldValue<>(startElement.getName().getLocalPart(), data));

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

        List<XmlHelpers.FieldValue<String, String>> fieldValueList;
        // Collect the list of fields a record should have
        if (schemaEl != null) {
            List<QName> recordSchemaFields = schemaEl.getChildElements().stream()
                    .filter(ch -> !XmlSchema.COMPLEX_TYPE.equals(ch.getType()))
                    .map(ch -> ch.getName()).collect(Collectors.toList());

            fieldValueList = alignFieldsToSchema(fieldValueStack, recordSchemaFields);
        } else {
            fieldValueList = new ArrayList<>();
            while (!fieldValueStack.isEmpty()) {
                fieldValueList.add(fieldValueStack.pop());
            }
        }

        if (captureRecordOnError != null) {
            // Capture the table name which has an error in the record.
            captureRecordOnError.push(ev);
        } else {
            // Write record to file if there are no errors.
            recordHandler.write(recordElementName, fieldValueList, cascadingStack.peek(), currLevel, previousFile());
        }
    }

    private List<XmlHelpers.FieldValue<String, String>> alignFieldsToSchema(
            List<XmlHelpers.FieldValue<String, String>> fieldValueStack, List<QName> schemaFields) {

        if (schemaFields == null || schemaFields.isEmpty()) {
            return fieldValueStack;
        }

        // Make a field-value map first.
        Map<String, String> fv = fieldValueStack.stream()
                .collect(Collectors.toMap(o -> o.field, o -> o.value));

        // Align header and data according to the order of fields defined in XSD for the record.
        // Force print fields that are missing for the record in the XML file.
        Stack<String[]> aligned = new Stack<>();
        return schemaFields.stream()
                .map( tagName -> XmlHelpers.toPrefixedTag(tagName) )
                .map( xsdFld -> new XmlHelpers.FieldValue<>(xsdFld,
                    Optional.ofNullable(fv.get(xsdFld)).orElse(XmlHelpers.EMPTY)) )
                .collect(Collectors.toList());
    }

    private String previousFile() {
        StartElement prevFile = tagPath.pop();
        String prevFileName = "ROOT";
        if (cascadingStack.size() > 1) {
            prevFileName = tagPath.peek().getName().getLocalPart();
        }
        tagPath.push(prevFile);
        return prevFileName;
    }

    private RecordFieldsCascade registerCascades(StartElement tag, RecordFieldsCascade parentRecCascade) {
        RecordFieldsCascade recordFieldsCascade = cascadeRegistry.get(tag.getName());
        if (recordFieldsCascade == null) {
            recordFieldsCascade = new RecordFieldsCascade(
                    tag, recordCascadesTemplates.get(tag.getName()), xsds);
            cascadeRegistry.put(tag.getName(), recordFieldsCascade);
        }

        return recordFieldsCascade.clearCurrentRecordCascades().cascadeFromParent(parentRecCascade);
        /*RecordFieldsCascade recordFieldsCascade = new RecordFieldsCascade(
                tag, recordCascadesTemplates.get(tag.getName()), xsds);
        return recordFieldsCascade.cascadeFromParent(parentRecCascade);*/
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

    public String getRecordTagGiven() {
        return recordTagGiven;
    }

    public QName getRecordTag() {
        return recordTag;
    }

    public StartElement getRootElement() {
        return rootElement;
    }

    public static class FlattenXmlBuilder {
        private String xmlFilename;
        private String recordTag = null;
        private RecordFieldsCascade.CascadePolicy cascadePolicy = RecordFieldsCascade.CascadePolicy.NONE;
        private Map<String, String[]> recordCascadesTemplates = Collections.emptyMap();
        private String[] xsdFiles;
        private RecordHandler recordHandler;

        public FlattenXmlBuilder setXmlFilename(String xmlFilename) {
            this.xmlFilename = xmlFilename;
            return this;
        }

        public FlattenXmlBuilder setRecordTag(String recordTag) {
            this.recordTag = recordTag;
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

        public FlattenXmlBuilder setRecordWriter(RecordHandler recordHandler) {
            this.recordHandler = recordHandler;
            return this;
        }

        public FlattenXml createFlattenXml()
                throws FileNotFoundException, XMLStreamException {
            // Input XML file, tag that identifies a record
            return new FlattenXml(xmlFilename, recordTag,
                    // Cascading data from parent record to child records
                    cascadePolicy, recordCascadesTemplates, xsdFiles, recordHandler);
        }
    }
}
