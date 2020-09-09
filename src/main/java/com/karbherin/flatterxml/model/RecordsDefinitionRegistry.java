package com.karbherin.flatterxml.model;

import org.yaml.snakeyaml.Yaml;

import static com.karbherin.flatterxml.XmlHelpers.*;

import javax.xml.namespace.QName;
import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

public final class RecordsDefinitionRegistry {

    private final Map<QName, List<QName>> recordFieldsMap;

    private final Map<String, String> prefixUriMap;
    private final Map<String, String> uriPrefixMap;

    private static final String KEY_VALUE_SEP = "=";
    private static final String LIST_SEP = ",";
    private static final String PREFIX_TAG_SEP = ":";
    /**
     * Parses lines that specify tags, either for specifying output fields or cascading fields.
     * A line can either specify a record(complex type) or a prefix used in the rest of the file.
     * Yaml format of record line: "complex-type-tag": ["simple-type-tag1", "simple-type-tag2", ...]
     * Yaml format of prefix line: "ns-prefix": "namespaceURI"
     * Format of complex-type-tag: "{namespaceURI}tag-name" | "ns-prefix:tag-name"
     * Format of simple-type-tag : simple name, prefix:tag-name, {namespaceURI}tag-name
     * @param spec - Map representation of configuration yaml file
     */
    private RecordsDefinitionRegistry(Map<String, Object> spec) {
        @SuppressWarnings("unchecked")
        Map<String, List<String>> recordSpecs = (Map<String, List<String>>) Optional.ofNullable(
                spec.get("records")
        ).orElse(Collections.emptyMap());

        Map<String, String> prefixUri = (Map<String, String>) Optional.ofNullable(
                        spec.get("namespaces")
        ).orElse(Collections.emptyMap());

        prefixUriMap = prefixUri;
        uriPrefixMap = prefixUriMap.entrySet().stream()
                .collect(Collectors.toMap(preUri -> preUri.getValue(), preUri -> preUri.getKey()));

        recordFieldsMap = recordSpecs.entrySet().stream()
                .map(ent -> parseRecordSpec(ent.getKey(), ent.getValue()))
                .collect(Collectors.toMap(fv -> fv.getField(), fv->fv.getValue()));
    }

    /**
     * Format of record line: "complex-type-tag=simple-type-tag1,simple-type-tag2, ..."
     * @param recordTagStr - Record tag, a complex type. Forms:
     *                     1) {http://ns-uri}tag - best but verbose
     *                     2) xs:tag             - most preferred
     *                     3) tag                - best if used for element of fieldsListStr
     * @param fieldsListStr - Each element in the list should be of the form as recordTagStr is.
     * @return
     */
    private FieldValue<QName, List<QName>> parseRecordSpec(String recordTagStr, List<String> fieldsListStr) {
        List<QName> fieldTagNames = fieldsListStr.stream()
                .map(tag -> parseTagAddPrefix(tag, uriPrefixMap, prefixUriMap))
                .collect(Collectors.toList());
        QName recordTagName = parseTagAddPrefix(recordTagStr, uriPrefixMap, prefixUriMap);
        return new FieldValue<>(recordTagName, Collections.unmodifiableList(fieldTagNames));
    }

    /**
     * Derived a qualified name tag string. If it has a namespace URI in the form of {URI}tag,
     * then a prefix is added by looking up the provided URI-Prefix map.
     * @param tagString - tag string to parse
     * @param uriPrefixMap - map of URIs and their assigned prefixes
     * @return
     */
    public static QName parseTagAddPrefix(String tagString, Map<String, String> uriPrefixMap,
                                          Map<String, String> prefixUriMap) {

        QName qName = QName.valueOf(tagString.trim());
        if (!isEmpty(qName.getNamespaceURI())) {

            // Prefix is missing. Look it up in the uri to prefix mapping
            qName = new QName(qName.getNamespaceURI(), qName.getLocalPart(),
                    emptyIfNull(uriPrefixMap.get(qName.getNamespaceURI())));
        } else {
            String[] prefixName = tagString.split(PREFIX_TAG_SEP);
            if (prefixName.length == 2) {
                String prefix = prefixName[0].trim();
                String tagName = prefixName[1].trim();
                qName = new QName(emptyIfNull(prefixUriMap.get(prefix)), tagName, prefix);
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
    public static RecordsDefinitionRegistry newInstance(File specFile) throws IOException {
        Yaml yaml = new Yaml();
        Map<String, Object> spec = yaml.load(new FileInputStream(specFile));
        return new RecordsDefinitionRegistry(spec);
    }

    private static RecordsDefinitionRegistry EMPTY_REGISTRY = new RecordsDefinitionRegistry(Collections.emptyMap());
    public static RecordsDefinitionRegistry newInstance() {
        return EMPTY_REGISTRY;
    }

    public String getPrefix(String namespaceUri) {
        return uriPrefixMap.get(namespaceUri);
    }

    public String getNamespaceUri(String prefix) {
        return prefixUriMap.get(prefix);
    }

    public List<QName> getRecordFields(QName recordTag) {
        return Collections.unmodifiableList(Optional.ofNullable(
                recordFieldsMap.get(recordTag)
        ).orElse(Collections.emptyList()));
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

}
