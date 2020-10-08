package com.karbherin.flatterxml.helper;

import org.junit.Test;

import java.util.*;

import static com.karbherin.flatterxml.helper.Utils.collapseSequences;
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

        seqs.add(seqs.remove(1));
        assertEquals("Testing against cycles in the graph but elements reversed",
                "col1,col2,col3,col4,col5", String.join(",", collapseSequences(seqs, counts)));
    }

    @Test
    public void testSplit() {
        String[] parts = "someval1|someval2##HEADER>#somekey1|somekey2".split("##HEADER>#");
        assertEquals("someval1|someval2", parts[0]);
        assertEquals("somekey1|somekey2", parts[1]);
    }
}
