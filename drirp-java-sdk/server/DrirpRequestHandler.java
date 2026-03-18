package org.bdware.drirp.server;

import org.bdware.drirp.model.DrirpMessage;

public interface DrirpRequestHandler {
    DrirpMessage handle(DrirpMessage request) throws Exception;
}


