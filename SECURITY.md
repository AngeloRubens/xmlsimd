# Security policy

Please report parser vulnerabilities privately before opening a public issue. Include a minimal XML
sample, affected API/backend and JVM version. Do not attach production healthcare or payment data.

The secure default rejects DTDs and external/custom entities, validates UTF-8 and limits parser
capacity/depth. Disabling UTF-8 validation is safe only when a trusted upstream component has
already validated the exact byte sequence. Flyweight views are valid only for the documented event
or parser-reset lifetime.

Schema validation, XML Signature, XML Encryption and MTOM/XOP are separate concerns and are not yet
security claims of the core parser. See `COMPATIBILITY.md` for the executable support boundary.
