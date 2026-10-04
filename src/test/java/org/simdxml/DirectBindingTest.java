package org.simdxml;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlValue;
import org.junit.jupiter.api.Test;

import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Functional coverage for the Direct/FFM binding path used by the direct SIMD core. */
class DirectBindingTest {
    @Test
    void bindsHeapSegmentWithDirectSlicesAndGeneratedAccess() {
        byte[] xml = "<catalog><book id='7'>A &amp; SIMD</book><book id='8'>second</book></catalog>"
                .getBytes(StandardCharsets.UTF_8);

        Catalog catalog = DirectSimdXmlParser.builder().withMaxDepth(16).build()
                .bind(MemorySegment.ofArray(xml), Catalog.class);

        assertEquals(2, catalog.books.size());
        assertEquals(7, catalog.books.get(0).id);
        assertEquals("A & SIMD", catalog.books.get(0).title);
        assertEquals("second", catalog.books.get(1).title);
    }

    @Test
    void bindsByteBufferAndRejectsNamespaceModeThatNeedsNamespaceResolution() {
        ByteBuffer input = ByteBuffer.wrap("<catalog><book id='3'>x</book></catalog>"
                .getBytes(StandardCharsets.UTF_8));
        Catalog catalog = DirectSimdXmlParser.builder().build().bind(input, Catalog.class);
        assertEquals(3, catalog.books.get(0).id);
        assertThrows(XmlBindingException.class, () -> {
            byte[] namespaced = "<catalog xmlns='urn:test'/>".getBytes(StandardCharsets.UTF_8);
            DirectSimdXmlParser.builder().build().bind(MemorySegment.ofArray(namespaced), Catalog.class);
        });
    }

    @XmlRootElement(name = "catalog")
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Catalog {
        @XmlElement(name = "book")
        public List<Book> books = new ArrayList<Book>();
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Book {
        @XmlAttribute
        public int id;
        @XmlValue
        public String title;
    }
}
