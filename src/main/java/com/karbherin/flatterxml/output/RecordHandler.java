package com.karbherin.flatterxml.output;

import com.karbherin.flatterxml.model.RecordFieldsCascade;
import com.karbherin.flatterxml.model.Pair;

import java.io.IOException;
import java.util.List;

public interface RecordHandler {

    void write(String fileName, Iterable<Pair<String, String>> fieldValueStack,
                      RecordFieldsCascade cascadedData, int currLevel, String previousFileName) throws IOException;

    List<String[]> getFilesWritten();

    void closeAllFileStreams();

}
