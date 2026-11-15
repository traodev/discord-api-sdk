package fr.traodev;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpServer;
import fr.traodev.event.MessageCreateEvent;
import fr.traodev.util.Json;
import org.junit.jupiter.api.Test;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.concurrent.*;
import java.util.zip.Deflater;

import static org.junit.jupiter.api.Assertions.*;

class GatewayTest {
    static final class FakeGateway implements AutoCloseable {
        final ServerSocket server;
        final BlockingQueue<Conn> connections = new LinkedBlockingQueue<>();

        FakeGateway() throws IOException {
            server = new ServerSocket(0, 10, java.net.InetAddress.getLoopbackAddress());
            Thread t = new Thread(() -> {
                try { while (true) { Socket s = server.accept(); connections.add(new Conn(s)); } } catch (IOException ignored) {}
            });
            t.setDaemon(true);
            t.start();
        }

        int port() { return server.getLocalPort(); }
        @Override public void close() throws IOException { server.close(); }
    }

    static final class Conn {
        final Socket socket;
        final BlockingQueue<JsonNode> received = new LinkedBlockingQueue<>();
        final Deflater deflater = new Deflater();
        volatile boolean compress;
        volatile String requestLine = "";
        private final OutputStream out;

        Conn(Socket s) throws IOException {
            this.socket = s;
            this.out = s.getOutputStream();
            Thread t = new Thread(this::run);
            t.setDaemon(true);
            t.start();
        }

