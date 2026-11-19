package org.tvshell.iptvnator.backend;

import static org.tvshell.iptvnator.backend.TestMain.assertEquals;
import static org.tvshell.iptvnator.backend.TestMain.assertTrue;

import org.tvshell.iptvnator.backend.DecoderChain.Stage;

import java.util.HashMap;
import java.util.Map;

public class DecoderMemoryTest {
    private static final long DAY = 24L * 60 * 60 * 1000;
    private static final String URL = "http://provider.example.test/live/synthetic/1.m3u8";

    private static final class MapStore implements DecoderMemory.Store {
        final Map<String, Object> values = new HashMap<>();

        @Override
        public Map<String, ?> entries() {
            return new HashMap<>(values);
        }

        @Override
        public void put(String key, String value) {
            values.put(key, value);
        }

        @Override
        public void remove(String key) {
            values.remove(key);
        }
    }

    public void testTheKeyIsAHashNotTheUrl() {
        String key = DecoderMemory.key(URL);
        assertTrue("64 hex characters", key.matches("[0-9a-f]{64}"));
        assertEquals("stable", key, DecoderMemory.key(URL));
        assertTrue("another stream, another key", !key.equals(DecoderMemory.key(URL + "?b=2")));
        MapStore store = new MapStore();
        new DecoderMemory(store).remember(key, Stage.SOFTWARE, 0);
        assertTrue("the URL is never stored", !store.values.toString().contains("example.test"));
    }

    public void testAStageAfterHardwareIsRecalled() {
        DecoderMemory memory = new DecoderMemory(new MapStore());
        String key = DecoderMemory.key(URL);
        assertEquals("nothing yet", null, memory.recall(key, 0));
        memory.remember(key, Stage.FFMPEG, 1000);
        assertEquals("recalled", Stage.FFMPEG, memory.recall(key, 2 * DAY));
    }

    public void testHardwareIsNotStoredAndClearsTheEntry() {
        MapStore store = new MapStore();
        DecoderMemory memory = new DecoderMemory(store);
        String key = DecoderMemory.key(URL);
        memory.remember(key, Stage.SOFTWARE, 0);
        memory.remember(key, Stage.HARDWARE, 1);
        assertEquals("cleared", null, memory.recall(key, 2));
        assertTrue("nothing stored", store.values.isEmpty());
    }

    public void testEntriesExpireAfterThirtyDays() {
        MapStore store = new MapStore();
        DecoderMemory memory = new DecoderMemory(store);
        String key = DecoderMemory.key(URL);
        memory.remember(key, Stage.SOFTWARE, 0);
        assertEquals("on the last day", Stage.SOFTWARE, memory.recall(key, 30 * DAY));
        assertEquals("a day later", null, memory.recall(key, 31 * DAY));
        assertTrue("the expired entry is dropped", store.values.isEmpty());
    }

    public void testForgetDropsTheEntry() {
        DecoderMemory memory = new DecoderMemory(new MapStore());
        String key = DecoderMemory.key(URL);
        memory.remember(key, Stage.SOFTWARE, 0);
        memory.forget(key);
        assertEquals("forgotten", null, memory.recall(key, 1));
    }

    public void testTheOldestEntriesGoBeyondTheCap() {
        MapStore store = new MapStore();
        DecoderMemory memory = new DecoderMemory(store);
        for (int index = 0; index <= DecoderMemory.MAX_ENTRIES; index++) {
            memory.remember(DecoderMemory.key(URL + index), Stage.SOFTWARE, 1000 + index);
        }
        assertEquals("capped", DecoderMemory.MAX_ENTRIES, store.values.size());
        assertEquals("the oldest dropped", null, memory.recall(DecoderMemory.key(URL + 0), 2000));
        assertEquals("the newest kept", Stage.SOFTWARE,
            memory.recall(DecoderMemory.key(URL + DecoderMemory.MAX_ENTRIES), 2000));
    }

    public void testUnreadableEntriesAreDropped() {
        MapStore store = new MapStore();
        DecoderMemory memory = new DecoderMemory(store);
        String key = DecoderMemory.key(URL);
        for (Object value : new Object[] {"vulkan:5", "software:soon", "software", 7, "hardware:5", "failed:5"}) {
            store.values.put(key, value);
            assertEquals(String.valueOf(value), null, memory.recall(key, 10));
            assertTrue(value + " dropped", store.values.isEmpty());
        }
        store.values.put("stale", "software:x");
        memory.remember(key, Stage.SOFTWARE, 10);
        assertTrue("pruned on the next write", !store.values.containsKey("stale"));
    }
}
