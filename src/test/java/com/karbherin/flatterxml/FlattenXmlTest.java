package com.karbherin.flatterxml;

import com.karbherin.flatterxml.output.DelimitedFileWriter;
import com.karbherin.flatterxml.output.StatusReporter;
import org.junit.Test;

import javax.xml.stream.XMLStreamException;
import java.io.*;
import java.util.List;
import java.util.stream.Collectors;

import static com.karbherin.flatterxml.FlattenXml.FlattenXmlBuilder;
import static com.karbherin.flatterxml.model.RecordFieldsCascade.CascadePolicy;
import static org.junit.Assert.assertEquals;

public class FlattenXmlTest {

    @Test
    public void test1() throws IOException, XMLStreamException {
        FlattenXml flattener = new FlattenXmlBuilder()
                .setCascadePolicy(CascadePolicy.ALL)
                .setRecordWriter(new DelimitedFileWriter("|", "target/test/results/emp_fulldump",
                        false, new StatusReporter()))
                .setXmlStream(new FileInputStream(
                        new File("src/test/resources/emp_ns.xml")))
                .create();

        assertEquals(3, flattener.parseFlatten());

        List<String> employee = fileLines("target/test/results/emp_fulldump/employee.csv");
        List<String> contact = fileLines("target/test/results/emp_fulldump/contact.csv");
        List<String> addresses = fileLines("target/test/results/emp_fulldump/addresses.csv");
        List<String> address = fileLines("target/test/results/emp_fulldump/address.csv");
        List<String> phones = fileLines("target/test/results/emp_fulldump/phones.csv");
        List<String> phone = fileLines("target/test/results/emp_fulldump/phone.csv");
        List<String> reroute = fileLines("target/test/results/emp_fulldump/reroute.csv");

        assertEquals(0, addresses.size());
        assertEquals(0, contact.size());
        assertEquals(0, phones.size());

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
    }

    public List<String> fileLines(String filePath) throws FileNotFoundException {
        return new BufferedReader(new FileReader(new File(filePath)))
                .lines().collect(Collectors.toList());
    }

}
