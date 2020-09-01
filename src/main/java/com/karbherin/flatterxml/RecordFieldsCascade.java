package com.karbherin.flatterxml;

import java.util.*;

public final class RecordFieldsCascade {
    private final String recordName;
    private final List<FieldValue> cascadeFieldValueList = new ArrayList<>();
    private final List<FieldValue> parentFieldValueList = new ArrayList<>();
    private final Map<String, Integer> positions;

    public static enum CascadePolicy {NONE, ALL};

    private static final String PREFIX_SEP = ":";

    public RecordFieldsCascade(String recordName, String[] primaryTags) {
        this.recordName = recordName;
        if (primaryTags == null) {
            positions = setupCascadeFields(new ArrayList<String>());
        } else {
            positions = setupCascadeFields(Arrays.asList(primaryTags));
        }
    }

    public RecordFieldsCascade(String recordName, Iterable<String> primaryTags) {
        this.recordName = recordName;
        positions = setupCascadeFields(primaryTags);
    }

    public void addCascadingData(String tagName, String tagValue, CascadePolicy policy) {
        Integer pos = positions.get(tagName);
        if (pos != null) {
            // Capture the data value at the designated location
            cascadeFieldValueList.set(pos, new FieldValue(tagName,
                    cascadeFieldValueList.get(pos).value + tagValue));
        } else if (policy == CascadePolicy.ALL) {
            // Append the tag-value pair only if policy is cascade ALL
            int last = cascadeFieldValueList.size() - 1;
            if (last >= 0 && cascadeFieldValueList.get(last).field.equals(tagName)) {
                cascadeFieldValueList.add(new FieldValue(tagName, cascadeFieldValueList.get(last).value + tagValue));
            } else {
                cascadeFieldValueList.add(new FieldValue(tagName, tagValue));
            }
        }
    }

    /**
     * Method to initially setup cascading fields that are provided by user.
     * These will act as a template for actual field and value cascades.
     * @param cascadeFields
     * @return
     */
    private Map<String, Integer> setupCascadeFields(Iterable<String> cascadeFields) {
        if (cascadeFields == null) {
            return Collections.emptyMap();
        }

        Map<String, Integer> primaryTagList = new TreeMap<>();
        int pos = 0;
        // Fields to cascade are specified by client or by XSD. Make the list unmodifiable
        for (String tag: cascadeFields) {
            if (!primaryTagList.containsKey(tag)) {
                primaryTagList.put(tag, pos++);
                // Reserve initial slots in the lists for tags to cascade from current record
                cascadeFieldValueList.add(new FieldValue(tag, ""));
            }
        }

        return Collections.unmodifiableMap(primaryTagList);
    }

    public void cascadeFromParent(RecordFieldsCascade parent) {
        if (!parentFieldValueList.isEmpty()) {
            throw new IllegalStateException("Cannot repeat cascading data from parent record");
        }
        if (parent != null) {
            for (FieldValue fv: parent.cascadeFieldValueList) {
                parentFieldValueList.add(new FieldValue(
                        String.format("%s.%s", parent.recordName, fv.field), fv.value));
            }
            // parentFieldValueList.addAll(parent.cascadeFieldValueList);
            parentFieldValueList.addAll(parent.parentFieldValueList);
        }
    }

    public List<String> getParentCascadedNames() {
        List<String> names = new ArrayList<>(parentFieldValueList.size());
        for (FieldValue fv: parentFieldValueList) {
            names.add(fv.field);
        }
        return names;
    }

    public List<String> getParentCascadedValues() {
        List<String> values = new ArrayList<>(parentFieldValueList.size());
        for (FieldValue fv: parentFieldValueList) {
            values.add(fv.value);
        }
        return values;
    }

    public Iterable<FieldValue> getCascadeFieldValueList() {
        return cascadeFieldValueList;
    }

    private static class FieldValue {
        final String field;
        final String value;

        FieldValue(String fld, String val) {
            field = fld;
            value = val;
        }
    }
}
