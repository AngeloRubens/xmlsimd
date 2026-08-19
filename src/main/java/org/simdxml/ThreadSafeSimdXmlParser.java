package org.simdxml;

/**
 * Shareable parser facade backed by ThreadLocal lock-free parser contexts.
 * Call {@link #removeThreadLocalState()} when a managed/pooled thread is permanently retired.
 */
public final class ThreadSafeSimdXmlParser {
    private final ParserContextPool<SimdXmlParser> contexts;

    ThreadSafeSimdXmlParser(int capacity, int maxDepth, VerticalProfile vertical, Utf8Validation utf8) {
        contexts = new ParserContextPool<>(() -> new SimdXmlParser(capacity, maxDepth, vertical, utf8));
    }

    public XmlDocument parse(byte[] input) { return invoke(parser -> parser.parse(input)); }
    public XmlDocument parse(byte[] input, int length) { return invoke(parser -> parser.parse(input, length)); }
    public <T> T parse(byte[] input, Class<T> type) { return invoke(parser -> parser.parse(input, type)); }
    public <T> T parse(byte[] input, int length, Class<T> type) { return invoke(parser -> parser.parse(input, length, type)); }
    public SoapMessage parseSoap(byte[] input) { return invoke(parser -> parser.parseSoap(input)); }
    public SoapMessage parseSoap(byte[] input, int length) { return invoke(parser -> parser.parseSoap(input, length)); }
    public HealthcareMessageInfo inspectHealthcare(byte[] input) { return invoke(parser -> parser.inspectHealthcare(input)); }
    public <R> R withHealthcareFlyweight(byte[] input, HealthcareFlyweightFunction<R> operation) {
        return invoke(parser -> parser.withHealthcareFlyweight(input, operation));
    }
    public PaymentMessageInfo inspectPayment(byte[] input) { return invoke(parser -> parser.inspectPayment(input)); }
    /** The parser context remains exclusively leased for the full callback, including virtual threads. */
    public <R> R withPaymentFlyweight(byte[] input, PaymentFlyweightFunction<R> operation) {
        return invoke(parser -> parser.withPaymentFlyweight(input, operation));
    }
    public VerticalMessageInfo inspectVertical(byte[] input) { return invoke(parser -> parser.inspectVertical(input)); }
    Object parseBound(byte[] input, java.util.Map<XmlExpandedName, Class<?>> roots) {
        return invoke(parser -> parser.parseBound(input, roots));
    }

    /* A returned reader owns its index until exhausted, so virtual threads receive a dedicated context. */
    public SimdXmlStreamReader stream(byte[] input) { return streamParser().stream(input); }
    public SimdXmlStreamReader stream(byte[] input, int length) { return streamParser().stream(input, length); }
    public SimdXmlStreamReader reusableStream(byte[] input) { return streamParser().reusableStream(input); }
    public SimdXmlStreamReader reusableStream(byte[] input, int length) { return streamParser().reusableStream(input, length); }
    public void scanReusable(byte[] input, XmlEventConsumer consumer) {
        invoke(parser -> { parser.scanReusable(input, consumer); return null; });
    }

    private SimdXmlParser streamParser() {
        return contexts.streamingContext();
    }
    private <T> T invoke(java.util.function.Function<SimdXmlParser, T> operation) {
        return contexts.apply(operation);
    }

    /** Releases the context belonging to the calling thread. */
    public void removeThreadLocalState() { contexts.removePlatformContext(); }
}
