package com.karbherin.flatterxml.feeder;

import com.karbherin.flatterxml.helper.ParsingHelpers;
import com.karbherin.flatterxml.model.Pair;
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

    // Buffer state
    private final ByteBuffer buffer = ByteBuffer.allocate(READ_BUFFER_SIZE);
    // Remaining data bytes in readBuf
    private int remaining = 0;

    private final ReadableByteChannel reader;
    private final List<Pipe.SinkChannel> pipes = new ArrayList<>();
    private final CharsetDecoder decoder;

    private final List<WritableByteChannel> channels = new ArrayList<>();
    private WritableByteChannel channel;
    private int channelNum = 0;

    private static final int READ_BUFFER_SIZE = 337;
    private static final String END_TAG_FORMAT = "</%s>";

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

        channel = Files.newByteChannel(
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
            channel.close();
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

        while ((remaining += reader.read(buffer)) > 0) {

            CharBuffer charBuf = CharBuffer.allocate(remaining);
            char[] str = charBuf.array();
            int limit = charBuf.limit();
            buffer.rewind();
            decoder.decode(buffer, charBuf, false);

            Pair<Integer, Integer> leftEdge, rightEdge, rootCoords;

            // FIND ROOT: Find root tag name in the document. Operates in first iteration of the loop.
            rootCoords = findRecordTag(str, limit);
            if (recordTag == null) {
                // Write out the root element and skip to next iteration of the loop to find the record tag
                //sendToAllChannels();
                sendToChannel( 0, 1 + rootCoords.getVal());
                moveRemaining();
                continue;
            }

            // FIND RECORD: Capture the location of first record start tag in the buffer
            leftEdge = findRecordTag(str, limit);

            // PRE RECORD: Write everything before the first record's start tag in the buffer
            sendToChannel( 0, leftEdge.getKey());

            // CLOSE RECORD: Write everything from the first record's start tag to the last record's end tag in the buffer
            rightEdge = ParsingHelpers.lastIndexOf(recordEndTag, str, leftEdge.getKey(), limit);
            if (rightEdge != TAG_NOTFOUND_COORDS) {
                sendToChannel(leftEdge.getKey(), 1 + rightEdge.getVal() - leftEdge.getKey());

                // Change pipe
                channelNum = (channelNum+1) % channels.size();
                channel = channels.get(channelNum);
            }
            else if (leftEdge == TAG_NOTFOUND_COORDS) { //rightEdge == TAG_NOTFOUND_COORDS
                // INSIDE RECORD: Neither the end tag nor the start tag for a record was found in this buffer.
                sendToChannel( 0, remaining);
                moveRemaining();
                continue;
            }

            // END ROOT: After the last record in the buffer find root tag. If found write and finish.
            if (findAndWriteAroundEndRootTag(rightEdge, str)) {
                return;
            }

            // UNCLOSED RECORD: Find any start or end tag near the end of the doc and write it out till there
            rightEdge = lastTagCoords(str, rightEdge.getVal()+1, limit);
            if (rightEdge != TAG_NOTFOUND_COORDS) {
                sendToChannel(leftEdge.getKey(), 1 + rightEdge.getVal() - leftEdge.getKey());
            }

            moveRemaining();
        }
    }

    /**
     * Adjusts pointers to the start of remaining unwritten bytes in the buffer.
     */
    private void moveRemaining() {
        byte[] bytes = new byte[remaining - buffer.position()];
        buffer.get(bytes);
        buffer.rewind();
        buffer.put(bytes);
        remaining = buffer.position();
    }

    private boolean findAndWriteAroundEndRootTag(Pair<Integer, Integer> edge, char[] str)
            throws IOException {

        // Find root tag after the last record in the buffer
        Pair<Integer, Integer> rootEndTagCoords = ParsingHelpers.lastIndexOf(
                rootEndTag, str, 1 + edge.getVal(), str.length);

        // Root end tag is present. Write it out to all channels and return
        if (rootEndTagCoords != TAG_NOTFOUND_COORDS) {
            sendToChannel(1 + edge.getVal(), rootEndTagCoords.getKey() - edge.getVal());
            //sendToAllChannels();
            sendToChannel(rootEndTagCoords.getKey(), 1 + rootEndTagCoords.getVal() - rootEndTagCoords.getKey());
            return true;
        }

        return false;
    }

    /**
     * Finds the root tag and returns the location of the root tag.
     * @param str - buffer to search in
     * @param limit  - last position in the buffer to search within
     * @return
     */
    private Pair<Integer, Integer> findRootTag(char[] str, int limit) {
        Pair<Integer, Integer> tagCoords = TAG_NOTFOUND_COORDS;

        if (rootTag == null) {
            int recordStartPos = 0;
            // Locate root tag
            tagCoords = locateRootTag(str, limit);
            if (tagCoords == TAG_NOTFOUND_COORDS) {
                throw new IllegalStateException("Cannot find root element. Buffer may be too small");
            }
            // Includes the delimiters < and >
            rootTag = String.valueOf(str, tagCoords.getKey(), 1 + tagCoords.getVal() - tagCoords.getKey());
            recordStartPos = tagCoords.getVal() + 1;
            // End tag including </ and >
            rootEndTag = String.format(END_TAG_FORMAT, rootTag.substring(1).split("\\s|\\>")[0]).toCharArray();
        }
        return tagCoords;
    }

    /**
     * Finds the root tag and the record tag and returns the location of the first record tag.
     * @param str - buffer to search in
     * @param limit  - last position in the buffer to search within
     * @return
     */
    private Pair<Integer, Integer> findRecordTag(char[] str, int limit) {
        Pair<Integer, Integer> tagCoords = TAG_NOTFOUND_COORDS;

        if (recordTag == null) {
            int recordStartPos = 0;
            // Locate record tag
            tagCoords = nextTagCoords(str, recordStartPos, limit);
            if (tagCoords == TAG_NOTFOUND_COORDS) {
                return TAG_NOTFOUND_COORDS;
            }
            // Includes the delimiters < and >
            recordTag = String.valueOf(str, tagCoords.getKey(),1 + tagCoords.getVal() - tagCoords.getKey());
            // End tag including </ and >
            recordEndTag = String.format(END_TAG_FORMAT, recordTag.substring(1).split("\\s|\\>")[0]).toCharArray();
        }
        return tagCoords;
    }

    /**
     * Start document and end document events are sent to all workers.
     * @param
     * @throws IOException
     */
    private void sendToAllChannels(int start, int count) throws IOException {
        for (WritableByteChannel pipe: pipes) {
            sendToChannel(pipe, start, count);
        }
    }

    private void sendToChannel(int start, int count) throws IOException {
        sendToChannel(channel, start, count);
    }

    private void sendToChannel(WritableByteChannel pipe, int start, int count) throws IOException {
        if (count < 1) {
            return;
        }

        buffer.position(start);
        ByteBuffer subBuffer = buffer.slice();
        subBuffer.limit(count);
        pipe.write(subBuffer);
        buffer.position(start + count);
    }
}
