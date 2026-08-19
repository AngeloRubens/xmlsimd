package org.simdxml;

/** Streaming ISO 20022/SEPA projection that materializes only returned audit/routing fields. */
final class PaymentXmlInspector {
    private final SimdXmlParser parser;
    private final StringBuilder splitText = new StringBuilder(64);
    private final XmlByteSlice capturedRaw = new XmlByteSlice();
    private final PaymentFlyweight flyweight = new PaymentFlyweight();
    PaymentXmlInspector(SimdXmlParser parser) { this.parser = parser; }

    PaymentMessageInfo inspect(byte[] input, boolean vertical) {
        SimdXmlStreamReader reader = parser.reusableStream(input);
        PaymentProtocol protocol = PaymentProtocol.UNKNOWN;
        String type = null, messageId = null, controlSum = null, iban = null, bic = null;
        long transactions = -1; int capture = PaymentXmlTokens.NONE;
        String capturedValue = null; splitText.setLength(0);
        while (reader.hasNext()) {
            XmlEvent event = reader.next();
            if (event == XmlEvent.START_ELEMENT) {
                if (type == null) {
                    int message = messageType(reader, vertical);
                    if (message != PaymentXmlTokens.NONE) {
                        type = PaymentXmlTokens.messageName(message);
                        protocol = message == PaymentXmlTokens.PAIN_001 || message == PaymentXmlTokens.PAIN_002
                                ? PaymentProtocol.SEPA : PaymentProtocol.ISO_20022;
                    }
                }
                int field = field(reader, vertical);
                if (field != PaymentXmlTokens.NONE && wantedProjection(field, messageId, controlSum, iban, bic, transactions)) {
                    capture = field; capturedValue = null; splitText.setLength(0);
                }
            } else if ((event == XmlEvent.TEXT || event == XmlEvent.CDATA) && capture != PaymentXmlTokens.NONE) {
                if (capture == PaymentXmlTokens.NB_OF_TXS && capturedValue == null && splitText.length() == 0)
                    transactions = positiveLong(reader.rawTextBytes());
                else {
                    String part = reader.text();
                    if (capturedValue == null) capturedValue = part;
                    else { if (splitText.length() == 0) splitText.append(capturedValue); splitText.append(part); }
                }
            } else if (event == XmlEvent.END_ELEMENT && capture != PaymentXmlTokens.NONE && matches(reader, capture, vertical)) {
                String value = splitText.length() == 0 ? capturedValue : splitText.toString();
                if (value != null) value = value.trim();
                if (capture == PaymentXmlTokens.MSG_ID && messageId == null) messageId = value;
                else if (capture == PaymentXmlTokens.CTRL_SUM && controlSum == null) controlSum = value;
                else if (capture == PaymentXmlTokens.IBAN && iban == null) iban = value;
                else if ((capture == PaymentXmlTokens.BICFI || capture == PaymentXmlTokens.BIC) && bic == null) bic = value;
                capture = PaymentXmlTokens.NONE;
            }
        }
        return new PaymentMessageInfo(protocol, type, messageId, transactions, controlSum, iban, bic);
    }

    <R> R withFlyweight(byte[] input, boolean vertical, PaymentFlyweightFunction<R> operation) {
        fill(input, vertical, flyweight);
        return operation.apply(flyweight);
    }

    private void fill(byte[] input, boolean vertical, PaymentFlyweight out) {
        SimdXmlStreamReader reader = parser.reusableStream(input);
        PaymentProtocol protocol = PaymentProtocol.UNKNOWN;
        String type = null;
        long transactions = -1;
        int capture = PaymentXmlTokens.NONE;
        String capturedValue = null; boolean rawCapture = false; splitText.setLength(0); out.clear();
        while (reader.hasNext()) {
            XmlEvent event = reader.next();
            if (event == XmlEvent.START_ELEMENT) {
                if (type == null) {
                    int message = messageType(reader, vertical);
                    if (message != PaymentXmlTokens.NONE) {
                        type = PaymentXmlTokens.messageName(message);
                        protocol = message == PaymentXmlTokens.PAIN_001 || message == PaymentXmlTokens.PAIN_002
                                ? PaymentProtocol.SEPA : PaymentProtocol.ISO_20022;
                    }
                }
                int field = field(reader, vertical);
                if (field != PaymentXmlTokens.NONE && wanted(field, out, transactions)) {
                    capture = field; capturedValue = null; rawCapture = false; splitText.setLength(0);
                }
            } else if ((event == XmlEvent.TEXT || event == XmlEvent.CDATA) && capture != PaymentXmlTokens.NONE) {
                if (capture == PaymentXmlTokens.NB_OF_TXS && capturedValue == null && splitText.length() == 0)
                    transactions = positiveLong(reader.rawTextBytes());
                else {
                    XmlByteSlice raw = reader.rawTextBytes();
                    boolean plain = event == XmlEvent.CDATA || !raw.contains((byte) '&');
                    if (capturedValue == null && !rawCapture && splitText.length() == 0 && plain) {
                        capturedRaw.reset(raw); rawCapture = true;
                    } else {
                        if (splitText.length() == 0) {
                            if (rawCapture) splitText.append(capturedRaw.decodeUtf8());
                            else if (capturedValue != null) splitText.append(capturedValue);
                        }
                        splitText.append(reader.text()); rawCapture = false; capturedValue = null;
                    }
                }
            } else if (event == XmlEvent.END_ELEMENT && capture != PaymentXmlTokens.NONE && matches(reader, capture, vertical)) {
                if (capture != PaymentXmlTokens.NB_OF_TXS) {
                    XmlValueFlyweight value = out.mutableValue(capture);
                    if (rawCapture) value.wrapPlainTrimmed(capturedRaw);
                    else {
                        String materialized = splitText.length() == 0 ? capturedValue : splitText.toString();
                        value.wrapMaterialized(materialized == null ? null : materialized.trim());
                    }
                }
                capture = PaymentXmlTokens.NONE;
            }
        }
        out.protocol(protocol); out.messageType(type); out.transactionCount(transactions);
    }

