package fr.traodev.gateway;

import java.io.ByteArrayOutputStream;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

public final class ZlibStreamDecoder {
    private final Inflater inflater = new Inflater();
    private byte[] pending = new byte[16384];
    private int length;
    private final byte[] chunk = new byte[16384];

    public synchronized byte[] feed(byte[] data) throws DataFormatException {
        if (length + data.length > pending.length)
            pending = java.util.Arrays.copyOf(pending, Math.max(pending.length * 2, length + data.length));
        System.arraycopy(data, 0, pending, length, data.length);
        length += data.length;
        if (length < 4 || pending[length - 4] != 0 || pending[length - 3] != 0
            || (pending[length - 2] & 0xFF) != 0xFF || (pending[length - 1] & 0xFF) != 0xFF) return null;
        inflater.setInput(pending, 0, length);
        ByteArrayOutputStream out = new ByteArrayOutputStream(length * 4);
        int n;
        while ((n = inflater.inflate(chunk)) > 0) out.write(chunk, 0, n);
        length = 0;
        return out.toByteArray();
    }

    public synchronized void close() {
        inflater.end();
    }
}
