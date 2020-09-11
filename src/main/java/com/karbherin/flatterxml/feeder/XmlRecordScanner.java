package com.karbherin.flatterxml.feeder;

import com.karbherin.flatterxml.helper.ParsingHelpers;
import com.karbherin.flatterxml.model.FieldValue;
import static com.karbherin.flatterxml.helper.ParsingHelpers.*;

import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
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

public class XmlRecordScanner implements XmlRecordEmitter {

    private final String xmlFile;
    private final Path xmlFilePath;
    private long skipRecs;
    private long firstNRecs;

    // Includes the delimiters < and >
    private String rootTag = null;
    private String recordTag = null;
    private char[] rootEndTag = null;
    private char[] recordEndTag = null;


    private final ReadableByteChannel reader;
    private final List<Pipe.SinkChannel> pipes = new ArrayList<>();
    private final CharsetDecoder decoder;

    private final List<WritableByteChannel> channels = new ArrayList<>();
    private final WritableByteChannel outChannel;

    private static final int READ_BUFFER_SIZE = 337;

    public XmlRecordScanner(String xmlFile, long skipRecs, long firstNRecs, CharsetDecoder decoder) throws IOException {
        this.xmlFile = xmlFile;
        this.skipRecs = skipRecs;
        this.firstNRecs = firstNRecs;

        xmlFilePath = Paths.get(xmlFile);

        if (decoder == null) {
            this.decoder = Charset.defaultCharset().newDecoder();
            this.decoder.onMalformedInput(CodingErrorAction.IGNORE);
            this.decoder.onUnmappableCharacter(CodingErrorAction.IGNORE);
        } else {
            this.decoder = decoder;
        }

        reader = Files.newByteChannel(xmlFilePath, StandardOpenOption.READ);

        outChannel = Files.newByteChannel(
                Paths.get("target/test/resources/out_"+xmlFilePath.getFileName()),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    public XmlRecordScanner(String xmlFile, long skipRecs, long firstNRecs) throws IOException {
        this(xmlFile, skipRecs, firstNRecs, null);
    }

    /**
     * Register a pipe to an events worker.
     * @param channel
     * @throws XMLStreamException
     */
    @Override
    public void registerChannel(Pipe.SinkChannel channel) throws XMLStreamException {
        pipes.add(channel);
    }

    /**
     * Start events feed after skipping a few records and limited to a few records after that.
     * @throws XMLStreamException
     * @throws IOException
     */
    @Override
    public void startStream() throws XMLStreamException, IOException {
        try {
            feed();
        } finally {

            reader.close();
            closeAllChannels();
            outChannel.close();
        }
    }

    /**
     * Flush and close channels to all the workers.
     * @throws XMLStreamException
     * @throws IOException
     */
    @Override
    public void closeAllChannels() throws XMLStreamException, IOException {
        for (Pipe.SinkChannel pipe : pipes) {
            pipe.close();
        };
    }

    private void feed() throws IOException {

        ByteBuffer readBuf = ByteBuffer.allocate(READ_BUFFER_SIZE);
        int readCount = 0;

        while ((readCount += reader.read(readBuf)) > 0) {

            CharBuffer charBuf = CharBuffer.allocate(readCount);
            readBuf.rewind();
            decoder.decode(readBuf, charBuf, false);

            char[] str = charBuf.array();
            int limit = charBuf.limit();

            // Works only in the first iteration of the loop.
            // Capture the locations of first record start tag and last record end tag in the buffer
            FieldValue<Integer, Integer> leftEdge, rightEdge;
            leftEdge = findRootAndRecordTags(str, limit);
            if (recordTag == null) {
                // If only the root tag is found so far then record tag may be available in the next read
                // Write out the root element
                //sendToAllChannels();
                sendToChannel(readBuf, outChannel, 0, leftEdge.getValue()+1);
                readCount = moveRemaining(readBuf, readCount);
                continue;
            }
            // Write everything before the first record's start tag in the buffer
            sendToChannel(readBuf, outChannel, 0, leftEdge.getField());

            // Write everything from the first record's start tag to the last record's end tag in the buffer
            rightEdge = ParsingHelpers.lastIndexOf(recordEndTag, str, leftEdge.getField(), limit);
            sendToChannel(readBuf, outChannel,
                    leftEdge.getField(), 1 + rightEdge.getValue() - leftEdge.getField());

            // No end record tag was found in this buffer
            if (rightEdge == TAG_NOTFOUND_COORDS && leftEdge == TAG_NOTFOUND_COORDS) {
                sendToChannel(readBuf, outChannel, 0, readCount);
                readCount = moveRemaining(readBuf, readCount);
                continue;
            }

            // Find root tag after the last record in the buffer
            FieldValue<Integer, Integer> rootEndTagCoords = ParsingHelpers.lastIndexOf(rootEndTag, str,
                    1 + rightEdge.getValue(), limit);
            // Root end tag is present. Write it out to all channels and return
            if (rootEndTagCoords != TAG_NOTFOUND_COORDS) {
                sendToChannel(readBuf, outChannel,
                        1 + rightEdge.getValue(), rootEndTagCoords.getField() - rightEdge.getValue());
                //sendToAllChannels();
                sendToChannel(readBuf, outChannel,
                        rootEndTagCoords.getField(), 1 + rootEndTagCoords.getValue() - rootEndTagCoords.getField());
                readCount = moveRemaining(readBuf, readCount);
                continue;
            }

            // Find any start or end tag near the end of the doc and write it out till there
            rightEdge = lastTagCoords(str, rightEdge.getValue()+1, limit);
            if (rightEdge != TAG_NOTFOUND_COORDS) {
                sendToChannel(readBuf, outChannel,
                        leftEdge.getField(), 1 + rightEdge.getValue() - leftEdge.getField());
            }

            readCount = moveRemaining(readBuf, readCount);
        }
    }

    private int moveRemaining(ByteBuffer buf, int limit) {
        byte[] bytes = new byte[limit - buf.position()];
        buf.get(bytes);
        buf.rewind();
        buf.put(bytes);
        return buf.position();
    }

    /**
     * Finds the root tag and the record tag and returns the location of the first record tag.
     * @param str - buffer to search in
     * @param limit  - last position in the buffer to search within
     * @return
     */
    private FieldValue<Integer, Integer> findRootAndRecordTags(char[] str, int limit) {
        FieldValue<Integer, Integer> tagCoords = TAG_NOTFOUND_COORDS, rootTagCoords = TAG_NOTFOUND_COORDS;
        int recordStartPos = 0;
        String endTagFormat = "</%s>";

        if (rootTag == null) {
            // Locate root tag
            tagCoords = locateRootTag(str, limit);
            if (tagCoords == TAG_NOTFOUND_COORDS) {
                throw new IllegalStateException("Cannot find root element. Buffer may be too small");
            }
            rootTagCoords = tagCoords;
            // Includes the delimiters < and >
            rootTag = String.valueOf(str, tagCoords.getField(), 1 + tagCoords.getValue() - tagCoords.getField());
            recordStartPos = tagCoords.getValue() + 1;
            // End tag including </ and >
            rootEndTag = String.format(endTagFormat, rootTag.substring(1).split("\\s|\\>")[0]).toCharArray();
        }

        if (recordTag == null) {
            // Locate record tag
            tagCoords = nextTagCoords(str, recordStartPos, limit);
            if (tagCoords == TAG_NOTFOUND_COORDS) {
                return rootTagCoords;
            }
            // Includes the delimiters < and >
            recordTag = String.valueOf(str, tagCoords.getField(),1 + tagCoords.getValue() - tagCoords.getField());
            // End tag including </ and >
            recordEndTag = String.format(endTagFormat, recordTag.substring(1).split("\\s|\\>")[0]).toCharArray();
        }

        return tagCoords;
    }

    /**
     * Start document and end document events are sent to all workers.
     * @param
     * @throws IOException
     */
    private void sendToAllChannels(ByteBuffer buf, int start, int count) throws IOException {
        for (WritableByteChannel pipe: pipes) {
            sendToChannel(buf, pipe, start, count);
        }
    }

    private void sendToChannel(ByteBuffer buf, WritableByteChannel pipe, int start, int count) throws IOException {
        if (count < 1) return;

        buf.position(start);
        ByteBuffer subBuffer = buf.slice();
        subBuffer.limit(count);
        pipe.write(subBuffer);
        buf.position(start + count);
    }
}
