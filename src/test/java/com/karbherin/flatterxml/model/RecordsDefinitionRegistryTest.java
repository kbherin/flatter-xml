package com.karbherin.flatterxml.model;

import static org.junit.Assert.*;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.xml.namespace.QName;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;

public class RecordsDefinitionRegistryTest {

    private static Map<String, String> uriPrefixMap = new HashMap<>();
    private static Map<String, String> prefixUriMap = new HashMap<>();

    @BeforeClass
    public static void setup() {
        uriPrefixMap.put("http://kbps.com/emp", "emp");
        uriPrefixMap.put("http://www.w3.org/2001/XMLSchema", "xs");
        uriPrefixMap.put("http://www.w3.org/2001/XMLSchema-instance", "xsi");
        prefixUriMap.put("emp", "http://kbps.com/emp");
        prefixUriMap.put("xs", "http://www.w3.org/2001/XMLSchema");
        prefixUriMap.put("xsi", "http://www.w3.org/2001/XMLSchema-instance");
    }

    @Test
    public void testParseTagAddPrefix_BareTag() {
        QName qName = RecordsDefinitionRegistry.parseTagAddPrefix("employee", uriPrefixMap, prefixUriMap);
        assertEquals("", qName.getNamespaceURI());
        assertEquals("employee", qName.getLocalPart());
    }

    @Test
    public void testParseTagAddPrefix_UriTag() {
        QName qName = RecordsDefinitionRegistry.parseTagAddPrefix("{http://kbps.com/emp}employee",
                uriPrefixMap, prefixUriMap);
        assertEquals("http://kbps.com/emp", qName.getNamespaceURI());
        assertEquals("employee", qName.getLocalPart());
        assertEquals("emp", qName.getPrefix());
    }

    @Test
    public void testNewInstance() throws IOException {
        RecordsDefinitionRegistry recSpec = RecordsDefinitionRegistry.newInstance(
                new File("src/test/resources/emp_output_fields.yaml"));

        // Config line: emp=http://kbps.com/emp
        // Config line: xsi=http://www.w3.org/2001/XMLSchema-instance
        assertEquals("emp", recSpec.getPrefix("http://kbps.com/emp"));
        assertEquals("http://kbps.com/emp", recSpec.getNamespaceUri("emp"));
        assertTrue(recSpec.getNamespaces().contains("http://kbps.com/emp"));
        assertTrue(recSpec.getNamespaces().contains("http://www.w3.org/2001/XMLSchema-instance"));
        assertTrue(recSpec.getPrefixes().contains("emp"));
        assertTrue(recSpec.getPrefixes().contains("xsi"));

        assertFalse("Commented prefix definition", recSpec.getPrefixes().contains("phone"));
        assertFalse("Commented prefix definition", recSpec.getNamespaces().contains("http://kbps.com/phone"));


        // Config line: emp:employee=employee-no,employee-name,{http://kbps.com/emp}department
        assertTrue(recSpec.getRecords().contains(QName.valueOf("{http://kbps.com/emp}employee")));
        Collection<QName> empFields = recSpec.getRecordFields(QName.valueOf("{http://kbps.com/emp}employee"));
        assertTrue(empFields.contains(QName.valueOf("employee-no")));
        assertFalse(empFields.contains(QName.valueOf("employee-name")));
        assertTrue(empFields.contains(QName.valueOf("{http://kbps.com/emp}department")));
        assertFalse(empFields.contains(QName.valueOf("salary")));

        // Config line: {http://kbps.com/emp}address=address-type,line1,emp:state,zip
        assertTrue(recSpec.getRecords().contains(QName.valueOf("{http://kbps.com/emp}address")));
        Collection<QName> addressFields = recSpec.getRecordFields(QName.valueOf("{http://kbps.com/emp}address"));
        assertTrue(addressFields.contains(QName.valueOf("address-type")));
        assertTrue(addressFields.contains(QName.valueOf("line1")));
        assertTrue(addressFields.contains(QName.valueOf("{http://kbps.com/emp}state")));
        assertFalse(addressFields.contains(QName.valueOf("line2")));


        assertFalse("Commented out record definition",
                recSpec.getRecords().contains(QName.valueOf("{http://kbps.com/phone}phone")));
    }
}
