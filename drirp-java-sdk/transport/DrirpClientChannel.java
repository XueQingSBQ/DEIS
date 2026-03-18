package org.bdware.drirp.transport;

import org.bdware.drirp.exception.DrirpClientException;
import org.bdware.drirp.model.DrirpMessage;

import java.net.URISyntaxException;

public interface DrirpClientChannel {
    void connect(String url) throws URISyntaxException;

    void close();

    boolean isConnected();

    DrirpMessage sendMessage(DrirpMessage request, long timeoutMs) throws DrirpClientException;
}


