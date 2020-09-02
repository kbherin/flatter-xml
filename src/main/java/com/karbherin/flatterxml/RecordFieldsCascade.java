package com.karbherin.flatterxml;

import javax.xml.namespace.QName;
import java.util.*;

public final class RecordFieldsCascade {
    private final QName recordName;
    private final List<FieldValue<QName>> cascadeFieldValueList = new ArrayList<>();
    private final List<FieldValue<String>> parentFieldValueList = new ArrayList<>();
    private final Map<QName, Integer> positions;

    public static enum CascadePolicy {NONE, ALL};

    private static final String PREFIX_SEP = ":";

    public RecordFieldsCascade(QName recordName, String[] primaryTags) {
        this.recordName = recordName;
        if (primaryTags == null) {
            positions = setupCascadeFields(new ArrayList<QName>());
        } else {
            List<QName> qtags = new ArrayList<>();
            for (String tag: primaryTags) {
                qtags.add(XmlHelpers.parsePrefixTag(tag));
            }
            positions = setupCascadeFields(qtags);
        }
    }

    public RecordFieldsCascade(QName recordName, Iterable<QName> primaryTags) {
        this.recordName = recordName;
        positions = setupCascadeFields(primaryTags);
    }

    public void addCascadingData(QName tagName, String tagValue, CascadePolicy policy) {
        Integer pos = positions.get(tagName);
        if (pos != null) {
            // Capture the data value at the designated location
            cascadeFieldValueList.set(pos, new FieldValue(tagName,
                    cascadeFieldValueList.get(pos).value + tagValue));
        } else if (policy == CascadePolicy.ALL) {
            // Append the tag-value pair only if policy is cascade ALL
            int last = cascadeFieldValueList.size() - 1;
            if (last >= 0 && cascadeFieldValueList.get(last).field.equals(tagName)) {
                cascadeFieldValueList.set(last, new FieldValue(tagName, cascadeFieldValueList.get(last).value + tagValue));
            } else {
                cascadeFieldValueList.add(new FieldValue<QName>(tagName, tagValue));
            }
        }
    }

    /**
     * Method to initially setup cascading fields that are provided by user.
     * These will act as a template for actual field and value cascades.
     * @param cascadeFields
     * @return
     */
    private Map<QName, Integer> setupCascadeFields(Iterable<QName> cascadeFields) {
        if (cascadeFields == null) {
            return Collections.emptyMap();
        }

        Map<QName, Integer> primaryTagList = new HashMap<>();
        int pos = 0;
        // Fields to cascade are specified by client or by XSD. Make the list unmodifiable
        for (QName tag: cascadeFields) {
            if (!primaryTagList.containsKey(tag)) {
                primaryTagList.put(tag, pos++);
                // Reserve initial slots in the lists for tags to cascade from current record
                cascadeFieldValueList.add(new FieldValue<QName>(tag, ""));
            }
        }

        return Collections.unmodifiableMap(primaryTagList);
    }

    /**
     * Cascades fields and their values from ancestral containers into current record.
     * @param parent
     * @return
     */
    public RecordFieldsCascade cascadeFromParent(RecordFieldsCascade parent) {
        if (parent != null) {
            // Current record fields are formatted as RecordName.RecordField
            for (FieldValue<QName> fv: parent.cascadeFieldValueList) {
                parentFieldValueList.add(new FieldValue<String>(
                        String.format("%s.%s", parent.recordName.getLocalPart(),
                                XmlHelpers.toPrefixedTag(fv.field)), fv.value));
            }

            // Parent record fields were already formatted as ParentRecordName.ParentRecordField
            parentFieldValueList.addAll(parent.parentFieldValueList);
        }
        return this;
    }

    /**
     * Clear only the current record cascades and retain parent record's cascades.
     * @return
     */
    public RecordFieldsCascade clearCurrentRecordCascades() {
        cascadeFieldValueList.clear();
        return this;
    }

    public Iterable<String> getParentCascadedNames() {
        List<String> names = new ArrayList<>(parentFieldValueList.size());
        for (FieldValue<String> fv: parentFieldValueList) {
            names.add(fv.field);
        }
        return names;
    }


    private List<String> parentCascadedValuesCache;
    public Iterable<String> getParentCascadedValues() {
        if (parentCascadedValuesCache != null) {
            return parentCascadedValuesCache;
        }

        parentCascadedValuesCache = new ArrayList<>(parentFieldValueList.size());
        for (FieldValue<String> fv: parentFieldValueList) {
            parentCascadedValuesCache.add(fv.value);
        }
        return parentCascadedValuesCache;
    }

    public Iterable<FieldValue<QName>> getCascadeFieldValueList() {
        return cascadeFieldValueList;
    }

    public QName getRecordName() {
        return recordName;
    }

    private static class FieldValue<K> {
        final K field;
        final String value;

        FieldValue(K fld, String val) {
            field = fld;
            value = val;
        }
    }
}
