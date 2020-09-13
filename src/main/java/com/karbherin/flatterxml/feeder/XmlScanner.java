package com.karbherin.flatterxml.feeder;

import java.io.IOException;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.Pipe;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.CharsetDecoder;
import java.util.Arrays;
import java.util.List;

class XmlScanner {

    // Reader
    private final CharsetDecoder decoder;
    private final ReadableByteChannel reader;

    // Reading buffer management
    private ByteBuffer buffer = ByteBuffer.allocate(READ_BUFFER_SIZE);   // Read buffer
    private int remaining;                                  // Unexplored number of bytes

    // Management of working buffer
    private char[] str;                                     // Active character buffer to scan
    private int cursor;                                     // Marker of unexplored region within str

    // Output management
    private List<Pipe.SinkChannel> channels;
    private WritableByteChannel channel; //
    private int channelNum = 0;
    private ByteBuffer composeBuffer = ByteBuffer.allocate(COMPOSE_BUFFER_SIZE); // Buffer to compose a writing


    private static final int READ_BUFFER_SIZE = 337;
    private static final int COMPOSE_BUFFER_SIZE = 10240;
    private static final double READ_LOWER_THRESHOLD = 0.2;

    public XmlScanner(ReadableByteChannel reader, CharsetDecoder decoder, List<Pipe.SinkChannel> channels) {

        this.decoder = decoder;
        this.reader = reader;
        this.remaining = 0;
        this.channels = channels;
        this.channel = channels.get(channelNum);
        this.str = new char[0];
        this.cursor = 0;
    }

    public char[] next() throws IOException {
        if (remaining <= READ_LOWER_THRESHOLD * READ_BUFFER_SIZE) {
            read();
        }
        return Arrays.copyOfRange(str, cursor, str.length);
    }

    public char[] readNext() throws IOException {
        read();
        return Arrays.copyOfRange(str, cursor, str.length);
    }

    /**
     * Composes all the bytes up to the given end position into a write buffer.
     *
     * @param end
     * @return
     */
    public char[] compose(int end) throws IOException {
        int count = countFromCursor(end);
        buffer.position(cursor);
        ByteBuffer subBuffer = buffer.slice();
        subBuffer.limit(count);
        composeBuffer.put(subBuffer);
        // Increment to new positions in buffer and work array
        buffer.position(end + 1);
        remaining -= count;
        cursor += count;
        return next();
    }

    public boolean hasRemaining() {
        return remaining > 0;
    }

    private void reset() throws IOException {
        cursor = 0;
        remaining = 0;
        readNext();
    }

    /**
     * Send the contents of composition buffer into the active channel.
     *
     * @throws IOException
     */
    public XmlScanner sendToChannel() throws IOException {
        int count = composeBuffer.position();
        composeBuffer.rewind();
        ByteBuffer writeBuffer = ByteBuffer.allocate(count);
        for (int i = 0; i < count; i++) {
            writeBuffer.put(composeBuffer.get());
        }

        writeBuffer.rewind();
        channel.write(writeBuffer);
        composeBuffer.rewind();
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
        int count = composeBuffer.position();
        composeBuffer.rewind();
        ByteBuffer writeBuffer = ByteBuffer.allocate(count);
        for (int i = 0; i < count; i++) {
            writeBuffer.put(composeBuffer.get());
        }

        for (WritableByteChannel channel : channels) {
            writeBuffer.rewind();
            System.out.println(channel.write(writeBuffer));
        }
        composeBuffer.rewind();
        return this;
    }


    /**
     * Read data from read channel into buffer.
     * @throws IOException
     */
    private void read() throws IOException {
        moveRemaining();
        remaining += Math.max(0, reader.read(buffer));
        decodeChars();
    }

    /**
     * Decodes a byte buffer into a character array and resets position to 0.
     * @return char[] - working character array for searching and parsing
     */
    private void decodeChars() {
        CharBuffer charBuf = CharBuffer.allocate(remaining);
        buffer.rewind();
        decoder.decode(buffer, charBuf, false);
        cursor = 0;
        str = charBuf.array();
    }

    /**
     * Adjusts pointers to the start of remaining unwritten bytes in the buffer.
     * Buffer's new position is its previous limit and the same is returned.
     */
    private void moveRemaining() {
        byte[] bytes = new byte[remaining];
        buffer.get(bytes);
        buffer.rewind();
        buffer.put(bytes);
        decodeChars();
    }

    private int countFromCursor(int end) {
        int count = 0;
        if (end == -1) {
            // Consider everything left in the buffer for composition
            return str.length - cursor;
        }
        count = end + 1 - cursor;
        if (count < 0) {
            throw new ArrayIndexOutOfBoundsException("End position is earlier than the cursor in character buffer");
        }
        return count;
    }

}
