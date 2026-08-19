package org.simdxml;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Concurrency policy shared by parser facades. Platform threads keep a lock-free local context;
 * short-lived virtual threads borrow from a bounded pool instead of pinning parser-sized state.
 * This class deliberately sits outside all scanning loops.
 */
final class ParserContextPool<T> {
    private final Supplier<T> factory;
    private final ThreadLocal<T> platformContext;
    private final ArrayBlockingQueue<T> virtualContexts;

    ParserContextPool(Supplier<T> factory) {
        this.factory = java.util.Objects.requireNonNull(factory, "factory");
        platformContext = ThreadLocal.withInitial(factory);
        int size = Math.min(64, Math.max(2, Runtime.getRuntime().availableProcessors() * 2));
        virtualContexts = new ArrayBlockingQueue<>(size);
    }

    T streamingContext() {
        return ThreadKinds.isVirtual(Thread.currentThread()) ? factory.get() : platformContext.get();
    }

    void accept(Consumer<T> operation) {
        if (!ThreadKinds.isVirtual(Thread.currentThread())) {
            operation.accept(platformContext.get());
            return;
        }
        T context = borrow();
        try { operation.accept(context); }
        finally { virtualContexts.offer(context); }
    }

    <R> R apply(Function<T, R> operation) {
        if (!ThreadKinds.isVirtual(Thread.currentThread())) return operation.apply(platformContext.get());
        T context = borrow();
        try { return operation.apply(context); }
        finally { virtualContexts.offer(context); }
    }

    void removePlatformContext() { platformContext.remove(); }

    private T borrow() {
        T context = virtualContexts.poll();
        return context != null ? context : factory.get();
    }
}
