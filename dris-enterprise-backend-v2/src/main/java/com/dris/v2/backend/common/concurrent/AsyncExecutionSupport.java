package com.dris.v2.backend.common.concurrent;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

public final class AsyncExecutionSupport {
    private static final Executor DIRECT_EXECUTOR = new Executor() {
        @Override
        public void execute(Runnable command) {
            if (command != null) {
                command.run();
            }
        }
    };

    private AsyncExecutionSupport() {
    }

    public static Executor directExecutor() {
        return DIRECT_EXECUTOR;
    }

    public static <T> CompletableFuture<T> supplyAsync(Executor executor, Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(supplier, executor == null ? DIRECT_EXECUTOR : executor);
    }

    public static void execute(Executor executor, Runnable task) {
        (executor == null ? DIRECT_EXECUTOR : executor).execute(task);
    }

    public static <T> T await(CompletableFuture<T> future, String operationName) {
        try {
            return future.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(operationName + " interrupted", exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            throw new IllegalStateException(operationName + " failed", cause);
        }
    }
}