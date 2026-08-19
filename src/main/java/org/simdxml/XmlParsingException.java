package org.simdxml;

/** Thrown when a document is not well formed or uses an unsupported XML feature. */
public final class XmlParsingException extends RuntimeException {
    private final int byteOffset;

    public XmlParsingException(String message, int byteOffset) {
        super(message + " at byte " + byteOffset);
        this.byteOffset = byteOffset;
    }

    public XmlParsingException(String message, int byteOffset, Throwable cause) {
        super(message + " at byte " + byteOffset, cause);
        this.byteOffset = byteOffset;
    }

    public int byteOffset() { return byteOffset; }
}
