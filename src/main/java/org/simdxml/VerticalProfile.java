package org.simdxml;

/**
 * Selects and executes a vertical strategy. The enum is the strategy registry: adding a
 * profile does not add a central dispatch switch to {@link SimdXmlParser}.
 */
public enum VerticalProfile {
    AUTO("auto") {
        @Override VerticalMessageInfo inspect(SimdXmlParser parser, byte[] input) {
            return VerticalProfileDetector.detect(input).inspect(parser, input);
        }
    },
    NONE("none", "off") {
        @Override VerticalMessageInfo inspect(SimdXmlParser parser, byte[] input) {
            throw new XmlBindingException("No vertical profile matched or vertical processing is disabled");
        }
    },
    SOAP_HEALTHCARE("soap", "healthcare", "soap_healthcare") {
        @Override VerticalMessageInfo inspect(SimdXmlParser parser, byte[] input) {
            return parser.inspectHealthcare(input);
        }
    },
    PAYMENTS("payment", "payments", "iso20022", "sepa") {
        @Override VerticalMessageInfo inspect(SimdXmlParser parser, byte[] input) {
            return parser.inspectPayment(input);
        }
    };

    private final String[] configurationNames;

    VerticalProfile(String... configurationNames) {
        this.configurationNames = configurationNames;
    }

    abstract VerticalMessageInfo inspect(SimdXmlParser parser, byte[] input);

    static VerticalProfile configured() {
        if (!Boolean.parseBoolean(System.getProperty("org.simdxml.vertical.enabled", "true"))) return NONE;
        String configured = System.getProperty("org.simdxml.vertical.profile", "auto")
                .toLowerCase(java.util.Locale.ROOT).replace('-', '_');
        for (VerticalProfile profile : values()) {
            for (String name : profile.configurationNames) {
                if (name.equals(configured)) return profile;
            }
        }
        throw new IllegalArgumentException("Unknown vertical profile: " + configured);
    }
}
