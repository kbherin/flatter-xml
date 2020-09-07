package com.karbherin.flatterxml;

import com.karbherin.flatterxml.feeder.XmlEventEmitter;
import com.karbherin.flatterxml.feeder.XmlEventWorkerPool;
import com.karbherin.flatterxml.feeder.XmlFlattenerWorkerFactory;
import com.karbherin.flatterxml.output.DelimitedFileHandler;
import com.karbherin.flatterxml.output.StatusReporter;
import com.karbherin.flatterxml.xsd.XmlSchema;
import org.apache.commons.cli.*;

import javax.xml.stream.XMLStreamException;
import java.io.*;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.karbherin.flatterxml.RecordFieldsCascade.CascadePolicy;

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
        OPTIONS.addOption("o", "output-dir", true, "Output directory for generating tabular files. Defaults to current directory");
        OPTIONS.addOption("d", "delimiter", true, "Delimiter. Defaults to a comma(,)");
        OPTIONS.addOption("r", "record-tag", true, "Primary record tag from where parsing begins. If not provided entire file will be parsed");
        OPTIONS.addOption("n", "n-records", true, "Number of records to process in the XML document");
        OPTIONS.addOption("p", "progress", true, "Report progress after a batch. Defaults to 100");
        OPTIONS.addOption("c", "cascades", true, "Data of specified tags on parent element is cascaded to child elements.\nNONE|ALL|XSD. Defaults to NONE.\nFormat: elem1:tag1,tag2;elem2:tag1,tag2;...");
        OPTIONS.addOption("x", "xsd", true, "XSD files. Comma separated list.\nFormat: emp.xsd,contact.xsd,...");
        OPTIONS.addOption("w", "workers", true, "Number of parallel workers. Defaults to 1");
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
            throws XMLStreamException, IOException, InterruptedException {


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

        int numWorkers = 1;
        if (cmd.hasOption("w")) {
            numWorkers = Integer.valueOf(cmd.getOptionValue("w"));
            if (numWorkers < 1) {
                throw new IllegalArgumentException("Number of workers cannot be less than 1");
            }
        }

        String recordTag = null;
        if (cmd.hasOption("r")) {
            recordTag = cmd.getOptionValue("r");
            if (recordTag.trim().length() > 0)
                setup.setRecordTag(recordTag);
        }

        CascadePolicy cascadePolicy = CascadePolicy.NONE;
        Map<String, String[]>  recordCascadesTemplates = Collections.emptyMap();
        if (cmd.hasOption("c")) {
            if (cmd.getOptionValue("c").trim().equalsIgnoreCase(
                    CascadePolicy.ALL.toString())) {

                setup.setCascadePolicy(CascadePolicy.ALL);
            } else if (cmd.getOptionValue("c").trim().equalsIgnoreCase(
                    CascadePolicy.XSD.toString())) {

                setup.setCascadePolicy(CascadePolicy.XSD);
            } else {

                recordCascadesTemplates = XmlHelpers.parseTagValueCascades(cmd.getOptionValue("c"));
                setup.setRecordCascadesTemplates(recordCascadesTemplates);
            }
        }

        List<XmlSchema> xsds = Collections.emptyList();
        if (cmd.hasOption("x")) {
            String[] xmlFiles = cmd.getOptionValue("x").split(",");
            xsds = XmlHelpers.parseXsds(xmlFiles);
            setup.setXsdFiles(xsds);
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

        String xmlFilePath = cmd.getArgs()[0];
        InputStream xmlStream = new FileInputStream(xmlFilePath);
        setup.setXmlStream(xmlStream);

        // Track status
        StatusReporter statusReporter = new StatusReporter();
        List<String[]> filesWritten = Collections.emptyList();
        String rootTagName = XmlHelpers.EMPTY;

        if (numWorkers == 1) {

            // Create XML flattener
            DelimitedFileHandler recordHandler = new DelimitedFileHandler(delimiter, outDir, XmlHelpers.EMPTY);
            setup.setRecordWriter(recordHandler);
            final FlattenXml flattener = setup.create();

            System.out.println(String.format("Parsing in batches of %d records", batchSize));
            if (recordTag != null) {
                System.out.println(String.format("Starting record tag provided is '%s'", flattener.getRecordTagGiven()));
            }

            // Single worker
            while (true) {
                long recsInBatch = 0; // Number of records processed in current batch
                if (firstNRecs == 0) {
                    // Process all XML records.
                    recsInBatch = flattener.parseFlatten(batchSize);
                } else {
                    // Process first N records.
                    recsInBatch = flattener.parseFlatten(
                            Math.min(batchSize, firstNRecs - statusReporter.getTotalRecordCount()));
                }
                statusReporter.incrementRecordCounter(recsInBatch);

                if (recordTag == null) {
                    System.out.println("Starting record tag not provided.");
                    System.out.println(String.format("Identified primary record tag '%s'",
                            XmlHelpers.toPrefixedTag(flattener.getRecordTag())));
                }

                statusReporter.showProgress();
                // If previous batch processed 0 records then the processing is complete.
                if (recsInBatch == 0 || recsInBatch < batchSize) {
                    break;
                }
            }

            filesWritten = recordHandler.getFilesWritten();
            rootTagName =  flattener.getRootElement().getName().getLocalPart();
            displayFilesGenerated(filesWritten, rootTagName);

        } else {

            // Initiate concurrent workers
            XmlEventEmitter emitter = new XmlEventEmitter(xmlFilePath);
            XmlFlattenerWorkerFactory workerFactory = XmlFlattenerWorkerFactory.newInstance(
                    xmlFilePath, outDir, delimiter,
                    recordTag, xsds, cascadePolicy, recordCascadesTemplates,
                    batchSize, statusReporter
            );
            XmlEventWorkerPool workerPool = new XmlEventWorkerPool();
            workerPool.execute(numWorkers, emitter, workerFactory);
            filesWritten = statusReporter.getFilesGenerated();
            rootTagName = emitter.getRootTag().getLocalPart();

            System.out.println();
            if (recordTag == null) {
                System.out.println("Starting record tag not provided.");
                System.out.println(String.format("Identified primary record tag '%s'",
                        XmlHelpers.toPrefixedTag(emitter.getRecordTag())));
            }

            for (int i = 1; i <= numWorkers; i++) {
                String fileSuffix = "_part" + i;
                displayFilesGenerated(filesWritten, rootTagName + fileSuffix);
            }
        }

        System.out.println("Total number of files produced: " + filesWritten.size());
    }

    private static void displayFilesGenerated(List<String[]> filesWritten, String rootTagName) {

        // Display the files generated
        StringBuilder filesTreeStr = new StringBuilder();
        Map<String, List<String[]>> groupedByParent = filesWritten.stream()
                .collect(Collectors.groupingBy(r -> r[2], Collectors.toList()));


        for (String[] child: groupedByParent.get(rootTagName)) {
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
        filesGen.append(file).append(System.lineSeparator());

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
