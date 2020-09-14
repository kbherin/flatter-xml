package com.karbherin.flatterxml;

import com.karbherin.flatterxml.feeder.XmlByteStreamEmitter;
import com.karbherin.flatterxml.feeder.XmlEventEmitter;
import com.karbherin.flatterxml.consumer.XmlEventWorkerPool;
import com.karbherin.flatterxml.consumer.XmlFileSplitterFactory;
import org.apache.commons.cli.*;

import javax.xml.stream.XMLStreamException;
import java.io.IOException;

public class XmlFileSplitterRunner {

    private static final HelpFormatter HELP_FORMATTER = new HelpFormatter();

    private final Options options = new Options();

    private void setupOptions() {
        options.addOption("o", "output-dir", true,
                "Output directory for generating tabular files. Defaults to current directory");
        options.addOption("n", "num-splits", true,
                "Number of splits");
    }

    private CommandLine parseCliArgs(String[] args) {
        CommandLineParser parser = new DefaultParser();
        try {
            return parser.parse(options, args);
        } catch (ParseException ex) {
            System.err.println(ex.getMessage());
            HELP_FORMATTER.printHelp( "XmlFileSplitterRunner [OPTIONS] XMLFile", options);
            throw new IllegalArgumentException("Could not understand the options provided to the program");
        }
    }

    public static void main(String[] args) throws InterruptedException, XMLStreamException, IOException {

        String outDir = "./";
        String xmlFilePath;

        XmlFileSplitterRunner runner = new XmlFileSplitterRunner();
        runner.setupOptions();
        CommandLine cmd = runner.parseCliArgs(args);

        if (cmd.hasOption("o")) {
            outDir = cmd.getOptionValue("o");
        }

        if (!cmd.hasOption("n")) {
            throw new IllegalArgumentException("Number of splits (-n) must be provided");
        }
        int numSplits = Integer.parseInt(cmd.getOptionValue("n"));
        if (numSplits < 1) {
            throw new IllegalArgumentException("Number of splits (-n) cannot be less than 1");
        }

        xmlFilePath = cmd.getArgs()[0];

        XmlEventWorkerPool workerPool = new XmlEventWorkerPool();
        XmlFileSplitterFactory workerFactory = XmlFileSplitterFactory.newInstance(outDir, xmlFilePath);

        workerPool.execute(numSplits, new XmlByteStreamEmitter(xmlFilePath), workerFactory);

        Long[] recCountsBySplit = workerFactory.getRecordsCount();
        for (int splitNum = 0; splitNum < recCountsBySplit.length; splitNum++) {
            System.out.printf("Number of records in split %d = %d%n", splitNum+1, recCountsBySplit[splitNum]);
        }
    }
}
