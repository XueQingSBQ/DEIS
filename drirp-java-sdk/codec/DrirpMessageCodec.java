package org.bdware.drirp.codec;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import org.bdware.drirp.model.DrirpMessage;

public final class DrirpMessageCodec {
    private static final Gson GSON = new Gson();

    private DrirpMessageCodec() {
    }

    public static String toJson(DrirpMessage message) {
        return GSON.toJson(message);
    }

    public static DrirpMessage fromJson(String json) {
        return GSON.fromJson(json, DrirpMessage.class);
    }

    public static String bodyToJson(JsonElement body) {
        if (body == null || body.isJsonNull()) {
            return "";
        }
        return body.toString();
    }

    public static JsonElement parseBody(String json) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            return JsonParser.parseString(json);
        } catch (Exception e) {
            return new JsonPrimitive(json);
        }
    }
}


