package com.karbherin.flatterxml.feeder;

import javax.xml.namespace.QName;
import javax.xml.stream.*;
import javax.xml.stream.events.XMLEvent;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

public class XmlEventEmitter {

    private final List<XMLEventWriter> channels = new ArrayList<>();
    private final List<PipedOutputStream> pipes = new ArrayList<>();
    private final String xmlFile;
    private long skipRecs;
    private long firstNRecs;
    private QName rootTag = null;
    private QName recordTag = null;

    // Return number of records processed
    private long recCounter = 0;

    /**
     * Split the XML file and distribute the records into multiple XMLs.
     * A record in the XML file is identified by the record tag supplied.
     * @param xmlFile
     * @param skipRecs   - 0 disables skipping records
     * @param firstNRecs - 0 disables limiting to first N records
     * @throws IOException
     * @throws XMLStreamException
     */
    public XmlEventEmitter(String xmlFile, long skipRecs, long firstNRecs) {
        this.xmlFile = xmlFile;
        this.skipRecs = skipRecs;
        this.firstNRecs = firstNRecs;
    }

    /**
     * Start events feed. The entire XML file is processed.
     * @throws XMLStreamException
     * @throws IOException
     */
    public XmlEventEmitter(String xmlFile) {
        this(xmlFile, 0, Long.MAX_VALUE);
    }

    /**
     * Start events feed after skipping a few records.
     * @param skipRecs - 0 disables skipping records
     * @throws XMLStreamException
     * @throws IOException
     */
    public XmlEventEmitter(String xmlFile, long skipRecs) {
        this(xmlFile, skipRecs, Long.MAX_VALUE);
    }


    /**
     * Register a pipe to an events worker.
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
     * Start events feed after skipping a few records and limited to a few records after that.
     * @throws XMLStreamException
     * @throws IOException
     */
    public void startStream() throws XMLStreamException, IOException {
        try {
            feed();
        } finally {
            closeAllChannels();
        }
    }

    /**
     * Start events feed after skipping a few records and limited to a few records after that.
     * @throws XMLStreamException
     * @throws IOException
     */
    private void feed() throws IOException, XMLStreamException {


        XMLEventReader reader = XMLInputFactory.newFactory().createXMLEventReader(
                new FileInputStream(xmlFile));

        int currentChannel = 0;
        boolean tracking = false;
        Stack<QName> tagPath = new Stack<>();

        while (reader.hasNext()) {

            XMLEvent ev = reader.nextEvent();

            // Process first N records after skipping M records

            if (ev.isStartElement()) {

                QName startTag = ev.asStartElement().getName();
                tagPath.push(startTag);

                // If caller does not specify the primary record tag, then
                // pick the first start element after encountering the XML root.
                if (rootTag != null) {
                    if (recordTag == null) {
                        recordTag = startTag;
                    }
                } else {
                    // Process XML root
                    rootTag = startTag;
                    // Send starting root tag to all channels
                    sendToAllChannels(ev);
                }

                if (startTag.equals(recordTag) && skipRecs == 0 && firstNRecs-- > 0) {
                    tracking = true;
                }
            }

            if (tracking) {
                channels.get(currentChannel).add(ev);
            } else if (ev.isStartDocument() || ev.isEndDocument() || rootTag == null) {
                sendToAllChannels(ev);
            }

            if (ev.isEndElement()) {
                QName endTag = ev.asEndElement().getName();

                // Send ending root tag to all channels
                if (endTag.equals(rootTag)) {
                    sendToAllChannels(ev);
                    break;
                }

                if (endTag.equals(recordTag)) {
                    if (skipRecs > 0) {
                        skipRecs--;
                    } else if (tracking) {
                        recCounter++;
                    }
                    tracking = false;
                    // Switch to next channel
                    currentChannel = (currentChannel + 1) % channels.size();
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

    public QName getRootTag() {
        return rootTag;
    }

    public QName getRecordTag() {
        return recordTag;
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
