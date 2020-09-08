package com.karbherin.flatterxml.feeder;

import com.karbherin.flatterxml.FlattenXml;
import com.karbherin.flatterxml.XmlHelpers;
import com.karbherin.flatterxml.output.DelimitedFileHandler;
import com.karbherin.flatterxml.output.StatusReporter;
import com.karbherin.flatterxml.output.RecordHandler;
import com.karbherin.flatterxml.xsd.XmlSchema;

import static com.karbherin.flatterxml.RecordFieldsCascade.CascadePolicy;

import java.io.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;

public class XmlFlattenerWorkerFactory implements XmlEventWorkerFactory {

    private final String xmlFileName;
    private final String outDir;
    private final String delimiter;
    private final String recordTag;
    private final List<XmlSchema> xsds;
    private final CascadePolicy cascadePolicy;
    private final Map<String, String[]> recordCascadesTemplates;
    private final long batchSize;
    private final StatusReporter statusReporter;

    private XmlFlattenerWorkerFactory(String xmlFilePath, String outDir, String delimiter,
                                      String recordTag, List<XmlSchema> xsds,
                                      CascadePolicy cascadePolicy,
                                      Map<String, String[]> recordCascadesTemplates,
                                      long batchSize, StatusReporter statusReporter) {

        this.xmlFileName = new File(xmlFilePath).getName(); // Extract base name from the XML file path
        this.outDir = outDir;
        this.delimiter = delimiter;
        this.recordTag = recordTag;
        this.xsds = xsds;
        this.cascadePolicy = cascadePolicy;
        this.recordCascadesTemplates = recordCascadesTemplates;
        this.batchSize = batchSize;
        this.statusReporter = statusReporter;
    }

    public static XmlFlattenerWorkerFactory newInstance(String xmlFilePath, String outDir, String delimiter,
                                                        String recordTag, List<XmlSchema> xsds,
                                                        CascadePolicy cascadePolicy,
                                                        Map<String, String[]> recordCascadesTemplates,
                                                        long batchSize, StatusReporter statusReporter) {

        return new XmlFlattenerWorkerFactory(xmlFilePath, outDir, delimiter, recordTag,
                xsds, cascadePolicy, recordCascadesTemplates,
                batchSize, statusReporter);
    }

    @Override
    public Thread newWorker(PipedInputStream channel, int workerNum, CountDownLatch workerCounter) {
        RecordHandler recordHandler = new DelimitedFileHandler(delimiter, outDir, "_part" + workerNum);

        // Create XML flattener
        final FlattenXml.FlattenXmlBuilder setup = new FlattenXml.FlattenXmlBuilder()
                .setRecordTag(recordTag)
                .setXsdFiles(xsds)
                .setCascadePolicy(cascadePolicy)
                .setRecordCascadesTemplates(recordCascadesTemplates)
                .setRecordWriter(recordHandler)
                .setXmlStream(channel);

        return new Thread(() -> {

            long totalRecs = 0L;
            FlattenXml flattener = null;
            try {
                flattener = setup.create();
            } catch (Throwable ex) {
                statusReporter.logError(new Exception("Exception occurred. Could not start worker"), workerNum);
                ex.printStackTrace();
            }

            while(flattener != null) {
                long recsInBatch; // Number of records processed in current batch

                try {
                    recsInBatch = flattener.parseFlatten(batchSize);
                    statusReporter.incrementRecordCounter(recsInBatch);
                    totalRecs += recsInBatch;
                } catch (Throwable ex) {
                    statusReporter.logError( new Exception(String.format(
                            "\nException occurred after processing %d records in total",
                            workerNum, flattener.getTotalRecordCounter()), ex), workerNum );
                    statusReporter.incrementRecordCounter(flattener.getBatchRecCounter());
                    break;
                }

                statusReporter.showProgress();

                // If previous batch processed 0 records then the processing is complete.
                if (recsInBatch == 0 || recsInBatch < batchSize) {
                    break;
                }
            }

            statusReporter.logInfo(String.format(
                    "\nWorker %d: Total number of records processed by the worker = %d", workerNum, totalRecs));

            String fileSuffix = "_part" + workerNum;
            statusReporter.addFilesGenerated(recordHandler.getFilesWritten().stream()
                    .map(tuple -> {
                        tuple[1] += fileSuffix;
                        tuple[2] += fileSuffix;
                        return tuple;
                    }).collect(Collectors.toList()));
            workerCounter.countDown();

        });
    }
}
