package com.karbherin.flatterxml.feeder;

import javax.xml.namespace.QName;
import javax.xml.stream.*;
import javax.xml.stream.events.XMLEvent;
import java.io.*;
import java.nio.channels.Channels;
import java.nio.channels.Pipe;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

public class XmlFileSplitterFactory implements XmlEventWorkerFactory {

    private final String outDir;
    private final String xmlFilePath;

    // The file name will be suffixed with "_part1|2|3.xml"
    private final String xmlFileName;
    private int channelNumber = 0;
    private final List<Long> workerRecordsCount = new ArrayList<>();

    private XmlFileSplitterFactory(String outDir, String xmlFilePath)
            throws IOException {
        this.xmlFilePath = xmlFilePath;
        this.outDir = outDir;

        // Extract base name from the XML file path
        xmlFileName = new File(this.xmlFilePath).getName();

        // Create output directory if it does not exist
        Files.createDirectories(Paths.get(outDir));
    }

    public static XmlFileSplitterFactory newInstance(String outDir, String xmlFilePath)
            throws IOException {
        return new XmlFileSplitterFactory(outDir, xmlFilePath);
    }

    @Override
    public Runnable newWorker(Pipe.SourceChannel channel, CountDownLatch workerCounter) {
        final int channelNum = channelNumber;
        workerRecordsCount.add(0L);
        channelNumber++;

        return () -> {
            XMLEventReader reader;
            XMLEventWriter writer = null;
            try {
                reader = XMLInputFactory.newFactory().createXMLEventReader(
                        new BufferedInputStream(Channels.newInputStream(channel)));
                writer = XMLOutputFactory.newFactory().createXMLEventWriter(
                        new BufferedOutputStream(new FileOutputStream(
                                String.format("%s/%s_part%d.xml", outDir, xmlFileName, channelNum))));

                QName recordTag = null;
                int tagStackSize = 0;

                while(reader.hasNext()) {
                    XMLEvent ev = reader.nextEvent();
                    writer.add(ev);

                    if (ev.isStartElement() && !ev.isStartDocument()) {
                        if (tagStackSize == 1) {
                            recordTag = ev.asStartElement().getName();
                        }
                        tagStackSize++;

                    } else if (ev.isEndElement()) {
                        tagStackSize--;
                        if (tagStackSize == 1) {
                            workerRecordsCount.set(channelNum, workerRecordsCount.get(channelNum));
                        }
                    }
                }
            } catch (XMLStreamException | IOException e) {
                e.printStackTrace();

            } finally {
                try {
                    if (writer != null) {
                        writer.flush();
                    }
                    channel.close();

                } catch (IOException | XMLStreamException ioException) {
                    ioException.printStackTrace();
                }
                workerCounter.countDown();
            }
        };
    }

    public Long[] getRecordsCount() {
        return workerRecordsCount.toArray(new Long[workerRecordsCount.size()]);
    }
}
