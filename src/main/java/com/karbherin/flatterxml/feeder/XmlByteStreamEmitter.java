package com.karbherin.flatterxml.feeder;

import com.karbherin.flatterxml.model.Pair;
import static com.karbherin.flatterxml.helper.ParsingHelpers.*;

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

public class XmlByteStreamEmitter implements XmlRecordEmitter {

    private final String xmlFile;
    private final Path xmlFilePath;
    private long skipRecs;
    private long firstNRecs;
    private final int numProducers;

    // Return number of records processed
    private long recCounter = 0;

    // Includes the delimiters < and >
    private String rootTag = null;
    private String recordTag = null;
    private char[] rootEndTag = null;
    private char[] recordEndTag = null;

    private final List<Pipe.SinkChannel> channels = new ArrayList<>();
    private final CharsetDecoder decoder;

    private final List<SeekableByteChannel> readers = new ArrayList<>();

    private static final String END_TAG_FORMAT = "</%s>";
    private static final int UNTIL_END = -1;

    public XmlByteStreamEmitter(String xmlFile, long skipRecs, long firstNRecs, CharsetDecoder decoder,
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

        for (int i = 0; i < numProducers; i++) {
            readers.add(Files.newByteChannel(xmlFilePath, StandardOpenOption.READ));
        }
    }

    public XmlByteStreamEmitter(String xmlFile) throws IOException {
        this(xmlFile, 0, Long.MAX_VALUE, null, 1);
    }

    public XmlByteStreamEmitter(String xmlFile, long skipRecs, long firstNRecs) throws IOException {
        this(xmlFile, skipRecs, firstNRecs, null, 1);
    }

    public XmlByteStreamEmitter(String xmlFile, long skipRecs) throws IOException {
        this(xmlFile, skipRecs, Long.MAX_VALUE, null, 1);
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
    public void startStream() throws XMLStreamException, IOException {
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
    public void closeAllChannels() throws XMLStreamException, IOException {
        for (Pipe.SinkChannel pipe : channels) {
            pipe.close();
        };

        for (ReadableByteChannel reader: readers) {
            reader.close();
        }
    }

    /**
     * Returns the number of records emitted.
     * @return
     */
    public long getRecCounter() {
        return recCounter;
    }

    private void docFeed() throws IOException {
        XmlScanner scanner = new XmlScanner(readers.get(0), decoder, channels);
        char[] str = scanner.next();

        Pair<Integer, Integer> coord = findRootTag(str);
        str = scanner.compose(coord.getVal());
        scanner.sendToAllChannels();
        str = scanner.readNext();
        coord = findFirstRecordTag(str);
        if (coord == TAG_NOTFOUND_COORDS) {
            throw new IllegalStateException("Record tag could not be found under the XML root");
        }

        do {
            coord = indexOf(recordEndTag, str, coord.getVal()+1, str.length);

            if (coord != TAG_NOTFOUND_COORDS) {
                str = scanner.compose(coord.getVal());
                // Write the record to active channel
                scanner.sendToChannel();
                recCounter++;

            } else {
                // If root element's end tag is detected and write it and exit
                coord = indexOf(rootEndTag, str, coord.getVal()+1, str.length);
                if (coord != TAG_NOTFOUND_COORDS) {
                    str = writeAroundRootEndTag(scanner, coord);
                    return;
                }
                str = scanner.compose(coord.getVal());
            }

        } while (scanner.hasRemaining());
    }

    private char[] writeAroundRootEndTag(XmlScanner scanner,  Pair<Integer, Integer> coord) throws IOException {
        char[] str = scanner.compose(coord.getKey()-1);
        scanner.sendToChannel();
        str = scanner.compose(UNTIL_END);
        scanner.sendToAllChannels();
        return str;
    }

    /**
     * Finds the root tag and returns the location of the root tag.
     * @param str - buffer to search in
     * @return
     */
    private Pair<Integer, Integer> findRootTag(char[] str) {
        Pair<Integer, Integer> tagCoord = TAG_NOTFOUND_COORDS;

        if (rootTag == null) {
            int recordStartPos = 0;
            // Locate root tag
            tagCoord = locateRootTag(str, str.length);
            if (tagCoord == TAG_NOTFOUND_COORDS) {
                throw new IllegalStateException("Cannot find root element. Buffer may be too small");
            }
            // Includes the delimiters < and >
            rootTag = String.valueOf(str, tagCoord.getKey(), 1 + tagCoord.getVal() - tagCoord.getKey());
            recordStartPos = tagCoord.getVal() + 1;
            // End tag including </ and >
            rootEndTag = String.format(END_TAG_FORMAT, rootTag.substring(1).split("\\s|\\>")[0]).toCharArray();
        }

        return tagCoord;
    }

    /**
     * Finds the root tag and the record tag and returns the location of the first record tag.
     * @param str - buffer to search in
     * @return
     */
    private Pair<Integer, Integer> findFirstRecordTag(char[] str) {
        Pair<Integer, Integer> tagCoord = TAG_NOTFOUND_COORDS;

        if (recordTag == null) {
            int recordStartPos = 0;
            // Locate record tag
            tagCoord = nextTagCoords(str, recordStartPos, str.length);
            if (tagCoord == TAG_NOTFOUND_COORDS) {
                return TAG_NOTFOUND_COORDS;
            }
            // Includes the delimiters < and >
            recordTag = String.valueOf(str, tagCoord.getKey(),1 + tagCoord.getVal() - tagCoord.getKey());
            // End tag including </ and >
            recordEndTag = String.format(END_TAG_FORMAT, recordTag.substring(1).split("\\s|\\>")[0]).toCharArray();
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
