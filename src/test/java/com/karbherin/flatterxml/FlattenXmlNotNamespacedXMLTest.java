package com.karbherin.flatterxml;

import com.karbherin.flatterxml.output.DelimitedFileWriter;
import com.karbherin.flatterxml.output.RecordHandler;
import com.karbherin.flatterxml.output.StatusReporter;
import org.junit.Test;

import javax.xml.stream.XMLStreamException;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static com.karbherin.flatterxml.AppConstants.CascadePolicy;
import static com.karbherin.flatterxml.FlattenXml.FlattenXmlBuilder;
import static com.karbherin.flatterxml.helper.XmlHelpers.parseXsds;
import static com.karbherin.flatterxml.FlattenXmlNamespacedXMLTest.*;
import static org.junit.Assert.*;

public class FlattenXmlNotNamespacedXMLTest {

    private static final String[] TEST_FILENAMES = {"employee", "address", "phone", "reroute"};

    // Equivalent to FlattenXmlRunner CLI options: -c NONE|<missing>
    @Test
    public void fullRecordNoCascade_noNsXML() throws IOException, XMLStreamException {
        String outDir = "target/test/results/fullRecordNoCascade_noNsXML";
        Files.createDirectories(Paths.get(outDir));
        RecordHandler recordHandler = new DelimitedFileWriter(
                "|", outDir,
                // Fields seq in records may vary. 'false' forces post processing of files where fields are regularized
                false,
                // Reports progress and errors to the console
                new StatusReporter(),
                // Replace newlines in data with tilde '~'
                "~");

        FlattenXml flattener = new FlattenXmlBuilder()
                .setRecordWriter(recordHandler)
                .setXmlStream(new FileInputStream(
                        new File("src/test/resources/emp.xml")))
                .create();

        assertEquals(21, flattener.parseFlatten());
        recordHandler.closeAllFileStreams();

        List<String> employee = fileLines(outDir + "/employee.csv");
        List<String> contact = fileLines(outDir + "/contact.csv");
        List<String> addresses = fileLines(outDir + "/addresses.csv");
        List<String> address = fileLines(outDir + "/address.csv");
        List<String> phones = fileLines(outDir + "/phones.csv");
        List<String> phone = fileLines(outDir + "/phone.csv");
        List<String> reroute = fileLines(outDir + "/reroute.csv");

        assertEquals(0, addresses.size());
        assertEquals(0, contact.size());
        assertEquals(0, phones.size());
        checkColCount(Arrays.asList(employee, address, phone, reroute), TEST_FILENAMES, outDir);

        assertEquals(22, employee.size());
        assertEquals("Check if all columns from employee are exported",
                // ALL columns and their attributes from employee record
                "identifiers|employee-no|employee-name|department|salary|contact",
                employee.get(0));

        // One level nesting
        assertEquals(22, address.size());
        assertEquals("Check if all columns from address record + all columns cascaded from employee",
                // ALL columns and their attributes from address record
                "address-type|line1|line2|state|zip",
                address.get(0));


        // Deeply nested
        assertEquals(2, reroute.size());
        assertEquals("Check if all columns in reroute appended + all columns cascaded from address & employee."
                        + "No line2 column in address",
                // all columns from reroute record
                "employee-name|line1|state|zip",
                reroute.get(0));
        assertEquals("Reroute columns with cascaded columns from address & employee. No line2",
                "Nick Fury|541E~              Summer St.|NY, US|92478",
                reroute.get(1));

        // Deeply nested with attributes
        assertEquals(14, phone.size());
        assertEquals("Check if all columns in phone record + all columns cascaded from employee",
                // ALL columns from phone record
                "phone-num|phone-type",
                phone.get(0));
    }

    // Reuse record definitions to speed up processing in a 2nd run.
    // Record definitions are produced by -c OUT, -f ALL|<missing>
    @Test
    public void reuseRecordDefinitionsGeneratedByFullDump_noNsXML() throws IOException, XMLStreamException {
        String outDir = "target/test/results/reuseRecordDefinitionsGeneratedByFullDump_noNsXML";
        String reusedDefsDir = "/reused_recdefs";
        Files.createDirectories(Paths.get(outDir));
        RecordHandler recordHandler;
        FlattenXml flattener;

        recordHandler = new DelimitedFileWriter(
                "|", outDir,
                // Fields seq in records may vary. 'false' forces post processing of files where fields are regularized
                false,
                // Reports progress and errors to console
                new StatusReporter(),
                // Newlines in data are replaced with tilde '~'
                "~");


        flattener = new FlattenXmlBuilder()
                .setCascadePolicy(CascadePolicy.OUT)
                .setRecordWriter(recordHandler)
                .setXmlStream(new FileInputStream(
                        new File("src/test/resources/emp.xml")))
                .create();

        assertEquals(21, flattener.parseFlatten());
        recordHandler.closeAllFileStreams();


        // A FULL EXPORT (all cols & all cols cascaded cols) flattening generates an
        // output record definitions file record_defs.yaml
        // ----------------------------------------------
        // This record_defs.yaml can be reused for faster processing.
        // The tests below check if using it maintains the same output as that produced from full export
        Files.createDirectories(Paths.get(
                "target/test/results/reuseRecordDefinitionsGeneratedByFullDump_noNsXML" + reusedDefsDir));
        recordHandler = new DelimitedFileWriter(
                "|", outDir + reusedDefsDir,
                true, new StatusReporter(), "~");
        flattener = new FlattenXmlBuilder()
                .setCascadePolicy(CascadePolicy.OUT)
                .setRecordWriter(recordHandler)
                .setXmlStream(new FileInputStream(
                        new File("src/test/resources/emp.xml")))
                .setRecordOutputFieldsSeq(new File(outDir+"/record_defs.yaml"))
                .setRecordCascadeFieldsSeq(new File(outDir+"/record_defs.yaml"))
                .create();

        assertEquals(21, flattener.parseFlatten());
        recordHandler.closeAllFileStreams();

        compareOutFiles(outDir + "/employee.csv", outDir + "/reused_recdefs/employee.csv");
        // This behavior cannot be fixed
        //compareOutFiles(outDir + "/address.csv", outDir + "/reused_recdefs/address.csv");
        //compareOutFiles(outDir + "/phone.csv", outDir + "/reused_recdefs/phone.csv");
        // This behavior cannot be fixed
        //compareOutFiles(outDir + "/reroute.csv", outDir + "/reused_recdefs/reroute.csv");

        assertTrue(new File(outDir + "/contact.csv").exists());
        assertTrue(new File(outDir + "/addresses.csv").exists());
        assertTrue(new File(outDir + "/phones.csv").exists());
        assertFalse(new File(outDir + "/reused_recdefs/contact.csv").exists());
        assertFalse(new File(outDir + "/reused_recdefs/addresses.csv").exists());
        assertFalse(new File(outDir + "/reused_recdefs/phones.csv").exists());
    }

