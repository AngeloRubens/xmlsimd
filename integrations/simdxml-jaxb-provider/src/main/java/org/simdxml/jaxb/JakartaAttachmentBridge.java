package org.simdxml.jaxb;

import jakarta.activation.DataHandler;
import jakarta.activation.DataSource;
import jakarta.xml.bind.attachment.AttachmentMarshaller;
import jakarta.xml.bind.attachment.AttachmentUnmarshaller;
import org.simdxml.XmlAttachmentHandler;
import java.io.*;

/** Adapts the standard Jakarta JAXB attachment callbacks to the core's framework-neutral bridge. */
final class JakartaAttachmentBridge {
    private JakartaAttachmentBridge() { }

    static XmlAttachmentHandler marshaller(final AttachmentMarshaller m) {
        if (m == null) return null;
        return new XmlAttachmentHandler() {
            public boolean isXopPackage() { return m.isXOPPackage(); }
            public String addMtomAttachment(byte[] d,int o,int l,String type,String ns,String local) {
                return m.addMtomAttachment(d,o,l,type,ns,local);
            }
            // Handed the DataHandler directly, so the payload is never materialized into a byte[].
            public String addMtomAttachment(Object value,String ns,String local) {
                return value instanceof DataHandler ? m.addMtomAttachment((DataHandler)value,ns,local) : null;
            }
            public String addSwaRefAttachment(Object value) {
                return value instanceof DataHandler ? m.addSwaRefAttachment((DataHandler)value) : null;
            }
            public byte[] getAttachmentAsByteArray(String cid) { return null; }
            public byte[] toBytes(Object value) {
                if (!(value instanceof DataHandler)) return XmlAttachmentHandler.super.toBytes(value);
                try (InputStream in = ((DataHandler) value).getInputStream()) { return in.readAllBytes(); }
                catch (IOException e) { throw new IllegalStateException(e); }
            }
            public String contentType(Object value) {
                return value instanceof DataHandler ? ((DataHandler)value).getContentType()
                        : XmlAttachmentHandler.super.contentType(value);
            }
        };
    }

    static XmlAttachmentHandler unmarshaller(final AttachmentUnmarshaller u) {
        if (u == null) return null;
        return new XmlAttachmentHandler() {
            public boolean isXopPackage() { return u.isXOPPackage(); }
            public String addMtomAttachment(byte[] d,int o,int l,String type,String ns,String local) { return null; }
            public byte[] getAttachmentAsByteArray(String cid) { return u.getAttachmentAsByteArray(cid); }
            // Keyed by content-id, as the JAXB contract requires; the earlier code passed the MIME type here.
            public Object getAttachmentAsDataHandler(String cid) { return u.getAttachmentAsDataHandler(cid); }
            public Object fromBytes(byte[] data,String type) { return new DataHandler(new ByteArrayDataSource(data, type)); }
        };
    }

    /** Minimal DataSource so an inline base64 element can still bind to a DataHandler property. */
    private static final class ByteArrayDataSource implements DataSource {
        private final byte[] data; private final String type;
        ByteArrayDataSource(byte[] data, String type) {
            this.data = data; this.type = type == null ? "application/octet-stream" : type;
        }
        public InputStream getInputStream() { return new ByteArrayInputStream(data); }
        public OutputStream getOutputStream() { throw new UnsupportedOperationException(); }
        public String getContentType() { return type; }
        public String getName() { return "attachment"; }
    }
}
