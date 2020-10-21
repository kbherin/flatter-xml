package com.karbherin.flatterxml.helper;

import com.karbherin.flatterxml.model.Pair;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.util.Collections.*;
import static java.util.Comparator.comparing;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;

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
                Map<String, Integer> nextTokens = successors.get(precede);

                nextTokens.put(successor, counts.get(i) + nextTokens.getOrDefault(successor, 0));
                precede = successor;
            }
        }

        Map<String, List<String>> successors2 = successors.entrySet().stream()
                .collect(Collectors.toMap(
                        e -> e.getKey(),
                        e -> {
                            Set<String> exclude = new HashSet<>();
                            List<String> list = e.getValue().entrySet().stream()
                                    .sorted(reverseOrder(comparing(Map.Entry::getValue)))
                                    .map(ent -> {
                                        exclude.addAll(ofNullable(successors.get(ent.getKey()))
                                                .orElse(emptyMap())
                                                .keySet());
                                        return ent.getKey();
                                    })
                                    .filter(col -> !exclude.contains(col))
                                    .collect(toList());
                            Collections.reverse(list);
                            return list;
                        }
                ));
        Deque<String> postOrder = new ArrayDeque<>();
        dfsSuccessors(successors2, postOrder, collapsed, null);

        for (int i = 0; !postOrder.isEmpty(); i++) {
            collapsed.set(i, postOrder.pop());
        }
        return collapsed;
    }

    private static void dfsSuccessors(Map<String, List<String>> successors,
                                      Deque<String> postOrder,
                                      List<String> marked,
                                      String startWith) {

        successors.getOrDefault(startWith, emptyList())
                .stream()
                .filter(col -> marked.indexOf(col) < 0)
                .forEach(nextCol -> {
                    marked.add(nextCol);
                    dfsSuccessors(successors, postOrder, marked, nextCol);
                    postOrder.push(nextCol);
                });
    }

    public static <T, N extends Number> N pathExists(Map<T, Map<T, N>> graph, T src, T tgt, N zeroWeight) {
        Map<T, N> successors = graph.get(src);

        if (successors != null) {
            for (T successor : successors.keySet()) {
                if (tgt.equals(successor) ||
                        !pathExists(graph, successor, tgt, zeroWeight).equals(zeroWeight)) {

                    return successors.get(successor);
                }
            }
        }

        return zeroWeight;
    }
}
