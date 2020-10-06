package com.karbherin.flatterxml.model;

import org.yaml.snakeyaml.Yaml;

import static com.karbherin.flatterxml.helper.XmlHelpers.*;

import javax.xml.namespace.QName;
import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.Collections.unmodifiableList;
import static java.util.Collections.unmodifiableMap;

public final class RecordDefinitions {

    private final Map<QName, Record> recordFieldsMap;
    private final Map<String, String> prefixUriMap;
    private final Map<String, String> uriPrefixMap;

    private static RecordDefinitions EMPTY_REGISTRY = new RecordDefinitions(Collections.emptyMap());
    private static final String PREFIX_TAG_SEP = ":";

    /**
     * Parses lines that specify tags, either for specifying output fields or cascading fields.
     * A line can either specify a record(complex type) or a prefix used in the rest of the file.
     * Yaml format of record line: "complex-type-tag": ["simple-type-tag1", "simple-type-tag2", ...]
     * Yaml format of prefix line: "ns-prefix": "namespaceURI"
     * Format of complex-type-tag: "{namespaceURI}tag-name" | "ns-prefix:tag-name"
     * Format of simple-type-tag : simple name, prefix:tag-name, {namespaceURI}tag-name
     * @param yamlSpec - Map representation of configuration yaml file
     */
    private RecordDefinitions(Map<String, Object> yamlSpec) {

        Map<String, String> prefixUri = (Map<String, String>) Optional.ofNullable(
                        yamlSpec.get("namespaces")
        ).orElse(Collections.emptyMap());

        prefixUriMap = prefixUri;
        uriPrefixMap = prefixUriMap.entrySet().stream()
                .collect(Collectors.toMap(preUri -> preUri.getValue(), preUri -> preUri.getKey()));

        recordFieldsMap = unmodifiableMap(
                Optional.ofNullable((Map<String, List<Object>>) yamlSpec.get("records"))
                        .orElse(Collections.emptyMap())
                .entrySet().stream()
                .map(record -> {
                    QName recordName = parseNameAddPrefix(record.getKey(), uriPrefixMap, prefixUriMap);
                    return new Record(
                            record.getKey(),
                            record.getValue().stream().map(field -> {
                                if (field instanceof String) {

                                    QName fieldName = parseNameAddPrefix(record.getKey(), uriPrefixMap, prefixUriMap);
                                    String namespaceUri = defaultIfEmpty(
                                            fieldName.getNamespaceURI(), recordName.getNamespaceURI());

                                    return new Field(field.toString(), Collections.emptyList(), namespaceUri);

                                } else { //if (field instanceof Map)

                                    return ((Map<String, List<String>>) field).entrySet().stream()
                                            .findFirst()
                                            .map(entry -> {
                                                QName fieldName = parseNameAddPrefix(record.getKey(),
                                                        uriPrefixMap, prefixUriMap);
                                                String namespaceUri = defaultIfEmpty(
                                                        fieldName.getNamespaceURI(), recordName.getNamespaceURI());

                                                return new Field(entry.getKey(), entry.getValue(), namespaceUri);
                                            })
                                            .get();
                                }

                            }).collect(Collectors.toList()));

                }).collect(Collectors.toMap(rec -> rec.recordName, rec -> rec)));
    }

    /**
     * Derive a qualified name from a tag string. If it has a namespace URI in the form of {URI}tag,
     * then a prefix is added by looking up the provided URI-Prefix map.
     * @param nameString - tag string to parse
     * @param uriPrefixMap - map of URIs and their assigned prefixes
     * @param prefixUriMap - map of NS prefix to their URIs
     * @return
     */
    public static QName parseNameAddPrefix(String nameString, Map<String, String> uriPrefixMap,
                                           Map<String, String> prefixUriMap) {

        return parseNameAddPrefix(nameString, uriPrefixMap, prefixUriMap, EMPTY);
    }

    /**
     * Derive a qualified name from a tag string. If it has a namespace URI in the form of {URI}tag,
     * then a prefix is added by looking up the provided URI-Prefix map.
     * @param nameString - tag string to parse
     * @param uriPrefixMap - map of URIs and their assigned prefixes
     * @param prefixUriMap - map of NS prefix to their URIs
     * @param defaultNamespace - namespace URI to use if nameString does not have one
     * @return
     */
    public static QName parseNameAddPrefix(String nameString, Map<String, String> uriPrefixMap,
                                           Map<String, String> prefixUriMap, String defaultNamespace) {

        QName qName = QName.valueOf(nameString.trim());
        if (!isEmpty(qName.getNamespaceURI())) {

            // Prefix is missing. Look it up in the uri to prefix mapping
            qName = new QName(qName.getNamespaceURI(), qName.getLocalPart(),
                    emptyIfNull(uriPrefixMap.get(qName.getNamespaceURI())));
        } else {
            String[] prefixName = nameString.split(PREFIX_TAG_SEP);
            if (prefixName.length == 2) {
                String prefix = prefixName[0].trim();
                String tagName = prefixName[1].trim();
                qName = new QName(emptyIfNull(prefixUriMap.get(prefix)), tagName, prefix);
            } else {
                if (defaultNamespace != null && !defaultNamespace.isEmpty()) {
                    qName = parseNameAddPrefix(String.format("{%s}%s", defaultNamespace, nameString.trim()),
                            uriPrefixMap, prefixUriMap, defaultNamespace);
                }
            }
        }
        return qName;
    }

