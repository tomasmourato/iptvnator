package org.tvshell.iptvnator.backend;

import java.util.List;

/**
 * {@link Json#validate} on the UTF-8 bytes of a provider answer, where they lie
 * ({@link Utf8Sink} chunks): nothing is decoded, copied or built.
 *
 * {@link #valueRange} accepts exactly the bodies whose decoded text {@link WebBackend#payloadOf}
 * would pass through as {@link Json.Raw}, and returns the byte range of the text it would write:
 * leading characters up to U+0020, one BOM and more such characters are skipped, the value must
 * be valid JSON as a browser's JSON.parse reads it, and only characters up to U+0020 may follow.
 * The bytes must also be well-formed UTF-8: decoding malformed bytes would replace them, so such
 * a body is left to the decoding path. For everything it accepts, the bytes in the range are
 * the bytes that decoding, trimming and re-encoding would produce.
 */
final class JsonBytes {

    private final List<Utf8Sink.Slice> slices;
    private int sliceIndex = -1;
    private byte[] bytes;
    private int pos;
    private int end;
    /** Bytes in the slices before the current one. */
    private long before;
    private int sliceStart;
    private int depth;

    private JsonBytes(Utf8Sink body) {
        this.slices = body.slices();
        nextSlice();
    }

    private static final class Invalid extends RuntimeException {
        private static final long serialVersionUID = 1L;

        Invalid() {
            super(null, null, false, false);
        }
    }

    /** {start, end} of the JSON value to pass through, or null when the body is not such JSON. */
    static long[] valueRange(Utf8Sink body) {
        JsonBytes reader = new JsonBytes(body);
        try {
            reader.skipTrimmed();
            if (reader.peek() == 0xEF) {
                reader.expect(0xEF);
                reader.expect(0xBB);
                reader.expect(0xBF);
                reader.skipTrimmed();
            }
            long start = reader.position();
            reader.skipValue();
            long stop = reader.position();
            reader.skipTrimmed();
            return reader.peek() == -1 ? new long[] { start, stop } : null;
        } catch (Invalid e) {
            return null;
        }
    }

    private boolean nextSlice() {
        while (++sliceIndex < slices.size()) {
            if (bytes != null) before += end - sliceStart;
            Utf8Sink.Slice slice = slices.get(sliceIndex);
            bytes = slice.bytes;
            pos = sliceStart = slice.offset;
            end = slice.offset + slice.length;
            if (pos < end) return true;
        }
        return false;
    }

    private long position() {
        return before + (pos - sliceStart);
    }

    /** The next byte (0..255), or -1 at the end. */
    private int peek() {
        if (pos == end && !nextSlice()) return -1;
        return bytes[pos] & 0xFF;
    }

    private int next() {
        int b = peek();
        if (b == -1) throw new Invalid();
        pos++;
        return b;
    }

    private void expect(int b) {
        if (next() != b) throw new Invalid();
    }

    /** What String.trim() removes: every character up to U+0020 (one byte each in UTF-8). */
    private void skipTrimmed() {
        int b;
        while ((b = peek()) != -1 && b <= 0x20) pos++;
    }

    private void skipWhitespace() {
        int b;
        while ((b = peek()) == ' ' || b == '\t' || b == '\n' || b == '\r') pos++;
    }

    private void skipValue() {
        int b = peek();
        switch (b) {
            case '{':
                container('}');
                return;
            case '[':
                container(']');
                return;
            case '"':
                skipString();
                return;
            case 't':
                word("true");
                return;
            case 'f':
                word("false");
                return;
            case 'n':
                word("null");
                return;
            default:
                if (b == '-' || isDigit(b)) {
                    skipNumber();
                    return;
                }
                throw new Invalid();
        }
    }

    private void container(int close) {
        if (++depth > Json.MAX_DEPTH) throw new Invalid();
        pos++; // { or [
        skipWhitespace();
        if (peek() == close) {
            pos++;
            depth--;
            return;
        }
        while (true) {
            skipWhitespace();
            if (close == '}') {
                if (peek() != '"') throw new Invalid();
                skipString();
                skipWhitespace();
                expect(':');
                skipWhitespace();
            }
            skipValue();
            skipWhitespace();
            int b = next();
            if (b == ',') continue;
            if (b != close) throw new Invalid();
            depth--;
            return;
        }
    }

    private void skipString() {
        pos++; // opening quote
        while (true) {
            int b = next();
            if (b == '"') return;
            if (b < 0x20) throw new Invalid();
            if (b == '\\') {
                int e = next();
                if (e == 'u') {
                    for (int i = 0; i < 4; i++) {
                        if (!isHex(next())) throw new Invalid();
                    }
                } else if ("\"\\/bfnrt".indexOf(e) < 0) {
                    throw new Invalid();
                }
            } else if (b >= 0x80) {
                skipUtf8Tail(b);
            }
        }
    }

    /** The continuation bytes of a well-formed sequence (Unicode table 3-7) led by {@code lead}. */
    private void skipUtf8Tail(int lead) {
        int lo = 0x80;
        int hi = 0xBF;
        int tail;
        if (lead >= 0xC2 && lead <= 0xDF) {
            tail = 1;
        } else if (lead >= 0xE0 && lead <= 0xEF) {
            tail = 2;
            if (lead == 0xE0) lo = 0xA0;
            if (lead == 0xED) hi = 0x9F; // no encoded surrogates
        } else if (lead >= 0xF0 && lead <= 0xF4) {
            tail = 3;
            if (lead == 0xF0) lo = 0x90;
            if (lead == 0xF4) hi = 0x8F;
        } else {
            throw new Invalid();
        }
        int b = next();
        if (b < lo || b > hi) throw new Invalid();
        for (int i = 1; i < tail; i++) {
            b = next();
            if (b < 0x80 || b > 0xBF) throw new Invalid();
        }
    }

    private void word(String word) {
        for (int i = 0; i < word.length(); i++) expect(word.charAt(i));
    }

    private void skipNumber() {
        if (peek() == '-') pos++;
        int b = next();
        if (!isDigit(b)) throw new Invalid();
        if (b != '0') skipDigits(); // a leading zero stands alone
        if (peek() == '.') {
            pos++;
            if (skipDigits() == 0) throw new Invalid();
        }
        if (peek() == 'e' || peek() == 'E') {
            pos++;
            if (peek() == '+' || peek() == '-') pos++;
            if (skipDigits() == 0) throw new Invalid();
        }
    }

    private int skipDigits() {
        int n = 0;
        while (isDigit(peek())) {
            pos++;
            n++;
        }
        return n;
    }

    private static boolean isDigit(int b) {
        return b >= '0' && b <= '9';
    }

    private static boolean isHex(int b) {
        return isDigit(b) || (b >= 'a' && b <= 'f') || (b >= 'A' && b <= 'F');
    }
}
