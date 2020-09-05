package com.karbherin.flatterxml;

import com.karbherin.flatterxml.output.DelimitedFileHandler;
import com.karbherin.flatterxml.output.RecordHandler;
import org.apache.commons.cli.*;

import javax.xml.stream.XMLStreamException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * CLI main class for flattening an XML file into tabular files.
 *
 * @author Kartik Bherin
 */
public class FlattenXmlRunner {

    private static final int DEFAULT_BATCH_SIZE = 100;
    private static final String INDENT = "  ";
    private static final Options OPTIONS = new Options();

    static {
        OPTIONS.addOption("o", "output-dir", true, "Output direction for generating tabular files. Defaults to current directory");
        OPTIONS.addOption("d", "delimiter", true, "Delimiter. Defaults to a comma");
        OPTIONS.addOption("r", "record-tag", true, "Primary record tag from where parsing begins. If not provided entire file will be parsed");
        OPTIONS.addOption("n", "n-records", true, "Number of records to process in the XML document");
        OPTIONS.addOption("p", "progress", false, "Report progress after a batch");
        OPTIONS.addOption("c", "cascades", true, "Data of specified tags on parent element is cascaded to child elements.\nFormat: elem1:tag1,tag2;elem2:tag1,tag2;");
        OPTIONS.addOption("x", "xsd", true, "XSD files. Comma separated list.");
    }

    private static void printHelp() {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp( "FlattenXmlRunner XMLFile [OPTIONS]", OPTIONS);
    }

    private static CommandLine parseCliArgs(String[] args) {
        CommandLineParser parser = new DefaultParser();
        try {
            return parser.parse(OPTIONS, args);
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

        String delimiter = "|", outDir = "csvs";
        if (cmd.hasOption("o")) {
            outDir = cmd.getOptionValue("o");
            createOutputDirectory(outDir);
        }
        if (cmd.hasOption("d")) {
            delimiter = cmd.getOptionValue("d");
        }

        DelimitedFileHandler recordHandler = new DelimitedFileHandler(delimiter, outDir);
        setup.setRecordWriter(recordHandler);

        if (cmd.hasOption("r")) {
            String recordTag = cmd.getOptionValue("r");
            if (recordTag.trim().length() > 0)
                setup.setRecordTag(recordTag);
        }
        if (cmd.hasOption("c")) {
            if (cmd.getOptionValue("c").trim().equalsIgnoreCase(
                    RecordFieldsCascade.CascadePolicy.ALL.toString())) {

                setup.setCascadePolicy(RecordFieldsCascade.CascadePolicy.ALL);
            } else if (cmd.getOptionValue("c").trim().equalsIgnoreCase(
                    RecordFieldsCascade.CascadePolicy.XSD.toString())) {

                setup.setCascadePolicy(RecordFieldsCascade.CascadePolicy.XSD);
            } else {

                setup.setRecordCascadesTemplates(
                        XmlHelpers.parseTagValueCascades(cmd.getOptionValue("c")));
            }
        }
        if (cmd.hasOption("x")) {
            setup.setXsdFiles(cmd.getOptionValue("x").split(","));
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
        List<String[]> filesWritten = recordHandler.getFilesWritten();
        StringBuilder filesTreeStr = new StringBuilder();
        System.out.println("\nFiles produced: " + filesWritten.size());
        Map<String, List<String[]>> groupedByParent = filesWritten.stream()
                .collect(Collectors.groupingBy(r -> r[2], Collectors.toList()));


        for (String[] child: groupedByParent.get(flattener.getRootElement().getName().getLocalPart())) {
            drillDownFilesHeap(groupedByParent, child[1], Integer.parseInt(child[0]), filesTreeStr);
        }

        System.out.println(filesTreeStr);
    }

    private static void drillDownFilesHeap(Map<String, List<String[]>> grouped, String file, int level,
                                           StringBuilder filesGen) {
        while (level-- > 2) {
            filesGen.append(INDENT);
        }
        if (level > 0) {
            filesGen.append("|__");
        }
        filesGen.append(file).append("\n");

        if (!grouped.containsKey(file)) {
            return;
        }
        for (String[] child: grouped.get(file)) {
            drillDownFilesHeap(grouped, child[1], Integer.parseInt(child[0]), filesGen);
        }
    }


    private static void createOutputDirectory(String outDir) throws FileNotFoundException {
        // Create output directory
        if (!new File(outDir).isDirectory()) {
            if (new File(outDir).mkdirs()) {
                throw new FileNotFoundException("Could not create the output directory");
            }
        }
    }
}
