package com.karbherin.flatterxml.feeder;

import com.karbherin.flatterxml.helper.XmlHelpers;
import com.karbherin.flatterxml.model.Pair;
import static com.karbherin.flatterxml.helper.ParsingHelpers.*;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.nio.channels.*;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;

public class XmlByteStreamEmitter implements XmlRecordEmitter {

    private final String xmlFile;
    private final Path xmlFilePath;
    private long skipRecs;
    private long firstNRecs;
    private final int numProducers;

    // Return number of records processed
    private final AtomicLong recCounter = new AtomicLong(0);

    // Includes the delimiters < and >
    private String rootTag = null;
    private String recordTag = null;
    private String rootEndTag = null;
    private String recordEndTag = null;

    private final List<Pipe.SinkChannel> channels = new ArrayList<>();
    private final CharsetDecoder decoder;

    private final long fileSize;
    private final long chunkSize;

    private static final String END_TAG_FORMAT = "</%s>";
    private static final int ALIGN_WORD_SIZE = 4;

    private XmlByteStreamEmitter(String xmlFile, long skipRecs, long firstNRecs, CharsetDecoder decoder,
                                int numProducers) throws IOException {
        this.xmlFile = xmlFile;
        this.skipRecs = skipRecs;
        this.firstNRecs = firstNRecs;
        this.numProducers = numProducers;

        xmlFilePath = Paths.get(xmlFile);

        if (decoder == null) {
            this.decoder = Charset.defaultCharset().newDecoder();
            this.decoder.onMalformedInput(CodingErrorAction.IGNORE);
            this.decoder.onUnmappableCharacter(CodingErrorAction.IGNORE);
        } else {
            this.decoder = decoder;
        }

        fileSize = xmlFilePath.toFile().length();
        chunkSize = fileSize / numProducers + (ALIGN_WORD_SIZE - fileSize % numProducers);
    }

    /**
     * Register a pipe to an events worker.
     * @param channel
     * @throws XMLStreamException
     */
    @Override
    public void registerChannel(Pipe.SinkChannel channel) throws XMLStreamException {
        channels.add(channel);
    }

    /**
     * Start events feed after skipping a few records and limited to a few records after that.
     * @throws XMLStreamException
     * @throws IOException
     */
    @Override
    public void startStream() throws IOException {
        try {
            docFeed();
        } finally {
            closeAllChannels();
        }
    }

    /**
     * Flush and close channels to all the workers.
     * @throws XMLStreamException
     * @throws IOException
     */
    @Override
    public void closeAllChannels() throws IOException {
        for (Pipe.SinkChannel pipe : channels) {
            pipe.close();
        };
    }

    /**
     * Returns the number of records emitted.
     * @return
     */
    @Override
    public long getRecCounter() {
        return recCounter.longValue();
    }

    @Override
    public QName getRootTag() {
        return XmlHelpers.parsePrefixTag(rootEndTag.replaceAll("[<>/]", XmlHelpers.EMPTY));
    }

    @Override
    public QName getRecordTag() {
        return XmlHelpers.parsePrefixTag(recordEndTag.replaceAll("[<>/]", XmlHelpers.EMPTY));
    }

    private void docFeed() throws IOException {
        SeekableByteChannel reader = Files.newByteChannel(xmlFilePath, StandardOpenOption.READ);
        XmlScanner scanner = new XmlScanner(reader, decoder, channels, chunkSize);
        String str = scanner.next();

        // Identify root tag
        Pair<Integer, Integer> coord = findRootTag(str);
        str = scanner.compose(str, coord.getVal());
        scanner.sendToAllChannels();
        // Identify record tag
        coord = findFirstRecordTag(str);
        if (coord == TAG_NOTFOUND_COORDS) {
            throw new IllegalStateException("Record tag could not be found under the XML root");
        }

        // Last scanner will write concluding XML root tag to all pipes
        XmlScanner workerScanner = null;

        final CountDownLatch workerCounter = new CountDownLatch(numProducers - 1);
        for (int t = 2; t <= numProducers; t++) {
            reader = Files.newByteChannel(xmlFilePath, StandardOpenOption.READ);
            long startPoint = (t-1) * chunkSize;
            long chunkLength = t == numProducers
                    ? fileSize -  startPoint
                    : chunkSize;

            reader.position(startPoint);
            workerScanner = new XmlScanner(reader, decoder, channels, chunkLength);

            new Thread(recordsWorker(workerScanner, workerCounter)).start();
        }

        feedRecords(scanner, str);

        try {
            workerCounter.await();
        } catch (InterruptedException ex) {
            ex.printStackTrace();
        }

        // Write the ending root tag
        if (workerScanner != null) {
            // Scanner of last thread
            workerScanner.sendToAllChannels();
        } else {
            // Single thread
            scanner.sendToAllChannels();
        }
    }

