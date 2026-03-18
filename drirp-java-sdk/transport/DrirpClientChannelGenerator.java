package org.bdware.drirp.transport;

import org.bdware.drirp.exception.DrirpClientException;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class DrirpClientChannelGenerator {
    private static final Map<String, DrirpClientChannel> ADHOC_CLIENTS = new ConcurrentHashMap<>();

    private DrirpClientChannelGenerator() {
    }

    public static DrirpClientChannel create(String url) throws DrirpClientException {
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();
            if (scheme == null) {
                throw new DrirpClientException("Missing URL scheme: " + url);
            }
            switch (scheme.toLowerCase()) {
                case "http":
                case "https":
                    return new HttpDrirpClientChannel();
                case "grpc":
                case "grpcs":
                    return new GrpcDrirpClientChannel();
                default:
                    DrirpClientChannel custom = ADHOC_CLIENTS.get(scheme.toLowerCase());
                    if (custom != null) {
                        return custom;
                    }
                    throw new DrirpClientException("Unsupported URL scheme: " + scheme);
            }
        } catch (Exception e) {
            if (e instanceof DrirpClientException) {
                throw (DrirpClientException) e;
            }
            throw new DrirpClientException("Invalid URL: " + url, e);
        }
    }

    public static void addClient(String scheme, DrirpClientChannel channel) {
        if (scheme == null || channel == null) {
            return;
        }
        ADHOC_CLIENTS.put(scheme.toLowerCase(), channel);
    }
}