        private void run() {
            try {
                InputStream raw = socket.getInputStream();
                StringBuilder head = new StringBuilder();
                int c, state = 0;
                while (state < 4 && (c = raw.read()) >= 0) {
                    head.append((char) c);
                    state = (c == '\r' && (state == 0 || state == 2)) || (c == '\n' && (state == 1 || state == 3)) ? state + 1 : (c == '\r' ? 1 : 0);
                }
                String h = head.toString();
                requestLine = h.lines().findFirst().orElse("");
                compress = requestLine.contains("compress=zlib-stream");
                String key = h.lines().filter(l -> l.toLowerCase().startsWith("sec-websocket-key:")).findFirst().orElseThrow().split(":")[1].trim();
                String accept = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1")
                        .digest((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes(StandardCharsets.US_ASCII)));
                out.write(("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: "
                        + accept + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                out.flush();
                DataInputStream in = new DataInputStream(raw);
                while (true) {
                    int b0 = in.read();
                    if (b0 < 0) return;
                    int op = b0 & 0x0F;
                    int b1 = in.read();
                    long len = b1 & 0x7F;
                    if (len == 126) len = in.readUnsignedShort(); else if (len == 127) len = in.readLong();
                    byte[] mask = new byte[4];
                    if ((b1 & 0x80) != 0) in.readFully(mask);
                    byte[] payload = new byte[(int) len];
                    in.readFully(payload);
                    for (int i = 0; i < payload.length; i++) payload[i] ^= mask[i % 4];
                    if (op == 8) { socket.close(); return; }
                    if (op == 1) {
                        JsonNode m = Json.parse(payload);
                        received.add(m);
                        if (m.path("op").asInt() == 1) send("{\"op\":11}");
                    }
                }
            } catch (Exception ignored) {   }
        }

        synchronized void send(String json) throws IOException {
            byte[] data = json.getBytes(StandardCharsets.UTF_8);
            int opcode = 1;
            if (compress) {
                deflater.setInput(data);
                byte[] buf = new byte[data.length + 512];
                int n = deflater.deflate(buf, 0, buf.length, Deflater.SYNC_FLUSH);
                data = Arrays.copyOf(buf, n);
                opcode = 2;
            }
            out.write(0x80 | opcode);
            if (data.length < 126) out.write(data.length);
            else if (data.length < 65536) { out.write(126); out.write(data.length >> 8); out.write(data.length); }
            else { out.write(127); for (int i = 7; i >= 0; i--) out.write((int) ((long) data.length >> (8 * i))); }
            out.write(data);
            out.flush();
        }

        JsonNode waitForOp(int op) throws Exception {
            long end = System.currentTimeMillis() + 8000;
            while (System.currentTimeMillis() < end) {
                JsonNode m = received.poll(100, TimeUnit.MILLISECONDS);
                if (m != null && m.path("op").asInt() == op) return m;
            }
            fail("timed out waiting for op " + op);
            return null;
        }
    }

    @Test
    void fullLifecycleIdentifyReadyDispatchHeartbeatResume() throws Exception {
        try (FakeGateway gw = new FakeGateway()) {
            HttpServer http = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            http.createContext("/", ex -> {
                byte[] out = ("{\"url\":\"ws://127.0.0.1:" + gw.port() + "\",\"shards\":1,\"session_start_limit\":"
                        + "{\"total\":1000,\"remaining\":999,\"reset_after\":1000,\"max_concurrency\":1}}").getBytes(StandardCharsets.UTF_8);
                ex.getResponseHeaders().add("Content-Type", "application/json");
                ex.sendResponseHeaders(200, out.length);
                ex.getResponseBody().write(out);
                ex.close();
            });
            http.start();
            System.setProperty("jdisc.api.base", "http://127.0.0.1:" + http.getAddress().getPort());

            Bot bot = Bot.builder("fake.token").shutdownHook(false).intents(Intent.GUILDS, Intent.GUILD_MESSAGES).build();
            CompletableFuture<MessageCreateEvent> msg = new CompletableFuture<>();
            bot.on(MessageCreateEvent.class, msg::complete);
            bot.start();

            Conn c1 = gw.connections.poll(5, TimeUnit.SECONDS);
            assertNotNull(c1);
            Thread.sleep(100);
            assertTrue(c1.requestLine.contains("v=10") && c1.requestLine.contains("encoding=json"), c1.requestLine);
            assertTrue(c1.compress, "zlib-stream should be negotiated by default");
            c1.send("{\"op\":10,\"d\":{\"heartbeat_interval\":300}}");
            JsonNode identify = c1.waitForOp(2);
            assertEquals("fake.token", identify.get("d").get("token").asText());
            assertEquals(Intent.GUILDS.bit() | Intent.GUILD_MESSAGES.bit(), identify.get("d").get("intents").asLong());
            assertEquals(0, identify.get("d").get("shard").get(0).asInt());
            assertEquals(1, identify.get("d").get("shard").get(1).asInt());

            c1.send("{\"op\":0,\"s\":1,\"t\":\"READY\",\"d\":{\"v\":10,\"session_id\":\"sess1\",\"resume_gateway_url\":\"ws://127.0.0.1:"
                    + gw.port() + "\",\"user\":{\"id\":\"777\",\"username\":\"mybot\",\"discriminator\":\"0\",\"bot\":true},"
                    + "\"guilds\":[{\"id\":\"10\",\"unavailable\":true}],\"application\":{\"id\":\"200\"}}}");
            bot.awaitReady();
            assertEquals(777L, bot.selfId());
            assertEquals(200L, bot.applicationId());
            assertTrue(bot.guildIds().contains(10L));

            c1.send("{\"op\":0,\"s\":2,\"t\":\"MESSAGE_CREATE\",\"d\":{\"id\":\"1\",\"channel_id\":\"2\",\"content\":\"hello gateway\"}}");
            assertEquals("hello gateway", msg.get(5, TimeUnit.SECONDS).content());

            JsonNode hb;
            do {
                hb = c1.waitForOp(1);
            } while (hb.path("d").asInt(-1) != 2);
            Thread.sleep(100);
            assertTrue(bot.latencyMs() >= 0);

            c1.send("{\"op\":7,\"d\":null}");
            Conn c2 = gw.connections.poll(10, TimeUnit.SECONDS);
            assertNotNull(c2, "client should reconnect after op 7");
            Thread.sleep(100);
            c2.send("{\"op\":10,\"d\":{\"heartbeat_interval\":5000}}");
            JsonNode resume = c2.waitForOp(6);
            assertEquals("sess1", resume.get("d").get("session_id").asText());
            assertEquals(2, resume.get("d").get("seq").asInt());
            assertEquals("fake.token", resume.get("d").get("token").asText());

            bot.close();
            http.stop(0);
        }
    }
}
