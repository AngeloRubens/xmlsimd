package org.simdxml;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Isolates how a bound value reaches the bean: generated ASM access, reflective {@code Field.set},
 * VarHandle, and reflective getter/setter pairs.
 *
 * <p>The access strategy is chosen by system property and resolved once per class, so each mode
 * needs its own JVM. Run one fork group per mode, for example:
 *
 * <pre>{@code
 * -p shape=fields  -jvmArgsAppend "-Dorg.simdxml.binding.generated=true"
 * -p shape=fields  -jvmArgsAppend "-Dorg.simdxml.binding.generated=false"
 * -p shape=fields  -jvmArgsAppend "-Dorg.simdxml.binding.generated=false -Dorg.simdxml.binding.access=varhandle"
 * }</pre>
 *
 * {@code shape=beans} uses private fields with public getter/setter pairs, which no ASM access can
 * cover, so it measures the reflective method path in every mode. The other shapes isolate the
 * remaining structural costs the plain field case hides: a primitive array instead of a list of
 * beans, an {@code @XmlElementWrapper} level, and a namespaced model, which forces the binder off
 * the empty-scope short circuit and onto expanded-name resolution.
 */
@BenchmarkMode(Mode.Throughput)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 8, time = 1)
@Fork(value = 2, jvmArgsAppend = {"-XX:+UseG1GC", "-XX:ActiveProcessorCount=2"})
@State(Scope.Benchmark)
public class BeanAccessBenchmark {
    @Param({"fields", "beans", "arrays", "wrapped", "namespaced"})
    public String shape;

    @Param({"32"})
    public int rows;

    private byte[] xml;
    private SimdUnmarshaller unmarshaller;
    private Class<?> type;

    @Setup(Level.Trial)
    public void setup() {
        xml = fixture().getBytes(StandardCharsets.UTF_8);
        unmarshaller = SimdJaxbContext.builder(type).withCapacity(xml.length).withMaxDepth(32)
                .build().createUnmarshaller();
        int size = boundSize(unmarshaller.unmarshal(xml, type));
        if (size != rows) throw new IllegalStateException("bound " + size + " rows, expected " + rows);
        // Report what the plan actually resolved, so a run cannot be mislabelled after the fact.
        System.out.println("# bean access: shape=" + shape
                + " generated=" + (GeneratedAccessFactory.get(type) != null)
                + " access=" + System.getProperty("org.simdxml.binding.access", "auto"));
    }

    private String fixture() {
        StringBuilder text = new StringBuilder();
        if ("arrays".equals(shape)) {
            type = ArrayCatalog.class;
            text.append("<catalog>");
            for (int i = 0; i < rows; i++) text.append("<value>").append(i).append("</value>");
            return text.append("</catalog>").toString();
        }
        if ("wrapped".equals(shape)) {
            type = WrappedCatalog.class;
            text.append("<catalog><rows>");
            for (int i = 0; i < rows; i++) text.append(row(i));
            return text.append("</rows></catalog>").toString();
        }
        if ("namespaced".equals(shape)) {
            type = NamespacedCatalog.class;
            text.append("<catalog xmlns='urn:bench'>");
            for (int i = 0; i < rows; i++) text.append(row(i));
            return text.append("</catalog>").toString();
        }
        type = "fields".equals(shape) ? FieldCatalog.class : BeanCatalog.class;
        text.append("<catalog>");
        for (int i = 0; i < rows; i++) text.append(row(i));
        return text.append("</catalog>").toString();
    }

    private static String row(int i) {
        return "<row><id>" + i + "</id><name>row " + i + "</name><score>" + i
                + ".5</score><active>true</active></row>";
    }

    private int boundSize(Object bound) {
        if (bound instanceof FieldCatalog) return ((FieldCatalog) bound).rows.size();
        if (bound instanceof BeanCatalog) return ((BeanCatalog) bound).getRows().size();
        if (bound instanceof ArrayCatalog) return ((ArrayCatalog) bound).value.length;
        if (bound instanceof WrappedCatalog) return ((WrappedCatalog) bound).rows.size();
        return ((NamespacedCatalog) bound).rows.size();
    }

    @Benchmark
    public void bind(Blackhole blackhole) {
        blackhole.consume(unmarshaller.unmarshal(xml, type));
    }

    @XmlRootElement(name = "catalog")
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class FieldCatalog {
        @XmlElement(name = "row") public List<FieldRow> rows = new ArrayList<FieldRow>();
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class FieldRow {
        @XmlElement public int id;
        @XmlElement public String name;
        @XmlElement public double score;
        @XmlElement public boolean active;
    }

    @XmlRootElement(name = "catalog")
    @XmlAccessorType(XmlAccessType.PROPERTY)
    public static class BeanCatalog {
        private List<BeanRow> rows = new ArrayList<BeanRow>();
        @XmlElement(name = "row") public List<BeanRow> getRows() { return rows; }
        public void setRows(List<BeanRow> value) { rows = value; }
    }

    @XmlAccessorType(XmlAccessType.PROPERTY)
    public static class BeanRow {
        private int id; private String name; private double score; private boolean active;
        @XmlElement public int getId() { return id; }
        public void setId(int value) { id = value; }
        @XmlElement public String getName() { return name; }
        public void setName(String value) { name = value; }
        @XmlElement public double getScore() { return score; }
        public void setScore(double value) { score = value; }
        @XmlElement public boolean isActive() { return active; }
        public void setActive(boolean value) { active = value; }
    }

    /** Primitive array: no List growth, no boxing beyond the conversion itself. */
    @XmlRootElement(name = "catalog")
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class ArrayCatalog {
        @XmlElement(name = "value") public int[] value;
    }

    /** One @XmlElementWrapper level between the root and the repeated element. */
    @XmlRootElement(name = "catalog")
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class WrappedCatalog {
        @jakarta.xml.bind.annotation.XmlElementWrapper(name = "rows")
        @XmlElement(name = "row") public List<FieldRow> rows = new ArrayList<FieldRow>();
    }

    /** A default namespace takes the binder off the empty-scope short circuit. */
    @XmlRootElement(name = "catalog", namespace = "urn:bench")
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class NamespacedCatalog {
        @XmlElement(name = "row", namespace = "urn:bench") public List<NamespacedRow> rows = new ArrayList<NamespacedRow>();
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class NamespacedRow {
        @XmlElement(namespace = "urn:bench") public int id;
        @XmlElement(namespace = "urn:bench") public String name;
        @XmlElement(namespace = "urn:bench") public double score;
        @XmlElement(namespace = "urn:bench") public boolean active;
    }
}
