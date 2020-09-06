package com.karbherin.flatterxml.feeder;

import com.karbherin.flatterxml.XmlHelpers;

import javax.xml.namespace.QName;
import javax.xml.stream.*;
import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

public class XmlEventEmitter {

    private final List<XMLEventWriter> channels = new ArrayList<>();
    private final List<PipedOutputStream> pipes = new ArrayList<>();
    private final XMLEventReader reader;
    private final String recordTagGiven;

    // Return number of records processed
    private long recCounter = 0;

    /**
     * Split the XML file and distribute the records into multiple XMLs.
     * A record in the XML file is identified by the record tag supplied.
     * @param xmlFile
     * @param recordTag
     * @throws IOException
     * @throws XMLStreamException
     */
    public XmlEventEmitter(String xmlFile,
                           String recordTag)
            throws IOException, XMLStreamException {

        // Each processing channel should run in its own thread
        reader = XMLInputFactory.newFactory().createXMLEventReader(new FileInputStream(xmlFile));
        this.recordTagGiven = recordTag;
    }

    /**
     * Split the XML assuming the first tag after the root element forms a record.
     * @param xmlFile
     * @throws IOException
     * @throws XMLStreamException
     */
    public XmlEventEmitter(String xmlFile)
            throws IOException, XMLStreamException {

        this(xmlFile, null);
    }


    /**
     * Register a pipe to a events client.
     * @param worker
     * @throws IOException
     * @throws XMLStreamException
     */
    public void registerChannel(PipedInputStream worker) throws IOException, XMLStreamException {
        XMLOutputFactory outputFactory = XMLOutputFactory.newFactory();
        PipedOutputStream pipe = new PipedOutputStream(worker);
        channels.add(outputFactory.createXMLEventWriter(pipe));
        pipes.add(pipe);
    }

    /**
     * Start events feed. The entire XML file is processed.
     * @throws XMLStreamException
     * @throws IOException
     */
    public void startStream() throws XMLStreamException, IOException {
        startStream(0, -1);
    }

    /**
     * Start events feed after skipping a few records.
     * @param skipRecs
     * @throws XMLStreamException
     * @throws IOException
     */
    public void startStream(int skipRecs) throws XMLStreamException, IOException {
        startStream(skipRecs, -1);
    }

    /**
     * Start events feed after skipping a few records and limited to a few records after that.
     * @param skipRecs
     * @param firstNRecs
     * @throws XMLStreamException
     * @throws IOException
     */
    public void startStream(int skipRecs, int firstNRecs) throws XMLStreamException, IOException {
        try {
            feed(skipRecs, firstNRecs);
        } finally {
            closeAllChannels();
        }
    }

    /**
     * Start events feed after skipping a few records and limited to a few records after that.
     * @param skipRecs
     * @param firstNRecs
     * @throws XMLStreamException
     * @throws IOException
     */
    private void feed(int skipRecs, int firstNRecs) throws XMLStreamException, IOException {

        int currentChannel = 0;
        QName recordTag = null;
        StartElement rootElement = null;
        boolean tracking = false;
        XMLEventFactory eventFactory = XMLEventFactory.newFactory();

        // Parsing state stack
        final Stack<StartElement> tagPath = new Stack<>();

        while (firstNRecs-- != 0 &&
                channels.size() > 0 && (firstNRecs < 0 || recCounter < firstNRecs) && reader.hasNext()) {

            XMLEvent ev = reader.nextEvent();
            if (ev.isStartDocument() || ev.isEndDocument()) {
                sendToAllChannels(ev);
                continue;
            }

            if (ev.isStartElement()) {

                StartElement startEl = ev.asStartElement();
                QName tagName = startEl.getName();
                tagPath.push(startEl);

                // If caller does not specify the primary record tag, then
                // pick the first start element after encountering the XML root.
                if (rootElement != null) {

                    if (recordTag == null) {
                        recordTag = tagName;
                    }
                } else {

                    // Process XML root
                    rootElement = startEl;
                    // The actual record tag string is parsed here as we now have the namespace context
                    if (recordTag != null) {
                        recordTag = XmlHelpers.parsePrefixTag(recordTagGiven,
                                startEl.getNamespaceContext(), rootElement.getName().getNamespaceURI());
                    }

                    // Send starting root tag to all channels
                    sendToAllChannels(ev);
                }

                if (skipRecs <= 0 && startEl.getName().equals(recordTag)) {
                    tracking = true;
                }

            }

            if (tracking && (ev.isCharacters() || ev.isStartElement() || ev.isEndElement())) {
                channels.get(currentChannel).add(ev);
            } else if (ev.isEndElement() && ev.asEndElement().getName().equals(rootElement.getName())) {
                // Send ending root tag to all channels
                sendToAllChannels(ev);
            }

            if (ev.isEndElement()) {
                StartElement startEl = tagPath.pop();
                EndElement endEl = ev.asEndElement();

                if (endEl.getName().equals(recordTag)) {
                    tracking = false;
                    // Switch to next channel
                    currentChannel = (currentChannel + 1) % channels.size();
                    // Update rec counter
                    recCounter++;
                    skipRecs--;
                }
            }
        }

    }

    /**
     * Number of records in the XML file that were processed.
     * @return
     */
    public long getRecCounter() {
        return recCounter;
    }

    /**
     * Start document and end document events are sent to all workers.
     * @param ev
     * @throws XMLStreamException
     */
    private void sendToAllChannels(XMLEvent ev) throws XMLStreamException {
        for (XMLEventWriter channel: channels) {
            channel.add(ev);
        }
    }

    /**
     * Flush and close channels to all the workers.
     * @throws XMLStreamException
     * @throws IOException
     */
    private void closeAllChannels() throws XMLStreamException, IOException {
        for(XMLEventWriter channel: channels) {
            channel.flush();
            channel.close();
        }
        for(PipedOutputStream pipe: pipes) {
            pipe.flush();
            pipe.close();
        }
    }
}
