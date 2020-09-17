package com.karbherin.flatterxml.feeder;

import com.karbherin.flatterxml.helper.XmlHelpers;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.Pipe;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.CharsetDecoder;
import java.util.List;

public class XmlScanner {

    // Reader
    private final CharsetDecoder decoder;
    private final ReadableByteChannel reader;
    private boolean endOfFile = false;
    private final long bytesReadLimit;

    // Reading management
    private long bytesRead = 0L;
    private int extendRead = 0;

    // Working buffer
    private ByteBuffer buffer = ByteBuffer.allocate(READ_BUFFER_SIZE);   // Read buffer

    // Output management
    private List<Pipe.SinkChannel> channels;
    private WritableByteChannel channel; //
    private int channelNum = 0;
    private ByteBuffer composeBuffer = ByteBuffer.allocate(COMPOSE_BUFFER_SIZE); // Buffer to compose a writing

    private static final int READ_BUFFER_SIZE = 2048;
    private static final int COMPOSE_BUFFER_SIZE = 2048;
    private static final int UNTIL_END = -1;

    protected XmlScanner(ReadableByteChannel reader, CharsetDecoder decoder, List<Pipe.SinkChannel> channels,
                         long bytesReadLimit) {

        this.decoder = decoder;
        this.reader = reader;
        this.channels = channels;
        this.channel = channels.get(channelNum);
        this.bytesReadLimit = bytesReadLimit;
    }

    protected XmlScanner(ReadableByteChannel reader, CharsetDecoder decoder, List<Pipe.SinkChannel> channels) {
        this(reader, decoder, channels, 0L);
        extendRead = 1;
    }

    /**
     * Reads next buffer of bytes from input, up to the bytes limit.
     * @return
     * @throws IOException
     */
    public String next() throws IOException {
        buffer.clear();

        // Calculate buffer size based on read boundary
        int bufferSize = (int) Math.min(bytesReadLimit - bytesRead - extendRead, buffer.capacity());
        buffer.limit(bufferSize < 0 ? buffer.capacity() : bufferSize);

        // Read
        int count = reader.read(buffer);
        if (count <= 0) {
            endOfFile = count < 0;
            return XmlHelpers.EMPTY;
        }
        bytesRead += count;
        return String.valueOf(decodeChars());
    }

    /**
     * Reads beyond the bytes limit.
     * @return
     * @throws IOException
     */
    public String hardNext() throws IOException {
        extendRead = 1;
        return next();
    }

    /**
     * Composes all the bytes up to the given end position into a write buffer.
     *
     * @param data
     * @return
     */
    public String compose(String data, int end) throws IOException {
        int cut = end == UNTIL_END ? data.length() : end+1;
        byte[] dataBytes = data.substring(0, cut).getBytes();

        if (composeBuffer.remaining() < dataBytes.length) {
            resizeComposeBuffer(dataBytes.length);
        }

        composeBuffer.put(dataBytes);
        return data.substring(cut) + next();
    }

    /**
     * Send the contents of composition buffer into the active channel.
     *
     * @throws IOException
     */
    public XmlScanner sendToChannel() throws IOException {
        composeBuffer.flip();
        channel.write(composeBuffer);
        composeBuffer.clear();
        return this;
    }

    /**
     * Start document and end document events are sent to all workers.
     * Send the contents of composition buffer to all channels.
     *
     * @param
     * @throws IOException
     */
    public XmlScanner sendToAllChannels() throws IOException {
        for (WritableByteChannel channel : channels) {
            composeBuffer.flip();
            channel.write(composeBuffer);
        }
        composeBuffer.clear();
        return this;
    }

    /**
     * Switches to the next output channel.
     * @return
     */
    public int switchOutputChannel() {
        channelNum = (channelNum + 1) % channels.size();
        channel = channels.get(channelNum);
        return channelNum;
    }

    /**
     * Returns if there is more data in the input stream.
     * @return
     */
    public boolean hasNext() {
        return !endOfFile && bytesRead < bytesReadLimit;
    }

    /**
     * Decodes a byte buffer into a character array and resets position to 0.
     * @return char[] - working character array for searching and parsing
     */
    private char[] decodeChars() {
        CharBuffer charBuf = CharBuffer.allocate(buffer.position());
        buffer.flip();
        decoder.decode(buffer, charBuf, buffer.position() < buffer.capacity());
        return charBuf.array();
    }

    private void resizeComposeBuffer(int dataLength) {
        // Allocate new buffer capacity
        int capacity = composeBuffer.capacity();
        int totalLength = dataLength + composeBuffer.position();
        while (capacity < totalLength) {
            capacity *= 2;
        }

        // Copy the contents
        int count = composeBuffer.position();
        ByteBuffer newBuffer = ByteBuffer.allocate(capacity);
        composeBuffer.rewind();
        ByteBuffer writeBuffer = composeBuffer.slice();
        writeBuffer.limit(count);
        newBuffer.put(writeBuffer);
        composeBuffer = newBuffer;
    }

}
