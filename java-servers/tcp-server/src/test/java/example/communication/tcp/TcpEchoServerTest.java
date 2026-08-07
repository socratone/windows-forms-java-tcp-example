package example.communication.tcp;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TcpEchoServerTest {
    private TcpEchoServer server;

    @AfterEach
    void stopServer() {
        if (server != null) server.close();
    }

    @Test
    void echoesUnicodeMessagesOverOnePersistentConnection() throws Exception {
        startServer();
        try (var socket = new Socket("127.0.0.1", server.getPort());
             var reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             var writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))) {
            for (var message : new String[] { "안녕하세요", "second !@#$%" }) {
                writer.write(message);
                writer.newLine();
                writer.flush();
                assertEquals(message, reader.readLine());
            }
        }
    }

    @Test
    void handlesConcurrentClients() throws Exception {
        startServer();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(echo("첫 번째"));
            var second = executor.submit(echo("두 번째"));
            assertEquals("첫 번째", first.get());
            assertEquals("두 번째", second.get());
        }
    }

    private void startServer() throws Exception {
        server = new TcpEchoServer(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
        server.start();
    }

    private Callable<String> echo(String message) {
        return () -> {
            try (var socket = new Socket("127.0.0.1", server.getPort());
                 var reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                 var writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))) {
                writer.write(message);
                writer.newLine();
                writer.flush();
                return reader.readLine();
            }
        };
    }
}

