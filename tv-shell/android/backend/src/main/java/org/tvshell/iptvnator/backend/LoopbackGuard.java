package org.tvshell.iptvnator.backend;

import java.util.Locale;

/**
 * Which WebView requests the shell refuses: requests for a backend path ({@code /api} or
 * {@code /api/...}) on a loopback host and a port other than the shell's own. Defense in depth:
 * the {@link ApiSecret} header only goes to the shell's origin anyway, so this keeps page content
 * from talking to some other local backend in the shell's name. Streams and logos that another app
 * on the device serves (a local IPTV proxy) keep working. Only http(s) and ws(s) URLs reach a
 * server; other schemes are left alone. Host names that resolve to loopback only through DNS are
 * not recognised.
 */
public final class LoopbackGuard {

    private LoopbackGuard() {}

    /** {@code port} is -1 when the URL names none; {@code host} may keep IPv6 brackets; {@code path} is decoded. */
    public static boolean blocks(String scheme, String host, int port, String path, int shellPort) {
        if (scheme == null || host == null || !isBackendPath(path)) return false;
        String s = scheme.toLowerCase(Locale.ROOT);
        boolean secure = s.equals("https") || s.equals("wss");
        if (!secure && !s.equals("http") && !s.equals("ws")) return false;
        if (!isLoopbackHost(host)) return false;
        int effective = port > 0 ? port : (secure ? 443 : 80);
        return effective != shellPort;
    }

    static boolean isBackendPath(String path) {
        if (path == null) return false;
        String p = path.toLowerCase(Locale.ROOT);
        return p.equals("/api") || p.startsWith("/api/");
    }

    static boolean isLoopbackHost(String rawHost) {
        String host = rawHost.trim().toLowerCase(Locale.ROOT);
        if (host.startsWith("[") && host.endsWith("]")) host = host.substring(1, host.length() - 1);
        while (host.endsWith(".")) host = host.substring(0, host.length() - 1);
        if (host.equals("localhost") || host.endsWith(".localhost")) return true;
        if (host.indexOf(':') >= 0) return isLoopbackIpv6(host);
        int[] v4 = ipv4(host);
        return v4 != null && (v4[0] == 127 || (v4[0] == 0 && v4[1] == 0 && v4[2] == 0 && v4[3] == 0));
    }

    /** Dotted-decimal IPv4 (the form Chromium canonicalises every IPv4 host to), else null. */
    private static int[] ipv4(String host) {
        String[] parts = host.split("\\.", -1);
        if (parts.length != 4) return null;
        int[] out = new int[4];
        for (int i = 0; i < 4; i++) {
            if (parts[i].isEmpty() || parts[i].length() > 3) return null;
            for (int c = 0; c < parts[i].length(); c++) {
                if (!Character.isDigit(parts[i].charAt(c))) return null;
            }
            out[i] = Integer.parseInt(parts[i]);
            if (out[i] > 255) return null;
        }
        return out;
    }

    /** ::1, ::, and IPv4-mapped or -compatible loopback / unspecified addresses. */
    private static boolean isLoopbackIpv6(String host) {
        int zone = host.indexOf('%');
        if (zone >= 0) host = host.substring(0, zone);
        int[] groups = ipv6(host);
        if (groups == null) return false;
        for (int i = 0; i < 5; i++) if (groups[i] != 0) return false;
        boolean low = groups[6] == 0 && (groups[7] == 0 || groups[7] == 1);
        if (groups[5] == 0) return low || (groups[6] >> 8) == 127; // ::1, ::, ::127.x.x.x
        if (groups[5] == 0xffff) return (groups[6] >> 8) == 127 || (groups[6] == 0 && groups[7] == 0);
        return false;
    }

    private static int[] ipv6(String host) {
        int[] tail = null;
        String text = host;
        int lastColon = text.lastIndexOf(':');
        if (text.indexOf('.', lastColon) >= 0) { // embedded IPv4 in the last 32 bits
            int[] v4 = ipv4(text.substring(lastColon + 1));
            if (v4 == null) return null;
            tail = new int[] {(v4[0] << 8) | v4[1], (v4[2] << 8) | v4[3]};
            text = text.substring(0, lastColon + 1) + "0:0";
        }
        int gap = text.indexOf("::");
        if (gap >= 0 && text.indexOf("::", gap + 1) >= 0) return null;
        String[] head = gap >= 0 ? split(text.substring(0, gap)) : split(text);
        String[] rest = gap >= 0 ? split(text.substring(gap + 2)) : new String[0];
        int total = head.length + rest.length;
        if (total > 8 || (gap < 0 && total != 8) || (gap >= 0 && total == 8)) return null;
        int[] groups = new int[8];
        for (int i = 0; i < head.length; i++) if ((groups[i] = group(head[i])) < 0) return null;
        for (int i = 0; i < rest.length; i++) if ((groups[8 - rest.length + i] = group(rest[i])) < 0) return null;
        if (tail != null) {
            groups[6] = tail[0];
            groups[7] = tail[1];
        }
        return groups;
    }

    private static String[] split(String part) {
        return part.isEmpty() ? new String[0] : part.split(":", -1);
    }

    private static int group(String text) {
        if (text.isEmpty() || text.length() > 4) return -1;
        try {
            return Integer.parseInt(text, 16);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
