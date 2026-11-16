package org.tvshell.iptvnator.backend;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * The compressed body as it comes off the wire: counts it against the cap ({@code limit}) and
 * checks it against the declared Content-Length, so a gzip or deflate answer can neither grow
 * past the cap nor end early unnoticed ({@link #requireComplete}).
 */
final class EncodedBody extends FilterInputStream {
    private final long declared;
    private final long limit;
    private final byte[] singleByte = new byte[1];
    private long count;
    private boolean eof;
    private IOException failure;

    EncodedBody(InputStream in, long declared, long limit) {
        super(in);
        this.declared = declared;
        this.limit = limit;
    }

    @Override
    public int read() throws IOException {
        return read(singleByte, 0, 1) == -1 ? -1 : singleByte[0] & 0xff;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        try {
            int read = super.read(buffer, offset, length);
            if (read == -1) eof = true;
            else count += read;
            if (count > limit) throw new HttpFetcher.TooLargeException();
            return read;
        } catch (IOException e) {
            failure = e;
            throw e;
        }
    }

    @Override
    public int available() {
        // Android's gzip decoder uses this to decide whether to try the next member.
        return eof || count == declared ? 0 : 1;
    }

    void requireComplete() throws IOException {
        if (failure != null) throw failure;
        if ((declared >= 0 && count != declared) || (declared < 0 && !eof && read() != -1)) {
            throw new IOException("Incomplete provider response");
        }
    }
}
