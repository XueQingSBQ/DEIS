package org.bdware.drirp.example;

import org.bdware.drirp.auth.HmacSha256AuthProvider;
import org.bdware.drirp.filter.DrirpAuthFilter;
import org.bdware.drirp.filter.DrirpReplayProtectionFilter;
import com.google.gson.JsonObject;
import org.bdware.drirp.model.DrirpMessage;
import org.bdware.drirp.opcode.DrirpOpcode;
import org.bdware.drirp.server.DrirpDispatcher;
import org.bdware.drirp.server.DrirpGrpcServer;
import org.bdware.drirp.server.DrirpHttpServer;

public class DrirpServerExample {
    public static void main(String[] args) throws Exception {
        HmacSha256AuthProvider authProvider = new HmacSha256AuthProvider("demo-secret", "demo-key", 5 * 60 * 1000L);
        DrirpDispatcher dispatcher = new DrirpDispatcher()
                .addFilter(new DrirpAuthFilter(authProvider))
                .addFilter(new DrirpReplayProtectionFilter())
                .setResponseSigner(authProvider)
                .register(DrirpOpcode.RESOLVE, request -> {
                    JsonObject resp = new JsonObject();
                    resp.addProperty("ok", true);
                    if (request.getBody() != null && request.getBody().isJsonObject()) {
                        resp.add("request", request.getBody().getAsJsonObject());
                    }
                    return DrirpMessage.ok(DrirpOpcode.RESOLVE, request.getRid(), resp);
                })
                .register(DrirpOpcode.QUERY, request -> DrirpMessage.ok(DrirpOpcode.QUERY, request.getRid(), request.getBody()))
                .register(DrirpOpcode.REGISTER, request -> DrirpMessage.ok(DrirpOpcode.REGISTER, request.getRid(), request.getBody()))
                .register(DrirpOpcode.UPDATE, request -> DrirpMessage.ok(DrirpOpcode.UPDATE, request.getRid(), request.getBody()))
                .register(DrirpOpcode.DELETE, request -> DrirpMessage.ok(DrirpOpcode.DELETE, request.getRid(), request.getBody()))
                .register(DrirpOpcode.ROUTER_QUERY, request -> DrirpMessage.ok(DrirpOpcode.ROUTER_QUERY, request.getRid(), request.getBody()));

        DrirpHttpServer httpServer = new DrirpHttpServer(8080, dispatcher);
        httpServer.start();

        DrirpGrpcServer grpcServer = new DrirpGrpcServer(9090, dispatcher);
        grpcServer.start();
        grpcServer.blockUntilShutdown();
    }
}


