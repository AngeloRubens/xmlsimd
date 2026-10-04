package org.simdxml;

import java.nio.charset.StandardCharsets;

/** Streaming HL7 v2.x projection of routing and audit fields from the MSH segment. */
final class Hl7v2Inspector {
    Hl7v2MessageInfo inspect(byte[] input) {
        int msh = findMsh(input);
        if (msh < 0) throw new XmlBindingException("MSH segment not found");
        if (msh + 3 >= input.length) throw new XmlBindingException("Truncated MSH segment");

        byte separator = input[msh + 3];
        if (separator == '\r' || separator == '\n')
            throw new XmlBindingException("Invalid MSH field separator");
        int end = msh + 4;
        while (end < input.length && input[end] != '\r' && input[end] != '\n') end++;

        return new Hl7v2MessageInfo(
                field(input, msh, end, separator, 2),
                field(input, msh, end, separator, 3),
                field(input, msh, end, separator, 4),
                field(input, msh, end, separator, 5),
                field(input, msh, end, separator, 6),
                field(input, msh, end, separator, 8),
                field(input, msh, end, separator, 9),
                field(input, msh, end, separator, 10),
                field(input, msh, end, separator, 11));
    }

    private static int findMsh(byte[] input) {
        int from = input.length >= 3 && (input[0] & 0xff) == 0xef
                && (input[1] & 0xff) == 0xbb && (input[2] & 0xff) == 0xbf ? 3 : 0;
        for (int i = from; i + 3 < input.length; i++) {
            if (i != from && input[i - 1] != '\r' && input[i - 1] != '\n') continue;
            if (input[i] == 'M' && input[i + 1] == 'S' && input[i + 2] == 'H') return i;
        }
        return -1;
    }

    private static String field(byte[] input, int from, int end, byte separator, int wanted) {
        int index = 0;
        int start = from;
        for (int i = from; i <= end; i++) {
            if (i != end && input[i] != separator) continue;
            if (index == wanted) return new String(input, start, i - start, StandardCharsets.UTF_8);
            index++;
            start = i + 1;
        }
        return "";
    }
}