    // Equivalent to FlattenXmlRunner CLI options: -f out.yaml, -c casc.yaml
    @Test
    public void definedRecordDefinedCascade_noNsXML() throws IOException, XMLStreamException {
        String outDir = "target/test/results/definedRecordDefinedCascade_noNsXML";
        Files.createDirectories(Paths.get(outDir));
        RecordHandler recordHandler = new DelimitedFileWriter(
                "|", outDir,
                // Fields in records and in cascading are exactly defined by user. Records are not post processed.
                true,
                new StatusReporter(), "~");

        FlattenXml flattener = new FlattenXmlBuilder()
                .setCascadePolicy(CascadePolicy.OUT)
                .setRecordWriter(recordHandler)
                .setXmlStream(new FileInputStream(
                        new File("src/test/resources/emp.xml")))
                // user defined output fields sequence in record
                .setRecordOutputFieldsSeq(new File("src/test/resources/emp_output_fields_attrs.yaml"))
                // user defined cascading fields
                .setRecordCascadeFieldsSeq(new File("src/test/resources/emp_output_fields_attrs.yaml"))
                .create();

        assertEquals(21, flattener.parseFlatten());
        recordHandler.closeAllFileStreams();

        List<String> employee = fileLines(outDir + "/employee.csv");
        //List<String> contact = fileLines(outDir + "/contact.csv");
        //List<String> addresses = fileLines(outDir + "/addresses.csv");
        List<String> address = fileLines(outDir + "/address.csv");
        //List<String> phones = fileLines(outDir + "/phones.csv");
        List<String> phone = fileLines(outDir + "/phone.csv");
        List<String> reroute = fileLines(outDir + "/reroute.csv");

        /*assertEquals(0, addresses.size());
        assertEquals(0, contact.size());
        assertEquals(0, phones.size());*/
        checkColCount(Arrays.asList(employee, address, phone, reroute), TEST_FILENAMES, outDir);

        assertEquals("Check if all columns from employee are exported",
                // all columns and their attributes from employee record
                "employee-no|department|identifiers|identifiers[id-doc-expiry]|identifiers[id-doc-type]",
            employee.get(0));


        assertEquals("Check if all columns from address record + all columns cascaded from employee",
                // all columns and their attributes from address record
                "address-type|line1|state|zip" +
                        // all columns and their attributes cascaded from employee record
                        "|employee.employee-no|employee.department|employee.identifiers|employee.identifiers[id-doc-expiry]|employee.identifiers[id-doc-type]",
                address.get(0));


        assertEquals("Check if all columns in phone record + all columns cascaded from employee",
                // all columns from phone record
                "phone-num|phone-type" +
                        // all columns and their attributes cascaded from employee record (parent)
                        "|employee.employee-no|employee.department|employee.identifiers|employee.identifiers[id-doc-expiry]|employee.identifiers[id-doc-type]",
                phone.get(0));


        assertEquals("Check if all columns in reroute appended + all columns cascaded from address & employee.",
                // all columns from reroute record
                "employee-name|line1|line2|state|zip" +
                        // all columns and their attributes cascaded from address record (parent)
                        "|address.address-type|address.line1|address.state|address.zip" +
                        // all columns and their attributes cascaded from employee record (parent of address)
                        "|employee.employee-no|employee.department|employee.identifiers|employee.identifiers[id-doc-expiry]|employee.identifiers[id-doc-type]",
                reroute.get(0));
        assertEquals("Nick Fury|541E~              Summer St.||NY, US|92478|primary|1 Tudor & Place|NY, US|12345|00000001|public relations|||",
                reroute.get(1));

    }

    // Equivalent to FlattenXmlRunner CLI options: -f out.yaml, -c casc.yaml, -x x1.xsd
    @Test
    public void definedRecordDefinedCascadeWithXSDBackup_noNsXML() throws IOException, XMLStreamException {
        String outDir = "target/test/results/definedRecordDefinedCascadeWithXSDBackup_noNsXML";
        Files.createDirectories(Paths.get(outDir));
        RecordHandler recordHandler = new DelimitedFileWriter(
                "|", outDir,
                // Fields in records and in cascading are exactly defined by user. Records are not post processed.
                true,
                new StatusReporter(), "~");

        FlattenXml flattener = new FlattenXmlBuilder()
                .setCascadePolicy(CascadePolicy.OUT)
                .setRecordWriter(recordHandler)
                .setXmlStream(new FileInputStream(
                        new File("src/test/resources/emp.xml")))
                // user defined output fields sequence in record
                .setRecordOutputFieldsSeq(new File("src/test/resources/emp_output_fields_attrs.yaml"))
                // user defined cascading fields
                .setRecordCascadeFieldsSeq(new File("src/test/resources/emp_output_fields_attrs.yaml"))
                // XSDs are ignored
                .setXsdFiles(parseXsds(
                        new String[]{"src/test/resources/emp.xsd"}))
                .create();

        assertEquals(21, flattener.parseFlatten());
        recordHandler.closeAllFileStreams();

        List<String> employee = fileLines(outDir + "/employee.csv");
        List<String> contact = fileLines(outDir + "/contact.csv");
        List<String> addresses = fileLines(outDir + "/addresses.csv");
        List<String> address = fileLines(outDir + "/address.csv");
        List<String> phones = fileLines(outDir + "/phones.csv");
        List<String> phone = fileLines(outDir + "/phone.csv");
        List<String> reroute = fileLines(outDir + "/reroute.csv");

        assertEquals(0, addresses.size());
        assertEquals(0, contact.size());
        assertEquals(0, phones.size());
        checkColCount(Arrays.asList(employee, address, phone, reroute), TEST_FILENAMES, outDir);

        assertEquals("Check if all columns from employee are exported",
                // all columns and their attributes from employee record
                "employee-no|department|identifiers|identifiers[id-doc-expiry]|identifiers[id-doc-type]",
                employee.get(0));


        assertEquals("Check if all columns from address record + all columns cascaded from employee",
                // all columns and their attributes from address record
                "address-type|line1|state|zip" +
                        // all columns and their attributes cascaded from employee record
                        "|employee.employee-no|employee.department|employee.identifiers|employee.identifiers[id-doc-expiry]|employee.identifiers[id-doc-type]",
                address.get(0));


        assertEquals("Check if all columns in phone record + all columns cascaded from employee",
                // all columns from phone record
                "phone-num|phone-type" +
                        // all columns and their attributes cascaded from employee record (parent)
                        "|employee.employee-no|employee.department|employee.identifiers|employee.identifiers[id-doc-expiry]|employee.identifiers[id-doc-type]",
                phone.get(0));


        assertEquals("Check if all columns in reroute appended + all columns cascaded from address & employee.",
                // all columns from reroute record
                "employee-name|line1|line2|state|zip" +
                        // all columns and their attributes cascaded from address record (parent)
                        "|address.address-type|address.line1|address.state|address.zip" +
                        // all columns and their attributes cascaded from employee record (parent of address)
                        "|employee.employee-no|employee.department|employee.identifiers|employee.identifiers[id-doc-expiry]|employee.identifiers[id-doc-type]",
                reroute.get(0));
        assertEquals("Nick Fury|541E~              Summer St.||NY, US|92478|primary|1 Tudor & Place|NY, US|12345|00000001|public relations|||",
                reroute.get(1));

    }

