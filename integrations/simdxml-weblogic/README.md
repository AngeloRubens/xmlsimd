# simdxml-weblogic

This module provides Oracle WebLogic Server 12c integration for the simdxml high-performance XML parser, addressing specific WebLogic 12c integration gaps:

- Context-path/XJC classloader issues
- JAXBElement handling
- Schema/type polymorphism support
- MTOM attachment processing

## WebLogic-Specific JAXB Provider

The `WeblogicSimdXmlJaxbContext` class extends the standard simdxml JAXB provider with WebLogic-specific workarounds and configurations:

### Usage

```java
// Basic usage
JAXBContext context = WeblogicSimdXmlJaxbContext.createContext(MyBean.class);

// MTOM-enabled usage
JAXBContext context = WeblogicSimdXmlJaxbContext.createMtomEnabledContext(MyBean.class);

// Context-path usage (for XJC-generated classes)
JAXBContext context = WeblogicSimdXmlJaxbContext.createContext("com.example.generated");
```

### WebLogic Server Configuration

To register simdxml as the JAXB provider in WebLogic Server:

1. Create a `jaxb.properties` file in the same package as your JAXB classes:

```
javax.xml.bind.context.factory=org.simdxml.weblogic.WeblogicSimdXmlJaxbContext
```

2. For server-wide registration (optional), add to WebLogic startup script:
   ```
   -Djavax.xml.bind.context.factory=org.simdxml.weblogic.WeblogicSimdXmlJaxbContext
   ```

### Addressed Integration Gaps

#### Context-path/XJC Issues
WebLogic's classloading can cause issues when using context paths with XJC-generated classes. The provider explicitly uses the thread context classloader and provides WebLogic-specific classloader properties.

#### JAXBElement Handling
WebLogic versions have varying levels of JAXBElement support. The provider includes workarounds for namespace URI handling and element processing.

#### Schema/Type Polymorphism
Enables WebLogic's schema polymorphism features through specific properties.

#### MTOM Support
Provides MTOM-enabled marshaller/unmarshaller configuration for WebLogic web services.

### Testing

Run the integration tests:
```bash
mvn test
```

### Requirements

- Oracle WebLogic Server 12c (12.2.1.4 or later)
- JDK 24 (for Vector API support)
- Jakarta XML Binding 4.0.2

### License

See the main project LICENSE file.