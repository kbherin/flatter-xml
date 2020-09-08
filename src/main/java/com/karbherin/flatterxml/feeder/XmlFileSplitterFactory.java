package com.karbherin.flatterxml.feeder;

import javax.xml.stream.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PipedInputStream;
import java.util.concurrent.CountDownLatch;

public class XmlFileSplitterFactory implements XmlEventWorkerFactory {

    private final String outDir;
    private final String xmlFilePath;

    // The file name will be suffixed with "_part1|2|3.xml"
    private final String xmlFileName;

    private XmlFileSplitterFactory(String outDir, String xmlFilePath) {
        this.xmlFilePath = xmlFilePath;
        this.outDir = outDir;

        // Extract base name from the XML file path
        xmlFileName = new File(this.xmlFilePath).getName();

        // Create output directory if it does not exist
        File outDirHandle = new File(outDir);
        outDirHandle.mkdirs();
    }

    public static XmlFileSplitterFactory newInstance(String outDir, String xmlFilePath) {
        return new XmlFileSplitterFactory(outDir, xmlFilePath);
    }

    @Override
    public Runnable newWorker(PipedInputStream channel, int channelNum, CountDownLatch workerCounter) {
        return new Thread(() -> {
            XMLEventReader reader;
            try {
                reader = XMLInputFactory.newFactory().createXMLEventReader(channel);
                XMLEventWriter os = XMLOutputFactory.newFactory().createXMLEventWriter(
                        new FileOutputStream(String.format("%s/%s_part%d.xml", outDir, xmlFileName, channelNum)));

                while(reader.hasNext()) {
                    os.add(reader.nextEvent());
                }

            } catch (XMLStreamException | IOException e) {
                e.printStackTrace();

            } finally {
                workerCounter.countDown();
                try {
                    channel.close();
                } catch (IOException ioException) {
                    ioException.printStackTrace();
                }
            }
        });
    }
}