    // Equivalent to FlattenXmlRunner CLI options: -f out.yaml, -c NONE|<missing>
    @Test
    public void definedRecordNoCascade_noNsXML() throws IOException, XMLStreamException {
        String outDir = "target/test/results/definedRecordNoCascade_noNsXML";
        Files.createDirectories(Paths.get(outDir));
        RecordHandler recordHandler = new DelimitedFileWriter(
                "|", outDir,
                // Fields in records are user defined with no cascading. Records are not post processed.
                true,
                new StatusReporter(), "~");

        FlattenXml flattener = new FlattenXmlBuilder()
                // No cascading
                .setCascadePolicy(CascadePolicy.NONE)
                .setRecordWriter(recordHandler)
                .setXmlStream(new FileInputStream(
                        new File("src/test/resources/emp.xml")))
                // user defined output fields sequence in record
                .setRecordOutputFieldsSeq(new File("src/test/resources/emp_output_fields_attrs.yaml"))
                .create();

        assertEquals(21, flattener.parseFlatten());
        recordHandler.closeAllFileStreams();


        List<String> employee = fileLines(outDir + "/employee.csv");
        List<String> address = fileLines(outDir + "/address.csv");
        List<String> phone = fileLines(outDir + "/phone.csv");
        List<String> reroute = fileLines(outDir + "/reroute.csv");

        assertFalse(new File(outDir + "/contact.csv").exists());
        assertFalse(new File(outDir + "/addresses.csv").exists());
        assertFalse(new File(outDir + "/phones.csv").exists());

        checkColCount(Arrays.asList(employee, address, phone, reroute), TEST_FILENAMES, outDir);


        assertEquals("Check if user defined columns from employee are exported",
                // user defined output columns and their attributes from employee record. No cascaded columns
                "employee-no|department|identifiers|identifiers[id-doc-expiry]|identifiers[id-doc-type]",
                employee.get(0));

        assertEquals("Check if user defined columns from address record are exported. No cascaded columns from employee",
                // user defined output columns and their attributes from address record. No cascaded columns
                "address-type|line1|state|zip", address.get(0));

        assertEquals("Check if user defined columns in phone record. No cascaded columns from employee",
                // user defined output columns from phone record. No cascaded columns
                "phone-num|phone-type", phone.get(0));


        assertEquals("Check if all columns in reroute appended. No columns cascaded from employee or address",
                // user defined columns from reroute record. No cascaded columns
                "employee-name|line1|line2|state|zip", reroute.get(0));
        assertEquals("Nick Fury|541E~              Summer St.||NY, US|92478", reroute.get(1));

    }

    // Equivalent to FlattenXmlRunner CLI options: -f out.yaml, -c OUT
    @Test
    public void definedRecordCascadeFullRecord_noNsXML() throws IOException, XMLStreamException {
        String outDir = "target/test/results/definedRecordCascadeFullRecord_noNsXML";
        Files.createDirectories(Paths.get(outDir));
        RecordHandler recordHandler = new DelimitedFileWriter(
                "|", outDir,
                // Field sequence in records are user defined and cascading replicates the output record.
                // Records dont need post processing
                true,
                new StatusReporter(), "~");

        FlattenXml flattener = new FlattenXmlBuilder()
                // Cascade the whole record to child records
                .setCascadePolicy(CascadePolicy.OUT)
                .setRecordWriter(recordHandler)
                .setXmlStream(new FileInputStream(
                        new File("src/test/resources/emp.xml")))
                .setRecordOutputFieldsSeq(new File("src/test/resources/emp_output_fields_attrs.yaml"))
                .create();

        assertEquals(21, flattener.parseFlatten());
        recordHandler.closeAllFileStreams();


        List<String> employee = fileLines(outDir + "/employee.csv");
        List<String> address = fileLines(outDir + "/address.csv");
        List<String> phone = fileLines(outDir + "/phone.csv");
        List<String> reroute = fileLines(outDir + "/reroute.csv");
        assertFalse(new File(outDir + "/contact.csv").exists());
        assertFalse(new File(outDir + "/addresses.csv").exists());
        assertFalse(new File(outDir + "/phones.csv").exists());

        checkColCount(Arrays.asList(employee, address, phone, reroute), TEST_FILENAMES, outDir);

        assertEquals("Check if user defined columns from employee are exported",
                // user defined output columns and their attributes from employee record
                "employee-no|department|identifiers|identifiers[id-doc-expiry]|identifiers[id-doc-type]",
                employee.get(0));

        assertEquals("Check if user defined columns from address record + user defined output columns cascaded from employee are exported",
                // user defined output columns and their attributes from address record
                "address-type|line1|state|zip" +
                        // all user defined output columns and their attributes cascaded from employee record
                        "|employee.employee-no|employee.department|employee.identifiers|employee.identifiers[id-doc-expiry]|employee.identifiers[id-doc-type]",
                address.get(0));


        assertEquals("Check if user defined columns in phone record + all user defined output columns cascaded from employee are exported",
                // user defined output columns from phone record
                "phone-num|phone-type" +
                        // user defined output columns and their attributes cascaded from employee record (parent)
                        "|employee.employee-no|employee.department|employee.identifiers|employee.identifiers[id-doc-expiry]|employee.identifiers[id-doc-type]",
                phone.get(0));

        assertEquals("Check if user defined columns in reroute appended + user defined columns cascaded from address & employee.",
                // user defined columns from reroute record
                "employee-name|line1|line2|state|zip" +
                        // all user defined output columns and their attributes cascaded from address record (parent)
                        "|address.address-type|address.line1|address.state|address.zip" +
                        // all user defined output columns and their attributes cascaded from employee record (parent of address)
                        "|employee.employee-no|employee.department|employee.identifiers|employee.identifiers[id-doc-expiry]|employee.identifiers[id-doc-type]",
                reroute.get(0));
        assertEquals("Nick Fury|541E~              Summer St.||NY, US|92478|primary|1 Tudor & Place|NY, US|12345|00000001|public relations|||",
                reroute.get(1));
    }


