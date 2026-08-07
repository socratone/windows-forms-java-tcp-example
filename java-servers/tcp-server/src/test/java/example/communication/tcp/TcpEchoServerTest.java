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

/** TCP 서버의 지속 연결 메시지 처리와 동시 접속을 검증한다. */
class TcpEchoServerTest {
    private TcpEchoServer server;

    @AfterEach
    void stopServer() {
        if (server != null) server.close();
    }

    @Test
    void echoesUnicodeMessagesOverOnePersistentConnection() throws Exception {
        // 소켓 하나에서 여러 줄을 연속 전송해 연결이 요청마다 끊기지 않는지 확인한다.
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
        // 두 가상 스레드가 동시에 접속해도 각자 자신의 응답을 받는지 확인한다.
        startServer();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(echo("첫 번째"));
            var second = executor.submit(echo("두 번째"));
            assertEquals("첫 번째", first.get());
            assertEquals("두 번째", second.get());
        }
    }

    private void startServer() throws Exception {
        // 포트 충돌을 피하도록 운영체제가 임시 포트를 선택하게 한다.
        server = new TcpEchoServer(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
        server.start();
    }

    private Callable<String> echo(String message) {
        // 동시성 테스트에서 실행기에 제출할 1회성 클라이언트 작업을 만든다.
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
