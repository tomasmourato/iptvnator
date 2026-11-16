package org.tvshell.iptvnator.backend;

import static org.tvshell.iptvnator.backend.TestMain.assertEquals;
import static org.tvshell.iptvnator.backend.TestMain.assertTrue;

import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

/** Byte pass-through of provider JSON: same answer as the decoding path, at a fraction of the heap. */
public class ProviderPayloadTest {

    private static final Charset UTF8 = HttpFetcher.UTF8;

    /** Synthetic bodies: valid, lenient, non-JSON, BOM, whitespace and non-ASCII cases. */
    private static final String[] BODIES = {
        "[]", "{}", "  [1, 2]\n", "\uFEFF{\"a\":1}", " \uFEFF \t[true,false,null]\r\n", "\u0001[0]\u0002", "42", "-0.5e+3",
        "\"synthetic\"", "{\"name\":\"Ação – 日本 \uD83C\uDFAC\",\"plot\":\"a\\nb\\u00e9\"}", "[{\"plot\":\"a\nb\"}]", "{\"a\":1,}",
        "Not JSON", "", "   ", "[1] x", "01", "1.", "-", "{\"a\" 1}", "[\"\\x\"]", "[\"\\u12G4\"]", "\uFEFF\uFEFF[1]",
        "{\"s\":\"left\uFEFFright\"}", "[1,2", "nul", "[\"tab\tin\"]", " [ { \"k\" : [ ] } ] ",
    };

    private static byte[] decodingPath(String action, byte[] body, Charset charset) {
        Map<String, Object> out = Json.object();
        out.put("action", action);
        out.put("payload", WebBackend.payloadOf(new String(body, charset)));
        return Json.writeUtf8(out).toByteArray();
    }

    /** {@code bytes} in a sink split every {@code step} bytes, so values cross chunk boundaries. */
    static Utf8Sink split(byte[] bytes, int step) {
        Utf8Sink sink = new Utf8Sink();
        for (int i = 0; i < bytes.length; i += step) {
            byte[] piece = Arrays.copyOfRange(bytes, i, Math.min(bytes.length, i + step));
            sink.appendRange(Utf8Sink.of(piece), 0, piece.length);
        }
        return sink;
    }

    private static byte[] envelope(String action, Utf8Sink body, String contentType, long decodeLimit) throws ProviderException {
        return ProviderPayload.envelope(action, new RawResult(200, body, contentType), decodeLimit).toByteArray();
    }

    public void testEnvelopeIsByteIdenticalToTheDecodingPath() throws Exception {
        for (String body : BODIES) {
            byte[] bytes = body.getBytes(UTF8);
            byte[] expected = decodingPath("get_vod_streams", bytes, UTF8);
            for (int step : new int[] { 1, 2, 3, 7, 4096 }) {
                byte[] actual = envelope("get_vod_streams", split(bytes, step), "application/json", 1 << 20);
                assertTrue("same bytes for " + Json.write(body) + " split " + step + ": " + new String(actual, UTF8),
                    Arrays.equals(expected, actual));
            }
            assertTrue("null action " + Json.write(body),
                Arrays.equals(decodingPath(null, bytes, UTF8), envelope(null, split(bytes, 5), null, 1 << 20)));
        }
    }

