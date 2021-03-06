package com.karbherin.flatterxml.feeder;

import javax.xml.namespace.QName;
import javax.xml.stream.*;
import javax.xml.stream.events.XMLEvent;
import java.io.*;
import java.nio.channels.Channels;
import java.nio.channels.Pipe;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

public class XmlRecordEventEmitter implements XmlRecordEmitter {

    private final XMLOutputFactory outputFactory = XMLOutputFactory.newFactory();
    private final List<XMLEventWriter> channels = new ArrayList<>();
    private final List<OutputStream> pipes = new ArrayList<>();
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
    private XmlRecordEventEmitter(String xmlFile, long skipRecs, long firstNRecs) {
        this.xmlFile = xmlFile;
        this.skipRecs = skipRecs;
        this.firstNRecs = firstNRecs;
    }

    /**
     * Register a pipe to an events worker.
     * @param channel
     * @throws IOException
     * @throws XMLStreamException
     */
    public void registerChannel(Pipe.SinkChannel channel) throws XMLStreamException {
        OutputStream pipe = new BufferedOutputStream(Channels.newOutputStream(channel));
        XMLEventWriter writer = outputFactory.createXMLEventWriter(pipe);
        channels.add(writer);
        pipes.add(pipe);
    }

    /**
     * Start events feed into the pipes.
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
     * Flush and close channels to all the workers.
     * @throws XMLStreamException
     * @throws IOException
     */
    @Override
    public void closeAllChannels() throws XMLStreamException, IOException {
        for(XMLEventWriter channel: channels) {
            channel.flush();
            channel.close();
        }
        for(OutputStream pipe: pipes) {
            pipe.flush();
            pipe.close();
        }
    }

    /**
     * Start events feed after skipping a few records and limited to a few records after that.
     * @throws XMLStreamException
     * @throws IOException
     */
    private void feed() throws IOException, XMLStreamException {


        XMLEventReader reader = XMLInputFactory.newFactory().createXMLEventReader(
                Channels.newInputStream(Files.newByteChannel(
                        Paths.get(xmlFile), StandardOpenOption.READ)));

        int currentChannel = 0;
        boolean tracking = false;
        XMLEventWriter channel = channels.get(currentChannel);

        while (reader.hasNext()) {

            XMLEvent ev = reader.nextEvent();
            // Process first N records after skipping M records

            if (ev.isStartElement()) {

                QName startTag = ev.asStartElement().getName();

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
                channel.add(ev);
            } else if (ev.isStartDocument() || ev.isEndDocument() || rootTag == null) {
                sendToAllChannels(ev);
            }

            if (ev.isEndElement()) {
                QName endTag = ev.asEndElement().getName();

                // Send ending root tag to all channels
                if (endTag.equals(rootTag)) {
                    sendToAllChannels(ev);
                }

                if (endTag.equals(recordTag)) {
                    if (skipRecs > 0) {
                        skipRecs--;
                    } else if (tracking) {
                        recCounter++;
                    }
                    tracking = false;
                    // Switch to next channel
                    currentChannel = (++currentChannel) % channels.size();
                    channel = channels.get(currentChannel);
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

    public static class XmlEventEmitterBuilder {
        private String xmlFile;
        private long skipRecs = 0;
        private long firstNRecs = Long.MAX_VALUE;

        public XmlEventEmitterBuilder setXmlFile(String xmlFile) {
            this.xmlFile = xmlFile;
            return this;
        }

        public XmlEventEmitterBuilder setSkipRecs(long skipRecs) {
            this.skipRecs = skipRecs;
            return this;
        }

        public XmlEventEmitterBuilder setFirstNRecs(long firstNRecs) {
            this.firstNRecs = firstNRecs;
            return this;
        }

        public XmlRecordEventEmitter create() {
            return new XmlRecordEventEmitter(xmlFile, skipRecs, firstNRecs);
        }
    }

}