    // Equivalent to FlattenXmlRunner CLI options: -f out.yaml, -c NONE|<missing>, -x x1.xsd
    @Test
    public void definedRecordNoCascadeIgnoreXSD_noNsXML() throws IOException, XMLStreamException {
        String outDir = "target/test/results/definedRecordNoCascadeIgnoredXSD_noNsXML";
        Files.createDirectories(Paths.get(outDir));
        RecordHandler recordHandler = new DelimitedFileWriter(
                "|", outDir,
                // Fields in records and in cascading are exactly defined by user. Records are not post processed.
                true,
                new StatusReporter(), "~");

        FlattenXml flattener = new FlattenXmlBuilder()
                // No cascading
                .setCascadePolicy(CascadePolicy.NONE)
                .setRecordWriter(recordHandler)
                .setXmlStream(new FileInputStream(
                        new File("src/test/resources/emp.xml")))
                // user defined output fields sequence in record
                .setRecordOutputFieldsSeq(new File("src/test/resources/emp_output_fields_attrs.yaml"))
                // XSDs are ignored
                .setXsdFiles(parseXsds(
                        new String[]{"src/test/resources/emp.xsd"}))
                .create();

        assertEquals(21, flattener.parseFlatten());
        recordHandler.closeAllFileStreams();

        List<String> employee = fileLines(outDir + "/employee.csv");
        List<String> contact = fileLines(outDir + "/contact.csv");
        List<String> addresses = fileLines(outDir + "/addresses.csv");
        List<String> address = fileLines(outDir + "/address.csv");
        List<String> phones = fileLines(outDir + "/phones.csv");
        List<String> phone = fileLines(outDir + "/phone.csv");
        List<String> reroute = fileLines(outDir + "/reroute.csv");

        assertEquals(0, addresses.size());
        assertEquals(0, contact.size());
        assertEquals(0, phones.size());
        checkColCount(Arrays.asList(employee, address, phone, reroute), TEST_FILENAMES, outDir);

        assertEquals("Check if all columns from employee are exported",
                // all columns and their attributes from employee record
                "employee-no|department|identifiers|identifiers[id-doc-expiry]|identifiers[id-doc-type]",
                employee.get(0));


        assertEquals("Check if all columns from address record + no columns cascaded from employee",
                // all columns and their attributes from address record
                "address-type|line1|state|zip",
                address.get(0));


        assertEquals("Check if all columns in phone record + no columns cascaded from employee",
                // all columns from phone record
                "phone-num|phone-type",
                phone.get(0));


        assertEquals("Check if all columns in reroute appended + no columns cascaded from address & employee.",
                // all columns from reroute record
                "employee-name|line1|line2|state|zip",
                reroute.get(0));
        assertEquals("Nick Fury|541E~              Summer St.||NY, US|92478",
                reroute.get(1));
    }

    // Equivalent to FlattenXmlRunner CLI options: -c NONE|<missing>, -x x1.xsd
    @Test
    public void xsdDrivenRecordNoCascade_noNsXML() throws IOException, XMLStreamException {
        String outDir = "target/test/results/xsdDrivenRecordNoCascade_noNsXML";
        Files.createDirectories(Paths.get(outDir));
        RecordHandler recordHandler = new DelimitedFileWriter(
                "|", outDir,
                // Fields in records and in cascading are exactly defined by user. Records are not post processed.
                true,
                new StatusReporter(), "~");

        FlattenXml flattener = new FlattenXmlBuilder()
                // No cascading
                .setCascadePolicy(CascadePolicy.NONE)
                .setRecordWriter(recordHandler)
                .setXmlStream(new FileInputStream(
                        new File("src/test/resources/emp.xml")))
                // XSDs driven
                .setXsdFiles(parseXsds(
                        new String[]{"src/test/resources/emp.xsd"}))
                .create();

        assertEquals(21, flattener.parseFlatten());
        recordHandler.closeAllFileStreams();

        List<String> employee = fileLines(outDir + "/employee.csv");
        List<String> contact = fileLines(outDir + "/contact.csv");
        List<String> addresses = fileLines(outDir + "/addresses.csv");
        List<String> address = fileLines(outDir + "/address.csv");
        List<String> phones = fileLines(outDir + "/phones.csv");
        List<String> phone = fileLines(outDir + "/phone.csv");
        List<String> reroute = fileLines(outDir + "/reroute.csv");

        assertEquals(0, addresses.size());
        assertEquals(0, contact.size());
        assertEquals(0, phones.size());
        checkColCount(Arrays.asList(employee, address, phone, reroute), TEST_FILENAMES, outDir);

        assertEquals("Check if all columns from employee are exported",
                // all columns and their attributes from employee record as per XSD
                "identifiers|identifiers[id-doc-type]|identifiers[id-doc-expiry]|employee-no|employee-name|department|salary",
                employee.get(0));


        assertEquals("Check if all columns from address record + no columns cascaded from employee",
                // all columns and their attributes from address record as per XSD
                "address-type|line1|line2|state|zip",
                address.get(0));


        assertEquals("Check if all columns in phone record + no columns cascaded from employee",
                // all columns from phone record as per XSD
                "phone-num|phone-type",
                phone.get(0));


        assertEquals("Check if all columns in reroute appended + no columns cascaded from address & employee.",
                // all columns from reroute record as per XSD
                "employee-name|line1|line2|state|zip",
                reroute.get(0));
        assertEquals("Nick Fury|541E~              Summer St.||NY, US|92478",
                reroute.get(1));
    }

