package com.karbherin.flatterxml;

import javax.xml.stream.XMLStreamException;
import java.io.IOException;

/**
 * CLI main class for flattening an XML file into tabular files.
 *
 * @author Kartik Bherin
 */
public class FlattenXmlRunner {

    private static final int MIN_NUM_OF_ARGS = 1;
    private static final int MAX_NUM_OF_ARGS = 6;
    private static final int DEFAULT_BATCH_SIZE = 50;

    /**
     * Command line method for running XML flattener.
     * @param args
     * @throws XMLStreamException
     * @throws IOException
     */
    public static void main(String[] args)
        throws XMLStreamException, IOException {

        if (args.length > 0 && args[0].equals("-h") ||
                args.length < MIN_NUM_OF_ARGS || args.length > MAX_NUM_OF_ARGS) {
            System.err.println(
                    "Usage: FlattenXmlRunner <xml-file-path> [<output-dir> <main-record-tag> <num-recs>" +
                            " <delimiter> <batch-size>]\n\n"+
                            "<main-record-tag>: Everything within it is flattened into individual tabular files.\n" +
                            "<num-recs>=0: if provided, processing stops after first N records. 0 implies full file.\n" +
                            "<batch-size>=100: progress is reported after each batch\n"
            );
            System.exit(-1);
        }

        final long firstNRecs = args.length > 3 ? Long.parseLong(args[3]) : 0;
        final long batchSize = args.length > 5 ? Long.parseLong(args[5]) : DEFAULT_BATCH_SIZE;

        FlattenXml flattener = new FlattenXml.FlattenXmlBuilder()
                .setXmlFilename(args[0])
                .setOutDir(args.length <= 1 ? "." : args[1])
                .setRecordTag(args.length <= 2 ? null : args[2])
                .setDelimiter(args.length <= 4 ? "," : args[4])
                .createFlattenXml();

        System.out.println(String.format("Parsing in batches of %d records", batchSize));
        long start = System.currentTimeMillis();
        long totalRecs = 0; // Total records processed so far

        boolean recordTagProvided = false;
        if (flattener.getRecordTag() == null) {
            System.out.println("Starting record tag not provided.");
        } else {
            recordTagProvided = true;
            System.out.println(String.format("Starting record tag provided is '%s'", flattener.getRecordTag()));
        }

        while(true) {
            long recsInBatch = 0; // Number of records processed in current batch
            if (firstNRecs == 0) {
                // Process all XML records.
                recsInBatch = flattener.parseFlatten(batchSize);
            } else {
                // Process first N records.
                recsInBatch = flattener.parseFlatten(Math.min(batchSize, firstNRecs - totalRecs));
            }
            totalRecs += recsInBatch;

            if (!recordTagProvided) {
                recordTagProvided = true;
                System.out.println(String.format("Identified primary record tag '%s'", flattener.getRecordTag()));
            }

            // Timings
            long end = System.currentTimeMillis();
            long durSec = (end - start) / 1000;
            if (durSec < 600) {
                System.out.print(String.format("\rProcessed %d XML records in %d seconds",
                        totalRecs, durSec));
            } else if (durSec < 3600) {
                System.out.print(String.format("\rProcessed %d XML records in %d minutes %d seconds",
                        totalRecs, durSec/60, durSec%60));
            } else {
                System.out.print(String.format("\rProcessed %d XML records in %d hours %d minutes %d seconds",
                        totalRecs, durSec/3600, durSec/60, durSec%60));
            }

            // If previous batch processed 0 records then the processing is complete.
            if (recsInBatch == 0 || recsInBatch < batchSize) {
                break;
            }
        }

        System.out.print("\nFiles produced: ");
        for (String fileName: flattener.getFilesWritten()) {
            System.out.print(fileName + ", ");
        }
    }
}
