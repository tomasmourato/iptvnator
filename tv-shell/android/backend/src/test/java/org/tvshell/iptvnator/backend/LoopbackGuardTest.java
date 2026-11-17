package org.tvshell.iptvnator.backend;

import static org.tvshell.iptvnator.backend.TestMain.assertTrue;

public class LoopbackGuardTest {

    private static final int SHELL = 43330;

    public void testTheShellsOwnOriginIsAllowed() {
        assertTrue("own port", !LoopbackGuard.blocks("http", "127.0.0.1", SHELL, "/api/health", SHELL));
        assertTrue("own port, other loopback name", !LoopbackGuard.blocks("http", "localhost", SHELL, "/api/health", SHELL));
    }

    public void testBackendPathsOnOtherLocalPortsAreBlocked() {
        String[] hosts = {
            "127.0.0.1", "127.1.2.3", "127.0.0.1.", "LOCALHOST", "localhost", "synthetic.localhost", "0.0.0.0",
            "[::1]", "::1", "[0:0:0:0:0:0:0:1]", "[::]", "[::ffff:127.0.0.1]", "[::ffff:7f00:1]", "[::ffff:0:0]",
            "[::127.0.0.1]", "[::1%25lo]"
        };
        for (String host : hosts) {
            assertTrue(host + " other port", LoopbackGuard.blocks("http", host, 43331, "/api/xtream", SHELL));
            assertTrue(host + " https", LoopbackGuard.blocks("HTTPS", host, 43331, "/api", SHELL));
            assertTrue(host + " websocket", LoopbackGuard.blocks("ws", host, 43331, "/api/socket", SHELL));
        }
        assertTrue("upper-case path", LoopbackGuard.blocks("http", "127.0.0.1", 43331, "/API/health", SHELL));
        assertTrue("default http port", LoopbackGuard.blocks("http", "127.0.0.1", -1, "/api/", SHELL));
        assertTrue("default https port", LoopbackGuard.blocks("https", "localhost", -1, "/api/x", SHELL));
        assertTrue("before the server port is known", LoopbackGuard.blocks("http", "127.0.0.1", SHELL, "/api/x", -1));
    }

    public void testLocalStreamsAndLogosPass() {
        String[] paths = {"/live/synthetic/1.ts", "/clip.webm", "/apix", "/apis/logo.png", "/", "", null, "/v1/api/x"};
        for (String path : paths) assertTrue("local path " + path, !LoopbackGuard.blocks("http", "127.0.0.1", 43331, path, SHELL));
    }

    public void testProvidersAndNonNetworkUrlsPass() {
        String[] hosts = {
            "synthetic.example", "10.0.2.2", "192.168.0.10", "128.0.0.1", "[2001:db8::1]", "[::ffff:192.0.2.1]",
            "[::2]", "localhost.example", "127.0.0.1.example", "1.2.3", "[1::1::1]", "[::ffff:127.0.0.256]"
        };
        for (String host : hosts) assertTrue(host, !LoopbackGuard.blocks("http", host, 8080, "/api/x", SHELL));
        assertTrue("data URL", !LoopbackGuard.blocks("data", null, -1, null, SHELL));
        assertTrue("blob URL", !LoopbackGuard.blocks("blob", "127.0.0.1", 43331, "/api/x", SHELL));
        assertTrue("no scheme", !LoopbackGuard.blocks(null, "127.0.0.1", 43331, "/api/x", SHELL));
    }
}
