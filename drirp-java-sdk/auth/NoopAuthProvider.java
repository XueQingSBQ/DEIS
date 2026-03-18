package org.bdware.drirp.auth;

import org.bdware.drirp.model.DrirpMessage;

public class NoopAuthProvider implements DrirpAuthProvider {
    @Override
    public void sign(DrirpMessage message) {
        // no-op
    }

    @Override
    public boolean verify(DrirpMessage message) {
        return true;
    }
}


