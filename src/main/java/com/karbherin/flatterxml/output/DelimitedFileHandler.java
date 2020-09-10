package com.karbherin.flatterxml.output;

import com.karbherin.flatterxml.model.RecordFieldsCascade;
import com.karbherin.flatterxml.model.FieldValue;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;


public class DelimitedFileHandler implements RecordHandler {

    private final byte[] delimiter;
    private final String outDir;
    private final List<String[]> filesWritten = new ArrayList<>();
    private final ConcurrentHashMap<String, ByteChannel> fileStreams = new ConcurrentHashMap<>();
    private final ThreadLocal<ByteBuffer> buffer = ThreadLocal.withInitial(() -> ByteBuffer.allocate(8192));

    private enum KeyValuePart {FIELD_PART, VALUE_PART};

    public DelimitedFileHandler(String delimiter, String outDir) {
        this.delimiter = delimiter.getBytes();
        this.outDir = outDir;
    }

    public void write(String fileName, Iterable<FieldValue<String, String>> fieldValueStack,
                      RecordFieldsCascade cascadedData, int currLevel, String previousFileName)
            throws IOException {

        final IOException[] exceptions = new IOException[1];
        ByteChannel out = fileStreams.computeIfAbsent(fileName, (fName) -> {
            ByteChannel newOut = null;
            try {
                String filePath = String.format("%s/%s.csv", outDir, fName);

                newOut = Files.newByteChannel(Paths.get(filePath),
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);

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
        for (Map.Entry<String, ByteChannel> entry: fileStreams.entrySet()) {
            try {
                ByteChannel out = entry.getValue();
                out.close();
            } catch (IOException ex) {
                ;
            }
        }
    }

    public List<String[]> getFilesWritten() {
        return filesWritten;
    }

    private void writeDelimited(ByteChannel out,
                                Iterable<FieldValue<String, String>> data,
                                KeyValuePart part, Iterable<FieldValue<String, String>> appendList)
            throws IOException {

        Iterator<FieldValue<String, String>> dataIt = data.iterator();
        if (!dataIt.hasNext()) {
            return;
        }

        ByteBuffer buf = buffer.get();
        buf.clear();

        if (part == KeyValuePart.FIELD_PART) {
            buf.put(dataIt.next().getField().getBytes());
            while (dataIt.hasNext()) {
                buf.put(delimiter)
                        .put(dataIt.next().getField().getBytes());
            }

            // Appendix
            for (FieldValue<String, String> appendData: appendList) {
                buf.put(delimiter)
                        .put(appendData.getField().getBytes());
            }

        } else {
            buf.put(dataIt.next().getValue().getBytes());
            while (dataIt.hasNext()) {
                buf.put(delimiter)
                        .put(dataIt.next().getValue().getBytes());
            }

            // Appendix
            for (FieldValue<String, String> appendData: appendList) {
                buf.put(delimiter)
                        .put(appendData.getValue().getBytes());
            }
        }

        // Final line separator
        buf.put(System.lineSeparator().getBytes());

        // Write to output channel
        buf.flip();
        out.write(buf);
    }

}
