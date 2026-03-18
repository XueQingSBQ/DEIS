package org.bdware.drirp.transport;

import com.google.gson.JsonElement;
import org.bdware.drirp.codec.DrirpMessageCodec;
import org.bdware.drirp.exception.DrirpClientException;
import org.bdware.drirp.grpc.DrirpProto;
import org.bdware.drirp.grpc.DrirpServiceGrpc;
import org.bdware.drirp.model.DrirpMessage;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.TimeUnit;

public class GrpcDrirpClientChannel implements DrirpClientChannel {
    private ManagedChannel channel;
    private DrirpServiceGrpc.DrirpServiceBlockingStub stub;

    @Override
    public void connect(String url) throws URISyntaxException {
        URI uri = new URI(url);
        String scheme = uri.getScheme();
        if (scheme == null || (!scheme.equalsIgnoreCase("grpc") && !scheme.equalsIgnoreCase("grpcs"))) {
            throw new URISyntaxException(url, "gRPC channel only supports grpc/grpcs");
        }
        String host = uri.getHost();
        int port = uri.getPort();
        if (host == null || host.isEmpty()) {
            throw new URISyntaxException(url, "missing host");
        }
        boolean useTls = scheme.equalsIgnoreCase("grpcs");
        if (port <= 0) {
            port = useTls ? 443 : 80;
        }
        ManagedChannelBuilder<?> builder = ManagedChannelBuilder.forAddress(host, port);
        if (useTls) {
            builder.useTransportSecurity();
        } else {
            builder.usePlaintext();
        }
        channel = builder.build();
        stub = DrirpServiceGrpc.newBlockingStub(channel);
    }

    @Override
    public void close() {
        if (channel != null) {
            channel.shutdown();
            channel = null;
            stub = null;
        }
    }

    @Override
    public boolean isConnected() {
        return channel != null && !channel.isShutdown();
    }

    @Override
    public DrirpMessage sendMessage(DrirpMessage request, long timeoutMs) throws DrirpClientException {
        if (stub == null) {
            throw new DrirpClientException("gRPC channel not connected");
        }
        DrirpProto.DrirpMessage.Builder builder = DrirpProto.DrirpMessage.newBuilder()
                .setCode(request.getCode());
        if (request.getRid() != null) {
            builder.setRid(request.getRid());
        }
        if (request.getMsg() != null) {
            builder.setMsg(request.getMsg());
        }
        String bodyJson = DrirpMessageCodec.bodyToJson(request.getBody());
        if (!bodyJson.isEmpty()) {
            builder.setBody(bodyJson);
        }
        if (request.getTs() != null) {
            builder.setTs(request.getTs());
        }
        if (request.getAuth() != null && request.getAuth().size() > 0) {
            builder.setAuth(request.getAuth().toString());
        }
        if (request.getOk() != null) {
            builder.setOk(request.getOk());
        }
        if (request.getErrorCode() != null) {
            builder.setErrorCode(request.getErrorCode());
        }
        if (request.getErrorMessage() != null) {
            builder.setErrorMessage(request.getErrorMessage());
        }
        if (request.getDetails() != null && !request.getDetails().isEmpty()) {
            builder.addAllDetails(request.getDetails());
        }
        if (request.getVersion() != null) {
            builder.setVersion(request.getVersion());
        }
        if (request.getTraceId() != null) {
            builder.setTraceId(request.getTraceId());
        }
        if (request.getNonce() != null) {
            builder.setNonce(request.getNonce());
        }
        if (request.getIdempotencyKey() != null) {
            builder.setIdempotencyKey(request.getIdempotencyKey());
        }
        DrirpProto.DrirpMessage response;
        try {
            response = stub.withDeadlineAfter(timeoutMs, TimeUnit.MILLISECONDS).call(builder.build());
        } catch (Exception e) {
            throw new DrirpClientException("gRPC request failed: " + e.getMessage(), e);
        }
        String rid = response.getRid();
        if (rid != null && rid.isEmpty()) {
            rid = null;
        }
        String msg = response.getMsg();
        if (msg != null && msg.isEmpty()) {
            msg = null;
        }
        JsonElement body = DrirpMessageCodec.parseBody(response.getBody());
        DrirpMessage result = new DrirpMessage(response.getCode(), rid, msg, body);
        if (response.getTs() > 0) {
            result.setTs(response.getTs());
        }
        if (response.getAuth() != null && !response.getAuth().isEmpty()) {
            JsonElement auth = DrirpMessageCodec.parseBody(response.getAuth());
            if (auth != null && auth.isJsonObject()) {
                result.setAuth(auth.getAsJsonObject());
            }
        }
        result.setOk(response.getOk());
        if (response.getErrorCode() != null && !response.getErrorCode().isEmpty()) {
            result.setErrorCode(response.getErrorCode());
        }
        if (response.getErrorMessage() != null && !response.getErrorMessage().isEmpty()) {
            result.setErrorMessage(response.getErrorMessage());
        }
        if (response.getDetailsCount() > 0) {
            result.setDetails(new java.util.ArrayList<String>(response.getDetailsList()));
        }
        if (response.getVersion() != null && !response.getVersion().isEmpty()) {
            result.setVersion(response.getVersion());
        }
        if (response.getTraceId() != null && !response.getTraceId().isEmpty()) {
            result.setTraceId(response.getTraceId());
        }
        if (response.getNonce() != null && !response.getNonce().isEmpty()) {
            result.setNonce(response.getNonce());
        }
        if (response.getIdempotencyKey() != null && !response.getIdempotencyKey().isEmpty()) {
            result.setIdempotencyKey(response.getIdempotencyKey());
        }
        return result;
    }
}



