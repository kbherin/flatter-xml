package com.karbherin.flatterxml;

import com.karbherin.flatterxml.feeder.XmlByteStreamEmitter;
import com.karbherin.flatterxml.consumer.XmlEventWorkerPool;
import com.karbherin.flatterxml.consumer.XmlFlattenerWorkerFactory;
import com.karbherin.flatterxml.feeder.XmlEventEmitter;
import com.karbherin.flatterxml.feeder.XmlRecordEmitter;
import com.karbherin.flatterxml.helper.XmlHelpers;
import com.karbherin.flatterxml.output.DelimitedFileHandler;
import com.karbherin.flatterxml.output.RecordHandler;
import com.karbherin.flatterxml.output.StatusReporter;
import com.karbherin.flatterxml.xsd.XmlSchema;
import org.apache.commons.cli.*;

import javax.xml.stream.XMLStreamException;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.karbherin.flatterxml.model.RecordFieldsCascade.CascadePolicy;

/**
 * CLI main class for flattening an XML file into tabular files.
 *
 * @author Kartik Bherin
 */
public class FlattenXmlRunner {

    private static final int DEFAULT_BATCH_SIZE = 100;
    private static final String INDENT = "  ";
    private static final HelpFormatter HELP_FORMATTER = new HelpFormatter();
    private static final String ENV_BYTE_STREAM_EMITTER = "BYTE_STREAM_EMITTER";

    private final Options options = new Options();
    private final FlattenXml.FlattenXmlBuilder setup;
    private String delimiter = "|";
    private String outDir = "csvs";
    private int numWorkers = 1;
    private String recordTag = null;
    private CascadePolicy cascadePolicy = CascadePolicy.NONE;
    private File recordCascadeFieldsDefFile = null;
    private File recordOutputFieldsDefFile = null;
    private List<XmlSchema> xsds = Collections.emptyList();
    private long firstNRecs;
    private long batchSize;
    private String xmlFilePath;
    private CommandLine cmd;

    private Collection<String[]> filesGenerated = Collections.emptyList();
    private String rootTagName = XmlHelpers.EMPTY;

    // Track status and progress
    private final StatusReporter statusReporter = new StatusReporter();

    private FlattenXmlRunner() {
        options.addOption("o", "output-dir", true,
                "Output directory for generating tabular files. Defaults to current directory");
        options.addOption("d", "delimiter", true,
                "Delimiter. Defaults to a comma(,)");
        options.addOption("r", "record-tag", true,
                "Primary record tag from where parsing begins. If not provided entire file will be parsed");
        options.addOption("n", "n-records", true,
                "Number of records to process in the XML document");
        options.addOption("p", "progress", true,
                "Report progress after a batch. Defaults to 100");
        options.addOption("f", "output-fields", true,
                "Desired output fields for each record(complex) type in a YAML file");
        options.addOption("c", "cascades", true,
                "Data for tags under a record(complex) type element is cascaded to child records."
                +"\nNONE|ALL|XSD|<record-fields-yaml>.\nDefaults to NONE");
        options.addOption("x", "xsd", true,
                "XSD files. Comma separated list.\nFormat: emp_ns.xsd,phone_ns.xsd,...");
        options.addOption("w", "workers", true,
                "Number of parallel workers. Defaults to 1");

        setup = new FlattenXml.FlattenXmlBuilder();
    }

    private void printHelp() {
        HELP_FORMATTER.printHelp( "FlattenXmlRunner [OPTIONS] XMLFile", options);
    }

    private CommandLine parseCliArgs(String[] args) {
        CommandLineParser parser = new DefaultParser();
        try {
            return parser.parse(options, args);
        } catch (ParseException ex) {
            System.err.println(ex.getMessage());
            printHelp();
            throw new IllegalArgumentException("Could not understand the options provided to the program");
        }
    }

    private void assignOptions() throws IOException, XMLStreamException {

        if (cmd.hasOption("o")) {
            outDir = cmd.getOptionValue("o");
            createOutputDirectory(outDir);
        }

        if (cmd.hasOption("d")) {
            delimiter = cmd.getOptionValue("d");
        }

        if (cmd.hasOption("w")) {
            numWorkers = Integer.parseInt(cmd.getOptionValue("w"));
            if (numWorkers < 1) {
                throw new IllegalArgumentException("Number of workers cannot be less than 1");
            }
        }

        if (cmd.hasOption("r")) {
            recordTag = cmd.getOptionValue("r");
        }

        if (cmd.hasOption("c")) {
            if (cmd.getOptionValue("c").trim().equalsIgnoreCase(
                    CascadePolicy.ALL.toString())) {

                cascadePolicy = CascadePolicy.ALL;
            } else if (cmd.getOptionValue("c").trim().equalsIgnoreCase(
                    CascadePolicy.XSD.toString())) {

                cascadePolicy = CascadePolicy.XSD;
            } else {
                // Is a filename
                recordCascadeFieldsDefFile = new File(cmd.getOptionValue("c"));
            }
        }

        if (cmd.hasOption("f")) {
            recordOutputFieldsDefFile = new File(cmd.getOptionValue("f"));
        }

        if (cmd.hasOption("x")) {
            String[] xmlFiles = cmd.getOptionValue("x").split(",");
            xsds = XmlHelpers.parseXsds(xmlFiles);
        }

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
        } else if (cmd.getArgs().length < 1) {
            printHelp();
            throw new IllegalArgumentException("Could not parse the arguments passed. XMLFile path is required");
        }

