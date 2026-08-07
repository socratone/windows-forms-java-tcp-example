package example.communication.tcp;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 여러 클라이언트의 한 줄 단위 UTF-8 메시지를 그대로 돌려주는 TCP 서버다. */
public final class TcpEchoServer implements AutoCloseable {
    private final InetSocketAddress address;
    // 클라이언트 연결마다 가상 스레드를 하나씩 할당해 서로 독립적으로 처리한다.
    private final ExecutorService clients = Executors.newVirtualThreadPerTaskExecutor();
    // accept 스레드에서도 종료 상태 변경을 즉시 볼 수 있어야 한다.
    private volatile boolean running;
    private ServerSocket serverSocket;
    private Thread acceptThread;

    public TcpEchoServer(InetSocketAddress address) {
        this.address = address;
    }

    public synchronized void start() throws IOException {
        // 소켓을 지정 주소에 바인딩한 뒤 별도 플랫폼 스레드에서 연결을 수락한다.
        if (running) {
            throw new IllegalStateException("서버가 이미 실행 중입니다.");
        }
        serverSocket = new ServerSocket();
        serverSocket.bind(address);
        running = true;
        acceptThread = Thread.ofPlatform().name("tcp-acceptor").start(this::acceptLoop);
        log("TCP 서버 시작: " + serverSocket.getLocalSocketAddress());
    }

    public int getPort() {
        if (serverSocket == null) {
            throw new IllegalStateException("서버가 시작되지 않았습니다.");
        }
        return serverSocket.getLocalPort();
    }

    public void awaitTermination() throws InterruptedException {
        var thread = acceptThread;
        if (thread != null) {
            thread.join();
        }
    }

    private void acceptLoop() {
        // 서버가 닫힐 때 accept가 IOException으로 풀리는 것은 정상 종료 흐름으로 간주한다.
        while (running) {
            try {
                var socket = serverSocket.accept();
                clients.submit(() -> handleClient(socket));
            } catch (IOException e) {
                if (running) {
                    log("클라이언트 연결 수락 오류: " + e.getMessage());
                }
            }
        }
    }

    private void handleClient(Socket socket) {
        var remote = socket.getRemoteSocketAddress();
        log("클라이언트 연결: " + remote);
        try (socket;
             var reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             var writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))) {
            String message;
            // 연결이 유지되는 동안 줄바꿈으로 구분된 메시지를 계속 에코한다.
            while ((message = reader.readLine()) != null) {
                log(remote + " > " + message);
                writer.write(message);
                writer.newLine();
                writer.flush();
            }
        } catch (IOException e) {
            log("클라이언트 통신 오류 (" + remote + "): " + e.getMessage());
        } finally {
            log("클라이언트 연결 종료: " + remote);
        }
    }

    @Override
    public synchronized void close() {
        // 서버 소켓을 닫아 대기 중인 accept를 깨우고 모든 클라이언트 작업을 중단한다.
        running = false;
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException ignored) {
                // 이미 종료된 소켓은 무시한다.
            }
        }
        clients.shutdownNow();
    }

    private static void log(String message) {
        System.out.printf("[%s] %s%n", LocalDateTime.now(), message);
    }

    public static void main(String[] args) throws Exception {
        // 명령행 인자가 없으면 로컬 주소와 예제의 기본 TCP 포트를 사용한다.
        var host = args.length >= 1 ? args[0] : "127.0.0.1";
        var port = args.length >= 2 ? Integer.parseInt(args[1]) : 5000;
        var server = new TcpEchoServer(new InetSocketAddress(InetAddress.getByName(host), port));
        // JVM 종료 시 열려 있는 서버 및 클라이언트 소켓을 정리한다.
        Runtime.getRuntime().addShutdownHook(Thread.ofPlatform().unstarted(server::close));
        server.start();
        server.awaitTermination();
    }
}
