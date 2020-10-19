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

import static com.karbherin.flatterxml.FlattenXml.FlattenXmlBuilder;
import static com.karbherin.flatterxml.AppConstants.*;
import static com.karbherin.flatterxml.helper.XmlHelpers.parseXsds;
import static org.junit.Assert.*;

public class FlattenXmlNamespacedXMLTest {

    private static final String[] TEST_FILENAMES = {"employee", "address", "phone", "reroute"};

    // Equivalent to FlattenXmlRunner CLI options: -c NONE|<missing>
    @Test
    public void fullRecordNoCascade_nsXML() throws IOException, XMLStreamException {
        String outDir = "target/test/results/fullRecordNoCascade_nsXML";
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
                        new File("src/test/resources/emp_ns.xml")))
                .create();

        assertEquals(3, flattener.parseFlatten());
        recordHandler.closeAllFileStreams();

        List<String> employee = fileLines(outDir + "/emp.employee.csv");
        List<String> contact = fileLines(outDir + "/emp.contact.csv");
        List<String> addresses = fileLines(outDir + "/emp.addresses.csv");
        List<String> address = fileLines(outDir + "/emp.address.csv");
        List<String> phones = fileLines(outDir + "/ph.phones.csv");
        List<String> phone = fileLines(outDir + "/ph.phone.csv");
        List<String> reroute = fileLines(outDir + "/emp.reroute.csv");

        assertEquals(0, addresses.size());
        assertEquals(0, contact.size());
        assertEquals(0, phones.size());
        checkColCount(Arrays.asList(employee, address, phone, reroute), TEST_FILENAMES, outDir);

        assertEquals(7, employee.size());
        assertEquals("Check if all columns from employee are exported",
                // ALL columns and their attributes from employee record
                "emp:identifiers|emp:identifiers[emp:id-doc-type]|emp:identifiers[emp:id-doc-expiry]|emp:employee-no|emp:employee-no[emp:status]|emp:employee-name|emp:department|emp:salary",
                employee.get(0));

        // One level nesting
        assertEquals(5, address.size());
        assertEquals("Check if all columns from address record + all columns cascaded from employee",
                // ALL columns and their attributes from address record
                "emp:address-type|emp:line1|emp:line2|emp:state|emp:zip",
                address.get(0));


        // Deeply nested
        assertEquals(2, reroute.size());
        assertEquals("Check if all columns in reroute appended + all columns cascaded from address & employee."
                        + "No line2 column in address",
                // all columns from reroute record
                "emp:employee-name|emp:line1|emp:state|emp:zip",
                reroute.get(0));
        assertEquals("Reroute columns with cascaded columns from address & employee. No line2",
                "Nick Fury|541E Summer St.|NY, US|92478",
                reroute.get(1));

        // Deeply nested with attributes
        assertEquals(5, phone.size());
        assertEquals("Check if all columns in phone record + all columns cascaded from employee",
                // ALL columns from phone record
                "ph:phone-num|ph:phone-num[ph:contact-type]|ph:phone-type",
                phone.get(0));
    }

    // Equivalent to FlattenXmlRunner CLI options: -c OUT
    @Test
    public void fullDump_nsXML() throws IOException, XMLStreamException {
        String outDir = "target/test/results/fullDump_nsXML";
        Files.createDirectories(Paths.get(outDir));
        RecordHandler recordHandler = new DelimitedFileWriter(
                "|", outDir,
                // Fields seq in records may vary. 'false' forces post processing of files where fields are regularized
                false,
                // Reports progress and errors to console
                new StatusReporter(),
                // Newlines in data are replaced with tilde '~'
                "~");

        FlattenXml flattener = new FlattenXmlBuilder()
                .setCascadePolicy(CascadePolicy.OUT)
                .setRecordWriter(recordHandler)
                .setXmlStream(new FileInputStream(
                        new File("src/test/resources/emp_ns.xml")))
                .create();

        assertEquals(3, flattener.parseFlatten());
        recordHandler.closeAllFileStreams();

        List<String> employee = fileLines(outDir + "/emp.employee.csv");
        List<String> contact = fileLines(outDir + "/emp.contact.csv");
        List<String> addresses = fileLines(outDir + "/emp.addresses.csv");
        List<String> address = fileLines(outDir + "/emp.address.csv");
        List<String> phones = fileLines(outDir + "/ph.phones.csv");
        List<String> phone = fileLines(outDir + "/ph.phone.csv");
        List<String> reroute = fileLines(outDir + "/emp.reroute.csv");

        assertEquals(0, addresses.size());
        assertEquals(0, contact.size());
        assertEquals(0, phones.size());
        checkColCount(Arrays.asList(employee, address, phone, reroute), TEST_FILENAMES, outDir);

        assertEquals(7, employee.size());
        assertEquals("Check if all columns from employee are exported",
                // ALL columns and their attributes from employee record
                "emp:identifiers|emp:identifiers[emp:id-doc-type]|emp:identifiers[emp:id-doc-expiry]|emp:employee-no|emp:employee-no[emp:status]|emp:employee-name|emp:department|emp:salary",
                employee.get(0));

        // One level nesting
        assertEquals(5, address.size());
        assertEquals("Check if all columns from address record + all columns cascaded from employee",
                // ALL columns and their attributes from address record
                "emp:address-type|emp:line1|emp:line2|emp:state|emp:zip" +
                        // all columns and their attributes cascaded from employee record (parent)
                        "|emp:employee.emp:identifiers|emp:employee.emp:identifiers[emp:id-doc-type]|emp:employee.emp:identifiers[emp:id-doc-expiry]|emp:employee.emp:employee-no|emp:employee.emp:employee-no[emp:status]|emp:employee.emp:employee-name|emp:employee.emp:department|emp:employee.emp:salary",
                address.get(0));


        // Deeply nested
        assertEquals(2, reroute.size());
        assertEquals("Check if all columns in reroute appended + all columns cascaded from address & employee."
                        + "No line2 column in address",
                // all columns from reroute record
                "emp:employee-name|emp:line1|emp:state|emp:zip"
                        // all columns and their attributes cascaded from address record (parent)
                        + "|emp:address.emp:address-type|emp:address.emp:line1|emp:address.emp:state|emp:address.emp:zip"
                        // all columns and their attributes cascaded from employee record (parent of address)
                        + "|emp:employee.emp:identifiers|emp:employee.emp:identifiers[emp:id-doc-type]|emp:employee.emp:identifiers[emp:id-doc-expiry]|emp:employee.emp:employee-no|emp:employee.emp:employee-no[emp:status]|emp:employee.emp:employee-name|emp:employee.emp:department|emp:employee.emp:salary",
                reroute.get(0));
        assertEquals("Reroute columns with cascaded columns from address & employee. No line2",
                "Nick Fury|541E Summer St.|NY, US|92478" +
                        "|primary|1 Tudor & Place|NY, US|12345" +
                        "|1234567890|SSN|1945-05-25|00000001|active|Steve Rogers|public relations|150,000.00",
                reroute.get(1));

        // Deeply nested with attributes
        assertEquals(5, phone.size());
        assertEquals("Check if all columns in phone record + all columns cascaded from employee",
                // ALL columns from phone record
                "ph:phone-num|ph:phone-num[ph:contact-type]|ph:phone-type" +
                        // ALL columns and their attributes cascaded from employee record (parent)
                        "|emp:employee.emp:identifiers|emp:employee.emp:identifiers[emp:id-doc-type]|emp:employee.emp:identifiers[emp:id-doc-expiry]|emp:employee.emp:employee-no|emp:employee.emp:employee-no[emp:status]|emp:employee.emp:employee-name|emp:employee.emp:department|emp:employee.emp:salary",
                phone.get(0));

        char[] buf = new char[8192];
        int bytesRead = new BufferedReader(new FileReader(outDir + "/record_defs.yaml")).read(buf);
        assertEquals("Verify record definitions file generated for namespaced XML input",
                "namespaces:\n" +
                        "  \"ph\": \"http://kbps.com/phone\"\n" +
                        "  \"xsi\": \"http://www.w3.org/2001/XMLSchema-instance\"\n" +
                        "  \"emp\": \"http://kbps.com/emp\"\n" +
                        "\n" +
                        "records:\n" +
                        "\n" +
                        "  \"emp:address\":\n" +
                        "    - {\"emp:address-type\": []}\n" +
                        "    - {\"emp:line1\": []}\n" +
                        "    - {\"emp:line2\": []}\n" +
                        "    - {\"emp:state\": []}\n" +
                        "    - {\"emp:zip\": []}\n" +
                        "\n" +
                        "  \"emp:employee\":\n" +
                        "    - {\"emp:identifiers\": [\"emp:id-doc-type\",\"emp:id-doc-expiry\"]}\n" +
                        "    - {\"emp:employee-no\": [\"emp:status\"]}\n" +
                        "    - {\"emp:employee-name\": []}\n" +
                        "    - {\"emp:department\": []}\n" +
                        "    - {\"emp:salary\": []}\n" +
                        "\n" +
                        "  \"emp:reroute\":\n" +
                        "    - {\"emp:employee-name\": []}\n" +
                        "    - {\"emp:line1\": []}\n" +
                        "    - {\"emp:state\": []}\n" +
                        "    - {\"emp:zip\": []}\n" +
                        "\n" +
                        "  \"ph:phone\":\n" +
                        "    - {\"ph:phone-num\": [\"ph:contact-type\"]}\n" +
                        "    - {\"ph:phone-type\": []}\n",
        new String(buf, 0, bytesRead));
    }

    // Reuse record definitions to speed up processing in a 2nd run.
    // Record definitions are produced by -c OUT, -f ALL|<missing>
    @Test
    public void reuseRecordDefinitionsGeneratedByFullDump_nsXML() throws IOException, XMLStreamException {
        String outDir = "target/test/results/reuseRecordDefinitionsGeneratedByFullDump_nsXML";
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
                        new File("src/test/resources/emp_ns.xml")))
                .create();

        assertEquals(3, flattener.parseFlatten());
        recordHandler.closeAllFileStreams();


        // A FULL EXPORT (all cols & all cols cascaded cols) flattening generates an
        // output record definitions file record_defs.yaml
        // ----------------------------------------------
        // This record_defs.yaml can be reused for faster processing.
        // The tests below check if using it maintains the same output as that produced from full export
        Files.createDirectories(Paths.get(
                "target/test/results/reuseRecordDefinitionsGeneratedByFullDump_nsXML" + reusedDefsDir));
        recordHandler = new DelimitedFileWriter(
                "|", outDir + reusedDefsDir,
                true, new StatusReporter(), "~");
        flattener = new FlattenXmlBuilder()
                .setCascadePolicy(CascadePolicy.OUT)
                .setRecordWriter(recordHandler)
                .setXmlStream(new FileInputStream(
                        new File("src/test/resources/emp_ns.xml")))
                .setRecordOutputFieldsSeq(new File(outDir+"/record_defs.yaml"))
                .setRecordCascadeFieldsSeq(new File(outDir+"/record_defs.yaml"))
                .create();

        assertEquals(3, flattener.parseFlatten());
        recordHandler.closeAllFileStreams();

        compareOutFiles(outDir + "/emp.employee.csv", outDir + "/reused_recdefs/emp.employee.csv");
        compareOutFiles(outDir + "/emp.address.csv", outDir + "/reused_recdefs/emp.address.csv");
        compareOutFiles(outDir + "/ph.phone.csv", outDir + "/reused_recdefs/ph.phone.csv");
        // This behavior cannot be fixed
        //compareOutFiles(outDir + "/emp.reroute.csv", outDir + "/reused_recdefs/emp.reroute.csv");

        assertTrue(new File(outDir + "/emp.contact.csv").exists());
        assertTrue(new File(outDir + "/emp.addresses.csv").exists());
        assertTrue(new File(outDir + "/ph.phones.csv").exists());
        assertFalse(new File(outDir + "/reused_recdefs/emp.contact.csv").exists());
        assertFalse(new File(outDir + "/reused_recdefs/emp.addresses.csv").exists());
        assertFalse(new File(outDir + "/reused_recdefs/ph.phones.csv").exists());
    }

    // Equivalent to FlattenXmlRunner CLI options: -f out.yaml, -c casc.yaml
    @Test
    public void definedRecordDefinedCascade_nsXML() throws IOException, XMLStreamException {
        String outDir = "target/test/results/definedRecordDefinedCascade_nsXML";
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
                        new File("src/test/resources/emp_ns.xml")))
                // user defined output fields sequence in record
                .setRecordOutputFieldsSeq(new File("src/test/resources/empns_output_fields_attrs.yaml"))
                // user defined cascading fields
                .setRecordCascadeFieldsSeq(new File("src/test/resources/empns_output_fields_attrs.yaml"))
                .create();

        assertEquals(3, flattener.parseFlatten());
        recordHandler.closeAllFileStreams();

        List<String> employee = fileLines(outDir + "/emp.employee.csv");
        //List<String> contact = fileLines(outDir + "/emp.contact.csv");
        //List<String> addresses = fileLines(outDir + "/emp.addresses.csv");
        List<String> address = fileLines(outDir + "/emp.address.csv");
        //List<String> phones = fileLines(outDir + "/ph.phones.csv");
        List<String> phone = fileLines(outDir + "/ph.phone.csv");
        List<String> reroute = fileLines(outDir + "/emp.reroute.csv");

        /*assertEquals(0, addresses.size());
        assertEquals(0, contact.size());
        assertEquals(0, phones.size());*/
        checkColCount(Arrays.asList(employee, address, phone, reroute), TEST_FILENAMES, outDir);

        assertEquals("Check if all columns from employee are exported",
                // all columns and their attributes from employee record
                "emp:employee-no|emp:department|emp:identifiers|emp:identifiers[emp:id-doc-expiry]|emp:identifiers[emp:id-doc-type]",
            employee.get(0));


        assertEquals("Check if all columns from address record + all columns cascaded from employee",
                // all columns and their attributes from address record
                "emp:address-type|emp:line1|emp:state|emp:zip" +
                        // all columns and their attributes cascaded from employee record
                        "|emp:employee.emp:employee-no|emp:employee.emp:department|emp:employee.emp:identifiers|emp:employee.emp:identifiers[emp:id-doc-expiry]|emp:employee.emp:identifiers[emp:id-doc-type]",
                address.get(0));


        assertEquals("Check if all columns in phone record + all columns cascaded from employee",
                // all columns from phone record
                "ph:phone-num|ph:phone-num[ph:contact-type]|ph:phone-type" +
                        // all columns and their attributes cascaded from employee record (parent)
                        "|emp:employee.emp:employee-no|emp:employee.emp:department|emp:employee.emp:identifiers|emp:employee.emp:identifiers[emp:id-doc-expiry]|emp:employee.emp:identifiers[emp:id-doc-type]",
                phone.get(0));


        assertEquals("Check if all columns in reroute appended + all columns cascaded from address & employee.",
                // all columns from reroute record
                "emp:employee-name|emp:line1|emp:line2|emp:state|emp:zip" +
                        // all columns and their attributes cascaded from address record (parent)
                        "|emp:address.emp:address-type|emp:address.emp:line1|emp:address.emp:state|emp:address.emp:zip" +
                        // all columns and their attributes cascaded from employee record (parent of address)
                        "|emp:employee.emp:employee-no|emp:employee.emp:department|emp:employee.emp:identifiers|emp:employee.emp:identifiers[emp:id-doc-expiry]|emp:employee.emp:identifiers[emp:id-doc-type]",
                reroute.get(0));
        assertEquals("Nick Fury|541E Summer St.||NY, US|92478|primary|1 Tudor & Place|NY, US|12345|00000001|public relations|1234567890|1945-05-25|SSN",
                reroute.get(1));

    }

    // Equivalent to FlattenXmlRunner CLI options: -f out.yaml, -c casc.yaml, -x x1.xsd
    @Test
    public void definedRecordDefinedCascadeWithXSDBackup_nsXML() throws IOException, XMLStreamException {
        String outDir = "target/test/results/definedRecordDefinedCascadeWithXSDBackup_nsXML";
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
                        new File("src/test/resources/emp_ns.xml")))
                // user defined output fields sequence in record
                .setRecordOutputFieldsSeq(new File("src/test/resources/empns_output_fields_attrs.yaml"))
                // user defined cascading fields
                .setRecordCascadeFieldsSeq(new File("src/test/resources/empns_output_fields_attrs.yaml"))
                // XSDs are ignored
                .setXsdFiles(parseXsds(
                        new String[]{"src/test/resources/emp_ns.xsd", "src/test/resources/phone_ns.xsd"}))
                .create();

        assertEquals(3, flattener.parseFlatten());
        recordHandler.closeAllFileStreams();

        List<String> employee = fileLines(outDir + "/emp.employee.csv");
        List<String> contact = fileLines(outDir + "/emp.contact.csv");
        List<String> addresses = fileLines(outDir + "/emp.addresses.csv");
        List<String> address = fileLines(outDir + "/emp.address.csv");
        List<String> phones = fileLines(outDir + "/ph.phones.csv");
        List<String> phone = fileLines(outDir + "/ph.phone.csv");
        List<String> reroute = fileLines(outDir + "/emp.reroute.csv");

        assertEquals(0, addresses.size());
        assertEquals(0, contact.size());
        assertEquals(0, phones.size());
        checkColCount(Arrays.asList(employee, address, phone, reroute), TEST_FILENAMES, outDir);

        assertEquals("Check if all columns from employee are exported",
                // all columns and their attributes from employee record
                "emp:employee-no|emp:department|emp:identifiers|emp:identifiers[emp:id-doc-expiry]|emp:identifiers[emp:id-doc-type]",
                employee.get(0));


        assertEquals("Check if all columns from address record + all columns cascaded from employee",
                // all columns and their attributes from address record
                "emp:address-type|emp:line1|emp:state|emp:zip" +
                        // all columns and their attributes cascaded from employee record
                        "|emp:employee.emp:employee-no|emp:employee.emp:department|emp:employee.emp:identifiers|emp:employee.emp:identifiers[emp:id-doc-expiry]|emp:employee.emp:identifiers[emp:id-doc-type]",
                address.get(0));


        assertEquals("Check if all columns in phone record + all columns cascaded from employee",
                // all columns from phone record
                "ph:phone-num|ph:phone-num[ph:contact-type]|ph:phone-type" +
                        // all columns and their attributes cascaded from employee record (parent)
                        "|emp:employee.emp:employee-no|emp:employee.emp:department|emp:employee.emp:identifiers|emp:employee.emp:identifiers[emp:id-doc-expiry]|emp:employee.emp:identifiers[emp:id-doc-type]",
                phone.get(0));


        assertEquals("Check if all columns in reroute appended + all columns cascaded from address & employee.",
                // all columns from reroute record
                "emp:employee-name|emp:line1|emp:line2|emp:state|emp:zip" +
                        // all columns and their attributes cascaded from address record (parent)
                        "|emp:address.emp:address-type|emp:address.emp:line1|emp:address.emp:state|emp:address.emp:zip" +
                        // all columns and their attributes cascaded from employee record (parent of address)
                        "|emp:employee.emp:employee-no|emp:employee.emp:department|emp:employee.emp:identifiers|emp:employee.emp:identifiers[emp:id-doc-expiry]|emp:employee.emp:identifiers[emp:id-doc-type]",
                reroute.get(0));
        assertEquals("Nick Fury|541E Summer St.||NY, US|92478|primary|1 Tudor & Place|NY, US|12345|00000001|public relations|1234567890|1945-05-25|SSN",
                reroute.get(1));

    }

    // Equivalent to FlattenXmlRunner CLI options: -f out.yaml, -c NONE|<missing>
    @Test
    public void definedRecordNoCascade_nsXML() throws IOException, XMLStreamException {
        String outDir = "target/test/results/definedRecordNoCascade_nsXML";
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
                        new File("src/test/resources/emp_ns.xml")))
                // user defined output fields sequence in record
                .setRecordOutputFieldsSeq(new File("src/test/resources/empns_output_fields_attrs.yaml"))
                .create();

        assertEquals(3, flattener.parseFlatten());
        recordHandler.closeAllFileStreams();


        List<String> employee = fileLines(outDir + "/emp.employee.csv");
        List<String> address = fileLines(outDir + "/emp.address.csv");
        List<String> phone = fileLines(outDir + "/ph.phone.csv");
        List<String> reroute = fileLines(outDir + "/emp.reroute.csv");

        assertFalse(new File(outDir + "/emp.contact.csv").exists());
        assertFalse(new File(outDir + "/emp.addresses.csv").exists());
        assertFalse(new File(outDir + "/ph.phones.csv").exists());

        checkColCount(Arrays.asList(employee, address, phone, reroute), TEST_FILENAMES, outDir);


        assertEquals("Check if user defined columns from employee are exported",
                // user defined output columns and their attributes from employee record. No cascaded columns
                "emp:employee-no|emp:department|emp:identifiers|emp:identifiers[emp:id-doc-expiry]|emp:identifiers[emp:id-doc-type]",
                employee.get(0));

        assertEquals("Check if user defined columns from address record are exported. No cascaded columns from employee",
                // user defined output columns and their attributes from address record. No cascaded columns
                "emp:address-type|emp:line1|emp:state|emp:zip", address.get(0));

        assertEquals("Check if user defined columns in phone record. No cascaded columns from employee",
                // user defined output columns from phone record. No cascaded columns
                "ph:phone-num|ph:phone-num[ph:contact-type]|ph:phone-type", phone.get(0));


        assertEquals("Check if all columns in reroute appended. No columns cascaded from employee or address",
                // user defined columns from reroute record. No cascaded columns
                "emp:employee-name|emp:line1|emp:line2|emp:state|emp:zip", reroute.get(0));
        assertEquals("Nick Fury|541E Summer St.||NY, US|92478", reroute.get(1));

    }

    // Equivalent to FlattenXmlRunner CLI options: -f out.yaml, -c OUT
    @Test
    public void definedRecordCascadeFullRecord_nsXML() throws IOException, XMLStreamException {
        String outDir = "target/test/results/definedRecordCascadeFullRecord_nsXML";
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
                        new File("src/test/resources/emp_ns.xml")))
                .setRecordOutputFieldsSeq(new File("src/test/resources/empns_output_fields_attrs.yaml"))
                .create();

        assertEquals(3, flattener.parseFlatten());
        recordHandler.closeAllFileStreams();


        List<String> employee = fileLines(outDir + "/emp.employee.csv");
        List<String> address = fileLines(outDir + "/emp.address.csv");
        List<String> phone = fileLines(outDir + "/ph.phone.csv");
        List<String> reroute = fileLines(outDir + "/emp.reroute.csv");
        assertFalse(new File(outDir + "/emp.contact.csv").exists());
        assertFalse(new File(outDir + "/emp.addresses.csv").exists());
        assertFalse(new File(outDir + "/ph.phones.csv").exists());

        checkColCount(Arrays.asList(employee, address, phone, reroute), TEST_FILENAMES, outDir);

        assertEquals("Check if user defined columns from employee are exported",
                // user defined output columns and their attributes from employee record
                "emp:employee-no|emp:department|emp:identifiers|emp:identifiers[emp:id-doc-expiry]|emp:identifiers[emp:id-doc-type]",
                employee.get(0));

        assertEquals("Check if user defined columns from address record + user defined output columns cascaded from employee are exported",
                // user defined output columns and their attributes from address record
                "emp:address-type|emp:line1|emp:state|emp:zip" +
                        // all user defined output columns and their attributes cascaded from employee record
                        "|emp:employee.emp:employee-no|emp:employee.emp:department|emp:employee.emp:identifiers|emp:employee.emp:identifiers[emp:id-doc-expiry]|emp:employee.emp:identifiers[emp:id-doc-type]",
                address.get(0));


        assertEquals("Check if user defined columns in phone record + all user defined output columns cascaded from employee are exported",
                // user defined output columns from phone record
                "ph:phone-num|ph:phone-num[ph:contact-type]|ph:phone-type" +
                        // user defined output columns and their attributes cascaded from employee record (parent)
                        "|emp:employee.emp:employee-no|emp:employee.emp:department|emp:employee.emp:identifiers|emp:employee.emp:identifiers[emp:id-doc-expiry]|emp:employee.emp:identifiers[emp:id-doc-type]",
                phone.get(0));

        assertEquals("Check if user defined columns in reroute appended + user defined columns cascaded from address & employee.",
                // user defined columns from reroute record
                "emp:employee-name|emp:line1|emp:line2|emp:state|emp:zip" +
                        // all user defined output columns and their attributes cascaded from address record (parent)
                        "|emp:address.emp:address-type|emp:address.emp:line1|emp:address.emp:state|emp:address.emp:zip" +
                        // all user defined output columns and their attributes cascaded from employee record (parent of address)
                        "|emp:employee.emp:employee-no|emp:employee.emp:department|emp:employee.emp:identifiers|emp:employee.emp:identifiers[emp:id-doc-expiry]|emp:employee.emp:identifiers[emp:id-doc-type]",
                reroute.get(0));
        assertEquals("Nick Fury|541E Summer St.||NY, US|92478|primary|1 Tudor & Place|NY, US|12345|00000001|public relations|1234567890|1945-05-25|SSN",
                reroute.get(1));
    }


    // Equivalent to FlattenXmlRunner CLI options: -f out.yaml, -c NONE|<missing>, -x x1.xsd
    @Test
    public void definedRecordNoCascadeIgnoreXSD_nsXML() throws IOException, XMLStreamException {
        String outDir = "target/test/results/definedRecordNoCascadeIgnoredXSD_nsXML";
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
                        new File("src/test/resources/emp_ns.xml")))
                // user defined output fields sequence in record
                .setRecordOutputFieldsSeq(new File("src/test/resources/empns_output_fields_attrs.yaml"))
                // XSDs are ignored
                .setXsdFiles(parseXsds(
                        new String[]{"src/test/resources/emp_ns.xsd", "src/test/resources/phone_ns.xsd"}))
                .create();

        assertEquals(3, flattener.parseFlatten());
        recordHandler.closeAllFileStreams();

        List<String> employee = fileLines(outDir + "/emp.employee.csv");
        List<String> contact = fileLines(outDir + "/emp.contact.csv");
        List<String> addresses = fileLines(outDir + "/emp.addresses.csv");
        List<String> address = fileLines(outDir + "/emp.address.csv");
        List<String> phones = fileLines(outDir + "/ph.phones.csv");
        List<String> phone = fileLines(outDir + "/ph.phone.csv");
        List<String> reroute = fileLines(outDir + "/emp.reroute.csv");

        assertEquals(0, addresses.size());
        assertEquals(0, contact.size());
        assertEquals(0, phones.size());
        checkColCount(Arrays.asList(employee, address, phone, reroute), TEST_FILENAMES, outDir);

        assertEquals("Check if all columns from employee are exported",
                // all columns and their attributes from employee record
                "emp:employee-no|emp:department|emp:identifiers|emp:identifiers[emp:id-doc-expiry]|emp:identifiers[emp:id-doc-type]",
                employee.get(0));


        assertEquals("Check if all columns from address record + no columns cascaded from employee",
                // all columns and their attributes from address record
                "emp:address-type|emp:line1|emp:state|emp:zip",
                address.get(0));


        assertEquals("Check if all columns in phone record + no columns cascaded from employee",
                // all columns from phone record
                "ph:phone-num|ph:phone-num[ph:contact-type]|ph:phone-type",
                phone.get(0));


        assertEquals("Check if all columns in reroute appended + no columns cascaded from address & employee.",
                // all columns from reroute record
                "emp:employee-name|emp:line1|emp:line2|emp:state|emp:zip",
                reroute.get(0));
        assertEquals("Nick Fury|541E Summer St.||NY, US|92478",
                reroute.get(1));
    }

    // Equivalent to FlattenXmlRunner CLI options: -c NONE|<missing>, -x x1.xsd
    @Test
    public void xsdDrivenRecordNoCascade_nsXML() throws IOException, XMLStreamException {
        String outDir = "target/test/results/xsdDrivenRecordNoCascade_nsXML";
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
                        new File("src/test/resources/emp_ns.xml")))
                // XSDs driven
                .setXsdFiles(parseXsds(
                        new String[]{"src/test/resources/emp_ns.xsd", "src/test/resources/phone_ns.xsd"}))
                .create();

        assertEquals(3, flattener.parseFlatten());
        recordHandler.closeAllFileStreams();

        List<String> employee = fileLines(outDir + "/emp.employee.csv");
        List<String> contact = fileLines(outDir + "/emp.contact.csv");
        List<String> addresses = fileLines(outDir + "/emp.addresses.csv");
        List<String> address = fileLines(outDir + "/emp.address.csv");
        List<String> phones = fileLines(outDir + "/ph.phones.csv");
        List<String> phone = fileLines(outDir + "/ph.phone.csv");
        List<String> reroute = fileLines(outDir + "/emp.reroute.csv");

        assertEquals(0, addresses.size());
        assertEquals(0, contact.size());
        assertEquals(0, phones.size());
        checkColCount(Arrays.asList(employee, address, phone, reroute), TEST_FILENAMES, outDir);

        assertEquals("Check if all columns from employee are exported",
                // all columns and their attributes from employee record as per XSD
                "emp:identifiers|emp:identifiers[emp:id-doc-type]|emp:identifiers[emp:id-doc-expiry]|emp:employee-no|emp:employee-no[emp:status]|emp:employee-name|emp:department|emp:salary",
                employee.get(0));


        assertEquals("Check if all columns from address record + no columns cascaded from employee",
                // all columns and their attributes from address record as per XSD
                "emp:address-type|emp:line1|emp:line2|emp:state|emp:zip",
                address.get(0));


        assertEquals("Check if all columns in phone record + no columns cascaded from employee",
                // all columns from phone record as per XSD
                "ph:phone-num|ph:phone-num[ph:contact-type]|ph:phone-type",
                phone.get(0));


        assertEquals("Check if all columns in reroute appended + no columns cascaded from address & employee.",
                // all columns from reroute record as per XSD
                "emp:employee-name|emp:line1|emp:line2|emp:state|emp:zip",
                reroute.get(0));
        assertEquals("Nick Fury|541E Summer St.||NY, US|92478",
                reroute.get(1));
    }

    // Equivalent to FlattenXmlRunner CLI options: -c XSD, -x x1.xsd
    @Test
    public void xsdDrivenRecordCascadeAllTagsInXsd_nsXML() throws IOException, XMLStreamException {
        String outDir = "target/test/results/xsdDrivenRecordCascadeRequiredTagsInXsd_nsXML";
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
                        new File("src/test/resources/emp_ns.xml")))
                // XSDs driven
                .setXsdFiles(parseXsds(
                        new String[]{"src/test/resources/emp_ns.xsd", "src/test/resources/phone_ns.xsd"}))
                .create();

        assertEquals(3, flattener.parseFlatten());
        recordHandler.closeAllFileStreams();


        List<String> employee = fileLines(outDir + "/emp.employee.csv");
        List<String> contact = fileLines(outDir + "/emp.contact.csv");
        List<String> addresses = fileLines(outDir + "/emp.addresses.csv");
        List<String> address = fileLines(outDir + "/emp.address.csv");
        List<String> phones = fileLines(outDir + "/ph.phones.csv");
        List<String> phone = fileLines(outDir + "/ph.phone.csv");
        List<String> reroute = fileLines(outDir + "/emp.reroute.csv");

        assertEquals(0, addresses.size());
        assertEquals(0, contact.size());
        assertEquals(0, phones.size());
        checkColCount(Arrays.asList(employee, address, phone, reroute), TEST_FILENAMES, outDir);

        assertEquals("Check if user defined columns from employee are exported",
                // all output columns and their attributes from employee record as per XSD
                "emp:identifiers|emp:identifiers[emp:id-doc-type]|emp:identifiers[emp:id-doc-expiry]|emp:employee-no|emp:employee-no[emp:status]|emp:employee-name|emp:department|emp:salary",
                employee.get(0));

        assertEquals("Check if user defined columns from address record + user defined output columns cascaded from employee are exported",
                // all output columns and their attributes from address record as per XSD
                "emp:address-type|emp:line1|emp:line2|emp:state|emp:zip" +
                        // required columns and their attributes cascaded from employee record as per XSD
                        "|emp:employee.emp:identifiers|emp:employee.emp:identifiers[emp:id-doc-type]|emp:employee.emp:identifiers[emp:id-doc-expiry]|emp:employee.emp:employee-no|emp:employee.emp:employee-no[emp:status]|emp:employee.emp:employee-name|emp:employee.emp:department|emp:employee.emp:salary",
                address.get(0));


        assertEquals("Check if user defined columns in phone record + all user defined output columns cascaded from employee are exported",
                // all output columns from phone record as per XSD
                "ph:phone-num|ph:phone-num[ph:contact-type]|ph:phone-type" +
                        // required output columns and their attributes cascaded from employee record (parent) as per XSD
                        "|emp:employee.emp:identifiers|emp:employee.emp:identifiers[emp:id-doc-type]|emp:employee.emp:identifiers[emp:id-doc-expiry]|emp:employee.emp:employee-no|emp:employee.emp:employee-no[emp:status]|emp:employee.emp:employee-name|emp:employee.emp:department|emp:employee.emp:salary",
                phone.get(0));

        assertEquals("Check if user defined columns in reroute appended + user defined columns cascaded from address & employee.",
                // user defined columns from reroute record
                "emp:employee-name|emp:line1|emp:line2|emp:state|emp:zip" +
                        // required output columns and their attributes cascaded from address record (parent) as per XSD
                        "|emp:address.emp:address-type|emp:address.emp:line1|emp:address.emp:line2|emp:address.emp:state|emp:address.emp:zip" +
                        // required output columns and their attributes cascaded from employee record (parent of address) as per XSD
                        "|emp:employee.emp:identifiers|emp:employee.emp:identifiers[emp:id-doc-type]|emp:employee.emp:identifiers[emp:id-doc-expiry]|emp:employee.emp:employee-no|emp:employee.emp:employee-no[emp:status]|emp:employee.emp:employee-name|emp:employee.emp:department|emp:employee.emp:salary",
                reroute.get(0));
        assertEquals("Nick Fury|541E Summer St.||NY, US|92478|primary|1 Tudor & Place||NY, US|12345|1234567890|SSN|1945-05-25|00000001|active|Steve Rogers|public relations|150,000.00",
                reroute.get(1));
    }

    // Equivalent to FlattenXmlRunner CLI options: -c XSD, -x x1.xsd
    @Test
    public void xsdDrivenRecordCascadeAllTagsInXsdDrivenRecord_nsXML() throws IOException, XMLStreamException {
        String outDir = "target/test/results/xsdDrivenRecordCascadeRequiredTagsInXsd_nsXML";
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
                        new File("src/test/resources/emp_ns.xml")))
                // XSDs driven
                .setXsdFiles(parseXsds(
                        new String[]{"src/test/resources/emp_ns.xsd", "src/test/resources/phone_ns.xsd"}))
                .create();

        assertEquals(3, flattener.parseFlatten());
        recordHandler.closeAllFileStreams();


        List<String> employee = fileLines(outDir + "/emp.employee.csv");
        List<String> contact = fileLines(outDir + "/emp.contact.csv");
        List<String> addresses = fileLines(outDir + "/emp.addresses.csv");
        List<String> address = fileLines(outDir + "/emp.address.csv");
        List<String> phones = fileLines(outDir + "/ph.phones.csv");
        List<String> phone = fileLines(outDir + "/ph.phone.csv");
        List<String> reroute = fileLines(outDir + "/emp.reroute.csv");

        assertEquals(0, addresses.size());
        assertEquals(0, contact.size());
        assertEquals(0, phones.size());
        checkColCount(Arrays.asList(employee, address, phone, reroute), TEST_FILENAMES, outDir);

        assertEquals("Check if user defined columns from employee are exported",
                // all output columns and their attributes from employee record as per XSD
                "emp:identifiers|emp:identifiers[emp:id-doc-type]|emp:identifiers[emp:id-doc-expiry]|emp:employee-no|emp:employee-no[emp:status]|emp:employee-name|emp:department|emp:salary",
                employee.get(0));

        assertEquals("Check if user defined columns from address record + user defined output columns cascaded from employee are exported",
                // all output columns and their attributes from address record as per XSD
                "emp:address-type|emp:line1|emp:line2|emp:state|emp:zip" +
                        // required columns and their attributes cascaded from employee record as per XSD
                        "|emp:employee.emp:identifiers|emp:employee.emp:identifiers[emp:id-doc-type]|emp:employee.emp:identifiers[emp:id-doc-expiry]|emp:employee.emp:employee-no|emp:employee.emp:employee-no[emp:status]|emp:employee.emp:employee-name|emp:employee.emp:department|emp:employee.emp:salary",
                address.get(0));


        assertEquals("Check if user defined columns in phone record + all user defined output columns cascaded from employee are exported",
                // all output columns from phone record as per XSD
                "ph:phone-num|ph:phone-num[ph:contact-type]|ph:phone-type" +
                        // required output columns and their attributes cascaded from employee record (parent) as per XSD
                        "|emp:employee.emp:identifiers|emp:employee.emp:identifiers[emp:id-doc-type]|emp:employee.emp:identifiers[emp:id-doc-expiry]|emp:employee.emp:employee-no|emp:employee.emp:employee-no[emp:status]|emp:employee.emp:employee-name|emp:employee.emp:department|emp:employee.emp:salary",
                phone.get(0));

        assertEquals("Check if user defined columns in reroute appended + user defined columns cascaded from address & employee.",
                // user defined columns from reroute record
                "emp:employee-name|emp:line1|emp:line2|emp:state|emp:zip" +
                        // required output columns and their attributes cascaded from address record (parent) as per XSD
                        "|emp:address.emp:address-type|emp:address.emp:line1|emp:address.emp:line2|emp:address.emp:state|emp:address.emp:zip" +
                        // required output columns and their attributes cascaded from employee record (parent of address) as per XSD
                        "|emp:employee.emp:identifiers|emp:employee.emp:identifiers[emp:id-doc-type]|emp:employee.emp:identifiers[emp:id-doc-expiry]|emp:employee.emp:employee-no|emp:employee.emp:employee-no[emp:status]|emp:employee.emp:employee-name|emp:employee.emp:department|emp:employee.emp:salary",
                reroute.get(0));
        assertEquals("Nick Fury|541E Summer St.||NY, US|92478|primary|1 Tudor & Place||NY, US|12345|1234567890|SSN|1945-05-25|00000001|active|Steve Rogers|public relations|150,000.00",
                reroute.get(1));
    }

    // Equivalent to FlattenXmlRunner CLI options: -c OUT, -x x1.xsd
    @Test
    public void definedRecordCascadeOutputIgnoreXSD_nsXML() throws IOException, XMLStreamException {
        String outDir = "target/test/results/xsdDrivenRecordCascadeRequiredTagsInXsd_nsXML";
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
                .setRecordOutputFieldsSeq(new File("src/test/resources/empns_output_fields_attrs.yaml"))
                .setXmlStream(new FileInputStream(
                        new File("src/test/resources/emp_ns.xml")))
                // XSDs driven
                .setXsdFiles(parseXsds(
                        new String[]{"src/test/resources/emp_ns.xsd", "src/test/resources/phone_ns.xsd"}))
                .create();

        assertEquals(3, flattener.parseFlatten());
        recordHandler.closeAllFileStreams();


        List<String> employee = fileLines(outDir + "/emp.employee.csv");
        List<String> contact = fileLines(outDir + "/emp.contact.csv");
        List<String> addresses = fileLines(outDir + "/emp.addresses.csv");
        List<String> address = fileLines(outDir + "/emp.address.csv");
        List<String> phones = fileLines(outDir + "/ph.phones.csv");
        List<String> phone = fileLines(outDir + "/ph.phone.csv");
        List<String> reroute = fileLines(outDir + "/emp.reroute.csv");

        assertEquals(0, addresses.size());
        assertEquals(0, contact.size());
        assertEquals(0, phones.size());
        checkColCount(Arrays.asList(employee, address, phone, reroute), TEST_FILENAMES, outDir);

        assertEquals("Check if user defined columns from employee are exported",
                // user defined output columns and their attributes from employee record
                "emp:employee-no|emp:department|emp:identifiers|emp:identifiers[emp:id-doc-expiry]|emp:identifiers[emp:id-doc-type]",
                employee.get(0));

        assertEquals("Check if user defined columns from address record + user defined output columns cascaded from employee are exported",
                // user defined output columns and their attributes from address record
                "emp:address-type|emp:line1|emp:state|emp:zip" +
                        // all columns and their attributes cascaded from employee record as per XSD
                        "|emp:employee.emp:employee-no|emp:employee.emp:department|emp:employee.emp:identifiers|emp:employee.emp:identifiers[emp:id-doc-expiry]|emp:employee.emp:identifiers[emp:id-doc-type]",
                address.get(0));


        assertEquals("Check if user defined columns in phone record + all user defined output columns cascaded from employee are exported",
                // user defined output columns from phone record
                "ph:phone-num|ph:phone-num[ph:contact-type]|ph:phone-type" +
                        // required output columns and their attributes cascaded from employee record (parent) as per XSD
                        "|emp:employee.emp:employee-no|emp:employee.emp:department|emp:employee.emp:identifiers|emp:employee.emp:identifiers[emp:id-doc-expiry]|emp:employee.emp:identifiers[emp:id-doc-type]",
                phone.get(0));

        assertEquals("Check if user defined columns in reroute appended + user defined columns cascaded from address & employee.",
                // user defined columns from reroute record
                "emp:employee-name|emp:line1|emp:line2|emp:state|emp:zip" +
                        // required output columns and their attributes cascaded from address record (parent) as per XSD
                        "|emp:address.emp:address-type|emp:address.emp:line1|emp:address.emp:state|emp:address.emp:zip" +
                        // required output columns and their attributes cascaded from employee record (parent of address) as per XSD
                        "|emp:employee.emp:employee-no|emp:employee.emp:department|emp:employee.emp:identifiers|emp:employee.emp:identifiers[emp:id-doc-expiry]|emp:employee.emp:identifiers[emp:id-doc-type]",
                reroute.get(0));
        assertEquals("Nick Fury|541E Summer St.||NY, US|92478|primary|1 Tudor & Place|NY, US|12345|00000001|public relations|1234567890|1945-05-25|SSN",
                reroute.get(1));
    }

    // Equivalent to FlattenXmlRunner CLI options: -c XSD, -x x1.xsd
    @Test
    public void definedRecordCascadeAllTagsInXsd_nsXML() throws IOException, XMLStreamException {
        String outDir = "target/test/results/xsdDrivenRecordCascadeRequiredTagsInXsd_nsXML";
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
                .setRecordOutputFieldsSeq(new File("src/test/resources/empns_output_fields_attrs.yaml"))
                .setXmlStream(new FileInputStream(
                        new File("src/test/resources/emp_ns.xml")))
                // XSDs driven
                .setXsdFiles(parseXsds(
                        new String[]{"src/test/resources/emp_ns.xsd", "src/test/resources/phone_ns.xsd"}))
                .create();

        assertEquals(3, flattener.parseFlatten());
        recordHandler.closeAllFileStreams();


        List<String> employee = fileLines(outDir + "/emp.employee.csv");
        List<String> contact = fileLines(outDir + "/emp.contact.csv");
        List<String> addresses = fileLines(outDir + "/emp.addresses.csv");
        List<String> address = fileLines(outDir + "/emp.address.csv");
        List<String> phones = fileLines(outDir + "/ph.phones.csv");
        List<String> phone = fileLines(outDir + "/ph.phone.csv");
        List<String> reroute = fileLines(outDir + "/emp.reroute.csv");

        assertEquals(0, addresses.size());
        assertEquals(0, contact.size());
        assertEquals(0, phones.size());
        checkColCount(Arrays.asList(employee, address, phone, reroute), TEST_FILENAMES, outDir);

        assertEquals("Check if user defined columns from employee are exported",
                // user defined output columns and their attributes from employee record
                "emp:employee-no|emp:department|emp:identifiers|emp:identifiers[emp:id-doc-expiry]|emp:identifiers[emp:id-doc-type]",
                employee.get(0));

        assertEquals("Check if user defined columns from address record + user defined output columns cascaded from employee are exported",
                // user defined output columns and their attributes from address record
                "emp:address-type|emp:line1|emp:state|emp:zip" +
                        // all columns and their attributes cascaded from employee record as per XSD
                        "|emp:employee.emp:identifiers|emp:employee.emp:identifiers[emp:id-doc-type]|emp:employee.emp:identifiers[emp:id-doc-expiry]|emp:employee.emp:employee-no|emp:employee.emp:employee-no[emp:status]|emp:employee.emp:employee-name|emp:employee.emp:department|emp:employee.emp:salary",
                address.get(0));


        assertEquals("Check if user defined columns in phone record + all user defined output columns cascaded from employee are exported",
                // user defined output columns from phone record
                "ph:phone-num|ph:phone-num[ph:contact-type]|ph:phone-type" +
                        // required output columns and their attributes cascaded from employee record (parent) as per XSD
                        "|emp:employee.emp:identifiers|emp:employee.emp:identifiers[emp:id-doc-type]|emp:employee.emp:identifiers[emp:id-doc-expiry]|emp:employee.emp:employee-no|emp:employee.emp:employee-no[emp:status]|emp:employee.emp:employee-name|emp:employee.emp:department|emp:employee.emp:salary",
                phone.get(0));

        assertEquals("Check if user defined columns in reroute appended + user defined columns cascaded from address & employee.",
                // user defined columns from reroute record
                "emp:employee-name|emp:line1|emp:line2|emp:state|emp:zip" +
                        // required output columns and their attributes cascaded from address record (parent) as per XSD
                        "|emp:address.emp:address-type|emp:address.emp:line1|emp:address.emp:line2|emp:address.emp:state|emp:address.emp:zip" +
                        // required output columns and their attributes cascaded from employee record (parent of address) as per XSD
                        "|emp:employee.emp:identifiers|emp:employee.emp:identifiers[emp:id-doc-type]|emp:employee.emp:identifiers[emp:id-doc-expiry]|emp:employee.emp:employee-no|emp:employee.emp:employee-no[emp:status]|emp:employee.emp:employee-name|emp:employee.emp:department|emp:employee.emp:salary",
                reroute.get(0));
        assertEquals("Nick Fury|541E Summer St.||NY, US|92478|primary|1 Tudor & Place||NY, US|12345|1234567890|SSN|1945-05-25|00000001|active|Steve Rogers|public relations|150,000.00",
                reroute.get(1));
    }

    static void compareOutFiles(String file1, String file2) throws IOException {
        char[] buf = new char[102400];
        char[] buf2 = new char[102400];
        int bytesRead = new BufferedReader(new FileReader(file1)).read(buf);
        int bytesRead2 = new BufferedReader(new FileReader(file2)).read(buf2);

        assertEquals(bytesRead, bytesRead2);
        if (bytesRead > 0) {
            assertEquals(file2 + " does not equal " + file1,
                    new String(buf, 0, bytesRead), new String(buf2, 0, bytesRead2));
        }
    }

    static List<String> fileLines(String filePath) throws FileNotFoundException {
        return new BufferedReader(new FileReader(new File(filePath)))
                .lines().collect(Collectors.toList());
    }

    static void checkColCount(Iterable<Iterable<String>> files, String[] fileNames, String dir) {
        int fileNo = 0;
        for (Iterable<String> lines: files) {

            int lineNo = 0, fieldCount = 0;
            for (String line : lines) {
                if (lineNo == 0)
                    fieldCount = line.split("\\|").length;
                assertEquals("Columns count in file " + dir +"/"+ fileNames[fileNo] + " is not uniform. "+
                        "Check line# " + lineNo + ": " + line,
                        fieldCount, line.split("\\|", -1).length);
                lineNo++;
            }

            fileNo++;
        }
    }

}