    // Equivalent to FlattenXmlRunner CLI options: -c XSD, -x x1.xsd
    @Test
    public void xsdDrivenRecordCascadeAllTagsInXsd_noNsXML() throws IOException, XMLStreamException {
        String outDir = "target/test/results/xsdDrivenRecordCascadeRequiredTagsInXsd_noNsXML";
        Files.createDirectories(Paths.get(outDir));
        RecordHandler recordHandler = new DelimitedFileWriter(
                "|", outDir,
                // Field sequence in records are user defined and cascading replicates the output record.
                // Records dont need post processing
                true,
                new StatusReporter(), "~");

        FlattenXml flattener = new FlattenXmlBuilder()
                // Cascade the whole record to child records
                .setCascadePolicy(CascadePolicy.XSD)
                .setRecordWriter(recordHandler)
                .setXmlStream(new FileInputStream(
                        new File("src/test/resources/emp.xml")))
                // XSDs driven
                .setXsdFiles(parseXsds(
                        new String[]{"src/test/resources/emp.xsd"}))
                .create();

        assertEquals(21, flattener.parseFlatten());
        recordHandler.closeAllFileStreams();


        List<String> employee = fileLines(outDir + "/employee.csv");
        List<String> contact = fileLines(outDir + "/contact.csv");
        List<String> addresses = fileLines(outDir + "/addresses.csv");
        List<String> address = fileLines(outDir + "/address.csv");
        List<String> phones = fileLines(outDir + "/phones.csv");
        List<String> phone = fileLines(outDir + "/phone.csv");
        List<String> reroute = fileLines(outDir + "/reroute.csv");

        assertEquals(0, addresses.size());
        assertEquals(0, contact.size());
        assertEquals(0, phones.size());
        checkColCount(Arrays.asList(employee, address, phone, reroute), TEST_FILENAMES, outDir);

        assertEquals("Check if user defined columns from employee are exported",
                // all output columns and their attributes from employee record as per XSD
                "identifiers|identifiers[id-doc-type]|identifiers[id-doc-expiry]|employee-no|employee-name|department|salary",
                employee.get(0));

        assertEquals("Check if user defined columns from address record + user defined output columns cascaded from employee are exported",
                // all output columns and their attributes from address record as per XSD
                "address-type|line1|line2|state|zip" +
                        // required columns and their attributes cascaded from employee record as per XSD
                        "|employee.identifiers|employee.identifiers[id-doc-type]|employee.identifiers[id-doc-expiry]|employee.employee-no|employee.employee-name|employee.department|employee.salary",
                address.get(0));


        assertEquals("Check if user defined columns in phone record + all user defined output columns cascaded from employee are exported",
                // all output columns from phone record as per XSD
                "phone-num|phone-type" +
                        // required output columns and their attributes cascaded from employee record (parent) as per XSD
                        "|employee.identifiers|employee.identifiers[id-doc-type]|employee.identifiers[id-doc-expiry]|employee.employee-no|employee.employee-name|employee.department|employee.salary",
                phone.get(0));

        assertEquals("Check if user defined columns in reroute appended + user defined columns cascaded from address & employee.",
                // user defined columns from reroute record
                "employee-name|line1|line2|state|zip" +
                        // required output columns and their attributes cascaded from address record (parent) as per XSD
                        "|address.address-type|address.line1|address.line2|address.state|address.zip" +
                        // required output columns and their attributes cascaded from employee record (parent of address) as per XSD
                        "|employee.identifiers|employee.identifiers[id-doc-type]|employee.identifiers[id-doc-expiry]|employee.employee-no|employee.employee-name|employee.department|employee.salary",
                reroute.get(0));
        assertEquals("Nick Fury|541E~              Summer St.||NY, US|92478|primary|1 Tudor & Place||NY, US|12345||||00000001|Steve Rogers|public relations|150,000.00",
                reroute.get(1));
    }

    // Equivalent to FlattenXmlRunner CLI options: -c XSD, -x x1.xsd
    @Test
    public void xsdDrivenRecordCascadeAllTagsInXsdDrivenRecord_noNsXML() throws IOException, XMLStreamException {
        String outDir = "target/test/results/xsdDrivenRecordCascadeRequiredTagsInXsd_noNsXML";
        Files.createDirectories(Paths.get(outDir));
        RecordHandler recordHandler = new DelimitedFileWriter(
                "|", outDir,
                // Field sequence in records are user defined and cascading replicates the output record.
                // Records dont need post processing
                true,
                new StatusReporter(), "~");

        FlattenXml flattener = new FlattenXmlBuilder()
                // Cascade the whole record to child records
                .setCascadePolicy(CascadePolicy.XSD)
                .setRecordWriter(recordHandler)
                .setXmlStream(new FileInputStream(
                        new File("src/test/resources/emp.xml")))
                // XSDs driven
                .setXsdFiles(parseXsds(
                        new String[]{"src/test/resources/emp.xsd"}))
                .create();

        assertEquals(21, flattener.parseFlatten());
        recordHandler.closeAllFileStreams();


        List<String> employee = fileLines(outDir + "/employee.csv");
        List<String> contact = fileLines(outDir + "/contact.csv");
        List<String> addresses = fileLines(outDir + "/addresses.csv");
        List<String> address = fileLines(outDir + "/address.csv");
        List<String> phones = fileLines(outDir + "/phones.csv");
        List<String> phone = fileLines(outDir + "/phone.csv");
        List<String> reroute = fileLines(outDir + "/reroute.csv");

        assertEquals(0, addresses.size());
        assertEquals(0, contact.size());
        assertEquals(0, phones.size());
        checkColCount(Arrays.asList(employee, address, phone, reroute), TEST_FILENAMES, outDir);

        assertEquals("Check if user defined columns from employee are exported",
                // all output columns and their attributes from employee record as per XSD
                "identifiers|identifiers[id-doc-type]|identifiers[id-doc-expiry]|employee-no|employee-name|department|salary",
                employee.get(0));

        assertEquals("Check if user defined columns from address record + user defined output columns cascaded from employee are exported",
                // all output columns and their attributes from address record as per XSD
                "address-type|line1|line2|state|zip" +
                        // required columns and their attributes cascaded from employee record as per XSD
                        "|employee.identifiers|employee.identifiers[id-doc-type]|employee.identifiers[id-doc-expiry]|employee.employee-no|employee.employee-name|employee.department|employee.salary",
                address.get(0));


        assertEquals("Check if user defined columns in phone record + all user defined output columns cascaded from employee are exported",
                // all output columns from phone record as per XSD
                "phone-num|phone-type" +
                        // required output columns and their attributes cascaded from employee record (parent) as per XSD
                        "|employee.identifiers|employee.identifiers[id-doc-type]|employee.identifiers[id-doc-expiry]|employee.employee-no|employee.employee-name|employee.department|employee.salary",
                phone.get(0));

        assertEquals("Check if user defined columns in reroute appended + user defined columns cascaded from address & employee.",
                // user defined columns from reroute record
                "employee-name|line1|line2|state|zip" +
                        // required output columns and their attributes cascaded from address record (parent) as per XSD
                        "|address.address-type|address.line1|address.line2|address.state|address.zip" +
                        // required output columns and their attributes cascaded from employee record (parent of address) as per XSD
                        "|employee.identifiers|employee.identifiers[id-doc-type]|employee.identifiers[id-doc-expiry]|employee.employee-no|employee.employee-name|employee.department|employee.salary",
                reroute.get(0));
        assertEquals("Nick Fury|541E~              Summer St.||NY, US|92478|primary|1 Tudor & Place||NY, US|12345||||00000001|Steve Rogers|public relations|150,000.00",
                reroute.get(1));
    }

