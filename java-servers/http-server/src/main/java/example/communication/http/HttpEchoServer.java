package example.communication.http;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class HttpEchoServer implements AutoCloseable {
    private final InetSocketAddress address;
    private ExecutorService executor;
    private HttpServer server;

    public HttpEchoServer(InetSocketAddress address) {
        this.address = address;
    }

    public synchronized void start() throws IOException {
        if (server != null) {
            throw new IllegalStateException("서버가 이미 실행 중입니다.");
        }
        server = HttpServer.create(address, 0);
        executor = Executors.newVirtualThreadPerTaskExecutor();
        server.setExecutor(executor);
        server.createContext("/", this::handle);
        server.start();
        log("HTTP 서버 시작: http://" + server.getAddress().getHostString() + ":" + getPort() + "/echo");
    }

    public int getPort() {
        if (server == null) {
            throw new IllegalStateException("서버가 시작되지 않았습니다.");
        }
        return server.getAddress().getPort();
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            if (!"/echo".equals(exchange.getRequestURI().getPath())) {
                send(exchange, 404, "요청한 경로를 찾을 수 없습니다.");
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().set("Allow", "POST");
                send(exchange, 405, "POST 메서드만 사용할 수 있습니다.");
                return;
            }

            var request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            log(exchange.getRemoteAddress() + " > " + request);
            send(exchange, 200, request);
        } catch (RuntimeException e) {
            log("HTTP 요청 처리 오류: " + e.getMessage());
            throw e;
        }
    }

    private static void send(HttpExchange exchange, int statusCode, String body) throws IOException {
        var bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    @Override
    public synchronized void close() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    private static void log(String message) {
        System.out.printf("[%s] %s%n", LocalDateTime.now(), message);
    }

    public static void main(String[] args) throws Exception {
        var host = args.length >= 1 ? args[0] : "127.0.0.1";
        var port = args.length >= 2 ? Integer.parseInt(args[1]) : 8080;
        var server = new HttpEchoServer(new InetSocketAddress(InetAddress.getByName(host), port));
        Runtime.getRuntime().addShutdownHook(Thread.ofPlatform().unstarted(server::close));
        server.start();
        Thread.currentThread().join();
    }
}

