package com.karbherin.flatterxml.model;

import com.karbherin.flatterxml.helper.XmlHelpers;
import com.karbherin.flatterxml.xsd.XmlSchema;
import com.karbherin.flatterxml.xsd.XsdElement;

import javax.xml.namespace.QName;
import javax.xml.stream.events.StartElement;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class RecordFieldsCascade {
    private final QName recordName;
    private final List<Pair<QName, String>> cascadePairList = new ArrayList<>();
    private final Map<String, Integer> positions;
    private final RecordFieldsCascade parent;
    private List<Pair<String, String>> toCascadeToChild = null;

    public enum CascadePolicy {NONE, ALL, XSD};

    public RecordFieldsCascade(StartElement recordName, List<QName> cascadingFields,
                               RecordFieldsCascade parent, List<XmlSchema> xsds) {
        this.recordName = recordName.getName();
        if (cascadingFields == null) {
            positions = setupCascadeFields(new ArrayList<QName>(), xsds);
        } else {
            positions = setupCascadeFields(cascadingFields, xsds);
        }

        this.parent = parent == null ? this : parent;
        cascadeFromParent();
    }

    public void addCascadingData(QName tagName, String tagValue, CascadePolicy policy) {
        Integer pos = positions.get(tagName.getLocalPart());
        if (pos != null) {
            // Capture the data value at the designated location
            cascadePairList.get(pos).setVal(tagValue);
        } else if (policy == CascadePolicy.ALL) {
            positions.put(tagName.getLocalPart(), cascadePairList.size());
            // Append the tag-value pair only if policy is cascade ALL
            cascadePairList.add(new Pair<>(tagName, tagValue));
        }
    }

    /**
     * Method to initially setup cascading fields that are provided by user.
     * These will act as a template for actual field and value cascades.
     * Fields to cascade are specified by client or by XSD. Make the list unmodifiable.
     * @param cascadeFields
     * @return
     */
    private Map<String, Integer> setupCascadeFields(List<QName> cascadeFields, List<XmlSchema> xsds) {

        Map<String, Integer> primaryTagList = new HashMap<>();
        final int[] mutablePos = new int[]{0};

        // Is caller explicitly specifies a fields cascade file then that is given priority.
        if (!cascadeFields.isEmpty()) {
            cascadeFields.stream()
                    .filter(tag -> !primaryTagList.containsKey(tag))
                    .forEach(tag -> {
                        primaryTagList.put(tag.getLocalPart(), mutablePos[0]++);
                        cascadePairList.add(new Pair<>(tag, XmlHelpers.EMPTY));
                    });
            return primaryTagList;
        }

        // Cascade all mandatory fields in XSDs.
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

        schemaRecFields
                .filter(tag -> !primaryTagList.containsKey(tag.getLocalPart()))
                .forEach(tag -> {
                    primaryTagList.put(tag.getLocalPart(), mutablePos[0]++);
                    cascadePairList.add(new Pair<>(tag, XmlHelpers.EMPTY));
                });

        return primaryTagList;
    }

    /**
     * Cascades fields and their values from ancestral containers into current record.
     * @return
     */
    public void cascadeFromParent() {

        if (parent.toCascadeToChild != null) {
            return;
        }

        // Current record fields are formatted as RecordName.RecordField
        parent.toCascadeToChild = parent.getCascadeFieldValueList().stream()
                .map(fv -> new Pair<>(
                    String.format("%s.%s", XmlHelpers.toPrefixedTag(parent.recordName),
                        fv.getKey().getLocalPart()), fv.getVal()))
                .collect(Collectors.toList());

        // Parent record fields were already formatted as ParentRecordName.ParentRecordField
        parent.toCascadeToChild.addAll(parent.parent.toCascadeToChild);
    }

    /**
     * Clear only the current record cascades and retain parent record's cascades.
     * @return
     */
    public RecordFieldsCascade clearCurrentRecordCascades() {
        for (int i = 0; i < cascadePairList.size(); i++) {
            cascadePairList.get(i).setVal(XmlHelpers.EMPTY);
        }
        return this;
    }

    /**
     * Clear the list of fields and values to cascade to child.
     */
    public RecordFieldsCascade clearToCascadeToChildList() {
        this.toCascadeToChild = null;
        return this;
    }

    public List<Pair<QName, String>> getCascadeFieldValueList() {
        return cascadePairList;
    }

    public List<Pair<String, String>> getParentCascadedFieldValueList() {
        return parent.toCascadeToChild;
    }

    public QName getRecordName() {
        return recordName;
    }

}
