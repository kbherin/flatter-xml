package com.karbherin.flatterxml;

import com.karbherin.flatterxml.xsd.XmlSchema;
import com.karbherin.flatterxml.xsd.XsdElement;

import javax.xml.namespace.QName;
import javax.xml.stream.events.StartElement;
import java.util.*;
import java.util.stream.Stream;

public final class RecordFieldsCascade {
    private final QName recordName;
    private final List<XmlHelpers.FieldValue<QName, String>> cascadeFieldValueList = new ArrayList<>();
    private final List<XmlHelpers.FieldValue<String, String>> parentFieldValueList = new ArrayList<>();
    private final Map<QName, Integer> positions;

    public enum CascadePolicy {NONE, ALL, XSD};

    public RecordFieldsCascade(StartElement recordName, String[] primaryTags, List<XmlSchema> xsds) {
        this.recordName = recordName.getName();
        if (primaryTags == null) {
            positions = setupCascadeFields(new ArrayList<QName>(), xsds);
        } else {
            List<QName> qtags = new ArrayList<>();
            for (String tag: primaryTags) {
                qtags.add(XmlHelpers.parsePrefixTag(tag, recordName.getNamespaceContext(),
                        recordName.getName().getNamespaceURI()));
            }
            positions = setupCascadeFields(qtags, xsds);
        }
    }

    public RecordFieldsCascade(QName recordName, List<QName> primaryTags, List<XmlSchema> xsds) {
        this.recordName = recordName;
        positions = setupCascadeFields(primaryTags, xsds);
    }

    public void addCascadingData(QName tagName, String tagValue, CascadePolicy policy) {
        Integer pos = positions.get(tagName);
        if (pos != null) {
            // Capture the data value at the designated location
            cascadeFieldValueList.get(pos).value = tagValue;
        } else if (policy == CascadePolicy.ALL) {
            // Append the tag-value pair only if policy is cascade ALL
            cascadeFieldValueList.add(new XmlHelpers.FieldValue<>(tagName, tagValue));
        }
    }

    /**
     * Method to initially setup cascading fields that are provided by user.
     * These will act as a template for actual field and value cascades.
     * Fields to cascade are specified by client or by XSD. Make the list unmodifiable.
     * @param cascadeFields
     * @return
     */
    private Map<QName, Integer> setupCascadeFields(List<QName> cascadeFields, List<XmlSchema> xsds) {
        if (cascadeFields == null)
            return Collections.emptyMap();

        Map<QName, Integer> primaryTagList = new HashMap<>();
        final int[] mutablePos = new int[]{0};

        // Find the element in all the XSDs
        XsdElement schemaRec = xsds.stream()
                .map(xsd -> xsd.getElementByName(recordName))
                .filter(el -> el != null)
                .findFirst().orElse(null);

        List<QName> schemaRecFieldsList = Collections.emptyList();
        Stream<QName> schemaRecFields = schemaRecFieldsList.stream();
        if (schemaRec != null) {
            schemaRecFields = (schemaRec.getChildElements()).stream()
                    .filter(field -> field.isRequired() && !field.getType().equals(XmlSchema.COMPLEX_TYPE))
                    .map(field -> field.getName());
        }

        Stream.concat(cascadeFields.stream(), schemaRecFields)
                .filter(tag -> !primaryTagList.containsKey(tag))
                .forEach(tag -> {
                    primaryTagList.put(tag, mutablePos[0]++);
                    cascadeFieldValueList.add(new XmlHelpers.FieldValue<>(tag, XmlHelpers.EMPTY));
                });

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
            for (XmlHelpers.FieldValue<QName, String> fv: parent.cascadeFieldValueList) {
                parentFieldValueList.add(new XmlHelpers.FieldValue<>(
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
        for (int i = 0; i < cascadeFieldValueList.size(); i++) {
            cascadeFieldValueList.get(i).value = XmlHelpers.EMPTY;
        }
        return this;
    }

    public Iterable<String> getParentCascadedNames() {
        List<String> names = new ArrayList<>(parentFieldValueList.size());
        for (XmlHelpers.FieldValue<String, String> fv: parentFieldValueList) {
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
        for (XmlHelpers.FieldValue<String, String> fv: parentFieldValueList) {
            parentCascadedValuesCache.add(fv.value);
        }
        return parentCascadedValuesCache;
    }

    public Iterable<XmlHelpers.FieldValue<QName, String>> getCascadeFieldValueList() {
        return cascadeFieldValueList;
    }

    public QName getRecordName() {
        return recordName;
    }

}