    private void feedRecords(XmlScanner scanner, String startingStr) throws IOException {
        String str = startingStr;

        Pair<Integer, Integer> recordStartTagCoord = indexOf(recordTag, str, 0);
        while (recordStartTagCoord == TAG_NOTFOUND_COORDS && scanner.hasNext()) {
            str += scanner.next();
            recordStartTagCoord = indexOf(recordTag, str, 0);
        }

        int startPos = recordStartTagCoord.getKey() - 1;
        str = str.substring(startPos);
        boolean unclosedTag = false;

        while (scanner.hasNext() || !str.isEmpty()) {

            // Detect record's end tag
            Pair<Integer, Integer>  coord = indexOf(recordEndTag, str, 0);
            if (coord != TAG_NOTFOUND_COORDS) {
                str = scanner.compose(str, coord.getVal());

                if (skipRecs == 0 && firstNRecs-- > 0) {
                    // Write the record to active channel
                    scanner.sendToChannel();
                    recCounter.incrementAndGet();
                    scanner.switchOutputChannel();
                } else if (skipRecs > 0) {
                    skipRecs--;
                }

            } else {

                // If root element's end tag is detected and write it and exit
                coord = indexOf(rootEndTag, str, coord.getVal() + 1);
                if (coord != TAG_NOTFOUND_COORDS) {
                    writeUptoRootEndTag(scanner, str, coord);
                    return;
                }
                recordStartTagCoord = indexOf(recordTag, str, 0);
                unclosedTag = lastIndexOf(">", str, 0).getKey()
                        < lastIndexOf("<", str, 0).getKey();
                str = scanner.compose(str, coord.getVal());
            }
        }

        // Does this block start a record, but not conclude it?  Ex: ...<employee><contact>...|
        // Does the chunk bound truncate a tag? Ex: ...<contact><addre|
        if (unclosedTag || recordStartTagCoord != TAG_NOTFOUND_COORDS) {
            Pair<Integer, Integer>  coord;

            // Hard the next blocks until broken record's end tag is found
            do {
                str += scanner.hardNext();
                coord = indexOf(recordEndTag, str, 0);
            } while (coord == TAG_NOTFOUND_COORDS);

            if (coord != TAG_NOTFOUND_COORDS) {
                scanner.compose(str, coord.getVal());
                scanner.sendToChannel();
                recCounter.incrementAndGet();
            }
        }
    }

    private Runnable recordsWorker(XmlScanner scanner, CountDownLatch workerCounter) {
        return () -> {
            try {
                feedRecords(scanner, XmlHelpers.EMPTY);
            } catch (Exception ex) {
                ex.printStackTrace();
            } finally {
                workerCounter.countDown();
            }
        };
    }

    private void writeUptoRootEndTag(XmlScanner scanner, String activeStr, Pair<Integer, Integer> coord)
            throws IOException {
        String str = scanner.compose(activeStr, coord.getKey()-1);
        scanner.sendToChannel();
        scanner.compose(str, str.length() - 1);
    }

    /**
     * Finds the root tag and returns the location of the root tag.
     * @param str - buffer to search in
     * @return
     */
    private Pair<Integer, Integer> findRootTag(String str) {
        Pair<Integer, Integer> tagCoord = TAG_NOTFOUND_COORDS;

        if (rootTag == null) {
            int recordStartPos = 0;
            // Locate root tag
            tagCoord = locateRootTag(str);
            if (tagCoord == TAG_NOTFOUND_COORDS) {
                throw new IllegalStateException("Cannot find root element. Buffer may be too small");
            }
            // Includes the delimiters < and >
            rootTag = str.substring(tagCoord.getKey(), tagCoord.getVal() + 1);
            recordStartPos = tagCoord.getVal() + 1;
            // End tag including </ and >
            rootEndTag = String.format(END_TAG_FORMAT, rootTag.substring(1).split("\\s|\\>")[0]);
        }

        return tagCoord;
    }

    /**
     * Finds the root tag and the record tag and returns the location of the first record tag.
     * @param str - buffer to search in
     * @return
     */
    private Pair<Integer, Integer> findFirstRecordTag(String str) {
        Pair<Integer, Integer> tagCoord = TAG_NOTFOUND_COORDS;

        if (recordTag == null) {
            int recordStartPos = 0;
            // Locate record tag
            tagCoord = nextTagCoord(str, recordStartPos);
            if (tagCoord == TAG_NOTFOUND_COORDS) {
                return TAG_NOTFOUND_COORDS;
            }
            // Includes the delimiters < and >
            recordTag = str.substring(tagCoord.getKey(), 1 + tagCoord.getVal());
            // End tag including </ and >
            recordEndTag = String.format(END_TAG_FORMAT, recordTag.substring(1).split("\\s|\\>")[0]);
        }

        return tagCoord;
    }

    public static class XmlByteStreamEmitterBuilder {
        private String xmlFile;
        private long skipRecs = 0;
        private long firstNRecs = Long.MAX_VALUE;
        private CharsetDecoder decoder = null;
        private int numProducers = 1;

        public XmlByteStreamEmitterBuilder setXmlFile(String xmlFile) {
            this.xmlFile = xmlFile;
            return this;
        }

        public XmlByteStreamEmitterBuilder setSkipRecs(long skipRecs) {
            this.skipRecs = skipRecs;
            return this;
        }

        public XmlByteStreamEmitterBuilder setFirstNRecs(long firstNRecs) {
            this.firstNRecs = firstNRecs;
            return this;
        }

        public XmlByteStreamEmitterBuilder setDecoder(CharsetDecoder decoder) {
            this.decoder = decoder;
            return this;
        }

        public XmlByteStreamEmitterBuilder setNumProducers(int numProducers) {
            this.numProducers = numProducers;
            return this;
        }

        public XmlByteStreamEmitter create() throws IOException {
            return new XmlByteStreamEmitter(xmlFile, skipRecs, firstNRecs, decoder, numProducers);
        }
    }

}
