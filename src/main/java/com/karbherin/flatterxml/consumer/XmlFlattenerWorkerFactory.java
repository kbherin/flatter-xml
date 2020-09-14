package com.karbherin.flatterxml.consumer;

import com.karbherin.flatterxml.FlattenXml;
import com.karbherin.flatterxml.model.RecordsDefinitionRegistry;
import com.karbherin.flatterxml.output.StatusReporter;
import com.karbherin.flatterxml.output.RecordHandler;
import com.karbherin.flatterxml.xsd.XmlSchema;

import static com.karbherin.flatterxml.model.RecordFieldsCascade.CascadePolicy;

import java.io.*;
import java.nio.channels.Channels;
import java.nio.channels.Pipe;
import java.util.List;
import java.util.concurrent.CountDownLatch;

public class XmlFlattenerWorkerFactory implements XmlEventWorkerFactory {

    private final String xmlFileName;
    private final String outDir;
    private final String delimiter;
    private final String recordTag;
    private final List<XmlSchema> xsds;
    private final CascadePolicy cascadePolicy;
    private final RecordsDefinitionRegistry recordCascadeFieldsSeq;
    private final RecordsDefinitionRegistry recordOutputFieldsSeq;
    private final long batchSize;
    private final StatusReporter statusReporter;
    private int workerNumber = 0;
    private RecordHandler recordHandler;

    private XmlFlattenerWorkerFactory(String xmlFilePath, String outDir, String delimiter,
                                      String recordTag, RecordHandler recordHandler,
                                      CascadePolicy cascadePolicy,
                                      List<XmlSchema> xsds,
                                      RecordsDefinitionRegistry recordCascadeFieldsSeq,
                                      RecordsDefinitionRegistry recordOutputFieldsSeq,
                                      long batchSize, StatusReporter statusReporter) {

        this.xmlFileName = new File(xmlFilePath).getName(); // Extract base name from the XML file path
        this.outDir = outDir;
        this.delimiter = delimiter;
        this.recordTag = recordTag;
        this.xsds = xsds;
        this.cascadePolicy = cascadePolicy;
        this.recordCascadeFieldsSeq = recordCascadeFieldsSeq;
        this.recordOutputFieldsSeq = recordOutputFieldsSeq;
        this.batchSize = batchSize;
        this.statusReporter = statusReporter;
        this.recordHandler = recordHandler;
    }

    public static XmlFlattenerWorkerFactory newInstance(String xmlFilePath, String outDir, String delimiter,
                                                        String recordTag, RecordHandler recordHandler,
                                                        CascadePolicy cascadePolicy,
                                                        List<XmlSchema> xsds,
                                                        File recordCascadeFieldsDefFile,
                                                        File recordOutputFieldsDefFile,
                                                        long batchSize, StatusReporter statusReporter)
            throws IOException {

        RecordsDefinitionRegistry recordCascadeFieldsSeq = null, recordOutputFieldsSeq = null;
        if (recordCascadeFieldsDefFile != null) {
            recordCascadeFieldsSeq = RecordsDefinitionRegistry.newInstance(recordCascadeFieldsDefFile);
        } else {
            recordCascadeFieldsSeq = RecordsDefinitionRegistry.newInstance();
        }
        if (recordOutputFieldsDefFile != null) {
            recordOutputFieldsSeq = RecordsDefinitionRegistry.newInstance(recordOutputFieldsDefFile);
        } else {
            recordOutputFieldsSeq = RecordsDefinitionRegistry.newInstance();
        }

        return new XmlFlattenerWorkerFactory(xmlFilePath, outDir, delimiter, recordTag, recordHandler,
                cascadePolicy, xsds, recordCascadeFieldsSeq, recordOutputFieldsSeq,
                batchSize, statusReporter);
    }

    @Override
    public Runnable newWorker(Pipe.SourceChannel channel, CountDownLatch workerCounter) {
        this.workerNumber++;

        final int workerNum = this.workerNumber;


        // Create XML flattener
        final FlattenXml.FlattenXmlBuilder setup = new FlattenXml.FlattenXmlBuilder()
                .setRecordTag(recordTag)
                .setXsdFiles(xsds)
                .setCascadePolicy(cascadePolicy)
                .setRecordCascadeFieldsSeq(recordCascadeFieldsSeq)
                .setRecordOutputFieldsSeq(recordOutputFieldsSeq)
                .setRecordWriter(recordHandler)
                .setXmlStream(Channels.newInputStream(channel));

        // Return the worker to run in a thread
        return () -> {

            try {
                long totalRecs = 0L;
                FlattenXml flattener = null;
                try {
                    flattener = setup.create();
                } catch (Throwable ex) {
                    statusReporter.logError(new Exception("Exception occurred. Could not start worker"), workerNum);
                    ex.printStackTrace();
                }

                while (flattener != null) {
                    long recsInBatch; // Number of records processed in current batch

                    try {
                        recsInBatch = flattener.parseFlatten(batchSize);
                        statusReporter.incrementRecordCounter(recsInBatch);
                        totalRecs += recsInBatch;
                    } catch (Throwable ex) {
                        statusReporter.logError(new Exception(String.format(
                                "\nException occurred after processing %d records in total",
                                workerNum, flattener.getTotalRecordCounter()), ex), workerNum);
                        statusReporter.incrementRecordCounter(flattener.getBatchRecCounter());
                        break;
                    }

                    statusReporter.showProgress();

                    // If previous batch processed 0 records then the processing is complete.
                    if (recsInBatch == 0 || recsInBatch < batchSize) {
                        break;
                    }
                }

                statusReporter.addFilesGenerated(recordHandler.getFilesWritten());
            } finally {
                workerCounter.countDown();
            }

        };
    }
}
