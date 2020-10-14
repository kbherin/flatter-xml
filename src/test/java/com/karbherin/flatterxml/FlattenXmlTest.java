package com.karbherin.flatterxml;

import com.karbherin.flatterxml.output.DelimitedFileWriter;
import com.karbherin.flatterxml.output.RecordHandler;
import com.karbherin.flatterxml.output.StatusReporter;
import org.junit.Test;

import javax.xml.stream.XMLStreamException;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

import static com.karbherin.flatterxml.FlattenXml.FlattenXmlBuilder;
import static com.karbherin.flatterxml.AppConstants.*;
import static org.junit.Assert.assertEquals;

public class FlattenXmlTest {

    @Test
    public void fullDumpNSTest() throws IOException, XMLStreamException {
        String outDir = "target/test/results/empns_fulldump";
        Files.createDirectories(Paths.get(outDir));
        RecordHandler recordHandler = new DelimitedFileWriter(
                "|", outDir,
                false, new StatusReporter(), "~");

        FlattenXml flattener = new FlattenXmlBuilder()
                .setCascadePolicy(CascadePolicy.ALL)
                .setRecordWriter(recordHandler)
                .setXmlStream(new FileInputStream(
                        new File("src/test/resources/emp_ns.xml")))
                .create();

        assertEquals(3, flattener.parseFlatten());
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

        assertEquals(7, employee.size());

        assertEquals("Check if all columns from employee are exported",
                // ALL columns and their attributes from employee record
                "emp:identifiers|emp:identifiers[emp:id-doc-type]|emp:identifiers[emp:id-doc-expiry]|emp:employee-no|emp:employee-no[emp:status]|emp:employee-name|emp:department|emp:salary",
                employee.get(0));
        assertEquals("1234567890|SSN|1945-05-25|00000001|active|Steve Rogers|public relations|150,000.00",
                employee.get(1));
        assertEquals("4567890123|MEDICARE|2021-06-30|00000001|active|Steve Rogers|public relations|150,000.00",
                employee.get(2));
        assertEquals("0000000000|SSN||00000002|suspended|Tony Stark|sales|89,000.00",
                employee.get(3));
        assertEquals("8908907890|MIT|2008-12-28|00000002|suspended|Tony Stark|sales|89,000.00",
                employee.get(4));
        assertEquals("1234567890|SSN||00000001|active|Steve Rogers|public relations|150,000.00",
                employee.get(5));
        assertEquals("4567890123|MEDICARE|2021-06-30|00000001|active|Steve Rogers|public relations|150,000.00",
                employee.get(6));

        // One level nesting
        assertEquals(5, address.size());
        assertEquals("Check if all columns from address record + all columns cascaded from employee",
                // ALL columns and their attributes from address record
                "emp:address-type|emp:line1|emp:line2|emp:state|emp:zip" +
                        // all columns and their attributes cascaded from employee record (parent)
                        "|emp:employee.emp:identifiers|emp:employee.emp:identifiers[emp:id-doc-type]|emp:employee.emp:identifiers[emp:id-doc-expiry]|emp:employee.emp:employee-no|emp:employee.emp:employee-no[emp:status]|emp:employee.emp:employee-name|emp:employee.emp:department|emp:employee.emp:salary",
                address.get(0));
        assertEquals("primary|1 Tudor & Place||NY, US|12345" +
                "|1234567890|SSN|1945-05-25|00000001|active|Steve Rogers|public relations|150,000.00",
                address.get(1));
        assertEquals("primary|1 Bloomington St|Suite 3000|DC, US|22344" +
                        "|0000000000|SSN||00000002|suspended|Tony Stark|sales|89,000.00",
                address.get(2));
        assertEquals("holiday|19 Wilmington View||NC, US|27617" +
                        "|0000000000|SSN||00000002|suspended|Tony Stark|sales|89,000.00",
                address.get(3));
        assertEquals("primary|1 Tudor & Place||NY, US|12345" +
                        "|1234567890|SSN||00000001|active|Steve Rogers|public relations|150,000.00",
                address.get(4));

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
        assertEquals("1234567890|primary|landline" +
                        "|1234567890|SSN|1945-05-25|00000001|active|Steve Rogers|public relations|150,000.00",
                phone.get(1));
        assertEquals("7279237008|primary|cell" +
                        "|0000000000|SSN||00000002|suspended|Tony Stark|sales|89,000.00",
                phone.get(2));
        assertEquals("9090909090|emergency|office" +
                        "|0000000000|SSN||00000002|suspended|Tony Stark|sales|89,000.00",
                phone.get(3));
        assertEquals("1234567890||landline" +
                        "|1234567890|SSN||00000001|active|Steve Rogers|public relations|150,000.00",
                phone.get(4));

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
                        "  \"address\":\n" +
                        "    - {\"emp:address-type\": []}\n" +
                        "    - {\"emp:line1\": []}\n" +
                        "    - {\"emp:line2\": []}\n" +
                        "    - {\"emp:state\": []}\n" +
                        "    - {\"emp:zip\": []}\n" +
                        "\n" +
                        "  \"employee\":\n" +
                        "    - {\"emp:identifiers\": [\"emp:id-doc-type\",\"emp:id-doc-expiry\"]}\n" +
                        "    - {\"emp:employee-no\": [\"emp:status\"]}\n" +
                        "    - {\"emp:employee-name\": []}\n" +
                        "    - {\"emp:department\": []}\n" +
                        "    - {\"emp:salary\": []}\n" +
                        "\n" +
                        "  \"phone\":\n" +
                        "    - {\"ph:phone-num\": [\"ph:contact-type\"]}\n" +
                        "    - {\"ph:phone-type\": []}\n" +
                        "\n" +
                        "  \"reroute\":\n" +
                        "    - {\"emp:employee-name\": []}\n" +
                        "    - {\"emp:line1\": []}\n" +
                        "    - {\"emp:state\": []}\n" +
                        "    - {\"emp:zip\": []}\n",
        new String(buf, 0, bytesRead));
    }

    @Test
    public void fullDumpNoNSTest() throws IOException, XMLStreamException {
        String outDir = "target/test/results/emp_fulldump";
        Files.createDirectories(Paths.get(outDir));

        RecordHandler recordHandler = new DelimitedFileWriter(
                "|", outDir,
                false, new StatusReporter(), "~");

        FlattenXml flattener = new FlattenXmlBuilder()
                .setCascadePolicy(CascadePolicy.ALL)
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

        assertEquals("#employee recs + header", 22, employee.size());

        assertEquals("Check if all columns from employee are exported",
                // all columns and their attributes from employee record
                "identifiers|employee-no|employee-name|department|salary|contact",
                employee.get(0));
        checkColCount(employee);


        assertEquals("|00000001|Steve Rogers|public relations|150,000.00|", employee.get(1));
        assertEquals("|00000002|Tony Stark|sales|89,000.00|", employee.get(2));
        assertEquals("|00000003|Natasha Romanov|finance|110,000.00|", employee.get(3));
        assertEquals("11111111|00000004|Clint Barton|sales|75,000.00|", employee.get(4));
        assertEquals("|00000004|Bruce Banner|sales|110,000.00|", employee.get(5));
        assertEquals("|00000001|Steve Rogers|public relations|150,000.00|", employee.get(6));
        assertEquals("|00000002|Tony Stark|sales|89,000.00|", employee.get(7));
        assertEquals("|00000003|Natasha Romanov|finance|110,000.00|", employee.get(8));
        assertEquals("|00000004|Clint Barton|sales|75,000.00|", employee.get(9));
        assertEquals("|00000004|Bruce Banner|sales|110,000.00|", employee.get(10));
        assertEquals("|00000001|Steve Rogers|public relations|150,000.00|", employee.get(11));
        assertEquals("|00000002|Tony Stark|sales|89,000.00|", employee.get(12));
        assertEquals("|00000003|Natasha Romanov|finance|110,000.00|", employee.get(13));
        assertEquals("|00000004|Clint Barton|sales|75,000.00|", employee.get(14));
        assertEquals("|00000004|Bruce Banner|sales|110,000.00|", employee.get(15));
        assertEquals("|00000001|Steve Rogers|public relations|150,000.00|", employee.get(16));
        assertEquals("|00000002|Tony Stark|sales|89,000.00|", employee.get(17));
        assertEquals("|00000003|Natasha Romanov|finance|110,000.00|", employee.get(18));
        assertEquals("|00000004|Clint Barton|sales|75,000.00|", employee.get(19));
        assertEquals("|00000004|Bruce Banner|sales|110,000.00|", employee.get(20));
        assertEquals("|00000001|Steve Rogers|public relations|150,000.00|", employee.get(21));


        assertEquals("Check if all columns from address record + all columns cascaded from employee",
                // all columns and their attributes from address record
                "address-type|line1|line2|state|zip" +
                        // all columns and their attributes cascaded from employee record
                        "|employee.employee-no|employee.employee-name|employee.department|employee.salary|employee.identifiers",
                address.get(0));
        checkColCount(address);
        assertEquals("primary|1 Tudor & Place||NY, US|12345|00000001|Steve Rogers|public relations|150,000.00|",
                address.get(1));
        assertEquals("primary|1 Bloomington St||DC, US|22344|00000002|Tony Stark|sales|89,000.00|",
                address.get(2));
        assertEquals("holiday|19 Wilmington View||NC, US|27617|00000002|Tony Stark|sales|89,000.00|",
                address.get(3));
        assertEquals("primary|36 Washinton Ave.||CO, US|22987|00000003|Natasha Romanov|finance|110,000.00|",
                address.get(4));
        assertEquals("primary|23 Lead Mine Rd.||NC, US|26516|00000004|Clint Barton|sales|75,000.00|11111111",
                address.get(5));
        assertEquals("primary|1 Tudor & Place||NY, US|12345|00000001|Steve Rogers|public relations|150,000.00|",
                address.get(6));
        assertEquals("primary|1 Bloomington St||DC, US|22344|00000002|Tony Stark|sales|89,000.00|",
                address.get(7));
        assertEquals("holiday|19 Wilmington View|Apt 311|NC, US|27617|00000002|Tony Stark|sales|89,000.00|",
                address.get(8));
        assertEquals("primary|36 Washinton Ave.||CO, US|22987|00000003|Natasha Romanov|finance|110,000.00|",
                address.get(9));
        assertEquals("primary|23 Lead Mine Rd.||NC, US|26516|00000004|Clint Barton|sales|75,000.00|",
                address.get(10));
        assertEquals("primary|1 Tudor & Place||NY, US|12345|00000001|Steve Rogers|public relations|150,000.00|",
                address.get(11));
        assertEquals("primary|1 Bloomington St||DC, US|22344|00000002|Tony Stark|sales|89,000.00|",
                address.get(12));
        assertEquals("holiday|19 Wilmington View|Apt 311|NC, US|27617|00000002|Tony Stark|sales|89,000.00|",
                address.get(13));
        assertEquals("primary|36 Washinton Ave.||CO, US|22987|00000003|Natasha Romanov|finance|110,000.00|",
                address.get(14));
        assertEquals("primary|23 Lead Mine Rd.||NC, US|26516|00000004|Clint Barton|sales|75,000.00|",
                address.get(15));
        assertEquals("primary|1 Tudor & Place||NY, US|12345|00000001|Steve Rogers|public relations|150,000.00|",
                address.get(16));
        assertEquals("primary|1 Bloomington St||DC, US|22344|00000002|Tony Stark|sales|89,000.00|",
                address.get(17));
        assertEquals("holiday|19 Wilmington View|Apt 311|NC, US|27617|00000002|Tony Stark|sales|89,000.00|",
                address.get(18));
        assertEquals("primary|36 Washinton Ave.||CO, US|22987|00000003|Natasha Romanov|finance|110,000.00|",
                address.get(19));
        assertEquals("primary|23 Lead Mine Rd.||NC, US|26516|00000004|Clint Barton|sales|75,000.00|",
                address.get(20));
        assertEquals("primary|1 Tudor & Place||NY, US|12345|00000001|Steve Rogers|public relations|150,000.00|",
                address.get(21));


        assertEquals("Check if all columns in phone record + all columns cascaded from employee",
                // all columns from phone record
                "phone-num|phone-type" +
                        // all columns and their attributes cascaded from employee record (parent)
                        "|employee.employee-no|employee.employee-name|employee.department|employee.salary|employee.identifiers",
                phone.get(0));
        checkColCount(phone);
        assertEquals("1234567890|landline|00000001|Steve Rogers|public relations|150,000.00|", phone.get(1));
        assertEquals("7279237008|cell|00000002|Tony Stark|sales|89,000.00|", phone.get(2));
        assertEquals("9090909090|office|00000002|Tony Stark|sales|89,000.00|", phone.get(3));
        assertEquals("1234567890|landline|00000001|Steve Rogers|public relations|150,000.00|", phone.get(4));
        assertEquals("7279237008|cell|00000002|Tony Stark|sales|89,000.00|", phone.get(5));
        assertEquals("9090909090|office|00000002|Tony Stark|sales|89,000.00|", phone.get(6));
        assertEquals("1234567890|landline|00000001|Steve Rogers|public relations|150,000.00|", phone.get(7));
        assertEquals("7279237008|cell|00000002|Tony Stark|sales|89,000.00|", phone.get(8));
        assertEquals("9090909090|office|00000002|Tony Stark|sales|89,000.00|", phone.get(9));
        assertEquals("1234567890|landline|00000001|Steve Rogers|public relations|150,000.00|", phone.get(10));
        assertEquals("7279237008|cell|00000002|Tony Stark|sales|89,000.00|", phone.get(11));
        assertEquals("9090909090|office|00000002|Tony Stark|sales|89,000.00|", phone.get(12));
        assertEquals("1234567890|landline|00000001|Steve Rogers|public relations|150,000.00|", phone.get(13));


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
    public void fullDumpNoNSTest_withoutEmptyContact() throws IOException, XMLStreamException {
        String outDir = "target/test/results/emp_fulldump";
        Files.createDirectories(Paths.get(outDir));

        RecordHandler recordHandler = new DelimitedFileWriter(
                "|", outDir,
                false, new StatusReporter(), "~");

        FlattenXml flattener = new FlattenXmlBuilder()
                .setCascadePolicy(CascadePolicy.ALL)
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

    @Test
    public void reuseRecDefsGeneratedByFullDumpNSTest() throws IOException, XMLStreamException {
        String outDir = "target/test/results/empns_fulldump_reuserecdefs";
        Files.createDirectories(Paths.get(outDir));
        RecordHandler recordHandler = new DelimitedFileWriter(
                "|", outDir,
                false, new StatusReporter(), "~");


        FlattenXml flattener = new FlattenXmlBuilder()
                .setCascadePolicy(CascadePolicy.ALL)
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
        // The tests below check if using it maintains the same exact output as that produced from full export flattening

        Files.createDirectories(Paths.get("target/test/results/empns_fulldump_reuserecdefs/reused_recdefs"));
        recordHandler = new DelimitedFileWriter(
                "|", outDir+"/reused_recdefs",
                false, new StatusReporter(), "~");
        flattener = new FlattenXmlBuilder()
                .setCascadePolicy(CascadePolicy.ALL)
                .setRecordWriter(recordHandler)
                .setXmlStream(new FileInputStream(
                        new File("src/test/resources/emp_ns.xml")))
                .setRecordOutputFieldsSeq(new File(outDir+"/record_defs.yaml"))
                .setRecordCascadeFieldsSeq(new File(outDir+"/record_defs.yaml"))
                .create();

        assertEquals(3, flattener.parseFlatten());
        recordHandler.closeAllFileStreams();

        compareOutFiles(outDir + "/employee.csv", outDir + "/reused_recdefs/employee.csv");
        compareOutFiles(outDir + "/address.csv", outDir + "/reused_recdefs/address.csv");
        compareOutFiles(outDir + "/phone.csv", outDir + "/reused_recdefs/phone.csv");
        compareOutFiles(outDir + "/reroute.csv", outDir + "/reused_recdefs/reroute.csv");
        compareOutFiles(outDir + "/addresses.csv", outDir + "/reused_recdefs/addresses.csv");
        compareOutFiles(outDir + "/phones.csv", outDir + "/reused_recdefs/phones.csv");
        compareOutFiles(outDir + "/contact.csv", outDir + "/reused_recdefs/contact.csv");

    }

    @Test
    public void definedRecsOutNSTest() throws IOException, XMLStreamException {
        String outDir = "target/test/results/empns_recdefs";
        Files.createDirectories(Paths.get(outDir));
        RecordHandler recordHandler = new DelimitedFileWriter(
                "|", outDir,
                true, new StatusReporter(), "~");

        FlattenXml flattener = new FlattenXmlBuilder()
                .setCascadePolicy(CascadePolicy.ALL)
                .setRecordWriter(recordHandler)
                .setXmlStream(new FileInputStream(
                        new File("src/test/resources/emp_ns.xml")))
                .setRecordOutputFieldsSeq(new File("src/test/resources/empns_output_fields_attrs.yaml"))
                .setRecordCascadeFieldsSeq(new File("src/test/resources/empns_output_fields_attrs.yaml"))
                .create();

        assertEquals(3, flattener.parseFlatten());
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

        assertEquals("Check if all columns from employee are exported",
                // all columns and their attributes from employee record
                "emp:employee-no|emp:department|emp:identifiers|emp:identifiers[emp:id-doc-expiry]|emp:identifiers[emp:id-doc-type]",
            employee.get(0));
        assertEquals("00000001|public relations|1234567890|1945-05-25|SSN",
            employee.get(1));
        assertEquals("00000001|public relations|4567890123|2021-06-30|MEDICARE",
            employee.get(2));
        assertEquals("00000002|sales|0000000000||SSN",
            employee.get(3));
        assertEquals("00000002|sales|0000000000|8908907890|2008-12-28|MIT",
            employee.get(4));
        assertEquals("00000001|public relations|1234567890||SSN",
            employee.get(5));
        assertEquals("00000001|public relations|1234567890|4567890123|2021-06-30|MEDICARE",
            employee.get(6));


        assertEquals("Check if all columns from address record + all columns cascaded from employee",
                // all columns and their attributes from address record
                "emp:address-type|emp:line1|emp:state|emp:zip" +
                        // all columns and their attributes cascaded from employee record
                        "|emp:employee.emp:employee-no|emp:employee.emp:department|emp:employee.emp:identifiers|emp:employee.emp:identifiers[emp:id-doc-expiry]|emp:employee.emp:identifiers[emp:id-doc-type]|emp:employee.emp:employee-no[emp:status]|emp:employee.emp:employee-name|emp:employee.emp:salary",
                address.get(0));
        assertEquals("primary|1 Tudor & Place|NY, US|12345|00000001|public relations|1234567890|1945-05-25|SSN|active|Steve Rogers|150,000.00",
                address.get(1));
        assertEquals("primary|1 Bloomington St|DC, US|22344|00000002|sales|0000000000||SSN|suspended|Tony Stark|89,000.00",
                address.get(2));
        assertEquals("holiday|19 Wilmington View|NC, US|27617|00000002|sales|0000000000||SSN|suspended|Tony Stark|89,000.00",
                address.get(3));
        assertEquals("primary|1 Tudor & Place|NY, US|12345|00000001|public relations|1234567890||SSN|active|Steve Rogers|150,000.00",
                address.get(4));


        assertEquals("Check if all columns in phone record + all columns cascaded from employee",
                // all columns from phone record
                "ph:phone-num|ph:phone-num[ph:contact-type]|ph:phone-type" +
                        // all columns and their attributes cascaded from employee record (parent)
                        "|emp:employee.emp:employee-no|emp:employee.emp:department|emp:employee.emp:identifiers|emp:employee.emp:identifiers[emp:id-doc-expiry]|emp:employee.emp:identifiers[emp:id-doc-type]|emp:employee.emp:employee-no[emp:status]|emp:employee.emp:employee-name|emp:employee.emp:salary",
                phone.get(0));
        assertEquals("1234567890|primary|landline|00000001|public relations|1234567890|1945-05-25|SSN|active|Steve Rogers|150,000.00",
                phone.get(1));
        assertEquals("7279237008|primary|cell|00000002|sales|0000000000||SSN|suspended|Tony Stark|89,000.00",
                phone.get(2));
        assertEquals("9090909090|emergency|office|00000002|sales|0000000000||SSN|suspended|Tony Stark|89,000.00",
                phone.get(3));
        assertEquals("1234567890||landline|00000001|public relations|1234567890||SSN|active|Steve Rogers|150,000.00",
                phone.get(4));


        assertEquals("Check if all columns in reroute appended + all columns cascaded from address & employee.",
                // all columns from reroute record
                "emp:employee-name|emp:line1|emp:state|emp:zip" +
                        // all columns and their attributes cascaded from address record (parent)
                        "|emp:address.emp:address-type|emp:address.emp:line1|emp:address.emp:state|emp:address.emp:zip" +
                        // all columns and their attributes cascaded from employee record (parent of address)
                        "|emp:employee.emp:employee-no|emp:employee.emp:department|emp:employee.emp:identifiers|emp:employee.emp:identifiers[emp:id-doc-expiry]|emp:employee.emp:identifiers[emp:id-doc-type]|emp:employee.emp:employee-no[emp:status]|emp:employee.emp:employee-name|emp:employee.emp:salary",
                reroute.get(0));
        assertEquals("Nick Fury|541E Summer St.|NY, US|92478|primary|1 Tudor & Place|NY, US|12345|00000001|public relations|1234567890|1945-05-25|SSN|active|Steve Rogers|150,000.00",
                reroute.get(1));

    }

    @Test
    public void definedRecsOutNoCascadeTest() throws IOException, XMLStreamException {
        String outDir = "target/test/results/emp_recdefs_nocasc";
        Files.createDirectories(Paths.get(outDir));
        RecordHandler recordHandler = new DelimitedFileWriter(
                "|", outDir,
                true, new StatusReporter(), "~");

        FlattenXml flattener = new FlattenXmlBuilder()
                .setCascadePolicy(CascadePolicy.NONE)
                .setRecordWriter(recordHandler)
                .setXmlStream(new FileInputStream(
                        new File("src/test/resources/emp.xml")))
                .setRecordOutputFieldsSeq(new File("src/test/resources/emp_output_fields_attrs.yaml"))
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


        assertEquals("Check if user defined columns from employee are exported",
                // user defined output columns and their attributes from employee record. No cascaded columns
                "employee-no|department|identifiers|identifiers[id-doc-expiry]|identifiers[id-doc-type]",
                employee.get(0));
        assertEquals("00000001|public relations|||", employee.get(1));
        assertEquals("00000002|sales|||", employee.get(2));
        assertEquals("00000003|finance|||", employee.get(3));
        assertEquals("00000004|sales|11111111||", employee.get(4));
        assertEquals("00000004|sales|||", employee.get(5));
        assertEquals("00000001|public relations|||", employee.get(6));
        assertEquals("00000002|sales|||", employee.get(7));
        assertEquals("00000003|finance|||", employee.get(8));
        assertEquals("00000004|sales|||", employee.get(9));
        assertEquals("00000004|sales|||", employee.get(10));
        assertEquals("00000001|public relations|||", employee.get(11));
        assertEquals("00000002|sales|||", employee.get(12));
        assertEquals("00000003|finance|||", employee.get(13));
        assertEquals("00000004|sales|||", employee.get(14));
        assertEquals("00000004|sales|||", employee.get(15));
        assertEquals("00000001|public relations|||", employee.get(16));
        assertEquals("00000002|sales|||", employee.get(17));
        assertEquals("00000003|finance|||", employee.get(18));
        assertEquals("00000004|sales|||", employee.get(19));
        assertEquals("00000004|sales|||", employee.get(20));
        assertEquals("00000001|public relations|||", employee.get(21));


        assertEquals("Check if user defined columns from address record are exported. No cascaded columns from employee",
                // user defined output columns and their attributes from address record. No cascaded columns
                "address-type|line1|state|zip", address.get(0));
        assertEquals("primary|1 Tudor & Place|NY, US|12345", address.get(1));
        assertEquals("primary|1 Bloomington St|DC, US|22344", address.get(2));
        assertEquals("holiday|19 Wilmington View|NC, US|27617", address.get(3));
        assertEquals("primary|36 Washinton Ave.|CO, US|22987", address.get(4));
        assertEquals("primary|23 Lead Mine Rd.|NC, US|26516", address.get(5));
        assertEquals("primary|1 Tudor & Place|NY, US|12345", address.get(6));
        assertEquals("primary|1 Bloomington St|DC, US|22344", address.get(7));
        assertEquals("holiday|19 Wilmington View|NC, US|27617", address.get(8));
        assertEquals("primary|36 Washinton Ave.|CO, US|22987", address.get(9));
        assertEquals("primary|23 Lead Mine Rd.|NC, US|26516", address.get(10));
        assertEquals("primary|1 Tudor & Place|NY, US|12345", address.get(11));
        assertEquals("primary|1 Bloomington St|DC, US|22344", address.get(12));
        assertEquals("holiday|19 Wilmington View|NC, US|27617", address.get(13));
        assertEquals("primary|36 Washinton Ave.|CO, US|22987", address.get(14));
        assertEquals("primary|23 Lead Mine Rd.|NC, US|26516", address.get(15));
        assertEquals("primary|1 Tudor & Place|NY, US|12345", address.get(16));
        assertEquals("primary|1 Bloomington St|DC, US|22344", address.get(17));
        assertEquals("holiday|19 Wilmington View|NC, US|27617", address.get(18));
        assertEquals("primary|36 Washinton Ave.|CO, US|22987", address.get(19));
        assertEquals("primary|23 Lead Mine Rd.|NC, US|26516", address.get(20));
        assertEquals("primary|1 Tudor & Place|NY, US|12345", address.get(21));

        assertEquals("Check if user defined columns in phone record. No cascaded columns from employee",
                // user defined output columns from phone record. No cascaded columns
                "phone-num|phone-num[contact-type]|phone-type", phone.get(0));
        assertEquals("1234567890||landline", phone.get(1));
        assertEquals("7279237008||cell", phone.get(2));
        assertEquals("9090909090||office", phone.get(3));
        assertEquals("1234567890||landline", phone.get(4));
        assertEquals("7279237008||cell", phone.get(5));
        assertEquals("9090909090||office", phone.get(6));
        assertEquals("1234567890||landline", phone.get(7));
        assertEquals("7279237008||cell", phone.get(8));
        assertEquals("9090909090||office", phone.get(9));
        assertEquals("1234567890||landline", phone.get(10));
        assertEquals("7279237008||cell", phone.get(11));
        assertEquals("9090909090||office", phone.get(12));
        assertEquals("1234567890||landline", phone.get(13));


        assertEquals("Check if all columns in reroute appended. No columns cascaded from employee or address",
                // user defined columns from reroute record. No cascaded columns
                "employee-name|line1|state|zip", reroute.get(0));
        assertEquals("Nick Fury|541E~              Summer St.|NY, US|92478", reroute.get(1));

    }

    @Test
    public void definedRecsOutAllCascadeTest() throws IOException, XMLStreamException {
        String outDir = "target/test/results/emp_recdefs";
        Files.createDirectories(Paths.get(outDir));
        RecordHandler recordHandler = new DelimitedFileWriter(
                "|", outDir,
                true, new StatusReporter(), "~");

        FlattenXml flattener = new FlattenXmlBuilder()
                .setCascadePolicy(CascadePolicy.ALL)
                .setRecordWriter(recordHandler)
                .setXmlStream(new FileInputStream(
                        new File("src/test/resources/emp.xml")))
                .setRecordOutputFieldsSeq(new File("src/test/resources/emp_output_fields_attrs.yaml"))
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

        assertEquals("Check if user defined columns from employee are exported",
                // user defined output columns and their attributes from employee record
                "employee-no|department|identifiers|identifiers[id-doc-expiry]|identifiers[id-doc-type]",
                employee.get(0));
        assertEquals("00000001|public relations|||", employee.get(1));
        assertEquals("00000002|sales|||", employee.get(2));
        assertEquals("00000003|finance|||", employee.get(3));
        assertEquals("00000004|sales|11111111||", employee.get(4));
        assertEquals("00000004|sales|||", employee.get(5));
        assertEquals("00000001|public relations|||", employee.get(6));
        assertEquals("00000002|sales|||", employee.get(7));
        assertEquals("00000003|finance|||", employee.get(8));
        assertEquals("00000004|sales|||", employee.get(9));
        assertEquals("00000004|sales|||", employee.get(10));
        assertEquals("00000001|public relations|||", employee.get(11));
        assertEquals("00000002|sales|||", employee.get(12));
        assertEquals("00000003|finance|||", employee.get(13));
        assertEquals("00000004|sales|||", employee.get(14));
        assertEquals("00000004|sales|||", employee.get(15));
        assertEquals("00000001|public relations|||", employee.get(16));
        assertEquals("00000002|sales|||", employee.get(17));
        assertEquals("00000003|finance|||", employee.get(18));
        assertEquals("00000004|sales|||", employee.get(19));
        assertEquals("00000004|sales|||", employee.get(20));
        assertEquals("00000001|public relations|||", employee.get(21));

        assertEquals("Check if user defined columns from address record + user defined output columns cascaded from employee are exported",
                // user defined output columns and their attributes from address record
                "address-type|line1|state|zip" +
                        // all user defined output columns and their attributes cascaded from employee record
                        "|employee.employee-no|employee.department|employee.identifiers|employee.identifiers[id-doc-expiry]|employee.identifiers[id-doc-type]",
                address.get(0));
        assertEquals("primary|1 Tudor & Place|NY, US|12345|00000001|public relations|||", address.get(1));
        assertEquals("primary|1 Bloomington St|DC, US|22344|00000002|sales|||", address.get(2));
        assertEquals("holiday|19 Wilmington View|NC, US|27617|00000002|sales|||", address.get(3));
        assertEquals("primary|36 Washinton Ave.|CO, US|22987|00000003|finance|||", address.get(4));
        assertEquals("primary|23 Lead Mine Rd.|NC, US|26516|00000004|sales|11111111||", address.get(5));
        assertEquals("primary|1 Tudor & Place|NY, US|12345|00000001|public relations|||", address.get(6));
        assertEquals("primary|1 Bloomington St|DC, US|22344|00000002|sales|||", address.get(7));
        assertEquals("holiday|19 Wilmington View|NC, US|27617|00000002|sales|||", address.get(8));
        assertEquals("primary|36 Washinton Ave.|CO, US|22987|00000003|finance|||", address.get(9));
        assertEquals("primary|23 Lead Mine Rd.|NC, US|26516|00000004|sales|||", address.get(10));
        assertEquals("primary|1 Tudor & Place|NY, US|12345|00000001|public relations|||", address.get(11));
        assertEquals("primary|1 Bloomington St|DC, US|22344|00000002|sales|||", address.get(12));
        assertEquals("holiday|19 Wilmington View|NC, US|27617|00000002|sales|||", address.get(13));
        assertEquals("primary|36 Washinton Ave.|CO, US|22987|00000003|finance|||", address.get(14));
        assertEquals("primary|23 Lead Mine Rd.|NC, US|26516|00000004|sales|||", address.get(15));
        assertEquals("primary|1 Tudor & Place|NY, US|12345|00000001|public relations|||", address.get(16));
        assertEquals("primary|1 Bloomington St|DC, US|22344|00000002|sales|||", address.get(17));
        assertEquals("holiday|19 Wilmington View|NC, US|27617|00000002|sales|||", address.get(18));
        assertEquals("primary|36 Washinton Ave.|CO, US|22987|00000003|finance|||", address.get(19));
        assertEquals("primary|23 Lead Mine Rd.|NC, US|26516|00000004|sales|||", address.get(20));
        assertEquals("primary|1 Tudor & Place|NY, US|12345|00000001|public relations|||", address.get(21));


        assertEquals("Check if user defined columns in phone record + all user defined output columns cascaded from employee are exported",
                // user defined output columns from phone record
                "phone-num|phone-num[contact-type]|phone-type" +
                        // user defined output columns and their attributes cascaded from employee record (parent)
                        "|employee.employee-no|employee.department|employee.identifiers|employee.identifiers[id-doc-expiry]|employee.identifiers[id-doc-type]",
                phone.get(0));
        assertEquals("1234567890||landline|00000001|public relations|||", phone.get(1));
        assertEquals("7279237008||cell|00000002|sales|||", phone.get(2));
        assertEquals("9090909090||office|00000002|sales|||", phone.get(3));
        assertEquals("1234567890||landline|00000001|public relations|||", phone.get(4));
        assertEquals("7279237008||cell|00000002|sales|||", phone.get(5));
        assertEquals("9090909090||office|00000002|sales|||", phone.get(6));
        assertEquals("1234567890||landline|00000001|public relations|||", phone.get(7));
        assertEquals("7279237008||cell|00000002|sales|||", phone.get(8));
        assertEquals("9090909090||office|00000002|sales|||", phone.get(9));
        assertEquals("1234567890||landline|00000001|public relations|||", phone.get(10));
        assertEquals("7279237008||cell|00000002|sales|||", phone.get(11));
        assertEquals("9090909090||office|00000002|sales|||", phone.get(12));
        assertEquals("1234567890||landline|00000001|public relations|||", phone.get(13));


        assertEquals("Check if user defined columns in reroute appended + user defined columns cascaded from address & employee.",
                // user defined columns from reroute record
                "employee-name|line1|state|zip" +
                        // all user defined output columns and their attributes cascaded from address record (parent)
                        "|address.address-type|address.line1|address.state|address.zip" +
                        // all user defined output columns and their attributes cascaded from employee record (parent of address)
                        "|employee.employee-no|employee.department|employee.identifiers|employee.identifiers[id-doc-expiry]|employee.identifiers[id-doc-type]",
                reroute.get(0));
        assertEquals("Nick Fury|541E~              Summer St.|NY, US|92478|primary|1 Tudor & Place|NY, US|12345|00000001|public relations|||",
                reroute.get(1));


    }

    @Test
    public void definedRecsOutDefinedCascadeTest() throws IOException, XMLStreamException {
        String outDir = "target/test/results/emp_recdefs_cascdefs";
        Files.createDirectories(Paths.get(outDir));
        RecordHandler recordHandler = new DelimitedFileWriter(
                "|", outDir,
                true, new StatusReporter(), "~");

        FlattenXml flattener = new FlattenXmlBuilder()
                .setRecordWriter(recordHandler)
                .setXmlStream(new FileInputStream(
                        new File("src/test/resources/emp.xml")))
                .setRecordOutputFieldsSeq(new File("src/test/resources/emp_output_fields.yaml"))
                .setRecordCascadeFieldsSeq(new File("src/test/resources/emp_output_fields.yaml"))
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

        assertEquals("Check if user defined columns from employee are exported",
                // user defined output columns and their attributes from employee record
                "employee-no|department", employee.get(0));
        assertEquals("00000001|public relations", employee.get(1));
        assertEquals("00000002|sales", employee.get(2));
        assertEquals("00000003|finance", employee.get(3));
        assertEquals("00000004|sales", employee.get(4));
        assertEquals("00000004|sales", employee.get(5));
        assertEquals("00000001|public relations", employee.get(6));
        assertEquals("00000002|sales", employee.get(7));
        assertEquals("00000003|finance", employee.get(8));
        assertEquals("00000004|sales", employee.get(9));
        assertEquals("00000004|sales", employee.get(10));
        assertEquals("00000001|public relations", employee.get(11));
        assertEquals("00000002|sales", employee.get(12));
        assertEquals("00000003|finance", employee.get(13));
        assertEquals("00000004|sales", employee.get(14));
        assertEquals("00000004|sales", employee.get(15));
        assertEquals("00000001|public relations", employee.get(16));
        assertEquals("00000002|sales", employee.get(17));
        assertEquals("00000003|finance", employee.get(18));
        assertEquals("00000004|sales", employee.get(19));
        assertEquals("00000004|sales", employee.get(20));
        assertEquals("00000001|public relations", employee.get(21));


        assertEquals("Check if user defined columns from address record + user defined cascade columns cascaded from employee are exported",
                // user defined output columns and their attributes from address record
                "address-type|line1|state|zip" +
                        // user defined cascade columns and their attributes cascaded from employee record
                        "|employee.employee-no|employee.department", address.get(0));
        assertEquals("primary|1 Tudor & Place|NY, US|12345|00000001|public relations", address.get(1));
        assertEquals("primary|1 Bloomington St|DC, US|22344|00000002|sales", address.get(2));
        assertEquals("holiday|19 Wilmington View|NC, US|27617|00000002|sales", address.get(3));
        assertEquals("primary|36 Washinton Ave.|CO, US|22987|00000003|finance", address.get(4));
        assertEquals("primary|23 Lead Mine Rd.|NC, US|26516|00000004|sales", address.get(5));
        assertEquals("primary|1 Tudor & Place|NY, US|12345|00000001|public relations", address.get(6));
        assertEquals("primary|1 Bloomington St|DC, US|22344|00000002|sales", address.get(7));
        assertEquals("holiday|19 Wilmington View|NC, US|27617|00000002|sales", address.get(8));
        assertEquals("primary|36 Washinton Ave.|CO, US|22987|00000003|finance", address.get(9));
        assertEquals("primary|23 Lead Mine Rd.|NC, US|26516|00000004|sales", address.get(10));
        assertEquals("primary|1 Tudor & Place|NY, US|12345|00000001|public relations", address.get(11));
        assertEquals("primary|1 Bloomington St|DC, US|22344|00000002|sales", address.get(12));
        assertEquals("holiday|19 Wilmington View|NC, US|27617|00000002|sales", address.get(13));
        assertEquals("primary|36 Washinton Ave.|CO, US|22987|00000003|finance", address.get(14));
        assertEquals("primary|23 Lead Mine Rd.|NC, US|26516|00000004|sales", address.get(15));
        assertEquals("primary|1 Tudor & Place|NY, US|12345|00000001|public relations", address.get(16));
        assertEquals("primary|1 Bloomington St|DC, US|22344|00000002|sales", address.get(17));
        assertEquals("holiday|19 Wilmington View|NC, US|27617|00000002|sales", address.get(18));
        assertEquals("primary|36 Washinton Ave.|CO, US|22987|00000003|finance", address.get(19));
        assertEquals("primary|23 Lead Mine Rd.|NC, US|26516|00000004|sales", address.get(20));
        assertEquals("primary|1 Tudor & Place|NY, US|12345|00000001|public relations", address.get(21));


        assertEquals("Check if user defined columns in phone record + user defined cascade columns cascaded from employee are exported",
                // user defined output columns from phone record
                "phone-num|phone-type" +
                        // user defined cascade columns and their attributes cascaded from employee record (parent)
                        "|employee.employee-no|employee.department", phone.get(0));
        assertEquals("1234567890|landline|00000001|public relations", phone.get(1));
        assertEquals("7279237008|cell|00000002|sales", phone.get(2));
        assertEquals("9090909090|office|00000002|sales", phone.get(3));
        assertEquals("1234567890|landline|00000001|public relations", phone.get(4));
        assertEquals("7279237008|cell|00000002|sales", phone.get(5));
        assertEquals("9090909090|office|00000002|sales", phone.get(6));
        assertEquals("1234567890|landline|00000001|public relations", phone.get(7));
        assertEquals("7279237008|cell|00000002|sales", phone.get(8));
        assertEquals("9090909090|office|00000002|sales", phone.get(9));
        assertEquals("1234567890|landline|00000001|public relations", phone.get(10));
        assertEquals("7279237008|cell|00000002|sales", phone.get(11));
        assertEquals("9090909090|office|00000002|sales", phone.get(12));
        assertEquals("1234567890|landline|00000001|public relations", phone.get(13));


        assertEquals("Check if user defined columns in reroute appended + user defined columns cascaded from address & employee.",
                // user defined output columns from reroute record
                "employee-name|line1|state|zip" +
                        // all user defined cascade columns and their attributes cascaded from address record (parent)
                        "|address.address-type|address.line1|address.state|address.zip" +
                        // all user defined cascade columns and their attributes cascaded from employee record (parent of address)
                        "|employee.employee-no|employee.department",
                reroute.get(0));
        assertEquals("Nick Fury|541E~              Summer St.|NY, US|92478|primary|1 Tudor & Place|NY, US|12345|00000001|public relations",
                reroute.get(1));

    }


    private void compareOutFiles(String file1, String file2) throws IOException {
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

    private List<String> fileLines(String filePath) throws FileNotFoundException {
        return new BufferedReader(new FileReader(new File(filePath)))
                .lines().collect(Collectors.toList());
    }

    private void checkColCount(Iterable<String> lines) {
        int i = 0, fieldCount = 0;
        for (String line: lines) {
            if (i == 0)
                fieldCount = line.split("\\|").length;
            assertEquals("Check line# " + i++ + ": " + line,
                    fieldCount, line.split("\\|", -1).length);
        }
    }

}