        xmlFilePath = cmd.getArgs()[0];
    }

    private void workAlone() throws IOException, XMLStreamException {

        setup.setRecordTag(recordTag)
                .setCascadePolicy(cascadePolicy)
                .setRecordCascadeFieldsSeq(recordCascadeFieldsDefFile)
                .setRecordOutputFieldsSeq(recordOutputFieldsDefFile)
                .setXsdFiles(xsds);

        InputStream xmlStream = new FileInputStream(xmlFilePath);
        setup.setXmlStream(xmlStream);

        // Create XML flattener
        DelimitedFileHandler recordHandler = new DelimitedFileHandler(delimiter, outDir);
        setup.setRecordWriter(recordHandler);
        final FlattenXml flattener = setup.create();

        System.out.printf("Parsing in batches of %d records%n", batchSize);
        if (recordTag != null) {
            System.out.printf("Starting record tag provided is '%s'%n", flattener.getRecordTagGiven());
        }
        boolean firstLoop = true;

        // Single worker
        while (true) {
            long recsInBatch; // Number of records processed in current batch
            if (firstNRecs == 0) {
                // Process all XML records.
                recsInBatch = flattener.parseFlatten(batchSize);
            } else {
                // Process first N records.
                recsInBatch = flattener.parseFlatten(
                        Math.min(batchSize, firstNRecs - statusReporter.getTotalRecordCount()));
            }
            statusReporter.incrementRecordCounter(recsInBatch);

            if (firstLoop && recordTag == null) {
                firstLoop = false;
                System.out.printf("Starting record tag not provided.\nIdentified primary record tag '%s'%n",
                        XmlHelpers.toPrefixedTag(flattener.getRecordTag()));
            }

            statusReporter.showProgress();
            // If previous batch processed 0 records then the processing is complete.
            if (recsInBatch == 0 || recsInBatch < batchSize) {
                break;
            }
        }

        recordHandler.closeAllFileStreams();
        filesGenerated = recordHandler.getFilesWritten();
        rootTagName =  flattener.getRootElement().getName().getLocalPart();
        displayFilesGenerated(filesGenerated, rootTagName);
    }

    private void workSwarm() throws InterruptedException, XMLStreamException, IOException {

        InputStream xmlStream = new FileInputStream(xmlFilePath);
        setup.setXmlStream(xmlStream);

        RecordHandler recordHandler = new DelimitedFileHandler(delimiter, outDir);

        // Initiate concurrent workers
        XmlRecordEmitter emitter;
        if (System.getenv(ENV_BYTE_STREAM_EMITTER) != null) {
            System.out.println("Employing XML byte stream for dispatching to workers");
            int numProducers = 1;
            if (numWorkers / 4 > 1) {
                numProducers = numWorkers / 4;
                System.out.printf("Using %d parallel stream emitters%n", numProducers);
            }

            emitter = new XmlByteStreamEmitter.XmlByteStreamEmitterBuilder()
                    .setXmlFile(xmlFilePath)
                    .setNumProducers(numProducers)
                    .create();
        } else {
            System.out.println("Employing XML event stream for dispatching to workers");
            emitter = new XmlEventEmitter.XmlEventEmitterBuilder()
                    .setXmlFile(xmlFilePath)
                    .create();
        }
        XmlFlattenerWorkerFactory workerFactory = XmlFlattenerWorkerFactory.newInstance(
                xmlFilePath, outDir, delimiter, recordTag, recordHandler,
                cascadePolicy, xsds, recordCascadeFieldsDefFile, recordOutputFieldsDefFile,
                batchSize, statusReporter);

        XmlEventWorkerPool workerPool = new XmlEventWorkerPool();
        workerPool.execute(numWorkers, emitter, workerFactory);
        statusReporter.showProgress();
        recordHandler.closeAllFileStreams();
        rootTagName = emitter.getRootTag().getLocalPart();

        System.out.println();
        if (recordTag == null) {
            System.out.printf("Starting record tag not provided.\nIdentified primary record tag '%s'%n",
                    XmlHelpers.toPrefixedTag(emitter.getRecordTag()));
        }

        filesGenerated = statusReporter.getFilesGenerated();
        displayFilesGenerated(filesGenerated, rootTagName);
    }

    private  Collection<String[]> run(String[] args) throws InterruptedException, XMLStreamException, IOException {
        cmd = parseCliArgs(args);
        assignOptions();


        if (numWorkers == 1) {
            workAlone();
        } else {
            workSwarm();
        }
        return filesGenerated;
    }

    /**
     * Command line method for running XML flattener.
     * @param args - command line arguments and options
     * @throws XMLStreamException - if XML file could not be parsed
     * @throws IOException - if XML file or output directories cannot be accessed
     */
    public static void main(String[] args)
            throws XMLStreamException, IOException, InterruptedException {

        FlattenXmlRunner runner = new FlattenXmlRunner();
        Collection<String[]> filesWritten = runner.run(args);
        System.out.printf("Total number of files produced in %s: %d%n---------------------------%n",
                runner.outDir, filesWritten.size());
    }

    private static void displayFilesGenerated(Collection<String[]> filesWritten, String rootTagName) {

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


    private static void createOutputDirectory(String outDir) throws IOException {
        // Create output directory path
        Files.createDirectories(Paths.get(outDir));
    }

}
