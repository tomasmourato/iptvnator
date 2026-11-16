package org.tvshell.iptvnator.backend;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

/** An answer kept as the bytes received (after gzip/deflate), in chunks: see {@link HttpFetcher#getRaw}. */
public final class RawResult {
    public final int status;
    final Utf8Sink body;
    public final String contentType;

    RawResult(int status, Utf8Sink body, String contentType) {
        this.status = status;
        this.body = body;
        this.contentType = contentType;
    }

    /** The charset the answer declares, UTF-8 when it declares none or an unknown one. */
    public Charset charset() {
        return HttpFetcher.charsetOf(contentType);
    }

    /** Reads a (decoded) body of at most {@code limit} bytes; {@code declared} is -1 when unknown. */
    static RawResult read(int status, String contentType, InputStream in, long declared, long limit) throws IOException {
        if (declared > limit) throw new HttpFetcher.TooLargeException();
        Utf8Sink body = Utf8Sink.readFrom(in, limit);
        if (body == null) throw new HttpFetcher.TooLargeException();
        if (declared >= 0 && body.length() != declared) throw new IOException("Incomplete provider response");
        return new RawResult(status, body, contentType);
    }
}
