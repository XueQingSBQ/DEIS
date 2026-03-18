package org.bdware.drirp.auth;

import org.bdware.drirp.model.DrirpMessage;

public interface DrirpAuthProvider {
    void sign(DrirpMessage message);

    boolean verify(DrirpMessage message);
}


