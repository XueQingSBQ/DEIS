package org.bdware.drirp.client;

import org.bdware.drirp.model.DrirpMessage;

public interface DrirpClientInterceptor {
    default void beforeSend(DrirpMessage request) {
    }

    default void afterReceive(DrirpMessage request, DrirpMessage response) {
    }

    default void onError(DrirpMessage request, Exception error) {
    }
}


