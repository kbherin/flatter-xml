package com.karbherin.flatterxml.output;

import com.karbherin.flatterxml.model.CascadedAncestorFields;
import com.karbherin.flatterxml.model.RecordFieldsCascade;
import com.karbherin.flatterxml.model.Pair;
import com.karbherin.flatterxml.model.RecordTypeHierarchy;

import javax.xml.namespace.QName;
import javax.xml.stream.events.Namespace;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public interface RecordHandler {

    void write(QName recordName,
               Iterable<Pair<String, String>> fieldValueStack,
               CascadedAncestorFields cascadedData)
            throws IOException;

    List<GeneratedResult> getFilesWritten();

    void closeAllFileStreams() throws IOException;

    void setXmlnsUriToPrefix(Map<String, Namespace> xmlnsUriToPrefix);

    final class GeneratedResult {
        public final int recordLevel;
        public final String recordType;
        public final String previousRecordType;

        public GeneratedResult(int recordLevel, String recordType, String previousRecordType) {
            this.recordLevel = recordLevel;
            this.recordType = recordType;
            this.previousRecordType = previousRecordType;
        }
    }

}
