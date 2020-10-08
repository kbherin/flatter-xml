package com.karbherin.flatterxml.helper;

import com.karbherin.flatterxml.model.Pair;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class Utils {
    public static<T> Stream<T> iteratorStream(Iterator<T> iterator) {
        return StreamSupport.stream(
                Spliterators.spliteratorUnknownSize(iterator, Spliterator.ORDERED), false);
    }

    public static boolean isEmpty(String str) {
        return str == null || str.trim().length() == 0;
    }

    public static <T> T defaultIfNull(T val, T defaultVal) {
        return val == null ? defaultVal : val;
    }

    public static String emptyIfNull(String str) {
        return defaultIfNull(str, XmlHelpers.EMPTY);
    }

    public static String defaultIfEmpty(String val, String defaultVal) {
        return val == null ? defaultVal : val;
    }

    public static int parseInt(String str, int defaultValue) {
        try {
            return Integer.valueOf(str);
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    public static List<String> collapseSequences(List<String[]> seqs, List<Integer> counts) {
        List<String> collapsed = new ArrayList<>();
        Map<String, Map<String, Integer>> successors = new HashMap<>();

        for (int i = 0; i < seqs.size(); i++) {
            String[] seq = seqs.get(i);
            String precede = null;
            for (String successor : seq) {
                successors.putIfAbsent(precede, new LinkedHashMap<>());
                Map<String, Integer> nextToken = successors.get(precede);

                nextToken.put(successor, counts.get(i) + nextToken.getOrDefault(successor, 0));
                precede = successor;
            }
        }

        navigateSuccessors(successors, null, collapsed);
        return collapsed;
    }

    private static void navigateSuccessors(Map<String, Map<String, Integer>> successors,
                                                   String startWith,
                                                   List<String> collapsed) {

        Map<String, Integer> successorsCount = successors.get(startWith);
        if (successorsCount == null)
            return;

        List<Map.Entry<String, Integer>> mostFreqSuccessors = new ArrayList<>(successorsCount.entrySet());
        Collections.sort(mostFreqSuccessors, (a, b) -> b.getValue() - a.getValue());

        for (Map.Entry<String, Integer> successor: mostFreqSuccessors) {
            int pos = collapsed.lastIndexOf(successor.getKey());
            if (pos < 0) {
                collapsed.add(successor.getKey());
            } else {
                Map<String, Integer> nextSuccessor = successors.get(successor.getKey());
                if (nextSuccessor == null ||
                        nextSuccessor.getOrDefault(startWith, 0) < successor.getValue()) {

                    collapsed.add(collapsed.remove(pos));
                }
            }

            if (pos < 0)
                navigateSuccessors(successors, successor.getKey(), collapsed);
        }
    }
}
