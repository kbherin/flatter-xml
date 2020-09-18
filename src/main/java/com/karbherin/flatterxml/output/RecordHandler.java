package com.karbherin.flatterxml.output;

import com.karbherin.flatterxml.model.CascadedAncestorFields;
import com.karbherin.flatterxml.model.RecordFieldsCascade;
import com.karbherin.flatterxml.model.Pair;
import com.karbherin.flatterxml.model.RecordTypeHierarchy;

import java.io.IOException;
import java.util.List;

public interface RecordHandler {

    void write(String fileName, Iterable<Pair<String, String>> fieldValueStack,
               CascadedAncestorFields cascadedData, RecordTypeHierarchy recordTypeAncestry)
            throws IOException;

    List<GeneratedResult> getFilesWritten();

    void closeAllFileStreams();


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
