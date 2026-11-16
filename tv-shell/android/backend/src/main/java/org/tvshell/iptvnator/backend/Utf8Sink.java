package org.tvshell.iptvnator.backend;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Appendable that encodes into UTF-8 bytes without allocating per call (OutputStreamWriter
 * allocates for every single character) and without ever copying what it already holds: the
 * bytes live in a list of chunks, 4 KB first and doubling up to just under 1 MB. A /parse answer
 * of tens of MB then costs its own size; a doubling array costs up to twice that, plus a final
 * copy. The body is served from the chunks ({@link #openStream()}, {@link #length()}).
 *
 * A provider answer read with {@link #readFrom} can be placed into another sink by reference
 * ({@link #appendRange}): an Xtream catalogue is then held once, from the socket to the WebView.
 *
 * An unpaired surrogate becomes '?', as String.getBytes(UTF-8) does.
 */
final class Utf8Sink implements Appendable {

    static final int FIRST_CHUNK = 4 * 1024;
    /** Just under 1 MB: one G1 region on a desktop JVM; ART keeps such an array in its large-object space. */
    static final int MAX_CHUNK = (1 << 20) - 4096;

    /** A run of bytes inside an array this sink holds (possibly one shared with another sink). */
    static final class Slice {
        final byte[] bytes;
        final int offset;
        final int length;

        Slice(byte[] bytes, int offset, int length) {
            this.bytes = bytes;
            this.offset = offset;
            this.length = length;
        }
    }

    private final int maxChunk;
    /** Every slice here is complete; the chunk being filled is {@link #buf}. */
    private final List<Slice> full = new ArrayList<Slice>();
    private long fullBytes;
    private byte[] buf;
    private int count;
    private char pendingHigh;

    Utf8Sink() {
        this(FIRST_CHUNK, MAX_CHUNK);
    }

    Utf8Sink(int firstChunk, int maxChunk) {
        this.maxChunk = Math.max(1, maxChunk);
        this.buf = new byte[Math.max(1, Math.min(firstChunk, this.maxChunk))];
    }

    private Utf8Sink(byte[] bytes) {
        this.maxChunk = MAX_CHUNK;
        this.buf = bytes;
        this.count = bytes.length;
    }

    /** A body made of {@code bytes} as they are (not copied). */
    static Utf8Sink of(byte[] bytes) {
        return new Utf8Sink(bytes == null ? new byte[0] : bytes);
    }

    /**
     * Reads {@code in} to its end straight into chunks (no doubling buffer, no final copy).
     * Returns null as soon as more than {@code limit} bytes arrive.
     */
    static Utf8Sink readFrom(InputStream in, long limit) throws IOException {
        Utf8Sink sink = new Utf8Sink(64 * 1024, MAX_CHUNK);
        while (true) {
            if (sink.count == sink.buf.length) sink.nextChunk();
            int read = in.read(sink.buf, sink.count, sink.buf.length - sink.count);
            if (read == -1) return sink;
            sink.count += read;
            if (sink.fullBytes + sink.count > limit) return null;
        }
    }

    @Override
    public Utf8Sink append(CharSequence csq) {
        CharSequence text = csq == null ? "null" : csq;
        return append(text, 0, text.length());
    }

    @Override
    public Utf8Sink append(CharSequence csq, int start, int end) {
        CharSequence text = csq == null ? "null" : csq;
        for (int i = start; i < end; i++) append(text.charAt(i));
        return this;
    }

    @Override
    public Utf8Sink append(char c) {
        if (pendingHigh != 0) {
            char high = pendingHigh;
            pendingHigh = 0;
            if (Character.isLowSurrogate(c)) {
                int cp = Character.toCodePoint(high, c);
                put(0xF0 | (cp >> 18));
                put(0x80 | ((cp >> 12) & 0x3F));
                put(0x80 | ((cp >> 6) & 0x3F));
                put(0x80 | (cp & 0x3F));
                return this;
            }
            put('?');
        }
        if (c < 0x80) {
            put(c);
        } else if (c < 0x800) {
            put(0xC0 | (c >> 6));
            put(0x80 | (c & 0x3F));
        } else if (Character.isHighSurrogate(c)) {
            pendingHigh = c;
        } else if (Character.isLowSurrogate(c)) {
            put('?');
        } else {
            put(0xE0 | (c >> 12));
            put(0x80 | ((c >> 6) & 0x3F));
            put(0x80 | (c & 0x3F));
        }
        return this;
    }

    /**
     * Appends bytes {@code from} (inclusive) to {@code to} (exclusive) of {@code source} by
     * reference: the arrays are shared, not copied, so {@code source} must not change afterwards.
     */
    Utf8Sink appendRange(Utf8Sink source, long from, long to) {
        finish();
        if (count > 0) full.add(new Slice(buf, 0, count));
        fullBytes += count;
        buf = new byte[FIRST_CHUNK];
        count = 0;
        long position = 0;
        for (Slice slice : source.slices()) {
            long start = Math.max(from, position);
            long end = Math.min(to, position + slice.length);
            if (start < end) {
                full.add(new Slice(slice.bytes, slice.offset + (int) (start - position), (int) (end - start)));
                fullBytes += end - start;
            }
            position += slice.length;
        }
        return this;
    }

    /** Every byte held, in order, as slices of the arrays themselves (nothing copied). */
    List<Slice> slices() {
        finish();
        List<Slice> all = new ArrayList<Slice>(full.size() + 1);
        all.addAll(full);
        if (count > 0) all.add(new Slice(buf, 0, count));
        return all;
    }

    /** Number of bytes written (a trailing unpaired high surrogate counts as its '?'). */
    long length() {
        finish();
        return fullBytes + count;
    }

    /** The bytes, read from the chunks in place; each call opens a new stream. */
    InputStream openStream() {
        List<InputStream> parts = new ArrayList<InputStream>();
        for (Slice slice : slices()) parts.add(new ByteArrayInputStream(slice.bytes, slice.offset, slice.length));
        return new SequenceInputStream(Collections.enumeration(parts));
    }

    /** The bytes as one array: a copy, for small bodies and tests. */
    byte[] toByteArray() {
        long length = length();
        if (length > Integer.MAX_VALUE) throw new IllegalStateException("body too large for one array");
        byte[] out = new byte[(int) length];
        int pos = 0;
        for (Slice slice : slices()) {
            System.arraycopy(slice.bytes, slice.offset, out, pos, slice.length);
            pos += slice.length;
        }
        return out;
    }

    private void finish() {
        if (pendingHigh != 0) {
            pendingHigh = 0;
            put('?');
        }
    }

    private void nextChunk() {
        if (count > 0) {
            full.add(new Slice(buf, 0, count));
            fullBytes += count;
        }
        buf = new byte[Math.min(maxChunk, Math.max(FIRST_CHUNK, buf.length * 2))];
        count = 0;
    }

    private void put(int b) {
        if (count == buf.length) nextChunk();
        buf[count++] = (byte) b;
    }
}
