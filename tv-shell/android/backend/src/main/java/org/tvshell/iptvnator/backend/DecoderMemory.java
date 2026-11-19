package org.tvshell.iptvnator.backend;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.tvshell.iptvnator.backend.DecoderChain.Stage;

/**
 * Remembers, per stream, the decoder stage that played it after a fallback, so the next playback
 * of that stream starts there instead of waiting for the first stage to stall again. The key is
 * the SHA-256 of the stream URL; the URL itself is never stored. Only stages after hardware are
 * kept. An entry expires after {@link #MAX_AGE_MS}; at most {@link #MAX_ENTRIES} are kept, the
 * oldest dropped first. Pure Java over a small {@link Store}, tested on the JVM.
 */
public final class DecoderMemory {
    public static final long MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000;
    public static final int MAX_ENTRIES = 500;

    /** Key-value storage: SharedPreferences on Android. Values are "{@code stage:millis}". */
    public interface Store {
        Map<String, ?> entries();

        void put(String key, String value);

        void remove(String key);
    }

    private final Store store;

    public DecoderMemory(Store store) {
        this.store = store;
    }

    /** The key for a stream: the lower-case hex SHA-256 of its URL. */
    public static String key(String url) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(url.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) hex.append(Character.forDigit((b >> 4) & 0xf, 16)).append(Character.forDigit(b & 0xf, 16));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e); // every Java platform has SHA-256
        }
    }

    /** The remembered stage for this stream, or null; an expired or unreadable entry is dropped. */
    public Stage recall(String key, long nowMs) {
        Object value = store.entries().get(key);
        if (value == null) return null;
        Stage stage = stageOf(value);
        long savedAt = timeOf(value);
        if (stage == null || stage == Stage.HARDWARE || stage == Stage.FAILED || savedAt < 0
            || nowMs - savedAt > MAX_AGE_MS) {
            store.remove(key);
            return null;
        }
        return stage;
    }

    /** The stage played this stream; hardware needs no memory, so it clears the entry instead. */
    public void remember(String key, Stage stage, long nowMs) {
        if (stage == Stage.HARDWARE || stage == Stage.FAILED) {
            forget(key);
            return;
        }
        store.put(key, stage.id() + ":" + nowMs);
        prune(nowMs);
    }

    public void forget(String key) {
        store.remove(key);
    }

    /** Drops expired and unreadable entries, then the oldest until {@link #MAX_ENTRIES} remain. */
    private void prune(long nowMs) {
        List<Map.Entry<String, ?>> kept = new ArrayList<>();
        for (Map.Entry<String, ?> entry : store.entries().entrySet()) {
            long savedAt = timeOf(entry.getValue());
            if (stageOf(entry.getValue()) == null || savedAt < 0 || nowMs - savedAt > MAX_AGE_MS) {
                store.remove(entry.getKey());
            } else {
                kept.add(entry);
            }
        }
        if (kept.size() <= MAX_ENTRIES) return;
        kept.sort((a, b) -> Long.compare(timeOf(a.getValue()), timeOf(b.getValue())));
        for (int index = 0; index < kept.size() - MAX_ENTRIES; index++) store.remove(kept.get(index).getKey());
    }

    private static Stage stageOf(Object value) {
        if (!(value instanceof String)) return null;
        String text = (String) value;
        int colon = text.indexOf(':');
        return colon < 0 ? null : Stage.parse(text.substring(0, colon));
    }

    private static long timeOf(Object value) {
        if (!(value instanceof String)) return -1;
        String text = (String) value;
        try {
            return Long.parseLong(text.substring(text.indexOf(':') + 1));
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
