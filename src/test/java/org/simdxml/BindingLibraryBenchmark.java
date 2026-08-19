package org.simdxml;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Unmarshaller;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlValue;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Equivalent object-binding benchmark: prewarmed simdxml JAXB-style facade versus JAXB RI. */
public final class BindingLibraryBenchmark {
    public static void main(String[] args) throws Exception {
        String mode = args.length > 0 ? args[0] : "simd-jaxb";
        int iterations = args.length > 1 ? Integer.parseInt(args[1]) : 50_000;
        int books = args.length > 2 ? Integer.parseInt(args[2]) : 32;
        byte[] xml = fixture(books);
        Body body = mode.equals("simd-jaxb") ? simd(xml.length) : mode.equals("jaxb-ri") ? jaxb() : fail(mode);
        long checksum = 0;
        for (int i = 0; i < 2_000; i++) checksum = Long.rotateLeft(checksum, 3) ^ body.bind(xml);
        long start = System.nanoTime();
        for (int i = 0; i < iterations; i++) checksum = Long.rotateLeft(checksum, 3) ^ body.bind(xml);
        double seconds = (System.nanoTime() - start) / 1e9;
        String access = mode.equals("simd-jaxb") ? SimdJaxbContext.bindingAccessStrategy() : "jaxb-runtime";
        System.out.printf(Locale.ROOT, "binding=%s access=%s size=%d bytes objects/s=%.2f ns/object=%.1f checksum=%d%n",
                mode, access, xml.length, iterations / seconds, seconds * 1e9 / iterations, checksum);
    }
    private static Body simd(int capacity) {
        SimdUnmarshaller unmarshaller = SimdJaxbContext.builder(Catalog.class)
                .withCapacity(capacity).withMaxDepth(64).build().createUnmarshaller();
        return xml -> checksum(unmarshaller.unmarshal(xml, Catalog.class));
    }
    private static Body jaxb() throws Exception {
        Unmarshaller unmarshaller = JAXBContext.newInstance(Catalog.class).createUnmarshaller();
        return xml -> checksum((Catalog) unmarshaller.unmarshal(new ByteArrayInputStream(xml)));
    }
    private static long checksum(Catalog catalog) {
        long hash = catalog.books.size();
        for (Book book : catalog.books) hash = hash * 31 + book.id + book.title.length();
        return hash;
    }
    private static byte[] fixture(int books) {
        StringBuilder xml = new StringBuilder("<catalog>");
        for (int i = 0; i < books; i++) xml.append("<book id='").append(i).append("'>XML-").append(i).append("</book>");
        return xml.append("</catalog>").toString().getBytes(StandardCharsets.UTF_8);
    }
    private static Body fail(String mode) { throw new IllegalArgumentException("Unknown binding: " + mode); }
    private interface Body { long bind(byte[] xml) throws Exception; }

    @XmlRootElement(name = "catalog") @XmlAccessorType(XmlAccessType.FIELD)
    public static final class Catalog {
        @XmlElement(name = "book") public List<Book> books = new ArrayList<>();
    }
    @XmlAccessorType(XmlAccessType.FIELD)
    public static final class Book {
        @XmlAttribute public int id;
        @XmlValue public String title;
    }
    private BindingLibraryBenchmark() { }
}
