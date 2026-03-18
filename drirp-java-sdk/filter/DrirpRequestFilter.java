package org.bdware.drirp.filter;

import org.bdware.drirp.model.DrirpMessage;

public interface DrirpRequestFilter {
    DrirpMessage beforeHandle(DrirpMessage request);
}


