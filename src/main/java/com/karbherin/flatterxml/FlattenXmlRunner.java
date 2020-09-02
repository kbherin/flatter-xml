package com.karbherin.flatterxml;

import org.apache.commons.cli.*;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * CLI main class for flattening an XML file into tabular files.
 *
 * @author Kartik Bherin
 */
public class FlattenXmlRunner {

    private static final int MIN_NUM_OF_ARGS = 1;
    private static final int MAX_NUM_OF_ARGS = 6;
    private static final int DEFAULT_BATCH_SIZE = 50;
    private static final String INDENT = "  ";

    private static Options opts = new Options();

    static {
        opts.addOption("o", "output-dir", true, "Output direction for generating tabular files. Defaults to current directory");
        opts.addOption("d", "delimiter", true, "Delimiter. Defaults to a comma");
        opts.addOption("r", "record-tag", true, "Primary record tag from where parsing begins. If not provided entire file will be parsed");
        opts.addOption("n", "n-records", true, "Number of records to process in the XML document");
        opts.addOption("p", "progress", false, "Report progress after a batch");
        opts.addOption("c", "cascades", true, "Data of specified tags on parent element is cascaded to child elements.\nFormat: elem1:tag1,tag2;elem2:tag1,tag2;");
    }

    private static void printHelp() {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp( "FlattenXmlRunner XMLFile [OPTIONS]", opts);
    }

    private static CommandLine parseCliArgs(String[] args) {
        CommandLineParser parser = new DefaultParser();
        try {
            return parser.parse(opts, args);
        } catch (ParseException ex) {
            printHelp();
            throw new IllegalArgumentException("Could not understand the options provided to the program");
        }
    }

    /**
     * Command line method for running XML flattener.
     * @param args
     * @throws XMLStreamException
     * @throws IOException
     */
    public static void main(String[] args)
        throws XMLStreamException, IOException {


        CommandLine cmd = parseCliArgs(args);

        FlattenXml.FlattenXmlBuilder setup = new FlattenXml.FlattenXmlBuilder();

        if (cmd.hasOption("o")) {
            setup.setOutDir(cmd.getOptionValue("o"));
        }
        if (cmd.hasOption("d")) {
            setup.setDelimiter(cmd.getOptionValue("d"));
        }
        if (cmd.hasOption("r")) {
            XmlHelpers.parseTagValueCascades(cmd.getOptionValue("r"));
        }
        if (cmd.hasOption("c")) {
            if (cmd.getOptionValue("c").trim().equalsIgnoreCase(RecordFieldsCascade.CascadePolicy.ALL.toString())) {
                setup.setCascadePolicy(RecordFieldsCascade.CascadePolicy.ALL);
            } else {
                setup.setRecordCascadesTemplates(XmlHelpers.parseTagValueCascades(cmd.getOptionValue("c")));
            }
        }

        final long firstNRecs, batchSize;
        try {
            firstNRecs = cmd.hasOption("n") ? Long.parseLong(cmd.getOptionValue("n")) : 0;
            batchSize = cmd.hasOption("p") && cmd.getOptionValue("p") != null
                    ? Long.parseLong(cmd.getOptionValue("p")) : DEFAULT_BATCH_SIZE;
        } catch (NumberFormatException ex) {
            printHelp();
            throw new NumberFormatException("Options -n and -p should be numeric");
        }
        if (cmd.getArgs().length > 1) {
            printHelp();
            throw new IllegalArgumentException("Too many XML files are passed as input. Only 1 file is allowed");
        }
        if (cmd.getArgs().length < 1) {
            printHelp();
            throw new IllegalArgumentException("Could not parse the arguments passed. XMLFile path is required");
        }
        setup.setXmlFilename(cmd.getArgs()[0]);

        final FlattenXml flattener = setup.createFlattenXml();

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

        // Display the files generated
        List<String> filesWritten = flattener.getFilesWritten();
        System.out.println("\nFiles produced: " + filesWritten.size());
        Collections.sort(filesWritten);
        StringBuffer filesGen = new StringBuffer();
        for (String fileName: filesWritten) {
            String[] lvlFile = fileName.split("\\.");
            int level = Integer.parseInt(lvlFile[0]);
            while (level-- > 2)
                filesGen.append(INDENT);
            if (level > 0)
                filesGen.append("|__");
            filesGen.append(lvlFile[1]).append("\n");
        }
        System.out.println(filesGen);
    }
}
