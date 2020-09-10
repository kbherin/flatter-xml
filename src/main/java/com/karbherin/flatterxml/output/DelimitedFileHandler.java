package com.karbherin.flatterxml.output;

import com.karbherin.flatterxml.model.RecordFieldsCascade;
import com.karbherin.flatterxml.model.FieldValue;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;


public class DelimitedFileHandler implements RecordHandler {

    private final byte[] delimiter;
    private final String outDir;
    private final String fileSuffix;
    private final List<String[]> filesWritten = new ArrayList<>();
    private final ConcurrentHashMap<String, OutputStream> fileStreams = new ConcurrentHashMap<>();

    private enum KeyValuePart {FIELD_PART, VALUE_PART};

    public DelimitedFileHandler(String delimiter, String outDir, String fileSuffix) {
        this.delimiter = delimiter.getBytes();
        this.outDir = outDir;
        this.fileSuffix = fileSuffix;
    }

    public void write(String fileName, Iterable<FieldValue<String, String>> fieldValueStack,
                      RecordFieldsCascade cascadedData, int currLevel, String previousFileName)
            throws IOException {

        final IOException[] exceptions = new IOException[1];
        OutputStream out = fileStreams.computeIfAbsent(fileName, (fName) -> {
            OutputStream newOut = null;
            try {
                newOut = new BufferedOutputStream(
                        new FileOutputStream(String.format("%s/%s%s.csv", outDir, fName, fileSuffix)));

                // Register the new file stream.
                filesWritten.add(new String[]{String.valueOf(currLevel), fName, previousFileName});


                // Writer header record into a newly opened file.
                writeDelimited(newOut, fieldValueStack, KeyValuePart.FIELD_PART, cascadedData.getParentCascadedFieldValueList());
            } catch (IOException ex) {
                exceptions[0] = ex;
            }

            return newOut;
        });

        if (exceptions[0] != null) {
            throw exceptions[0];
        }

        writeDelimited(out, fieldValueStack, KeyValuePart.VALUE_PART, cascadedData.getParentCascadedFieldValueList());
    }

    public void closeAllFileStreams() {
        for (Map.Entry<String, OutputStream> entry: fileStreams.entrySet()) {
            try {
                OutputStream out = entry.getValue();
                out.flush();
                out.close();
            } catch (IOException ex) {
                ;
            }
        }
    }

    public List<String[]> getFilesWritten() {
        return filesWritten;
    }

    private void writeDelimited(OutputStream out,
                                Iterable<FieldValue<String, String>> data,
                                KeyValuePart part, Iterable<FieldValue<String, String>> appendList)
            throws IOException {

        Iterator<FieldValue<String, String>> dataIt = data.iterator();
        if (!dataIt.hasNext())
            return;

        if (part == KeyValuePart.FIELD_PART) {
            out.write(dataIt.next().getField().getBytes());
            while (dataIt.hasNext()) {
                out.write(delimiter);
                out.write(dataIt.next().getField().getBytes());
            }

            // Appendix
            for (FieldValue<String, String> append: appendList) {
                out.write(delimiter);
                out.write(append.getField().getBytes());
            }
        } else {
            out.write(dataIt.next().getValue().getBytes());
            while (dataIt.hasNext()) {
                out.write(delimiter);
                out.write(dataIt.next().getValue().getBytes());
            }

            // Appendix
            for (FieldValue<String, String> append: appendList) {
                out.write(delimiter);
                out.write(append.getValue().getBytes());
            }
        }



        out.write(System.lineSeparator().getBytes());
    }

}