    private static int messageType(SimdXmlStreamReader reader, boolean vertical) {
        if (vertical) return PaymentXmlTokens.message(reader);
        String name = localName(reader.name());
        if ("CstmrCdtTrfInitn".equals(name)) return PaymentXmlTokens.PAIN_001;
        if ("CstmrPmtStsRpt".equals(name)) return PaymentXmlTokens.PAIN_002;
        if ("FIToFICstmrCdtTrf".equals(name)) return PaymentXmlTokens.PACS_008;
        if ("FIToFIPmtStsRpt".equals(name)) return PaymentXmlTokens.PACS_002;
        if ("BkToCstmrStmt".equals(name)) return PaymentXmlTokens.CAMT_053;
        if ("BkToCstmrDbtCdtNtfctn".equals(name)) return PaymentXmlTokens.CAMT_054;
        return PaymentXmlTokens.NONE;
    }
    private static int field(SimdXmlStreamReader reader, boolean vertical) {
        if (vertical) return PaymentXmlTokens.field(reader);
        String name = localName(reader.name());
        if ("MsgId".equals(name)) return PaymentXmlTokens.MSG_ID; if ("NbOfTxs".equals(name)) return PaymentXmlTokens.NB_OF_TXS;
        if ("CtrlSum".equals(name)) return PaymentXmlTokens.CTRL_SUM; if ("IBAN".equals(name)) return PaymentXmlTokens.IBAN;
        if ("BICFI".equals(name)) return PaymentXmlTokens.BICFI; if ("BIC".equals(name)) return PaymentXmlTokens.BIC;
        return PaymentXmlTokens.NONE;
    }
    private static boolean matches(SimdXmlStreamReader reader, int token, boolean vertical) {
        if (vertical) return PaymentXmlTokens.matches(reader, token);
        return field(reader, false) == token;
    }
    private static boolean wanted(int token, PaymentFlyweight out, long count) {
        return token == PaymentXmlTokens.MSG_ID && !out.messageId().isPresent()
                || token == PaymentXmlTokens.CTRL_SUM && !out.controlSum().isPresent()
                || token == PaymentXmlTokens.IBAN && !out.firstIban().isPresent()
                || (token == PaymentXmlTokens.BICFI || token == PaymentXmlTokens.BIC) && !out.firstBic().isPresent()
                || token == PaymentXmlTokens.NB_OF_TXS && count < 0;
    }
    private static boolean wantedProjection(int token, String msg, String sum, String iban, String bic, long count) {
        return token == PaymentXmlTokens.MSG_ID && msg == null || token == PaymentXmlTokens.CTRL_SUM && sum == null
                || token == PaymentXmlTokens.IBAN && iban == null
                || (token == PaymentXmlTokens.BICFI || token == PaymentXmlTokens.BIC) && bic == null
                || token == PaymentXmlTokens.NB_OF_TXS && count < 0;
    }
    private static long positiveLong(XmlByteSlice value) {
        long result = 0;
        if (value.length() == 0) throw new XmlBindingException("Empty NbOfTxs");
        for (int i = 0; i < value.length(); i++) {
            int digit = value.byteAt(i) - '0'; if (digit < 0 || digit > 9) throw new XmlBindingException("Invalid NbOfTxs");
            result = Math.addExact(Math.multiplyExact(result, 10), digit);
        }
        return result;
    }
    private static String localName(String name) {
        int colon = name.lastIndexOf(':');
        return colon < 0 ? name : name.substring(colon + 1);
    }
}