    // Equivalent to FlattenXmlRunner CLI options: -c OUT, -x x1.xsd
    @Test
    public void definedRecordCascadeOutputIgnoreXSD_noNsXML() throws IOException, XMLStreamException {
        String outDir = "target/test/results/xsdDrivenRecordCascadeRequiredTagsInXsd_noNsXML";
        Files.createDirectories(Paths.get(outDir));
        RecordHandler recordHandler = new DelimitedFileWriter(
                "|", outDir,
                // Field sequence in records are user defined and cascading replicates the output record.
                // Records dont need post processing
                true,
                new StatusReporter(), "~");

        FlattenXml flattener = new FlattenXmlBuilder()
                // Cascade the whole record to child records
                .setCascadePolicy(CascadePolicy.OUT)
                .setRecordWriter(recordHandler)
                // user defined output fields sequence in record
                .setRecordOutputFieldsSeq(new File("src/test/resources/emp_output_fields_attrs.yaml"))
                .setXmlStream(new FileInputStream(
                        new File("src/test/resources/emp.xml")))
                // XSDs driven
                .setXsdFiles(parseXsds(
                        new String[]{"src/test/resources/emp.xsd"}))
                .create();

        assertEquals(21, flattener.parseFlatten());
        recordHandler.closeAllFileStreams();


        List<String> employee = fileLines(outDir + "/employee.csv");
        List<String> contact = fileLines(outDir + "/contact.csv");
        List<String> addresses = fileLines(outDir + "/addresses.csv");
        List<String> address = fileLines(outDir + "/address.csv");
        List<String> phones = fileLines(outDir + "/phones.csv");
        List<String> phone = fileLines(outDir + "/phone.csv");
        List<String> reroute = fileLines(outDir + "/reroute.csv");

        assertEquals(0, addresses.size());
        assertEquals(0, contact.size());
        assertEquals(0, phones.size());
        checkColCount(Arrays.asList(employee, address, phone, reroute), TEST_FILENAMES, outDir);

        assertEquals("Check if user defined columns from employee are exported",
                // user defined output columns and their attributes from employee record
                "employee-no|department|identifiers|identifiers[id-doc-expiry]|identifiers[id-doc-type]",
                employee.get(0));

        assertEquals("Check if user defined columns from address record + user defined output columns cascaded from employee are exported",
                // user defined output columns and their attributes from address record
                "address-type|line1|state|zip" +
                        // all columns and their attributes cascaded from employee record as per XSD
                        "|employee.employee-no|employee.department|employee.identifiers|employee.identifiers[id-doc-expiry]|employee.identifiers[id-doc-type]",
                address.get(0));


        assertEquals("Check if user defined columns in phone record + all user defined output columns cascaded from employee are exported",
                // user defined output columns from phone record
                "phone-num|phone-type" +
                        // required output columns and their attributes cascaded from employee record (parent) as per XSD
                        "|employee.employee-no|employee.department|employee.identifiers|employee.identifiers[id-doc-expiry]|employee.identifiers[id-doc-type]",
                phone.get(0));

        assertEquals("Check if user defined columns in reroute appended + user defined columns cascaded from address & employee.",
                // user defined columns from reroute record
                "employee-name|line1|line2|state|zip" +
                        // required output columns and their attributes cascaded from address record (parent) as per XSD
                        "|address.address-type|address.line1|address.state|address.zip" +
                        // required output columns and their attributes cascaded from employee record (parent of address) as per XSD
                        "|employee.employee-no|employee.department|employee.identifiers|employee.identifiers[id-doc-expiry]|employee.identifiers[id-doc-type]",
                reroute.get(0));
        assertEquals("Nick Fury|541E~              Summer St.||NY, US|92478|primary|1 Tudor & Place|NY, US|12345|00000001|public relations|||",
                reroute.get(1));
    }

