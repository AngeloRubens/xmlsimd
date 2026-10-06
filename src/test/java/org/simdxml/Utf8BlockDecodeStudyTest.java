package org.simdxml;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Utf8BlockDecodeStudyTest {
    @Test
    void matchesJdkForAsciiMixedAndSupplementaryTextAcrossBlockBoundaries() throws Exception {
        for (int prefix = 0; prefix < 40; prefix++) {
            String value = "x".repeat(prefix) + "café 東京 🧪" + "z".repeat(73);
            byte[] input = value.getBytes(StandardCharsets.UTF_8);
            char[] output = new char[input.length];
            int length = Utf8BlockDecodeBenchmark.decode(input, input.length, output);
            assertEquals(value, new String(output, 0, length), "prefix length=" + prefix);
        }
    }

    @Test
    void rejectsMalformedUtf8LikeStrictJdkDecoder() {
        byte[][] malformed = {
                {(byte) 0xc0, (byte) 0xaf},
                {(byte) 0xe2, (byte) 0x82},
                {(byte) 0xed, (byte) 0xa0, (byte) 0x80},
                {(byte) 0xf4, (byte) 0x90, (byte) 0x80, (byte) 0x80},
                {(byte) 0xe2, 0x28, (byte) 0xa1}
        };
        for (byte[] input : malformed) {
            assertThrows(MalformedInputException.class,
                    () -> Utf8BlockDecodeBenchmark.decode(input, input.length, new char[input.length]));
            assertThrows(CharacterCodingException.class,
                    () -> StandardCharsets.UTF_8.newDecoder()
                            .onMalformedInput(CodingErrorAction.REPORT)
                            .onUnmappableCharacter(CodingErrorAction.REPORT)
                            .decode(ByteBuffer.wrap(input)));
        }
    }

    @Test
    void acceptsAsciiOnlyInputThatFillsSeveralSwarBlocks() throws Exception {
        byte[] input = "<entry>ascii XML 123456789</entry>".repeat(12).getBytes(StandardCharsets.UTF_8);
        char[] output = new char[input.length];
        int length = Utf8BlockDecodeBenchmark.decode(input, input.length, output);
        assertEquals(new String(input, StandardCharsets.UTF_8), new String(output, 0, length));
    }
}
