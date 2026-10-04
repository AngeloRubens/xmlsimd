package org.simdxml;

/**
 * Framework-neutral attachment bridge used by the JAXB adapters. The core never links against
 * {@code jakarta.activation} or {@code javax.activation}: {@code DataHandler} values travel through
 * this interface as {@code Object} and are only ever unwrapped inside the provider bridges.
 */
public interface XmlAttachmentHandler {
    boolean isXopPackage();

    /** MTOM attachment for a {@code byte[]}, passed by offset and length so the core never copies. */
    String addMtomAttachment(byte[] data, int offset, int length, String mimeType,
            String namespace, String localName);

    /**
     * MTOM attachment for a value the provider can take directly, typically a {@code DataHandler}.
     * Returns null when the concrete type is not accepted, in which case the caller falls back to
     * {@link #toBytes(Object)} plus the {@code byte[]} overload.
     */
    default String addMtomAttachment(Object value, String namespace, String localName) { return null; }

    /** swaRef attachment ({@code @XmlAttachmentRef}); returns the URI to write as element content. */
    default String addSwaRefAttachment(Object value) { return null; }

    byte[] getAttachmentAsByteArray(String contentId);

    /**
     * The attachment as a {@code DataHandler}, or null when the provider cannot supply one; the
     * caller then falls back to {@link #getAttachmentAsByteArray(String)} plus {@link #fromBytes}.
     */
    default Object getAttachmentAsDataHandler(String contentId) { return null; }

    /** Wraps raw bytes in the provider's {@code DataHandler}, carrying {@code contentType} through. */
    default Object fromBytes(byte[] data, String contentType) { return data; }

    default byte[] toBytes(Object value) { return value instanceof byte[] ? (byte[]) value : null; }

    default String contentType(Object value) { return "application/octet-stream"; }
}
