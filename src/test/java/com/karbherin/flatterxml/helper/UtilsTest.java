package com.karbherin.flatterxml.helper;

import org.junit.Test;

import java.util.*;

import static com.karbherin.flatterxml.helper.Utils.collapseSequences;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class UtilsTest {

    @Test
    public void testCollapseSequences1() {

        List<String[]> seqs = new ArrayList<>();
        List<Integer> counts = new ArrayList<>();
        seqs.add(new String[] {"col1", "col2", "col4"});
        counts.add(3);
        seqs.add(new String[] {"col1", "col2", "col3"});
        counts.add(2);
        seqs.add(new String[] {"col1", "col2", "col5"});
        counts.add(2);
        assertEquals("After col2, col4 is most frequent and, col3 and col5 are equally likely",
                "col1,col2,col4,col3,col5", String.join(",", collapseSequences(seqs, counts)));

        seqs = new ArrayList<>();
        counts = new ArrayList<>();
        seqs.add(new String[] {"col1", "col2", "col4"});
        counts.add(3);
        seqs.add(new String[] {"col1", "col2", "col5"});
        counts.add(2);
        seqs.add(new String[] {"col1", "col2", "col3"});
        counts.add(2);
        assertEquals("Col3 and col5 swap places but continue as equally likely",
                "col1,col2,col4,col5,col3", String.join(",", collapseSequences(seqs, counts)));

        seqs.add(seqs.remove(1));
        assertEquals("col1,col2,col4,col3,col5", String.join(",", collapseSequences(seqs, counts)));
    }

    @Test
    public void testCollapseSequences2() {
        List<String[]> seqs = new ArrayList<>();
        List<Integer> counts = new ArrayList<>();
        seqs.add(new String[] {"col1", "col2", "col4"});
        counts.add(3);
        seqs.add(new String[] {"col1", "col2", "col3"});
        counts.add(2);
        seqs.add(new String[] {"col1", "col2", "col5"});
        counts.add(2);
        seqs.add(new String[] {"col1", "col2", "col5", "col4"});
        counts.add(2);
        assertEquals("When col5 is present it occurs before col4",
                "col1,col2,col5,col4,col3", String.join(",", collapseSequences(seqs, counts)));

        seqs = new ArrayList<>();
        counts = new ArrayList<>();
        seqs.add(new String[] {"col1", "col2", "col4"});
        counts.add(3);
        seqs.add(new String[] {"col1", "col2", "col3"});
        counts.add(2);
        seqs.add(new String[] {"col1", "col2", "col5"});
        counts.add(2);
        seqs.add(new String[] {"col1", "col2", "col5", "col4"});
        counts.add(1);
        assertEquals("When col5 is present it occurs before col4 even though col5 only ever appears once",
                "col1,col2,col5,col4,col3", String.join(",", collapseSequences(seqs, counts)));

        seqs = new ArrayList<>();
        counts = new ArrayList<>();
        seqs.add(new String[] {"col1", "col2", "col4"});
        counts.add(4);
        seqs.add(new String[] {"col1", "col2", "col3"});
        counts.add(2);
        seqs.add(new String[] {"col1", "col2", "col5"});
        counts.add(2);
        seqs.add(new String[] {"col1", "col2", "col5", "col4"});
        counts.add(1);
        assertEquals("When col5 is present it occurs before col4 even if col4 follows col2 more frequently",
                "col1,col2,col5,col4,col3", String.join(",", collapseSequences(seqs, counts)));
    }

    @Test
    public void testCollapseSequences3() {
        List<String[]> seqs = new ArrayList<>();
        List<Integer> counts = new ArrayList<>();
        seqs.add(new String[] {"col1", "col2", "col3", "col4"});
        counts.add(3);
        seqs.add(new String[] {"col1", "col2", "col4"});
        counts.add(2);
        seqs.add(new String[] {"col1", "col2", "col5"});
        counts.add(2);
        seqs.add(new String[] {"col1", "col2", "col4", "col5"});
        counts.add(2);
        seqs.add(new String[] {"col1", "col2", "col3", "col4", "col5"});
        counts.add(3);
        assertEquals("col1,col2,col3,col4,col5", String.join(",", collapseSequences(seqs, counts)));
    }

    @Test
    public void testCollapseSequences4() {
        List<String[]> seqs = new ArrayList<>();
        List<Integer> counts = new ArrayList<>();
        seqs.add(new String[] {"col1", "col2", "col3", "col4"});
        counts.add(2);
        seqs.add(new String[] {"col1", "col2", "col5", "col4"});
        counts.add(2);
        seqs.add(new String[] {"col1", "col2", "col4", "col5"});
        counts.add(2);
        assertEquals("Testing against cycles in the graph",
                "col1,col2,col3,col5,col4", String.join(",", collapseSequences(seqs, counts)));

        // Flip order of last two sequences
        seqs.add(seqs.remove(1));
        assertEquals("Testing against cycles in the graph but elements reversed",
                "col1,col2,col3,col4,col5", String.join(",", collapseSequences(seqs, counts)));
    }

    @Test
    public void testCollapseSequences5() {
        List<String[]> seqs = new ArrayList<>();
        List<Integer> counts = new ArrayList<>();
        seqs.add(new String[] {"emp:identifiers","emp:identifiers[emp:id-doc-type]","emp:identifiers[emp:id-doc-expiry]","emp:employee-no","emp:employee-no[emp:status]","emp:employee-name","emp:department","emp:salary"});
        seqs.add(new String[] {"emp:identifiers","emp:identifiers[emp:id-doc-type]","emp:employee-no","emp:employee-no[emp:status]","emp:employee-name","emp:department","emp:salary"});
        counts.add(1);
        counts.add(1);
        assertEquals(
                "emp:identifiers,emp:identifiers[emp:id-doc-type],emp:identifiers[emp:id-doc-expiry],emp:employee-no,emp:employee-no[emp:status],emp:employee-name,emp:department,emp:salary",
                String.join(",", collapseSequences(seqs, counts)));
    }

    @Test
    public void testCollapseSequences6() {
        List<String[]> seqs = new ArrayList<>();
        List<Integer> counts = new ArrayList<>();
        seqs.add("emp:address-type|emp:line1|emp:line2|emp:state|emp:zip|emp:employee.emp:identifiers|emp:employee.emp:identifiers[emp:id-doc-type]|emp:employee.emp:identifiers[emp:id-doc-expiry]|emp:employee.emp:employee-no|emp:employee.emp:employee-no[emp:status]|emp:employee.emp:employee-name|emp:employee.emp:department|emp:employee.emp:salary"
                .split("\\|"));
        seqs.add("emp:address-type|emp:line1|emp:state|emp:zip|emp:employee.emp:identifiers|emp:employee.emp:identifiers[emp:id-doc-type]|emp:employee.emp:identifiers[emp:id-doc-expiry]|emp:employee.emp:employee-no|emp:employee.emp:employee-no[emp:status]|emp:employee.emp:employee-name|emp:employee.emp:department|emp:employee.emp:salary"
                .split("\\|"));
        counts.add(2);
        counts.add(2);
        assertEquals("emp:address-type|emp:line1|emp:line2|emp:state|emp:zip" +
                        "|emp:employee.emp:identifiers|emp:employee.emp:identifiers[emp:id-doc-type]|emp:employee.emp:identifiers[emp:id-doc-expiry]|emp:employee.emp:employee-no|emp:employee.emp:employee-no[emp:status]|emp:employee.emp:employee-name|emp:employee.emp:department|emp:employee.emp:salary",
                String.join("|", collapseSequences(seqs, counts)));
    }
}
