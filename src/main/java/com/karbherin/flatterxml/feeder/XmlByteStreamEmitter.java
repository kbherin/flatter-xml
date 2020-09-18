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
import java.util.StringJoiner;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;

public class XmlByteStreamEmitter implements XmlRecordEmitter {

    private final String xmlFile;
    private final Path xmlFilePath;
    private long skipRecs;
    private long firstNRecs;
    private final Charset charset;
    private final int numProducers;

    // Return number of records processed
    private final AtomicLong recCounter = new AtomicLong(0);

    // Includes the delimiters < and >
    private String rootTag = null;
    private String recordTag = null;
    private String rootEndTag = null;
    private String recordEndTag = null;

    private final List<Pipe.SinkChannel> channels = new ArrayList<>();

    private final long fileSize;
    private final long chunkSize;

    private static final String END_TAG_FORMAT = "</%s>";
    private static final int ALIGN_WORD_SIZE = 4;

    private XmlByteStreamEmitter(String xmlFile, long skipRecs, long firstNRecs, Charset charset,
                                int numProducers) throws IOException {
        this.xmlFile = xmlFile;
        this.skipRecs = skipRecs;
        this.firstNRecs = firstNRecs;
        this.charset = charset;
        this.numProducers = numProducers;

        xmlFilePath = Paths.get(xmlFile);
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

    /**
     * XML document feeder. The main loop that handles interactions between producers and workers.
     * It takes care of breaking the XML into chunks for processing by separate producers.
     * @throws IOException
     */
    private void docFeed() throws IOException {
        CharsetDecoder decoder = newDecoder();
        SeekableByteChannel reader = Files.newByteChannel(xmlFilePath, StandardOpenOption.READ);
        int workersPerProducer = channels.size() / numProducers;
        XmlScanner scanner = new XmlScanner(reader, decoder, allocateWorkers(0), chunkSize);
        String str = scanner.next();

        // Identify root tag
        Pair<Integer, Integer> coord = findRootTag(str);
        str = scanner.compose(str, coord.getVal());
        scanner.sendToAllChannels(channels);

        // Identify record tag
        coord = findFirstRecordTag(str);
        if (coord == TAG_NOTFOUND_COORDS) {
            throw new IllegalStateException("Record tag could not be found under the XML root");
        }

        // Last scanner will write concluding XML root tag to all pipes
        XmlScanner lastWorkerScanner = null;

        final CountDownLatch workerCounter = new CountDownLatch(numProducers - 1);
        for (int t = 1; t < numProducers; t++) {
            SeekableByteChannel workerReader = Files.newByteChannel(xmlFilePath, StandardOpenOption.READ);
            CharsetDecoder workerDecoder = newDecoder();

            long startPoint = t * chunkSize;
            long chunkLength = t == numProducers - 1
                    ? fileSize -  startPoint
                    : chunkSize;

            workerReader.position(startPoint);
            XmlScanner workerScanner = new XmlScanner(workerReader, workerDecoder, allocateWorkers(t), chunkLength);
            lastWorkerScanner = workerScanner;

            Thread worker = new Thread(recordsWorker(workerScanner, workerCounter));
            worker.setPriority(Thread.MAX_PRIORITY-1);
            worker.start();
        }

        feedRecords(scanner, str);

        try {
            workerCounter.await();
        } catch (InterruptedException ex) {
            ex.printStackTrace();
        }

        // Write the ending root tag
        if (lastWorkerScanner != null) {
            // Scanner of last thread
            lastWorkerScanner.sendToAllChannels(channels);
        } else {
            // Single thread
            scanner.sendToAllChannels(channels);
        }
    }

    /**
     * Records processor writes XML records within the designated chunk to the assigned worker.
     * @param scanner
     * @param startingStr
     * @throws IOException
     */
    private void feedRecords(XmlScanner scanner, String startingStr) throws IOException {
        String str = startingStr;

        Pair<Integer, Integer> recordStartTagCoord = indexOf(recordTag, str, 0);
        while (recordStartTagCoord == TAG_NOTFOUND_COORDS && scanner.hasNext()) {
            // Retain the last few characters of the data read in the current file read operation
            str = str.substring(Math.max(0, str.length() - 2 * recordTag.length())) + scanner.next();
            recordStartTagCoord = indexOf(recordTag, str, 0);
        }

        int startPos = recordStartTagCoord.getKey();
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
            lookAheadChunkForRecordEnd(str, scanner);
        }
    }

    /**
     * Work executor.
     * @param scanner
     * @param workerCounter
     * @return
     */
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

    /**
     * Write up to the beginning of terminal root tag to the assigned worker.
     * @param scanner
     * @param activeStr
     * @param coord
     * @throws IOException
     */
    private void writeUptoRootEndTag(XmlScanner scanner, String activeStr, Pair<Integer, Integer> coord)
            throws IOException {
        String str = scanner.compose(activeStr, coord.getKey()-1);
        scanner.sendToChannel();
        scanner.compose(str, str.length() - 1);
    }


    /**
     * Allocates an exclusive set of workers for each producer.
     * @param producerNum
     * @return
     */
    private List<Pipe.SinkChannel> allocateWorkers(int producerNum) {
        List<Pipe.SinkChannel> subList = new ArrayList<>(channels.size() / numProducers);
        for (int ch = 0; ch < channels.size(); ch++) {
            if (producerNum == ch % numProducers) {
                subList.add(channels.get(ch));
            }
        }
        return subList;
    }

    /**
     * Looks into next chunk to find the closing record tag.
     * @param activeStr
     * @param scanner
     * @throws IOException
     */
    private void lookAheadChunkForRecordEnd(String activeStr, XmlScanner scanner) throws IOException {
        Pair<Integer, Integer>  coord;
        String str = activeStr;

        // Look into next blocks until broken record's end tag is found
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

    /**
     * Creates a new character decoder
     * @return
     */
    private CharsetDecoder newDecoder() {
        return charset.newDecoder()
            .onMalformedInput(CodingErrorAction.IGNORE)
            .onUnmappableCharacter(CodingErrorAction.IGNORE);
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
            rootEndTag = String.format(END_TAG_FORMAT, rootTag.substring(1).split("\\s|>")[0]);
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
            // Includes the tag opener <
            recordTag = str.substring(tagCoord.getKey(), 1 + tagCoord.getVal()).split("\\s|>")[0];
            // End tag including </ and >
            recordEndTag = String.format(END_TAG_FORMAT, recordTag.substring(1));
        }

        return tagCoord;
    }

    public static class XmlByteStreamEmitterBuilder {
        private String xmlFile;
        private long skipRecs = 0;
        private long firstNRecs = Long.MAX_VALUE;
        private Charset charset = Charset.defaultCharset();
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

        public XmlByteStreamEmitterBuilder setCharset(String charset) {
            this.charset = Charset.forName(charset);
            return this;
        }

        public XmlByteStreamEmitterBuilder setCharset(Charset charset) {
            this.charset = charset;
            return this;
        }

        public XmlByteStreamEmitterBuilder setNumProducers(int numProducers) {
            this.numProducers = numProducers;
            return this;
        }

        public XmlByteStreamEmitter create() throws IOException {
            return new XmlByteStreamEmitter(xmlFile, skipRecs, firstNRecs, charset, numProducers);
        }
    }

}
