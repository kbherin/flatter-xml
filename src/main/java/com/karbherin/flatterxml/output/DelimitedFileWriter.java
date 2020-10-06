package com.karbherin.flatterxml.output;

import com.karbherin.flatterxml.helper.XmlHelpers;
import com.karbherin.flatterxml.model.CascadedAncestorFields;
import com.karbherin.flatterxml.model.OpenCan;
import com.karbherin.flatterxml.model.Pair;
import com.karbherin.flatterxml.model.RecordTypeHierarchy;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;


public class DelimitedFileWriter implements RecordHandler {

    private final String delimiter;
    private final String outDir;
    // If user does not provide output fields sequence then the fields can vary between records
    private final boolean outFieldsDefined;

    private final List<GeneratedResult> filesWritten = new ArrayList<>();
    private final ConcurrentHashMap<String, ByteChannel> fileStreams = new ConcurrentHashMap<>();
    private final ThreadLocal<ByteBuffer> buffer = ThreadLocal.withInitial(() -> ByteBuffer.allocate(8192));
    private final ConcurrentHashMap<String, ConcurrentSkipListSet<String>> allRecHeaders = new ConcurrentHashMap<>();

    private enum KeyValuePart {FIELD_PART, VALUE_PART}

    public DelimitedFileWriter(String delimiter, String outDir, boolean outFieldsDefined) {
        this.delimiter = delimiter;
        this.outDir = outDir;
        this.outFieldsDefined = outFieldsDefined;
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
                if (outFieldsDefined) {
                    writeDelimited(newOut, fieldValueStack, KeyValuePart.FIELD_PART,
                            cascadedData.getCascadedAncestorFields(), fileName);
                }
            } catch (IOException ex) {
                exception.val = ex;
            }

            allRecHeaders.put(fileName, new ConcurrentSkipListSet<>());

            return newOut;
        });

        if (exception.val != null) {
            throw exception.val;
        }

        writeDelimited(out, fieldValueStack, KeyValuePart.VALUE_PART,
                cascadedData.getCascadedAncestorFields(), fileName);
    }

    @Override
    public void closeAllFileStreams() throws IOException {
        for (Map.Entry<String, ByteChannel> entry: fileStreams.entrySet()) {
            ByteChannel out = entry.getValue();
            try {
                out.close();
            } catch (IOException ignored) {
            }
        }

        if (!outFieldsDefined) {
            for (String fileName : fileStreams.keySet()) {
                realignRecords(allRecHeaders.get(fileName).toArray(new String[0]), fileName);
            }
        }
    }

    private void realignRecords(final String[] headers, String fileName) throws IOException {
        if (headers.length == 0)
            return;

        Arrays.sort(headers, Comparator.comparingInt(String::length).reversed());
        String delimiterRx = String.format("\\%s", String.join("\\", delimiter.split("")));
        String cleanHeader = headers[0].substring(2, headers[0].length()-1);
        List<String> allCols = Arrays.asList(
                cleanHeader.split(delimiterRx));

        Map<String, Integer> colsPos = new HashMap<>();
        for (int i = 0; i < allCols.size(); i++) {
            String col = allCols.get(i);
            colsPos.put(col, i);
        }

        for (int i = 1; i < headers.length; i++) {
            String[] cols = headers[i].substring(2, headers[i].length()-1).split(delimiterRx);
            for (String col : cols) {
                if (!colsPos.containsKey(col)) {
                    colsPos.put(col, allCols.size());
                    allCols.add(col);
                }
            }
        }


        String inFileName = String.format("%s/%s.csv", outDir, fileName);
        String outFileName = String.format("%s/tmp_%s.csv", outDir, fileName);
        BufferedReader inFile = new BufferedReader(new FileReader(inFileName));
        BufferedWriter outFile = new BufferedWriter(new FileWriter(outFileName));

        outFile.write(String.join(delimiter, allCols));
        outFile.write(System.lineSeparator());

        OpenCan<IOException> exception = new OpenCan<>();
        inFile.lines().forEach(line -> {
            String[] valuesCols = line.split("\\#");
            String[] vals = valuesCols[0].split(delimiterRx);
            String[] cols = valuesCols[1].substring(1, valuesCols[1].length() - 1).split(delimiterRx);

            String[] rec = new String[allCols.size()];
            for (int i = 0; i < cols.length; i++) {
                rec[colsPos.get(cols[i])] = vals[i];
            }

            String record = Arrays.stream(rec).map(field -> field == null ? "" : field)
                    .collect(Collectors.joining(delimiter));
            try {
                outFile.write(record);
                outFile.write(System.lineSeparator());
            } catch (IOException ex) {
                exception.val = ex;
            }
        });

        if (exception.val != null) {
            throw exception.val;
        }

        outFile.flush();
        outFile.close();

        Files.deleteIfExists(Paths.get(inFileName));
        Files.move(Paths.get(outFileName), Paths.get(inFileName));
    }

    @Override
    public List<GeneratedResult> getFilesWritten() {
        return filesWritten;
    }

    private void writeDelimited(ByteChannel out,
                                Iterable<Pair<String, String>> data,
                                KeyValuePart part, Iterable<Pair<String, String>> appendList,
                                String fileName)
            throws IOException {

        if (!data.iterator().hasNext())
            return;

        StringJoiner colValues = new StringJoiner(delimiter);
        StringJoiner colNames = new StringJoiner(delimiter, "#[", "]");

        if (part == KeyValuePart.FIELD_PART) {
            for (Pair<String, String> fv : data) {
                colValues.add(fv.getKey());
            }

            // Appendix
            for (Pair<String, String> fv: appendList) {
                colValues.add(fv.getKey());
            }

        } else {
            for (Pair<String, String> fv : data) {
                colValues.add(fv.getVal());

                if (!outFieldsDefined) {
                    colNames.add(fv.getKey());
                }
            }

            // Appendix
            for (Pair<String, String> fv: appendList) {
                colValues.add(fv.getVal());

                if (!outFieldsDefined) {
                    colNames.add(fv.getKey());
                }
            }
        }

        ByteBuffer buf = buffer.get();
        buf.clear();
        buf.put(colValues.toString().getBytes());

        // If user did not provide output fields sequence then the fields can vary between records.
        // Append the record's header.
        if (!outFieldsDefined) {
            String colNamesStr = colNames.toString();
            buf.put(colNamesStr.getBytes());
            allRecHeaders.get(fileName).add(colNamesStr);
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
