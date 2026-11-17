package org.tvshell.iptvnator.backend;

import static org.tvshell.iptvnator.backend.TestMain.assertEquals;
import static org.tvshell.iptvnator.backend.TestMain.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.SecureRandom;

public class ApiSecretTest {

    private static final String SYNTHETIC = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    public void testOnlyTheExactValueIsAllowed() {
        ApiSecret secret = new ApiSecret(SYNTHETIC);
        assertTrue("no header", !secret.allows(null));
        assertTrue("empty header", !secret.allows(""));
        assertTrue("wrong value", !secret.allows(SYNTHETIC.replace('0', '1')));
        assertTrue("prefix of the value", !secret.allows(SYNTHETIC.substring(0, 63)));
        assertTrue("value plus extra", !secret.allows(SYNTHETIC + "0"));
        assertTrue("padded value", !secret.allows(" " + SYNTHETIC));
        assertTrue("a combined duplicate header", !secret.allows(SYNTHETIC + ", " + SYNTHETIC));
        assertTrue("exact value", secret.allows(SYNTHETIC));
    }

    public void testEachLaunchGetsItsOwnSecret() {
        SecureRandom random = new SecureRandom();
        ApiSecret first = ApiSecret.generate(random);
        ApiSecret second = ApiSecret.generate(random);
        String value = first.startScript(ApiSecret.PLACEHOLDER);
        assertTrue("256 bits in hex", value.matches("[0-9a-f]{64}"));
        assertTrue("accepts its own value", first.allows(value));
        assertTrue("a second launch rejects the first value", !second.allows(value));
    }

    public void testTheShippedScriptTemplateTakesTheValueOnce() throws IOException {
        String template = read(new FileInputStream(scriptTemplate()));
        String script = new ApiSecret(SYNTHETIC).startScript(template);
        assertTrue("value filled in", script.contains("'" + SYNTHETIC + "'"));
        assertTrue("placeholder gone", !script.contains(ApiSecret.PLACEHOLDER));
        assertTrue("names the header the server checks", script.contains("'" + ApiSecret.HEADER + "'"));
        for (String bad : new String[] {null, "no placeholder", ApiSecret.PLACEHOLDER + ApiSecret.PLACEHOLDER}) {
            try {
                new ApiSecret(SYNTHETIC).startScript(bad);
                throw new TestMain.AssertionFailed("accepted a template without exactly one placeholder");
            } catch (IllegalArgumentException expected) {
                assertTrue("message keeps the value out", !String.valueOf(expected.getMessage()).contains(SYNTHETIC));
            }
        }
    }

    public void testTheValueNeverReachesLogs() {
        ApiSecret secret = new ApiSecret(SYNTHETIC);
        assertTrue("toString redacted", !String.valueOf(secret).contains(SYNTHETIC));
        assertTrue("header redacted", !DiagnosticRedactor.redact(ApiSecret.HEADER + ": " + SYNTHETIC).contains(SYNTHETIC));
    }

    public void testForbiddenIsA403NotASignInChallenge() throws IOException {
        WebBackend.Response response = ApiSecret.forbidden();
        assertEquals("status", 403, response.status);
        assertTrue("json", response.contentType.startsWith("application/json"));
        assertEquals("not cached", "no-store", response.headers.get("Cache-Control"));
        assertTrue("no challenge header", !response.headers.containsKey("WWW-Authenticate"));
        assertEquals("body", "{\"message\":\"Forbidden\",\"status\":403}", read(response.openBody()));
    }

    /** The asset the APK injects; the backend tests run from tv-shell/android/backend. */
    private static File scriptTemplate() {
        String path = "app/src/main/assets/tvshell/api-secret.js";
        for (File base : new File[] {new File(".."), new File("."), new File("tv-shell/android")}) {
            File file = new File(base, path);
            if (file.isFile()) return file;
        }
        throw new TestMain.AssertionFailed("api-secret.js not found from " + new File(".").getAbsolutePath());
    }

    private static String read(InputStream in) throws IOException {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int n;
            while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
            return new String(out.toByteArray(), "UTF-8");
        } finally {
            in.close();
        }
    }
}
