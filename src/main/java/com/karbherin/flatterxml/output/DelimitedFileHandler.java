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

    public void write(String fileName, Iterable<XmlHelpers.FieldValue<String, String>> fieldValueStack,
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
                                Iterable<XmlHelpers.FieldValue<String, String>> data,
                                KeyValuePart part, Iterable<String> appendList)
            throws IOException {

        Iterator<XmlHelpers.FieldValue<String, String>> dataIt = data.iterator();
        if (!dataIt.hasNext())
            return;

        if (part == KeyValuePart.FIELD_PART) {
            out.write(dataIt.next().field.getBytes());
            while (dataIt.hasNext()) {
                out.write(delimiter);
                out.write(dataIt.next().field.getBytes());
            }
        } else {
            out.write(dataIt.next().value.getBytes());
            while (dataIt.hasNext()) {
                out.write(delimiter);
                out.write(dataIt.next().value.getBytes());
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
