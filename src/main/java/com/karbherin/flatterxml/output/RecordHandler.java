package com.karbherin.flatterxml.output;

import com.karbherin.flatterxml.RecordFieldsCascade;
import com.karbherin.flatterxml.XmlHelpers;

import java.io.IOException;
import java.util.List;
import java.util.Stack;

public interface RecordHandler {

    void write(String fileName, Iterable<XmlHelpers.FieldValue<String, String>> fieldValueStack,
                      RecordFieldsCascade cascadedData, int currLevel, String previousFileName) throws IOException;

    List<String[]> getFilesWritten();

    void closeAllFileStreams();

}
