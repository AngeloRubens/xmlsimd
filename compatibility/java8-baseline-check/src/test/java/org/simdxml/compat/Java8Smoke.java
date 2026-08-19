package org.simdxml.compat;

import java.nio.charset.StandardCharsets;
import org.simdxml.PaymentFlyweightView;
import org.simdxml.PaymentFlyweightFunction;
import org.simdxml.SimdXmlParser;
import org.simdxml.Utf8Validation;
import org.simdxml.VerticalProfile;

/** Executed by a real Java 8 runtime after the release-8 linkage compilation. */
public final class Java8Smoke {
    public static void main(String[] args) {
        final byte[] xml = ("<Document><CstmrCdtTrfInitn><GrpHdr><MsgId>java8</MsgId>"
                + "<NbOfTxs>3</NbOfTxs><CtrlSum>12.50</CtrlSum></GrpHdr>"
                + "<IBAN>IT00TEST</IBAN><BICFI>TESTIT00</BICFI></CstmrCdtTrfInitn></Document>")
                .getBytes(StandardCharsets.UTF_8);
        SimdXmlParser parser = SimdXmlParser.builder().withCapacity(4096).withMaxDepth(32)
                .withUtf8Validation(Utf8Validation.STRICT).withVerticalProfile(VerticalProfile.PAYMENTS).build();
        String result = parser.withPaymentFlyweight(xml, new PaymentFlyweightFunction<String>() {
            @Override public String apply(PaymentFlyweightView value) {
                if (!value.messageId().isZeroCopy() || value.transactionCount() != 3) throw new AssertionError();
                return value.messageId().value() + ':' + value.controlSum().value();
            }
        });
        String expectedBackend = args.length == 0 ? "scalar-swar64" : args[0];
        if (!"java8:12.50".equals(result) || !expectedBackend.equals(parser.indexingStrategy()))
            throw new AssertionError(result + " / " + parser.indexingStrategy());
        System.out.println("java8-baseline=ok backend=" + parser.indexingStrategy());
    }
    private Java8Smoke() { }
}
