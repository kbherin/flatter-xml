package com.karbherin.flatterxml.output;

import com.karbherin.flatterxml.RecordFieldsCascade;
import com.karbherin.flatterxml.XmlHelpers;

import java.io.*;
import java.util.*;

public class DelimitedFileHandler implements RecordHandler {

    private final byte[] delimiter;
    private final String outDir;
    private final List<String[]> filesWritten = new ArrayList<>();
    private final Map<String, OutputStream> fileStreams = new HashMap<>();

    private enum KeyValuePart {FIELD_PART, VALUE_PART};

    public DelimitedFileHandler(String delimiter, String outDir) {
        this.delimiter = delimiter.getBytes();
        this.outDir = outDir;
    }

    public void write(String fileName, Stack<XmlHelpers.FieldValue<String, String>> fieldValueStack,
                      RecordFieldsCascade cascadedData, int currLevel, String previousFileName)
            throws IOException {

        OutputStream out = fileStreams.get(fileName);
        if (out == null) {
            out = new FileOutputStream(String.format("%s/%s.csv", outDir, fileName));
            // Register the new file stream.
            fileStreams.put(fileName, out);
            filesWritten.add(new String[]{ String.valueOf(currLevel), fileName, previousFileName });

            // Writer header record into a newly opened file.
            writeDelimited(out, fieldValueStack, KeyValuePart.FIELD_PART, cascadedData.getParentCascadedNames());
        }

        writeDelimited(out, fieldValueStack, KeyValuePart.VALUE_PART, cascadedData.getParentCascadedValues());
    }

    public void closeAllFileStreams() {
        for (Map.Entry<String, OutputStream> entry: fileStreams.entrySet()) {
            try {
                entry.getValue().close();
            } catch (IOException ex) {
                ;
            }
        }
    }

    public List<String[]> getFilesWritten() {
        return filesWritten;
    }

    private void writeDelimited(OutputStream out,
                                Stack<XmlHelpers.FieldValue<String, String>> data,
                                KeyValuePart part, Iterable<String> appendList)
            throws IOException {

        if (data.isEmpty()) {
            return;
        }

        if (part == KeyValuePart.FIELD_PART) {
            out.write(data.pop().field.getBytes());
            while (!data.isEmpty()) {
                out.write(delimiter);
                out.write(data.pop().field.getBytes());
            }
        } else {
            out.write(data.pop().value.getBytes());
            while (!data.isEmpty()) {
                out.write(delimiter);
                out.write(data.pop().value.getBytes());
            }
        }

        // Appendix
        for (String append: appendList) {
            out.write(delimiter);
            out.write(append.getBytes());
        }

        out.write(System.lineSeparator().getBytes());
    }

}
