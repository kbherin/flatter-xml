package com.karbherin.flatterxml.model;

import static com.karbherin.flatterxml.model.RecordDefinitions.newInstance;
import static com.karbherin.flatterxml.model.RecordDefinitions.parseNameAddPrefix;
import static org.junit.Assert.*;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.xml.namespace.QName;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class RecordDefinitionsTest {

    private static Map<String, String> uriPrefixMap = new HashMap<>();
    private static Map<String, String> prefixUriMap = new HashMap<>();

    @BeforeClass
    public static void setup() {
        uriPrefixMap.put("http://kbps.com/emp", "emp");
        uriPrefixMap.put("http://kbps.com/phone", "ph");
        uriPrefixMap.put("http://www.w3.org/2001/XMLSchema", "xs");
        uriPrefixMap.put("http://www.w3.org/2001/XMLSchema-instance", "xsi");
        prefixUriMap.put("emp", "http://kbps.com/emp");
        prefixUriMap.put("ph", "http://kbps.com/phone");
        prefixUriMap.put("xs", "http://www.w3.org/2001/XMLSchema");
        prefixUriMap.put("xsi", "http://www.w3.org/2001/XMLSchema-instance");
    }

    @Test
    public void testParseTagAddPrefix_BareTag() {
        QName qName = parseNameAddPrefix("employee", uriPrefixMap, prefixUriMap);
        assertEquals("", qName.getNamespaceURI());
        assertEquals("employee", qName.getLocalPart());
    }

    @Test
    public void testParseTagAddPrefix_UriTag() {
        QName qName = parseNameAddPrefix("{http://kbps.com/emp}employee",
                uriPrefixMap, prefixUriMap);
        assertEquals("http://kbps.com/emp", qName.getNamespaceURI());
        assertEquals("employee", qName.getLocalPart());
        assertEquals("emp", qName.getPrefix());
    }

    @Test
    public void testNewInstance() throws IOException {
        RecordDefinitions recSpec = newInstance(
                new File("src/test/resources/empns_output_fields.yaml"));

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
        Collection<QName> empFields = recSpec.getRecordFieldNames(QName.valueOf("{http://kbps.com/emp}employee"));
        assertEquals(2, empFields.size());
        assertTrue(empFields.contains(QName.valueOf("{http://kbps.com/emp}employee-no")));
        assertTrue(empFields.contains(QName.valueOf("{http://kbps.com/emp}department")));

        // Config line: {http://kbps.com/emp}address=address-type,line1,emp:state,zip
        assertTrue(recSpec.getRecords().contains(QName.valueOf("{http://kbps.com/emp}address")));
        Collection<QName> addressFields = recSpec.getRecordFieldNames(QName.valueOf("{http://kbps.com/emp}address"));
        assertEquals(4, addressFields.size());
        assertTrue(addressFields.contains(QName.valueOf("{http://kbps.com/emp}address-type")));
        assertTrue(addressFields.contains(QName.valueOf("{http://kbps.com/emp}line1")));
        assertTrue(addressFields.contains(QName.valueOf("{http://kbps.com/emp}state")));
        assertFalse(addressFields.contains(QName.valueOf("{http://kbps.com/emp}line2")));


        assertFalse("Commented out record definition",
                recSpec.getRecords().contains(QName.valueOf("{http://kbps.com/phone}phone")));
    }

    @Test
    public void testNewInstance_Namespaced() throws IOException {
        RecordDefinitions outputSpec = newInstance(
                new File("src/test/resources/empns_output_fields_attrs.yaml"));

        assertEquals(3, outputSpec.getNamespaces().size());

        assertEquals(3, outputSpec.getRecords().size());

        assertTrue(outputSpec.getRecords().contains(
                parseNameAddPrefix("emp:employee", uriPrefixMap, prefixUriMap)));

        assertTrue(outputSpec.getRecords().contains(
                parseNameAddPrefix("{http://kbps.com/emp}address", uriPrefixMap, prefixUriMap)));

        assertTrue(outputSpec.getRecords().contains(
                parseNameAddPrefix("{http://kbps.com/phone}phone", uriPrefixMap, prefixUriMap)));

        assertEquals("NS prefix qualified employee record tag", 3, outputSpec.getRecordFieldNames(
                parseNameAddPrefix("emp:employee", uriPrefixMap, prefixUriMap)).size());

        assertEquals("NS URI qualified phone record tag",2, outputSpec.getRecordFieldNames(
                parseNameAddPrefix("{http://kbps.com/phone}phone", uriPrefixMap, prefixUriMap)).size());

        assertEquals("NS qualified attribute", 1, outputSpec.getRecordFieldAttributes(
                parseNameAddPrefix("{http://kbps.com/phone}phone", uriPrefixMap, prefixUriMap),
                parseNameAddPrefix("{http://kbps.com/phone}phone-num", uriPrefixMap, prefixUriMap)).size());

        assertEquals("Not NS qualified attribute", 0, outputSpec.getRecordFieldAttributes(
                parseNameAddPrefix("{http://kbps.com/phone}phone", uriPrefixMap, prefixUriMap),
                parseNameAddPrefix("phone-type", uriPrefixMap, prefixUriMap)).size());

        assertEquals(2, outputSpec.getRecordFieldAttributes(
                parseNameAddPrefix("emp:employee", uriPrefixMap, prefixUriMap),
                parseNameAddPrefix("emp:identifiers", uriPrefixMap, prefixUriMap)).size());

        assertTrue("NS qualified attribute", outputSpec.getRecordFieldAttributes(
                parseNameAddPrefix("emp:employee", uriPrefixMap, prefixUriMap),
                parseNameAddPrefix("emp:identifiers", uriPrefixMap, prefixUriMap))
                .contains(parseNameAddPrefix("{http://kbps.com/emp}id-doc-type", uriPrefixMap, prefixUriMap)));

        assertTrue("Not NS qualified attribute", outputSpec.getRecordFieldAttributes(
                parseNameAddPrefix("emp:employee", uriPrefixMap, prefixUriMap),
                parseNameAddPrefix("emp:identifiers", uriPrefixMap, prefixUriMap))
                .contains(parseNameAddPrefix("emp:id-doc-expiry", uriPrefixMap, prefixUriMap)));
    }
}
