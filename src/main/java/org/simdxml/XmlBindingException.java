package org.simdxml;

/** Raised when streamed XML cannot be mapped to the requested Java type. */
public final class XmlBindingException extends RuntimeException {
    public XmlBindingException(String message) { super(message); }
    public XmlBindingException(String message, Throwable cause) { super(message, cause); }
}
