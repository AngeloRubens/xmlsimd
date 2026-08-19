package org.simdxml;

/** Runtime-pluggable validator; reflection is used only to construct the optional Vector backend. */
interface Utf8ValidationStrategy {
    void validate(byte[] input, int length);
    String name();
}