    /**
     * Factory method creates new records definition registry from a file
     * @param specFile
     * @return
     * @throws IOException
     */
    public static RecordDefinitions newInstance(File specFile) throws IOException {
        Yaml yaml = new Yaml();
        Map<String, Object> spec = yaml.load(new FileInputStream(specFile));
        return new RecordDefinitions(spec);
    }

    /**
     * Returns an empty record fields sequence.
     * @return empty record definition
     */
    public static RecordDefinitions newInstance() {
        return EMPTY_REGISTRY;
    }

    public String getPrefix(String namespaceUri) {
        return uriPrefixMap.get(namespaceUri);
    }

    public String getNamespaceUri(String prefix) {
        return prefixUriMap.get(prefix);
    }

    /**
     * Get fields of a record type.
     * @param recordName
     * @return field names on a record type
     */
    public List<QName> getRecordFieldNames(QName recordName) {
        Record record = recordFieldsMap.get(recordName);
        if (record == null) {
            return Collections.emptyList();
        }
        return record.fieldNames;
    }

    /**
     * Get fields of a record type.
     * @param recordName
     * @return field names on a record type
     */
    public List<Field> getRecordFields(QName recordName) {
        Record record = recordFieldsMap.get(recordName);
        if (record == null) {
            return Collections.emptyList();
        }
        return record.fields;
    }

    /**
     * Get attributes of a field on a record type.
     * @param recordName
     * @param fieldName
     * @return attribute names of a field on a record type
     */
    public List<QName> getRecordFieldAttributes(QName recordName, QName fieldName) {
        Record record = recordFieldsMap.get(recordName);
        if (record == null) {
            return Collections.emptyList();
        }

        Field field = record.fieldMap.get(fieldName);
        if (field == null) {
            return Collections.emptyList();
        }

        return field.fieldAttributes;
    }

    public Set<String> getPrefixes() {
        return prefixUriMap.keySet();
    }

    public Set<String> getNamespaces() {
        return uriPrefixMap.keySet();
    }

    public Set<QName> getRecords() {
        return recordFieldsMap.keySet();
    }

    /**
     * Format of record line: "complex-type-tag=simple-type-tag1,simple-type-tag2, ..."
     * @param listName - Record tag, a complex type. Forms:
     *                     1) {http://ns-uri}tag - best but verbose
     *                     2) xs:tag             - most preferred
     *                     3) tag                - best if used for element of fieldsListStr
     * @param nameList - Each element in the list should be of the form as recordTagStr is.
     * @return
     */
    private Pair<QName, List<QName>> parseNamedList(String listName, List<String> nameList, String defaultNamespace) {
        List<QName> fieldTagNames = nameList.stream()
                .map(tag -> parseNameAddPrefix(tag, uriPrefixMap, prefixUriMap, defaultNamespace))
                .collect(Collectors.toList());
        QName recordTagName = parseNameAddPrefix(listName, uriPrefixMap, prefixUriMap, defaultNamespace);
        return new Pair<>(recordTagName, Collections.unmodifiableList(fieldTagNames));
    }

    public class Record {
        private final QName recordName;
        private final List<Field> fields;
        private final List<QName> fieldNames;
        private final Map<QName, Field> fieldMap;

        private Record(String recordName, List<Field> fields) {
            this.recordName = parseNameAddPrefix(recordName, uriPrefixMap, prefixUriMap);
            this.fieldMap = unmodifiableMap(fields.stream()
                    .collect(Collectors.toMap(fld -> fld.fieldName, fld -> fld)));
            this.fields = unmodifiableList(fields);
            this.fieldNames = unmodifiableList(fields.stream()
                    .map(field -> field.fieldName).collect(Collectors.toList()));
        }

        public QName getRecordName() {
            return recordName;
        }

        public List<Field> getFields() {
            return fields;
        }

        public List<QName> getFieldNames() {
            return fieldNames;
        }
    }

    public class Field implements SchemaElementWithAttributes {
        private final QName fieldName;
        private final List<QName> fieldAttributes;

        private Field(String fieldName, List<String> fieldAttributes, String defaultNamespace) {
            Pair<QName, List<QName>> pair = parseNamedList(fieldName, fieldAttributes, defaultNamespace);
            this.fieldName = pair.getKey();
            this.fieldAttributes = unmodifiableList(pair.getVal());
        }

        @Override
        public List<QName> getAttributes() {
            return unmodifiableList(fieldAttributes);
        }

        @Override
        public QName getName() {
            return fieldName;
        }
    }

}