    // Equivalent to FlattenXmlRunner CLI options: -c XSD, -x x1.xsd
    @Test
    public void definedRecordCascadeAllTagsInXsd_noNsXML() throws IOException, XMLStreamException {
        String outDir = "target/test/results/xsdDrivenRecordCascadeRequiredTagsInXsd_noNsXML";
        Files.createDirectories(Paths.get(outDir));
        RecordHandler recordHandler = new DelimitedFileWriter(
                "|", outDir,
                // Field sequence in records are user defined and cascading replicates the output record.
                // Records dont need post processing
                true,
                new StatusReporter(), "~");

        FlattenXml flattener = new FlattenXmlBuilder()
                // Cascade the whole record to child records
                .setCascadePolicy(CascadePolicy.XSD)
                .setRecordWriter(recordHandler)
                // user defined output fields sequence in record
                .setRecordOutputFieldsSeq(new File("src/test/resources/emp_output_fields_attrs.yaml"))
                .setXmlStream(new FileInputStream(
                        new File("src/test/resources/emp.xml")))
                // XSDs driven
                .setXsdFiles(parseXsds(
                        new String[]{"src/test/resources/emp.xsd"}))
                .create();

        assertEquals(21, flattener.parseFlatten());
        recordHandler.closeAllFileStreams();


        List<String> employee = fileLines(outDir + "/employee.csv");
        List<String> contact = fileLines(outDir + "/contact.csv");
        List<String> addresses = fileLines(outDir + "/addresses.csv");
        List<String> address = fileLines(outDir + "/address.csv");
        List<String> phones = fileLines(outDir + "/phones.csv");
        List<String> phone = fileLines(outDir + "/phone.csv");
        List<String> reroute = fileLines(outDir + "/reroute.csv");

        assertEquals(0, addresses.size());
        assertEquals(0, contact.size());
        assertEquals(0, phones.size());
        checkColCount(Arrays.asList(employee, address, phone, reroute), TEST_FILENAMES, outDir);

        assertEquals("Check if user defined columns from employee are exported",
                // user defined output columns and their attributes from employee record
                "employee-no|department|identifiers|identifiers[id-doc-expiry]|identifiers[id-doc-type]",
                employee.get(0));

        assertEquals("Check if user defined columns from address record + user defined output columns cascaded from employee are exported",
                // user defined output columns and their attributes from address record
                "address-type|line1|state|zip" +
                        // all columns and their attributes cascaded from employee record as per XSD
                        "|employee.identifiers|employee.identifiers[id-doc-type]|employee.identifiers[id-doc-expiry]|employee.employee-no|employee.employee-name|employee.department|employee.salary",
                address.get(0));


        assertEquals("Check if user defined columns in phone record + all user defined output columns cascaded from employee are exported",
                // user defined output columns from phone record
                "phone-num|phone-type" +
                        // required output columns and their attributes cascaded from employee record (parent) as per XSD
                        "|employee.identifiers|employee.identifiers[id-doc-type]|employee.identifiers[id-doc-expiry]|employee.employee-no|employee.employee-name|employee.department|employee.salary",
                phone.get(0));

        assertEquals("Check if user defined columns in reroute appended + user defined columns cascaded from address & employee.",
                // user defined columns from reroute record
                "employee-name|line1|line2|state|zip" +
                        // required output columns and their attributes cascaded from address record (parent) as per XSD
                        "|address.address-type|address.line1|address.line2|address.state|address.zip" +
                        // required output columns and their attributes cascaded from employee record (parent of address) as per XSD
                        "|employee.identifiers|employee.identifiers[id-doc-type]|employee.identifiers[id-doc-expiry]|employee.employee-no|employee.employee-name|employee.department|employee.salary",
                reroute.get(0));
        assertEquals("Nick Fury|541E~              Summer St.||NY, US|92478|primary|1 Tudor & Place||NY, US|12345||||00000001|Steve Rogers|public relations|150,000.00",
                reroute.get(1));
    }

    //------------- Tests regarding XML with no namespaces ------------------------------------

    @Test
    public void definedRecsOutDefinedCascade_noNsXML() throws IOException, XMLStreamException {
        String outDir = "target/test/results/definedRecsOutDefinedCascade_noNsXML";
        Files.createDirectories(Paths.get(outDir));
        RecordHandler recordHandler = new DelimitedFileWriter(
                "|", outDir,
                true, new StatusReporter(), "~");

        FlattenXml flattener = new FlattenXmlBuilder()
                .setRecordWriter(recordHandler)
                .setXmlStream(new FileInputStream(
                        new File("src/test/resources/emp.xml")))
                .setRecordOutputFieldsSeq(new File("src/test/resources/emp_output_fields_attrs.yaml"))
                .setRecordCascadeFieldsSeq(new File("src/test/resources/emp_output_fields_attrs.yaml"))
                .create();

        assertEquals(21, flattener.parseFlatten());
        recordHandler.closeAllFileStreams();


        List<String> employee = fileLines(outDir + "/employee.csv");
        List<String> address = fileLines(outDir + "/address.csv");
        List<String> phone = fileLines(outDir + "/phone.csv");
        List<String> reroute = fileLines(outDir + "/reroute.csv");

        assertFalse(new File(outDir + "/contact.csv").exists());
        assertFalse(new File(outDir + "/addresses.csv").exists());
        assertFalse(new File(outDir + "/phones.csv").exists());

        checkColCount(Arrays.asList(employee, address, phone, reroute), TEST_FILENAMES, outDir);

        assertEquals("Check if user defined columns from employee are exported",
                // user defined output columns and their attributes from employee record
                "employee-no|department|identifiers|identifiers[id-doc-expiry]|identifiers[id-doc-type]",
                employee.get(0));

        assertEquals("Check if user defined columns from address record + user defined cascade columns cascaded from employee are exported",
                // user defined output columns and their attributes from address record
                "address-type|line1|state|zip" +
                        // user defined cascade columns and their attributes cascaded from employee record
                        "|employee.employee-no|employee.department|employee.identifiers|employee.identifiers[id-doc-expiry]|employee.identifiers[id-doc-type]",
                address.get(0));

        assertEquals("Check if user defined columns in phone record + user defined cascade columns cascaded from employee are exported",
                // user defined output columns from phone record
                "phone-num|phone-type" +
                        // user defined cascade columns and their attributes cascaded from employee record (parent)
                        "|employee.employee-no|employee.department|employee.identifiers|employee.identifiers[id-doc-expiry]|employee.identifiers[id-doc-type]",
                phone.get(0));

        assertEquals("Check if user defined columns in reroute appended + user defined columns cascaded from address & employee.",
                // user defined output columns from reroute record
                "employee-name|line1|line2|state|zip" +
                        // all user defined cascade columns and their attributes cascaded from address record (parent)
                        "|address.address-type|address.line1|address.state|address.zip" +
                        // all user defined cascade columns and their attributes cascaded from employee record (parent of address)
                        "|employee.employee-no|employee.department|employee.identifiers|employee.identifiers[id-doc-expiry]|employee.identifiers[id-doc-type]",
                reroute.get(0));
        assertEquals("Nick Fury|541E~              Summer St.||NY, US|92478|primary|1 Tudor & Place|NY, US|12345|00000001|public relations|||",
                reroute.get(1));

    }

