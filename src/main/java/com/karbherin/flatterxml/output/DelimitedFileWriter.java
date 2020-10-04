package com.karbherin.flatterxml.output;

import com.karbherin.flatterxml.helper.XmlHelpers;
import com.karbherin.flatterxml.model.CascadedAncestorFields;
import com.karbherin.flatterxml.model.OpenCan;
import com.karbherin.flatterxml.model.Pair;
import com.karbherin.flatterxml.model.RecordTypeHierarchy;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;


public class DelimitedFileWriter implements RecordHandler {

    private final byte[] delimiter;
    private final String outDir;
    private final List<GeneratedResult> filesWritten = new ArrayList<>();
    private final ConcurrentHashMap<String, ByteChannel> fileStreams = new ConcurrentHashMap<>();
    private final ThreadLocal<ByteBuffer> buffer = ThreadLocal.withInitial(() -> ByteBuffer.allocate(8192));

    private enum KeyValuePart {FIELD_PART, VALUE_PART}

    public DelimitedFileWriter(String delimiter, String outDir) {
        this.delimiter = delimiter.getBytes();
        this.outDir = outDir;
    }

    @Override
    public void write(String fileName, Iterable<Pair<String, String>> fieldValueStack,
                      CascadedAncestorFields cascadedData, RecordTypeHierarchy recordTypeAncestry)
            throws IOException {

        String previousFileName = previousFile(recordTypeAncestry, fileName);
        int currLevel = recordTypeAncestry.recordLevel();

        final OpenCan<IOException> exception = new OpenCan<>();
        ByteChannel out = fileStreams.computeIfAbsent(fileName, (fName) -> {

            ByteChannel newOut = null;
            try {
                String filePath = String.format("%s/%s.csv", outDir, fName);

                newOut = Files.newByteChannel(Paths.get(filePath),
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);

                // Register the new file stream.
                filesWritten.add(new GeneratedResult(currLevel, fName, previousFileName));

                // Writer header record into a newly opened file.
                writeDelimited(newOut, fieldValueStack, KeyValuePart.FIELD_PART, cascadedData.getCascadedAncestorFields());
            } catch (IOException ex) {
                exception.val = ex;
            }

            return newOut;
        });

        if (exception.val != null) {
            throw exception.val;
        }

        writeDelimited(out, fieldValueStack, KeyValuePart.VALUE_PART, cascadedData.getCascadedAncestorFields());
    }

    @Override
    public void closeAllFileStreams() {
        for (Map.Entry<String, ByteChannel> entry: fileStreams.entrySet()) {
            ByteChannel out = entry.getValue();
            try {
                out.close();
            } catch (IOException ignored) {
            }
        }
    }

    @Override
    public List<GeneratedResult> getFilesWritten() {
        return filesWritten;
    }

    private void writeDelimited(ByteChannel out,
                                Iterable<Pair<String, String>> data,
                                KeyValuePart part, Iterable<Pair<String, String>> appendList)
            throws IOException {

        Iterator<Pair<String, String>> dataIt = data.iterator();
        if (!dataIt.hasNext()) {
            return;
        }

        ByteBuffer buf = buffer.get();
        buf.clear();

        if (part == KeyValuePart.FIELD_PART) {
            buf.put(dataIt.next().getKey().getBytes());
            while (dataIt.hasNext()) {
                buf.put(delimiter)
                        .put(dataIt.next().getKey().getBytes());
            }

            // Appendix
            for (Pair<String, String> appendData: appendList) {
                buf.put(delimiter)
                        .put(appendData.getKey().getBytes());
            }

        } else {
            buf.put(dataIt.next().getVal().getBytes());
            while (dataIt.hasNext()) {
                buf.put(delimiter)
                        .put(dataIt.next().getVal().getBytes());
            }

            // Appendix
            for (Pair<String, String> appendData: appendList) {
                buf.put(delimiter)
                        .put(appendData.getVal().getBytes());
            }
        }

        // Final line separator
        buf.put(System.lineSeparator().getBytes());

        // Write to output channel
        buf.flip();
        out.write(buf);
    }


    private String previousFile(RecordTypeHierarchy recordTypeAncestry, String fileName) {
        String previousFileName = recordTypeAncestry.parentRecordType().recordName().getLocalPart();
        if (fileName.equals(previousFileName)) {
            previousFileName = XmlHelpers.EMPTY;
        }
        return previousFileName;
    }

}
