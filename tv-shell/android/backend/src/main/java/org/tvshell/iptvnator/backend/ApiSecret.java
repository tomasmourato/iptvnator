package org.tvshell.iptvnator.backend;

import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.SecureRandom;

/**
 * The per-launch secret every {@code /api} request must carry in the {@value #HEADER} header, so
 * other apps on the device cannot use the loopback server as a fetch proxy. Only the shell's own
 * WebView holds it: a document-start script, registered natively for the shell's origin, adds the
 * header to same-origin /api requests ({@code assets/tvshell/api-secret.js}). Unlike a cookie,
 * nothing attaches it automatically, so a redirect to another local server never carries it.
 * The server never sends the value, and it is never logged ({@link #toString()} is redacted).
 */
public final class ApiSecret {

    public static final String HEADER = "X-TvShell-Api";
    /** Where {@link #startScript} puts the value in the script template. */
    public static final String PLACEHOLDER = "__TVSHELL_API_SECRET__";
    private static final int BYTES = 32;
    private static final char[] HEX = "0123456789abcdef".toCharArray();
    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final byte[] FORBIDDEN = "{\"message\":\"Forbidden\",\"status\":403}".getBytes(UTF8);

    private final String value;

    ApiSecret(String value) {
        this.value = value;
    }

    /** 256 random bits, hex-encoded (java.util.Base64 needs Android 8; the shell supports 7.1). */
    public static ApiSecret generate(SecureRandom random) {
        byte[] raw = new byte[BYTES];
        random.nextBytes(raw);
        char[] hex = new char[BYTES * 2];
        for (int i = 0; i < BYTES; i++) {
            hex[i * 2] = HEX[(raw[i] >> 4) & 0xf];
            hex[i * 2 + 1] = HEX[raw[i] & 0xf];
        }
        return new ApiSecret(new String(hex));
    }

    /** The template with this launch's value; the template must name the placeholder exactly once. */
    public String startScript(String template) {
        int at = template == null ? -1 : template.indexOf(PLACEHOLDER);
        if (at < 0 || template.indexOf(PLACEHOLDER, at + 1) >= 0) {
            throw new IllegalArgumentException("the /api script template must contain the placeholder once");
        }
        return template.replace(PLACEHOLDER, value);
    }

    /** Whether a request's {@value #HEADER} header value is this secret (constant-time comparison). */
    public boolean allows(String headerValue) {
        return headerValue != null && MessageDigest.isEqual(value.getBytes(UTF8), headerValue.getBytes(UTF8));
    }

    /**
     * The answer to a request without the secret. 403, not 401: the web app reads a 401 from its
     * backend as a server sign-in challenge.
     */
    public static WebBackend.Response forbidden() {
        return new WebBackend.Response(403, "application/json; charset=utf-8", FORBIDDEN.clone());
    }

    @Override
    public String toString() {
        return "ApiSecret[redacted]";
    }
}
