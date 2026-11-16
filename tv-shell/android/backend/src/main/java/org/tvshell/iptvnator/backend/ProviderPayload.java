package org.tvshell.iptvnator.backend;

import java.util.Map;

/**
 * The {@code {"action":..,"payload":..}} answer of the portal routes, built from a provider
 * answer kept as bytes ({@link HttpFetcher#getRaw}).
 *
 * A UTF-8 JSON answer goes into the envelope by reference ({@link Utf8Sink#appendRange}): the
 * catalogue is held once, as received, and an 80 MB get_vod_streams fits where decoding it to a
 * String (and encoding it again) would need three to five times that. Anything else (another
 * charset, lenient JSON, plain text) is decoded and goes through {@link WebBackend#payloadOf} as
 * before, within the smaller {@link HttpFetcher#MAX_BODY_BYTES} cap that path was sized for.
 * Both ways write the same bytes for the same answer.
 */
final class ProviderPayload {

    private ProviderPayload() {
    }

    static Utf8Sink envelope(String action, RawResult raw) throws ProviderException {
        return envelope(action, raw, HttpFetcher.MAX_BODY_BYTES);
    }

    static Utf8Sink envelope(String action, RawResult raw, long decodeLimit) throws ProviderException {
        long[] range = HttpFetcher.UTF8.equals(raw.charset()) ? JsonBytes.valueRange(raw.body) : null;
        if (range != null) {
            Utf8Sink out = new Utf8Sink();
            out.append("{\"action\":");
            Json.write(action, out);
            out.append(",\"payload\":");
            out.appendRange(raw.body, range[0], range[1]);
            out.append('}');
            return out;
        }
        if (raw.body.length() > decodeLimit) throw HttpFetcher.tooLarge();
        String text = new String(raw.body.toByteArray(), raw.charset());
        Map<String, Object> out = Json.object();
        out.put("action", action);
        out.put("payload", WebBackend.payloadOf(text));
        return Json.writeUtf8(out);
    }
}
