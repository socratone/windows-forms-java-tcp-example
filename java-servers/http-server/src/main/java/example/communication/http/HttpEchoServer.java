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

/** POST /echo 요청의 UTF-8 본문을 그대로 반환하는 간단한 HTTP 서버다. */
public final class HttpEchoServer implements AutoCloseable {
    private final InetSocketAddress address;
    private ExecutorService executor;
    private HttpServer server;

    public HttpEchoServer(InetSocketAddress address) {
        this.address = address;
    }

    public synchronized void start() throws IOException {
        // 동시에 start가 호출되어 서버가 중복 생성되는 것을 막는다.
        if (server != null) {
            throw new IllegalStateException("서버가 이미 실행 중입니다.");
        }
        server = HttpServer.create(address, 0);
        // 각 HTTP 교환을 가벼운 가상 스레드에서 독립적으로 처리한다.
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
        // exchange를 닫아 요청/응답 스트림이 예외 발생 시에도 정리되도록 한다.
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

            // 클라이언트와 약속한 UTF-8로 요청 본문 전체를 문자열로 변환한다.
            var request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            log(exchange.getRemoteAddress() + " > " + request);
            send(exchange, 200, request);
        } catch (RuntimeException e) {
            log("HTTP 요청 처리 오류: " + e.getMessage());
            throw e;
        }
    }

    private static void send(HttpExchange exchange, int statusCode, String body) throws IOException {
        // 문자열의 글자 수가 아닌 실제 UTF-8 바이트 수를 Content-Length로 전달한다.
        var bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    @Override
    public synchronized void close() {
        // 새 요청 수락을 중단한 뒤 요청 처리용 실행기도 종료한다.
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
        // 명령행 인자가 없으면 로컬 주소와 기본 HTTP 포트를 사용한다.
        var host = args.length >= 1 ? args[0] : "127.0.0.1";
        var port = args.length >= 2 ? Integer.parseInt(args[1]) : 8080;
        var server = new HttpEchoServer(new InetSocketAddress(InetAddress.getByName(host), port));
        // Ctrl+C 또는 JVM 종료 시 포트와 실행기 자원이 남지 않게 정리한다.
        Runtime.getRuntime().addShutdownHook(Thread.ofPlatform().unstarted(server::close));
        server.start();
        // 서버 프로세스가 즉시 끝나지 않도록 메인 스레드를 계속 대기시킨다.
        Thread.currentThread().join();
    }
}
