package com.karbherin.flatterxml.output;

import com.karbherin.flatterxml.helper.Utils;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static com.karbherin.flatterxml.helper.XmlHelpers.EMPTY;


public class DelimitedFileWriter implements RecordHandler {

    private final String delimiterStr;
    private final byte[] delimiter;
    private final String delimiterRx;
    private final String outDir;
    // If user does not provide output fields sequence then the fields can vary between records
    private final boolean outFieldsDefined;

    private final List<GeneratedResult> filesWritten = new ArrayList<>();
    private final ConcurrentHashMap<String, ByteChannel> fileStreams = new ConcurrentHashMap<>();
    private final ThreadLocal<ByteBuffer> buffer = ThreadLocal.withInitial(() -> ByteBuffer.allocate(8192));
    private final Map<String, ConcurrentHashMap<String, Pair<Integer, Integer>>> allRecHeaders = new HashMap<>();
    private final AtomicInteger headingNumber = new AtomicInteger(0);

    private enum KeyValuePart {FIELD_PART, VALUE_PART}
    private static final String DATA_HEADER_SEP = "##HEADER>#";

    public DelimitedFileWriter(String delimiter, String outDir, boolean outFieldsDefined) {
        this.delimiterStr = delimiter;
        this.outDir = outDir;
        this.outFieldsDefined = outFieldsDefined;
        this.delimiter = delimiter.getBytes();
        this.delimiterRx = String.format("\\%s",
                String.join("\\", delimiterStr.split("")));
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

            allRecHeaders.put(fileName, new ConcurrentHashMap<>());

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
                realignRecords(allRecHeaders.get(fileName).keySet().toArray(new String[0]), fileName);
            }
        }
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

        Iterator<Pair<String, String>> dataIt = data.iterator();
        if (!dataIt.hasNext()) {
            return;
        }

        ByteBuffer buf = buffer.get();
        buf.clear();
        StringJoiner colNames = new StringJoiner(delimiterStr);

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

            Pair<String, String> fv = dataIt.next();
            buf.put(fv.getVal().getBytes());
            colNames.add(fv.getKey());

            while (dataIt.hasNext()) {
                fv = dataIt.next();
                buf.put(delimiter)
                        .put(fv.getVal().getBytes());

                if (!outFieldsDefined) {
                    colNames.add(fv.getKey());
                }
            }

            // Appendix
            for (Pair<String, String> fva: appendList) {
                buf.put(delimiter)
                        .put(fva.getVal().getBytes());

                if (!outFieldsDefined) {
                    colNames.add(fva.getKey());
                }
            }
        }

        // If user did not provide output fields sequence then the fields can vary between records.
        // Append the record's header id to the record data.
        if (!outFieldsDefined) {
            String colNamesStr = colNames.toString();

            // All headers for a given output filename
            ConcurrentHashMap<String, Pair<Integer, Integer>> fileHeadersRegistry = allRecHeaders.get(fileName);

            // Assign a new header id. to the header string if not seen before
            Pair<Integer, Integer> headerIdCounts = fileHeadersRegistry.computeIfAbsent(colNamesStr,
                    ign -> new Pair<>(headingNumber.incrementAndGet(), 0));

            // Increment the count of the header
            headerIdCounts.setVal(headerIdCounts.getVal() + 1);

            // Append the header id. to the output record
            buf.put(DATA_HEADER_SEP.getBytes())
                    .put(fileHeadersRegistry.get(colNamesStr).getKey().toString().getBytes());
        }

        // Final line separator
        buf.put(System.lineSeparator().getBytes());

        // Write to output channel
        buf.flip();
        out.write(buf);
    }

    private void realignRecords(final String[] headers, String fileName) throws IOException {
        if (headers.length == 0)
            return;

        Map<String, Pair<Integer, Integer>> fileHeaders = allRecHeaders.get(fileName);

        // Arrays.sort(headers, Comparator.comparingInt(String::length).reversed());
        List<Map.Entry<String, Pair<Integer, Integer>>> headerStats = fileHeaders.entrySet().stream()
                .sorted((e1, e2) -> e2.getKey().compareTo(e1.getKey()))
                .collect(Collectors.toList());

        List<String> allCols = Utils.collapseSequences(
                headerStats.stream()
                        .map(h -> h.getKey().split(delimiterRx))
                        .collect(Collectors.toList()),
                headerStats.stream()
                        .map(h -> h.getValue().getVal())
                        .collect(Collectors.toList()));

        Map<String, Integer> colsPos = new HashMap<>();
        int pos = 0;
        for (String col: allCols) {
            colsPos.put(col, pos++);
        }

        Map<Integer, String[]> indexToHeader = headerStats.stream()
                .collect(Collectors.toMap(
                        entry -> entry.getValue().getKey(),
                        entry -> entry.getKey().split(delimiterRx)));

        String inFileName = String.format("%s/%s.csv", outDir, fileName);
        String outFileName = String.format("%s/tmp_%s.csv", outDir, fileName);
        BufferedReader inFile = new BufferedReader(new FileReader(inFileName));
        BufferedWriter outFile = new BufferedWriter(new FileWriter(outFileName));

        outFile.write(String.join(delimiterStr, allCols));
        outFile.write(System.lineSeparator());

        // Reformat the file for each record to have the same list of columns
        OpenCan<IOException> exception = new OpenCan<>();
        inFile.lines().map(line -> line.split(DATA_HEADER_SEP)).forEach(lineParts -> {
            assert lineParts.length == 2;
            String[] values = lineParts[0].split(delimiterRx);
            String[] colNames = indexToHeader.get(Integer.parseInt(lineParts[1]));
            assert colNames.length == values.length;

            String[] outRec = new String[allCols.size()];
            for (int i = 0, len = colNames.length; i < len; i++) {
                outRec[colsPos.get(colNames[i])] = values[i];
            }

            // Write the record
            try {
                for (int i = 0, len = outRec.length; i < len; i++) {
                    String col = outRec[i];
                    outFile.write(col == null ? EMPTY : col);
                    if (i != 0) {
                        outFile.write(delimiterStr);
                    }
                }
                outFile.newLine();
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

    private String previousFile(RecordTypeHierarchy recordTypeAncestry, String fileName) {
        String previousFileName = recordTypeAncestry.parentRecordType().recordName().getLocalPart();
        if (fileName.equals(previousFileName)) {
            previousFileName = EMPTY;
        }
        return previousFileName;
    }

}
