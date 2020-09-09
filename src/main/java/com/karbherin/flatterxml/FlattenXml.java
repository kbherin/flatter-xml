package com.karbherin.flatterxml;

import com.karbherin.flatterxml.model.FieldValue;
import com.karbherin.flatterxml.model.RecordFieldsCascade;
import com.karbherin.flatterxml.model.RecordsDefinitionRegistry;
import com.karbherin.flatterxml.output.RecordHandler;
import com.karbherin.flatterxml.xsd.XmlSchema;
import com.karbherin.flatterxml.xsd.XsdElement;

import javax.xml.namespace.QName;
import javax.xml.stream.*;
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
    private final String recordTagGiven;
    private QName recordTag = null;  // Will be populated from recordTagGiven
    private final XMLEventReader reader;
    private final List<XmlSchema> xsds = new ArrayList<>();
    private final RecordFieldsCascade.CascadePolicy cascadePolicy;
    private StartElement rootElement;
    private final RecordsDefinitionRegistry recordCascadesRegistry;
    private final RecordsDefinitionRegistry outputRecordFieldsSeq;

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

    // Output
    private final RecordHandler recordHandler;
    private long totalRecordCounter = 0L;
    private long batchRecCounter = 0L;

    // Helpers
    private final XMLEventFactory eventFactory = XMLEventFactory.newFactory();

    private FlattenXml(InputStream xmlStream, String recordTag,
                       RecordFieldsCascade.CascadePolicy cascadePolicy,
                       RecordsDefinitionRegistry recordCascadesRegistry,
                       RecordsDefinitionRegistry outputRecordFieldsSeq,
                       List<XmlSchema> xsds, RecordHandler recordHandler)
            throws XMLStreamException {

        this.recordTagGiven = recordTag;
        this.reader = XMLInputFactory.newFactory().createXMLEventReader(xmlStream);
        this.recordCascadesRegistry = recordCascadesRegistry;
        this.cascadePolicy = cascadePolicy;
        this.recordHandler = recordHandler;
        this.xsds.addAll(xsds);
        this.outputRecordFieldsSeq = outputRecordFieldsSeq;
    }

    /**
     * Flattens an entire XML file into tabular files.
     * @return
     * @throws XMLStreamException
     * @throws IOException
     */
    public long parseFlatten() throws XMLStreamException, IOException {
        long recCounter = 0L;
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
        // Batch record counter
        batchRecCounter = 0L;

        while (reader.hasNext() && batchRecCounter < firstNRecs) {
            final XMLEvent ev;

            try {
                ev = reader.nextEvent();
            } catch (XMLStreamException ex) {
                throw decorateParseError(ex);
            }

            if (ev.isEndDocument()) break;

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
                    cascadingStack.push(new RecordFieldsCascade(el, Collections.emptyList(), null, xsds));
                    tagPath.push(el);
                    prevEv = ev;
                    // User did not specify the primary record tag. Skip root element.
                    continue;
                }

                // Detect nesting boundary
                if (prevEv.isStartElement()) {
                    currRecordCascade = cascadingStack.peek();

                    // Add a cascading rule for a newly nested record.
                    if (!currRecordCascade.getRecordName().equals( tagPath.peek().getName() )) {

                        if (reuseRecordCascade != null &&
                                reuseRecordCascade.getRecordName().equals(tagPath.peek().getName())) {
                            // Reuse parent cascades if the record continues to be the same.
                            // The cascading templates will be reused.
                            currRecordCascade = reuseRecordCascade.clearCurrentRecordCascades();
                        } else {
                            // Cascade fields and values from parent record to this new record.
                            currRecordCascade = newRecordCascade(tagPath.peek(), currRecordCascade);
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
                    reuseRecordCascade = cascadingStack.pop().clearToCascadeToChildList();
                } else {
                    tagStack.push(ev);
                    inElement = false;
                }

                // Reached the end of the top level record.
                if (tagStack.empty() || endElement.getName().equals(recordTag)) {
                    tracking = false;
                    ++batchRecCounter;
                    ++totalRecordCounter;
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

        return batchRecCounter;
    }


    private void writeRecord(Stack<XMLEvent> captureRecordOnError) throws IOException {
        if (tagStack.isEmpty()) {
            return;
        }
        XMLEvent ev;
        Stack<FieldValue<String, String>> fieldValueStack = new Stack<>();

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

            fieldValueStack.push(new FieldValue<>(startElement.getName().getLocalPart(), data));

            // Capture the entire record if a parsing error has occurred in the record.
            if (captureRecordOnError != null) {
                captureRecordOnError.push(endElement);
                captureRecordOnError.push(eventFactory.createCharacters(data));
                captureRecordOnError.push(startElement);
            }
        }

        // The tabular file to write the record to.
        StartElement envelope = ev.asStartElement();
        String recordTagName = envelope.getName().getLocalPart();
        tagStack.push(ev);     // Add it back on to tag stack.

        // Final sequence of fields will be captured in this list
        List<FieldValue<String, String>> fieldValueList;

        // User specified list of output fields takes the top priority
        List<QName> outputFieldsSeq = outputRecordFieldsSeq.getRecordFields(envelope.getName());

        // Align XML tags and data with desired field sequence or XSD field sequence
        if (!outputFieldsSeq.isEmpty()) {
            // Align data from XML with the desired output fields sequence
            fieldValueList = alignFieldsToOutputFieldsSeq(fieldValueStack, outputFieldsSeq);

        } else {
            // Align data from XML with the sequence of fields in XSDs
            // Lookup schema for a list of fields a record can legitimately have
            XsdElement schemaEl = xsds.stream()
                    .map(xsd -> xsd.getElementByName(envelope.getName()))
                    .filter(xsd -> xsd != null)
                    .findFirst().orElse(null);

            if (schemaEl != null) {
                // Collect the list of fields a record should have
                List<QName> recordSchemaFields = schemaEl.getChildElements().stream()
                        .filter(ch -> !XmlSchema.COMPLEX_TYPE.equals(ch.getType()))
                        .map(ch -> ch.getName()).collect(Collectors.toList());

                // Align with fields sequence in XSD
                fieldValueList = alignFieldsToSchema(fieldValueStack, recordSchemaFields);
            } else {

                // Fallback. Dump everything in the sequence they appear in the XML file
                fieldValueList = new ArrayList<>();
                while (!fieldValueStack.isEmpty()) {
                    fieldValueList.add(fieldValueStack.pop());
                }
            }
        }

        if (captureRecordOnError != null) {
            // Capture the table name which has an error in the record.
            captureRecordOnError.push(ev);
        } else {
            // Write record to file if there are no errors.
            recordHandler.write(recordTagName, fieldValueList, cascadingStack.peek(), currLevel, previousFile());
        }
    }

    private List<FieldValue<String, String>> alignFieldsToOutputFieldsSeq(
            List<FieldValue<String, String>> fieldValueStack, List<QName> outFieldsSeq) {

        // Make a field-value map first.
        Map<String, String> fv = fieldValueStack.stream()
                .collect(Collectors.toMap(o -> o.getField(), o -> o.getValue()));

        Stack<String[]> aligned = new Stack<>();
        return outFieldsSeq.stream()
                .map( tagName -> tagName.getLocalPart() )
                .map( tagName -> new FieldValue<>(tagName,
                        XmlHelpers.emptyIfNull(fv.get(tagName))) )
                .collect(Collectors.toList());
    }

    private List<FieldValue<String, String>> alignFieldsToSchema(
            List<FieldValue<String, String>> fieldValueStack, List<QName> schemaFields) {

        if (schemaFields == null || schemaFields.isEmpty()) {
            return fieldValueStack;
        }

        // Make a field-value map first.
        Map<String, String> fv = fieldValueStack.stream()
                .collect(Collectors.toMap(o -> o.getField(), o -> o.getValue()));

        // Align header and data according to the order of fields defined in XSD for the record.
        // Force print fields that are missing for the record in the XML file.
        Stack<String[]> aligned = new Stack<>();
        return schemaFields.stream()
                .map( tagName -> new FieldValue<>(tagName.getLocalPart(),
                    XmlHelpers.emptyIfNull(fv.get(tagName.getLocalPart()))) )
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

    private RecordFieldsCascade newRecordCascade(StartElement tag, RecordFieldsCascade parentRecCascade) {
        RecordFieldsCascade recordFieldsCascade = new RecordFieldsCascade(
                tag, recordCascadesRegistry.getRecordFields(tag.getName()), parentRecCascade, xsds);
        return recordFieldsCascade;
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

    public long getTotalRecordCounter() {
        return totalRecordCounter;
    }

    public long getBatchRecCounter() {
        return batchRecCounter;
    }

    public static class FlattenXmlBuilder {
        private InputStream xmlStream;
        private String recordTag = null;
        private RecordFieldsCascade.CascadePolicy cascadePolicy = RecordFieldsCascade.CascadePolicy.NONE;
        private RecordsDefinitionRegistry recordCascadesRegistry = RecordsDefinitionRegistry.newInstance();
        private RecordsDefinitionRegistry outputRecordFieldsSeq = RecordsDefinitionRegistry.newInstance();
        private List<XmlSchema> xsds = Collections.emptyList();
        private RecordHandler recordHandler;

        public FlattenXmlBuilder setXmlStream(InputStream xmlStream) {
            this.xmlStream = xmlStream;
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

        public FlattenXmlBuilder setRecordCascadesRegistry(RecordsDefinitionRegistry recordCascadesRegistry) {
            this.recordCascadesRegistry = recordCascadesRegistry;
            return this;
        }

        public FlattenXmlBuilder setOutputRecordFieldsSeq(RecordsDefinitionRegistry outputRecordFieldsSeq) {
            this.outputRecordFieldsSeq = outputRecordFieldsSeq;
            return this;
        }

        public FlattenXmlBuilder setXsdFiles(List<XmlSchema> xsds) {
            if (xsds != null) {
                this.xsds = xsds;
            }
            return this;
        }

        public FlattenXmlBuilder setRecordWriter(RecordHandler recordHandler) {
            this.recordHandler = recordHandler;
            return this;
        }

        public FlattenXml create() throws XMLStreamException {
            // Input XML file, tag that identifies a record
            return new FlattenXml(xmlStream, recordTag,
                    // Cascading data from parent record to child records
                    cascadePolicy, recordCascadesRegistry, outputRecordFieldsSeq, xsds, recordHandler);
        }
    }
}
