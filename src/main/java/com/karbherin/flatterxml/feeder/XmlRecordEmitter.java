package com.karbherin.flatterxml.feeder;

import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.nio.channels.Pipe;

public interface XmlRecordEmitter {

    /**
     * Register a pipe to an events worker.
     * @param channel
     * @throws IOException
     * @throws XMLStreamException
     */
    void registerChannel(Pipe.SinkChannel channel) throws XMLStreamException;

    /**
     * Start events feed after skipping a few records and limited to a few records after that.
     * @throws XMLStreamException
     * @throws IOException
     */
    void startStream() throws XMLStreamException, IOException;

    /**
     * Flush and close channels to all the workers.
     * @throws XMLStreamException
     * @throws IOException
     */
    void closeAllChannels() throws XMLStreamException, IOException;

}
