package org.simdxml.javax;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.xml.bind.attachment.AttachmentMarshaller;
import javax.xml.bind.attachment.AttachmentUnmarshaller;
import org.simdxml.XmlAttachmentHandler;
import java.io.*;

/** Adapts the standard javax JAXB attachment callbacks to the core's framework-neutral bridge. */
final class JavaxAttachmentBridge {
    private JavaxAttachmentBridge() { }

    static XmlAttachmentHandler marshaller(final AttachmentMarshaller m) {
        if (m == null) return null;
        return new XmlAttachmentHandler() {
            public boolean isXopPackage() { return m.isXOPPackage(); }
            public String addMtomAttachment(byte[] d,int o,int l,String type,String ns,String local) {
                return m.addMtomAttachment(d,o,l,type,ns,local);
            }
            public String addMtomAttachment(Object value,String ns,String local) {
                return value instanceof DataHandler ? m.addMtomAttachment((DataHandler)value,ns,local) : null;
            }
            public String addSwaRefAttachment(Object value) {
                return value instanceof DataHandler ? m.addSwaRefAttachment((DataHandler)value) : null;
            }
            public byte[] getAttachmentAsByteArray(String cid) { return null; }
            public byte[] toBytes(Object value) {
                if (!(value instanceof DataHandler)) return XmlAttachmentHandler.super.toBytes(value);
                try (InputStream in = ((DataHandler) value).getInputStream()) {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    byte[] buffer = new byte[8192];
                    for (int read; (read = in.read(buffer)) >= 0; ) out.write(buffer, 0, read);
                    return out.toByteArray();
                } catch (IOException e) { throw new IllegalStateException(e); }
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
            public Object getAttachmentAsDataHandler(String cid) { return u.getAttachmentAsDataHandler(cid); }
            public Object fromBytes(byte[] data,String type) { return new DataHandler(new ByteArrayDataSource(data, type)); }
        };
    }

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
