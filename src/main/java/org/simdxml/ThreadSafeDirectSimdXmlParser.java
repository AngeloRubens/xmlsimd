package org.simdxml;

import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.util.function.Function;

/** Shareable direct scanner: ThreadLocal on platform threads, bounded pool on virtual threads. */
public final class ThreadSafeDirectSimdXmlParser {
    private final ParserContextPool<DirectSimdXmlParser> contexts;
    ThreadSafeDirectSimdXmlParser(int maxDepth, Utf8Validation utf8) {
        contexts = new ParserContextPool<>(() -> new DirectSimdXmlParser(maxDepth, utf8));
    }
    public void scan(ByteBuffer input, DirectXmlEventConsumer consumer) { invoke(parser -> parser.scan(input, consumer)); }
    public void scan(ByteBuffer input, DirectXmlEventConsumerEx consumer) { invoke(parser -> parser.scan(input, consumer)); }
    public void scan(MemorySegment input, DirectXmlEventConsumer consumer) { invoke(parser -> parser.scan(input, consumer)); }
    public void scan(MemorySegment input, DirectXmlEventConsumerEx consumer) { invoke(parser -> parser.scan(input, consumer)); }
    public HealthcareMessageInfo inspectHealthcare(ByteBuffer input) {
        return invokeResult(parser -> parser.inspectHealthcare(input));
    }
    public HealthcareMessageInfo inspectHealthcare(MemorySegment input) {
        return invokeResult(parser -> parser.inspectHealthcare(input));
    }
    public void removeThreadLocalState() { contexts.removePlatformContext(); }
    private void invoke(java.util.function.Consumer<DirectSimdXmlParser> action) {
        contexts.accept(action);
    }
    private <T> T invokeResult(Function<DirectSimdXmlParser, T> action) {
        return contexts.apply(action);
    }
}