    public void testOnlyStrictWellFormedJsonPassesThrough() {
        for (String body : BODIES) {
            boolean raw = WebBackend.payloadOf(body) instanceof Json.Raw;
            long[] range = JsonBytes.valueRange(split(body.getBytes(UTF8), 3));
            assertEquals("passes through iff payloadOf keeps it raw: " + Json.write(body), Boolean.valueOf(raw), Boolean.valueOf(range != null));
        }
        int[][] malformed = { { 0x80 }, { 0xC0, 0xAF }, { 0xC3 }, { 0xE0, 0x80, 0xAF }, { 0xED, 0xA0, 0x80 }, { 0xF0, 0x8F, 0xBF, 0xBF },
            { 0xF4, 0x90, 0x80, 0x80 }, { 0xF5, 0x80, 0x80, 0x80 }, { 0xFF }, { 0xE2, 0x82 } };
        for (int[] sequence : malformed) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.write('[');
            out.write('"');
            for (int b : sequence) out.write(b);
            out.write('"');
            out.write(']');
            assertTrue("malformed UTF-8 is decoded, not passed: " + Arrays.toString(sequence), JsonBytes.valueRange(Utf8Sink.of(out.toByteArray())) == null);
        }
        StringBuilder deep = new StringBuilder();
        for (int i = 0; i < 100_000; i++) deep.append('[');
        assertTrue("hostile nesting is refused, no stack overflow", JsonBytes.valueRange(Utf8Sink.of(deep.toString().getBytes(UTF8))) == null);
    }

    public void testOtherCharsetsAndLargeNonJsonAreDecodedWithinTheirCap() throws Exception {
        Charset latin1 = Charset.forName("ISO-8859-1");
        byte[] latin = "[\"caf\u00e9\"]".getBytes(latin1);
        assertTrue("a declared charset is honoured",
            Arrays.equals(decodingPath("a", latin, latin1), envelope("a", Utf8Sink.of(latin), "application/json; charset=ISO-8859-1", 1 << 20)));
        byte[] json = "[1,2,3,4,5,6,7,8,9]".getBytes(UTF8);
        assertTrue("JSON passes through over the decoding cap", Arrays.equals(decodingPath("a", json, UTF8), envelope("a", Utf8Sink.of(json), null, 4)));
        try {
            envelope("a", Utf8Sink.of("Not JSON, and long".getBytes(UTF8)), null, 4);
            throw new TestMain.AssertionFailed("text over the decoding cap must be refused");
        } catch (ProviderException e) {
            assertEquals("code", "ERR_RESPONSE_TOO_LARGE", e.code);
        }
    }

    public void testChunkedReadStopsAtTheLimit() throws Exception {
        byte[] body = new byte[200_000];
        Arrays.fill(body, (byte) 'x');
        Utf8Sink sink = Utf8Sink.readFrom(new ByteArrayInputStream(body), body.length);
        assertTrue("whole body kept", Arrays.equals(body, sink.toByteArray()));
        assertTrue("held in several chunks", sink.slices().size() > 1);
        assertTrue("over the limit", Utf8Sink.readFrom(new ByteArrayInputStream(body), body.length - 1) == null);
        assertTrue("pass-through cap is a quarter of the heap at most",
            HttpFetcher.MAX_PASSTHROUGH_BYTES <= Runtime.getRuntime().maxMemory() / 4 && HttpFetcher.MAX_PASSTHROUGH_BYTES >= HttpFetcher.MAX_BODY_BYTES);
    }

    public void testRawFetchDecodesGzipAndKeepsChunkedBodies() throws Exception {
        byte[] body = "[{\"name\":\"Synthetic \u00e9\"}]".getBytes(UTF8);
        for (boolean gzip : new boolean[] { false, true }) {
            HttpServer server = CatalogServer.start(gzip ? CatalogServer.gzip(body) : body, gzip, 0);
            try {
                RawResult raw = new HttpFetcher().getRaw("http://127.0.0.1:" + server.getAddress().getPort() + "/player_api.php", null, 2000);
                assertTrue("body as received, gzip=" + gzip, Arrays.equals(body, raw.body.toByteArray()));
            } finally {
                server.stop(0);
            }
        }
    }

    static final int CHILD_HEAP_MB = 128;
    /** About 30 MB: twice the decoding cap of a 128 MB heap (16 MB), 90% of its pass-through cap (32 MB). */
    static final int CHILD_ITEMS = 75_000;

    public void testLargeCatalogPassesThroughABoundedHeap() throws Exception {
        String java = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
        ProcessBuilder builder = new ProcessBuilder(java, "-Xmx" + CHILD_HEAP_MB + "m", "-cp", System.getProperty("java.class.path"),
            LargeCatalog.class.getName(), String.valueOf(CHILD_ITEMS));
        builder.redirectErrorStream(true);
        Process child = builder.start();
        String output = new String(LocalMain.readAll(child.getInputStream()), UTF8).trim();
        assertEquals("child exit, output: " + output, 0, Integer.valueOf(child.waitFor()));
    }

    /** A local stand-in provider answering one synthetic catalogue, streamed with chunked encoding. */
    static final class CatalogServer {
        private CatalogServer() {
        }

        static byte[] gzip(byte[] body) throws IOException {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
                gzip.write(body);
            }
            return out.toByteArray();
        }

        /** Serves {@code body}, or {@code items} generated catalogue entries when {@code body} is null. */
        static HttpServer start(final byte[] body, final boolean gzip, final int items) throws IOException {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                if (gzip) exchange.getResponseHeaders().set("Content-Encoding", "gzip");
                exchange.sendResponseHeaders(200, 0); // chunked, no declared length
                try (OutputStream out = exchange.getResponseBody()) {
                    if (body != null) out.write(body);
                    else LargeCatalog.write(out, items);
                }
            });
            server.start();
            return server;
        }
    }

    /** Child JVM of {@link #testLargeCatalogPassesThroughABoundedHeap}. */
    public static final class LargeCatalog {
        private LargeCatalog() {
        }

        static byte[] item(int i) {
            return ("{\"num\":" + i + ",\"name\":\"Filme sint\u00e9tico " + i + " \u2013 A\u00e7\u00e3o\",\"stream_type\":\"movie\",\"stream_id\":" + i
                + ",\"stream_icon\":\"http://127.0.0.1:43331/art/" + i + ".jpg\",\"rating\":\"7.5\",\"added\":\"1700000000\","
                + "\"category_id\":\"201\",\"container_extension\":\"mp4\",\"plot\":\"" + "Synthetic plot. ".repeat(10) + "\"}").getBytes(UTF8);
        }

        static long write(OutputStream out, int items) throws IOException {
            long n = 1;
            out.write('[');
            for (int i = 0; i < items; i++) {
                if (i > 0) {
                    out.write(',');
                    n++;
                }
                byte[] item = item(i);
                out.write(item);
                n += item.length;
            }
            out.write(']');
            return n + 1;
        }

        public static void main(String[] args) throws Exception {
            int items = Integer.parseInt(args[0]);
            long size = write(OutputStream.nullOutputStream(), items);
            if (size <= HttpFetcher.MAX_BODY_BYTES || size > HttpFetcher.MAX_PASSTHROUGH_BYTES) {
                System.out.println("size " + size + " outside (" + HttpFetcher.MAX_BODY_BYTES + ", " + HttpFetcher.MAX_PASSTHROUGH_BYTES + "]");
                System.exit(5);
            }
            HttpServer server = CatalogServer.start(null, false, items);
            try {
                WebBackend backend = new WebBackend(new HttpFetcher(), "/api");
                String id = WebBackendTest.register(backend, "http://127.0.0.1:" + server.getAddress().getPort());
                WebBackend.Response r = backend.handle("GET", "/xtream",
                    WebBackendTest.query("targetId", id, "username", "synthetic", "password", "synthetic", "action", "get_vod_streams"), null);
                byte[] prefix = "{\"action\":\"get_vod_streams\",\"payload\":[".getBytes(UTF8);
                InputStream in = r.openBody();
                byte[] head = in.readNBytes(prefix.length);
                long total = head.length;
                byte[] buffer = new byte[64 * 1024];
                int read;
                int last = -1;
                while ((read = in.read(buffer)) != -1) {
                    total += read;
                    last = buffer[read - 1];
                }
                boolean ok = r.status == 200 && Arrays.equals(prefix, head) && last == '}' && total == r.bodyLength()
                    && total == prefix.length - 1 + size + 1;
                System.out.println("status=" + r.status + " provider=" + size + " served=" + total + " head=" + new String(head, UTF8).substring(0, 20));
                System.exit(ok ? 0 : 4);
            } catch (OutOfMemoryError e) {
                System.out.println("OutOfMemoryError");
                System.exit(3);
            } finally {
                server.stop(0);
            }
        }
    }
}
