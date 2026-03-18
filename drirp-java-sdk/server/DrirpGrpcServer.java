package org.bdware.drirp.server;

import com.google.gson.JsonElement;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import org.bdware.drirp.codec.DrirpMessageCodec;
import org.bdware.drirp.grpc.DrirpProto;
import org.bdware.drirp.grpc.DrirpServiceGrpc;
import org.bdware.drirp.model.DrirpMessage;

import java.io.IOException;

public class DrirpGrpcServer {
    private final int port;
    private final DrirpDispatcher dispatcher;
    private Server server;

    public DrirpGrpcServer(int port, DrirpDispatcher dispatcher) {
        if (dispatcher == null) {
            throw new IllegalArgumentException("dispatcher is required");
        }
        this.port = port;
        this.dispatcher = dispatcher;
    }

    public void start() throws IOException {
        server = ServerBuilder.forPort(port)
                .addService(new DrirpServiceImpl(dispatcher))
                .build()
                .start();
    }

    public void stop() {
        if (server != null) {
            server.shutdown();
        }
    }

    public void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }

    private static class DrirpServiceImpl extends DrirpServiceGrpc.DrirpServiceImplBase {
        private final DrirpDispatcher dispatcher;

        private DrirpServiceImpl(DrirpDispatcher dispatcher) {
            this.dispatcher = dispatcher;
        }

        @Override
        public void call(DrirpProto.DrirpMessage request, StreamObserver<DrirpProto.DrirpMessage> responseObserver) {
            DrirpMessage req = new DrirpMessage(
                    request.getCode(),
                    request.getRid().isEmpty() ? null : request.getRid(),
                    request.getMsg().isEmpty() ? null : request.getMsg(),
                    DrirpMessageCodec.parseBody(request.getBody())
            );
            if (request.getTs() > 0) {
                req.setTs(request.getTs());
            }
            if (request.getAuth() != null && !request.getAuth().isEmpty()) {
                JsonElement auth = DrirpMessageCodec.parseBody(request.getAuth());
                if (auth != null && auth.isJsonObject()) {
                    req.setAuth(auth.getAsJsonObject());
                }
            }
            req.setOk(request.getOk());
            if (request.getErrorCode() != null && !request.getErrorCode().isEmpty()) {
                req.setErrorCode(request.getErrorCode());
            }
            if (request.getErrorMessage() != null && !request.getErrorMessage().isEmpty()) {
                req.setErrorMessage(request.getErrorMessage());
            }
            if (request.getDetailsCount() > 0) {
                req.setDetails(new java.util.ArrayList<String>(request.getDetailsList()));
            }
            if (request.getVersion() != null && !request.getVersion().isEmpty()) {
                req.setVersion(request.getVersion());
            }
            if (request.getTraceId() != null && !request.getTraceId().isEmpty()) {
                req.setTraceId(request.getTraceId());
            }
            if (request.getNonce() != null && !request.getNonce().isEmpty()) {
                req.setNonce(request.getNonce());
            }
            if (request.getIdempotencyKey() != null && !request.getIdempotencyKey().isEmpty()) {
                req.setIdempotencyKey(request.getIdempotencyKey());
            }
            DrirpMessage resp = dispatcher.handle(req);
            if (resp == null) {
                resp = DrirpMessage.error(req.getCode(), req.getRid(), "empty response");
            }
            DrirpProto.DrirpMessage.Builder builder = DrirpProto.DrirpMessage.newBuilder()
                    .setCode(resp.getCode());
            if (resp.getRid() != null) {
                builder.setRid(resp.getRid());
            }
            if (resp.getMsg() != null) {
                builder.setMsg(resp.getMsg());
            }
            String body = DrirpMessageCodec.bodyToJson(resp.getBody());
            if (!body.isEmpty()) {
                builder.setBody(body);
            }
            if (resp.getTs() != null) {
                builder.setTs(resp.getTs());
            }
            if (resp.getAuth() != null && resp.getAuth().size() > 0) {
                builder.setAuth(resp.getAuth().toString());
            }
            if (resp.getOk() != null) {
                builder.setOk(resp.getOk());
            }
            if (resp.getErrorCode() != null) {
                builder.setErrorCode(resp.getErrorCode());
            }
            if (resp.getErrorMessage() != null) {
                builder.setErrorMessage(resp.getErrorMessage());
            }
            if (resp.getDetails() != null && !resp.getDetails().isEmpty()) {
                builder.addAllDetails(resp.getDetails());
            }
            if (resp.getVersion() != null) {
                builder.setVersion(resp.getVersion());
            }
            if (resp.getTraceId() != null) {
                builder.setTraceId(resp.getTraceId());
            }
            if (resp.getNonce() != null) {
                builder.setNonce(resp.getNonce());
            }
            if (resp.getIdempotencyKey() != null) {
                builder.setIdempotencyKey(resp.getIdempotencyKey());
            }
            responseObserver.onNext(builder.build());
            responseObserver.onCompleted();
        }
    }
}