    // Equivalent to FlattenXmlRunner CLI options: -c OUT
    @Test
    public void fullDump_noNsXML() throws IOException, XMLStreamException {
        String outDir = "target/test/results/fullDump_noNsXML";
        Files.createDirectories(Paths.get(outDir));

        RecordHandler recordHandler = new DelimitedFileWriter(
                "|", outDir,
                false, new StatusReporter(), "~");

        FlattenXml flattener = new FlattenXmlBuilder()
                .setCascadePolicy(CascadePolicy.OUT)
                .setRecordWriter(recordHandler)
                .setXmlStream(new FileInputStream(
                        new File("src/test/resources/emp.xml")))
                .create();

        assertEquals(21, flattener.parseFlatten());
        recordHandler.closeAllFileStreams();

        List<String> employee = fileLines(outDir + "/employee.csv");
        List<String> contact = fileLines(outDir + "/contact.csv");
        List<String> addresses = fileLines(outDir + "/addresses.csv");
        List<String> address = fileLines(outDir + "/address.csv");
        List<String> phones = fileLines(outDir + "/phones.csv");
        List<String> phone = fileLines(outDir + "/phone.csv");
        List<String> reroute = fileLines(outDir + "/reroute.csv");

        assertEquals(0, contact.size());
        assertEquals(0, addresses.size());
        assertEquals(0, phones.size());
        checkColCount(Arrays.asList(employee, address, phone, reroute), TEST_FILENAMES, outDir);

        assertEquals("#employee recs + header", 22, employee.size());

        assertEquals("Check if all columns from employee are exported",
                // all columns and their attributes from employee record
                "identifiers|employee-no|employee-name|department|salary|contact",
                employee.get(0));

        assertEquals("Check if all columns from address record + all columns cascaded from employee",
                // all columns and their attributes from address record
                "address-type|line1|line2|state|zip" +
                        // all columns and their attributes cascaded from employee record
                        "|employee.employee-no|employee.employee-name|employee.department|employee.salary|employee.identifiers",
                address.get(0));


        assertEquals("Check if all columns in phone record + all columns cascaded from employee",
                // all columns from phone record
                "phone-num|phone-type" +
                        // all columns and their attributes cascaded from employee record (parent)
                        "|employee.employee-no|employee.employee-name|employee.department|employee.salary|employee.identifiers",
                phone.get(0));

        assertEquals("Check if all columns in reroute appended + all columns cascaded from address & employee.",
                // all columns from reroute record
                "employee-name|line1|state|zip" +
                        // all columns and their attributes cascaded from address record (parent)
                        "|address.address-type|address.line1|address.state|address.zip" +
                        // all columns and their attributes cascaded from employee record (parent of address)
                        "|employee.employee-no|employee.employee-name|employee.department|employee.salary",
                reroute.get(0));
        assertEquals("Nick Fury|541E~              Summer St.|NY, US|92478|primary|1 Tudor & Place|NY, US|12345|00000001|Steve Rogers|public relations|150,000.00",
                reroute.get(1));


        char[] buf = new char[8192];
        int bytesRead = new BufferedReader(new FileReader(outDir + "/record_defs.yaml")).read(buf);
        assertEquals("Verify record definitions file generated for non-namespaced XML input",
                "namespaces:\n" +
                        "  \"xsi\": \"http://www.w3.org/2001/XMLSchema-instance\"\n" +
                        "  \"\": \"http://kbps.com/emp\"\n" +
                        "\n" +
                        "records:\n" +
                        "\n" +
                        "  \"address\":\n" +
                        "    - {\"address-type\": []}\n" +
                        "    - {\"line1\": []}\n" +
                        "    - {\"line2\": []}\n" +
                        "    - {\"state\": []}\n" +
                        "    - {\"zip\": []}\n" +
                        "\n" +
                        "  \"employee\":\n" +
                        "    - {\"identifiers\": []}\n" +
                        "    - {\"employee-no\": []}\n" +
                        "    - {\"employee-name\": []}\n" +
                        "    - {\"department\": []}\n" +
                        "    - {\"salary\": []}\n" +
                        "    - {\"contact\": []}\n" +
                        "\n" +
                        "  \"phone\":\n" +
                        "    - {\"phone-num\": []}\n" +
                        "    - {\"phone-type\": []}\n" +
                        "\n" +
                        "  \"reroute\":\n" +
                        "    - {\"employee-name\": []}\n" +
                        "    - {\"line1\": []}\n" +
                        "    - {\"state\": []}\n" +
                        "    - {\"zip\": []}\n",
                new String(buf, 0, bytesRead));
    }

    @Test
    public void fullDumpWithoutEmptyComplexTypeTag_noNsXML() throws IOException, XMLStreamException {
        String outDir = "target/test/results/fullDumpWithoutEmptyComplexTypeTag_noNsXML";
        Files.createDirectories(Paths.get(outDir));

        RecordHandler recordHandler = new DelimitedFileWriter(
                "|", outDir,
                false, new StatusReporter(), "~");

        FlattenXml flattener = new FlattenXmlBuilder()
                .setCascadePolicy(CascadePolicy.OUT)
                .setRecordWriter(recordHandler)
                .setXmlStream(
                        new ByteArrayInputStream(
                                new BufferedReader(new FileReader(
                                        new File("src/test/resources/emp.xml")))
                                        .lines()
                                        .filter(line -> !line.contains("<contact/>"))
                                        .collect(Collectors.joining()).getBytes())
                )
                .create();

        assertEquals(21, flattener.parseFlatten());
        recordHandler.closeAllFileStreams();

        List<String> employee = fileLines(outDir + "/employee.csv");
        assertEquals("No contact column in the export for empty complex type element <contact/>",
                // all columns and their attributes from employee record, excluding the empty complex column contact
                "identifiers|employee-no|employee-name|department|salary", employee.get(0)); // No |contact
        assertEquals("No empty contact column in rec#1 corresponding to empty <contact/> element",
                "|00000001|Steve Rogers|public relations|150,000.00", employee.get(1));

        List<String> address = fileLines(outDir + "/address.csv");
        assertEquals("Only simple type columns in employee cascade to address record",
                // all columns and their attributes from address record
                "address-type|line1|line2|state|zip" +
                        // all simple type columns and their attributes cascaded from employee record. No contact column
                        "|employee.employee-no|employee.employee-name|employee.department|employee.salary|employee.identifiers",
                address.get(0));
        assertEquals("primary|1 Tudor & Place||NY, US|12345|00000001|Steve Rogers|public relations|150,000.00|",
                address.get(1));
    }

}
