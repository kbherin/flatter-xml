package com.karbherin.flatterxml;

import com.karbherin.flatterxml.helper.XmlHelpers;
import com.karbherin.flatterxml.model.ElementWithAttributes;
import com.karbherin.flatterxml.model.Pair;
import com.karbherin.flatterxml.model.RecordFieldsCascade;
import com.karbherin.flatterxml.model.RecordDefinitions;
import com.karbherin.flatterxml.output.RecordHandler;
import com.karbherin.flatterxml.xsd.XmlSchema;
import com.karbherin.flatterxml.xsd.XsdElement;

import static com.karbherin.flatterxml.helper.XmlHelpers.*;

import javax.xml.namespace.QName;
import javax.xml.stream.*;
import javax.xml.stream.events.*;
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
    private final RecordDefinitions recordCascadesRegistry;
    private final RecordDefinitions outputRecordFieldsSeq;

    // Maps namespace URIs in the xmlns declarations to prefixes
    private final Map<String, Namespace> xmlnsUriToPrefix = new HashMap<>();

    // Parsing state
    private final Deque<XMLEvent> tagStack = new ArrayDeque<>();
    private final Deque<RecordFieldsCascade> cascadingStack = new ArrayDeque<>();
    private final Deque<StartElement> tagPath = new ArrayDeque<>();
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
                       RecordDefinitions recordCascadesRegistry,
                       RecordDefinitions outputRecordFieldsSeq,
                       List<XmlSchema> xsds, RecordHandler recordHandler)
            throws XMLStreamException {

        this.recordTagGiven = recordTag;
        this.reader = XMLInputFactory.newFactory().createXMLEventReader(new BufferedInputStream(xmlStream));
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
        return flattenXmlDoc(firstNRecords);
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
                    iteratorStream((Iterator<Namespace>) rootElement.getNamespaces()).forEach(ns -> {
                        xmlnsUriToPrefix.put(ns.getNamespaceURI(), ns);
                    });
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
                    if (!currRecordCascade.recordName().equals( tagPath.peek().getName() )) {

                        if (reuseRecordCascade != null &&
                                reuseRecordCascade.recordName().equals(tagPath.peek().getName())) {
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
                if (tagStack.isEmpty() || endElement.getName().equals(recordTag)) {
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


    private void writeRecord(Deque<XMLEvent> captureRecordOnError) throws IOException {
        if (tagStack.isEmpty()) {
            return;
        }
        XMLEvent ev;
        Deque<Pair<StartElement, String>> pairStack = new ArrayDeque<>();

        if (tagStack.isEmpty()) {
            return;
        }

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

            pairStack.push(new Pair<>(startElement, data));

            // Capture the entire record if a parsing error has occurred in the record.
            if (captureRecordOnError != null) {
                captureRecordOnError.push(endElement);
                captureRecordOnError.push(eventFactory.createCharacters(data));
                captureRecordOnError.push(startElement);
            }
        }

        // The tabular file to write the record to.
        StartElement envelope = ev.asStartElement();
        QName recordName = envelope.getName();
        tagStack.push(ev);     // Add it back on to tag stack.

        // Capture the record name which has an error in the record.
        if (captureRecordOnError != null) {
            captureRecordOnError.push(ev);
            return;
        }

        // User specified list of output fields takes the top priority
        List<? extends ElementWithAttributes> outputFieldsSeq = outputRecordFieldsSeq.getRecordFields(recordName);

        // Final sequence of fields will be captured in this list
        List<List<Pair<String, String>>> records = null;

        // Goal: Align XML tags and data with desired field sequence or XSD field sequence or fallback to dump all
        if (!outputFieldsSeq.isEmpty()) {
            // 1. Align data from XML with the desired output fields sequence
            records = alignFieldsToSchema(pairStack, outputFieldsSeq);

        } else {
            // 2: Align data from XML with the sequence of fields in XSDs

            // Lookup schema for a list of fields a record can legitimately have
            XsdElement schemaEl = xsds.stream()
                    .map(xsd -> xsd.getElementByName(recordName))
                    .filter(xsd -> xsd != null)
                    .findFirst().orElse(null);

            if (schemaEl != null) {
                // Collect the list of fields a record should have
                List<ElementWithAttributes> recordSchemaFields = schemaEl.getChildElements().stream()
                        .filter(ch -> !XmlSchema.COMPLEX_TYPE.equals(ch.getType()))
                        .collect(Collectors.toList());

                // Align with fields sequence in XSD
                records = alignFieldsToSchema(pairStack, recordSchemaFields);
            }
        }

        // 2. Final fallback - dump all XML fields and values
        if (records == null) {
            // Fallback. Dump everything in the sequence they appear in the XML file
            records = alignFieldsToSchema(pairStack, null);
        }

        // Write record to file if there are no errors.
        for (List<Pair<String, String>> record: records)
            recordHandler.write(recordName.getLocalPart(), record, cascadingStack.peek(), cascadingStack.peek());
    }

    private List<Pair<String, String>> extractAttributesData(StartElement dataElem, ElementWithAttributes schemaElem) {
        String elemName = toPrefixedTag(dataElem.getName());
        return schemaElem.getAttributes().stream().map(schemaAttr -> {
            Attribute attrData = dataElem.getAttributeByName(schemaAttr);
            if (attrData == null) {
                return new Pair<>(String.format("%s[%s]", elemName, toPrefixedTag(schemaAttr)),
                        EMPTY);
            } else {
                return new Pair<>(String.format("%s[%s]", elemName, toPrefixedTag(attrData.getName())),
                        attrData.getValue());
            }
        }).collect(Collectors.toList());
    }

    private List<List<Pair<String, String>>> alignFieldsToSchema(
            Collection<Pair<StartElement, String>> pairStack, List<? extends ElementWithAttributes> schemaFields) {

        // Make a field-value map first. Group by tag names to catch repetitions.
        Map<QName, List<Pair<StartElement, String>>> fieldGroups = pairStack.stream()
                .collect(Collectors.groupingBy(pair -> pair.getKey().getName(), LinkedHashMap::new, Collectors.toList()));

        List<List<Pair<String, String>>> records = new ArrayList<>();
        records.add(new ArrayList<>());

        List<QName> fieldsListing;
        boolean fieldSeqPref;

        if (schemaFields == null || schemaFields.isEmpty()) {
            fieldsListing = fieldGroups.keySet().stream().collect(Collectors.toList());
            fieldSeqPref = false;
        } else {
            fieldsListing = schemaFields.stream().map(field -> field.getName()).collect(Collectors.toList());
            fieldSeqPref = true;
        }

        // Align header and data according to the order of fields defined in XSD for the record.
        // Force print fields that are missing for the record in the XML file.
        for (int i = 0; i < fieldsListing.size(); i++) {
            QName tagName = fieldsListing.get(i);
            List<Pair<StartElement, String>> fieldValues = fieldGroups.get(tagName);

            if (fieldValues == null) {
                String prefix = xmlnsUriToPrefix.get(tagName.getNamespaceURI()).getPrefix();
                for (List<Pair<String, String>> rec : records) {
                    rec.add(new Pair<>(prefix+":"+tagName.getLocalPart(), EMPTY));
                }
                continue;
            }

            int repetition = 0;
            List<List<Pair<String, String>>> baseRecords = records;

            // Replicate result records as many times a field is repeated and
            // add each value for a repeated field to one set of replicated records
            for (Pair<StartElement, String> fv : fieldValues) {

                // Clone the records list if we are dealing with the first repetition of a field
                if (repetition == 1) {
                    baseRecords = new ArrayList<>(records.size());
                    baseRecords.addAll(records);
                }

                StartElement dataElem = fv.getKey();
                Pair<String, String> fieldNameValue = new Pair<>(toPrefixedTag(dataElem.getName()), fv.getVal());
                List<Pair<String, String>> attrsData = null;
                int numAttrs = 0;
                if (fieldSeqPref) {
                    ElementWithAttributes schemaElem = schemaFields.get(i);
                    attrsData = extractAttributesData(dataElem, schemaElem);
                    numAttrs = attrsData.size();
                }

                for (List<Pair<String, String>> rec : baseRecords) {

                    if (repetition == 0) {
                        rec.add(fieldNameValue);
                        if (attrsData != null) {
                            for (Pair<String, String> attrData : attrsData) {
                                rec.add(attrData);
                            }
                        }
                    } else {

                        // Clone the record if we have tags repeated
                        List<Pair<String, String>> recCopy = rec.stream().collect(Collectors.toList());
                        // Update the last field in the record
                        recCopy.set(recCopy.size() - numAttrs - 1, fieldNameValue);
                        if (numAttrs > 0) {
                            for (int j = 0; j < numAttrs; j++) {
                                Pair<String, String> attrData = attrsData.get(j);
                                rec.set(recCopy.size() - numAttrs + j, attrData);
                            }
                        }
                        // Add to the collection of records
                        records.add(recCopy);
                    }
                }

                repetition++;
            }
        }

        return records;
    }

    private RecordFieldsCascade newRecordCascade(StartElement tag, RecordFieldsCascade parentRecCascade) {
        RecordFieldsCascade recordFieldsCascade = new RecordFieldsCascade(
                tag, recordCascadesRegistry.getRecordFieldNames(tag.getName()), parentRecCascade, xsds);
        return recordFieldsCascade;
    }

    private XMLStreamException decorateParseError(XMLStreamException ex) throws IOException {
        Deque<XMLEvent> errorRec = new ArrayDeque<>();
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
        private RecordDefinitions recordCascadeFieldsSeq = RecordDefinitions.newInstance();
        private RecordDefinitions recordOutputFieldsSeq = RecordDefinitions.newInstance();
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

        public FlattenXmlBuilder setRecordCascadeFieldsSeq(File yamlFileName) throws IOException {
            if (yamlFileName == null) {
                this.recordCascadeFieldsSeq = RecordDefinitions.newInstance();
            } else {
                this.recordCascadeFieldsSeq = RecordDefinitions.newInstance(yamlFileName);
            }
            return this;
        }

        public FlattenXmlBuilder setRecordOutputFieldsSeq(File yamlFileName) throws IOException {
            if (yamlFileName == null) {
                this.recordOutputFieldsSeq = RecordDefinitions.newInstance();
            } else {
                this.recordOutputFieldsSeq = RecordDefinitions.newInstance(yamlFileName);
            }
            return this;
        }

        public FlattenXmlBuilder setRecordCascadeFieldsSeq(RecordDefinitions recordCascadeFieldsSeq) {
            this.recordCascadeFieldsSeq = recordCascadeFieldsSeq;
            return this;
        }

        public FlattenXmlBuilder setRecordOutputFieldsSeq(RecordDefinitions recordOutputFieldsSeq) {
            this.recordOutputFieldsSeq = recordOutputFieldsSeq;
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
                    cascadePolicy, recordCascadeFieldsSeq, recordOutputFieldsSeq, xsds, recordHandler);
        }
    }
}
