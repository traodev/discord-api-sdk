package fr.traodev;

import fr.traodev.gateway.ZlibStreamDecoder;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.Deflater;

import static org.junit.jupiter.api.Assertions.*;

class ZlibStreamDecoderTest {
    private static byte[] compress(Deflater d, String s) {
        byte[] in = s.getBytes(StandardCharsets.UTF_8);
        d.setInput(in);
        byte[] buf = new byte[in.length + 256];
        int n = d.deflate(buf, 0, buf.length, Deflater.SYNC_FLUSH);
        return Arrays.copyOf(buf, n);
    }

    @Test
    void decodesSharedContextAcrossMessagesAndSplitFrames() throws Exception {
        Deflater d = new Deflater();
        ZlibStreamDecoder dec = new ZlibStreamDecoder();
        byte[] m1 = compress(d, "{\"op\":10,\"d\":{\"heartbeat_interval\":41250}}");
        byte[] m2 = compress(d, "{\"op\":0,\"t\":\"MESSAGE_CREATE\",\"s\":1,\"d\":{\"content\":\"héllo wörld\"}}");

        assertEquals("{\"op\":10,\"d\":{\"heartbeat_interval\":41250}}", new String(dec.feed(m1), StandardCharsets.UTF_8));

        int cut = m2.length / 2;
        assertNull(dec.feed(Arrays.copyOfRange(m2, 0, cut)));
        String out = new String(dec.feed(Arrays.copyOfRange(m2, cut, m2.length)), StandardCharsets.UTF_8);
        assertTrue(out.contains("héllo wörld"));
    }
}
