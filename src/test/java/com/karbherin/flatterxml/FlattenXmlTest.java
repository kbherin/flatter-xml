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
import static com.karbherin.flatterxml.model.RecordFieldsCascade.CascadePolicy;
import static org.junit.Assert.assertEquals;

public class FlattenXmlTest {

    @Test
    public void test1() throws IOException, XMLStreamException {
        String outDir = "target/test/results/emp_fulldump";
        Files.createDirectories(Paths.get("target/test/results/emp_fulldump"));
        RecordHandler recordHandler = new DelimitedFileWriter(
                "|", outDir,
                false, new StatusReporter());

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
        assertEquals(5, phone.size());

        assertEquals(7, employee.size());
        assertEquals("emp:identifiers|emp:identifiers[emp:id-doc-type]|emp:identifiers[emp:id-doc-expiry]|emp:employee-no|emp:employee-no[emp:status]|emp:employee-name|emp:department|emp:salary",
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
        assertEquals("emp:address-type|emp:line1|emp:line2|emp:state|emp:zip" +
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
        assertEquals("Reroute header with cascading. No line2",
                "emp:employee-name|emp:line1|emp:state|emp:zip" +
                "|emp:address.emp:address-type|emp:address.emp:line1|emp:address.emp:state|emp:address.emp:zip" +
                "|emp:employee.emp:identifiers|emp:employee.emp:identifiers[emp:id-doc-type]|emp:employee.emp:identifiers[emp:id-doc-expiry]|emp:employee.emp:employee-no|emp:employee.emp:employee-no[emp:status]|emp:employee.emp:employee-name|emp:employee.emp:department|emp:employee.emp:salary",
                reroute.get(0));
        assertEquals("Reroute data. No line2", "Nick Fury|541E Summer St.|NY, US|92478" +
                        "|primary|1 Tudor & Place|NY, US|12345" +
                        "|1234567890|SSN|1945-05-25|00000001|active|Steve Rogers|public relations|150,000.00",
                reroute.get(1));

        // Deeply nested with attributes
        assertEquals("ph:phone-num|ph:phone-num[ph:contact-type]|ph:phone-type" +
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
    }

    public List<String> fileLines(String filePath) throws FileNotFoundException {
        return new BufferedReader(new FileReader(new File(filePath)))
                .lines().collect(Collectors.toList());
    }

}
