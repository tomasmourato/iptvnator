package org.tvshell.iptvnator.player;

import android.content.Context;
import android.content.SharedPreferences;

import org.tvshell.iptvnator.backend.DecoderMemory;

import java.util.Map;

/** {@link DecoderMemory} in a private SharedPreferences file; the shell's Erase data clears it. */
public final class DecoderMemoryStore implements DecoderMemory.Store {
    private static final String FILE = "decoder_memory";

    private final SharedPreferences prefs;

    DecoderMemoryStore(Context context) {
        prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static void clear(Context context) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().clear().apply();
    }

    @Override
    public Map<String, ?> entries() {
        return prefs.getAll();
    }

    @Override
    public void put(String key, String value) {
        prefs.edit().putString(key, value).apply();
    }

    @Override
    public void remove(String key) {
        prefs.edit().remove(key).apply();
    }
}
