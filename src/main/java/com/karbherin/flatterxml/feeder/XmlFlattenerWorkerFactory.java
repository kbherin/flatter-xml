package com.karbherin.flatterxml.feeder;

import com.karbherin.flatterxml.FlattenXml;
import com.karbherin.flatterxml.RecordFieldsCascade;
import com.karbherin.flatterxml.XmlHelpers;
import com.karbherin.flatterxml.output.StatusReporter;
import com.karbherin.flatterxml.output.RecordHandler;
import com.karbherin.flatterxml.xsd.XmlSchema;

import javax.xml.stream.*;
import java.io.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

public class XmlFlattenerWorkerFactory implements XmlEventWorkerFactory {

    private final String outDir;
    private final String xmlFileName;
    private final FlattenXml flattener;
    private final long batchSize;
    private final StatusReporter statusReporter;

    private XmlFlattenerWorkerFactory(String xmlFilePath, String outDir, FlattenXml flattener,
                                      long batchSize, StatusReporter statusReporter) {

        this.outDir = outDir;
        this.flattener = flattener;
        this.xmlFileName = new File(xmlFilePath).getName(); // Extract base name from the XML file path
        this.batchSize = batchSize;
        this.statusReporter = statusReporter;
    }

    public static XmlFlattenerWorkerFactory newInstance(String xmlFilePath, InputStream xmlStream, String outDir,
                                                        String recordTag, String[] xsdFiles,
                                                        RecordFieldsCascade.CascadePolicy cascadePolicy,
                                                        Map<String, String[]> recordCascadesTemplates,
                                                        RecordHandler recordWriter,
                                                        long batchSize, StatusReporter statusReporter)
            throws FileNotFoundException, XMLStreamException {

        List<XmlSchema> xsds = XmlHelpers.parseXsds(xsdFiles);

        // Create XML flattener
        FlattenXml.FlattenXmlBuilder setup = new FlattenXml.FlattenXmlBuilder();
        setup.setRecordTag(recordTag)
                .setXsdFiles(xsds)
                .setCascadePolicy(cascadePolicy)
                .setRecordCascadesTemplates(recordCascadesTemplates)
                .setRecordWriter(recordWriter)
                .setXmlStream(xmlStream);

        FlattenXml flattener = setup.createFlattenXml();

        return new XmlFlattenerWorkerFactory(xmlFilePath, outDir, flattener, batchSize, statusReporter);
    }

    @Override
    public Thread newWorker(PipedInputStream channel, int channelNum, CountDownLatch workerCounter) {
        return new Thread(() -> {

            while(true) {
                long recsInBatch = 0L; // Number of records processed in current batch

                try {
                    recsInBatch = flattener.parseFlatten(batchSize);
                    statusReporter.incrementRecordCounter(recsInBatch);
                } catch (XMLStreamException|IOException ex) {
                    statusReporter.reportException(new Exception(String.format(
                            "Exception occurred in worker# %d after processing %d records in total",
                            channelNum, flattener.getTotalRecordCounter()),
                            ex));
                    statusReporter.incrementRecordCounter(flattener.getBatchRecCounter());
                    break;
                }

                // If previous batch processed 0 records then the processing is complete.
                if (recsInBatch == 0 || recsInBatch < batchSize) {
                    break;
                }
            }

        });
    }
}
