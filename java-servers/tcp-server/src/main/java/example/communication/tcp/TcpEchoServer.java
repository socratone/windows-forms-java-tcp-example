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

public final class TcpEchoServer implements AutoCloseable {
    private final InetSocketAddress address;
    private final ExecutorService clients = Executors.newVirtualThreadPerTaskExecutor();
    private volatile boolean running;
    private ServerSocket serverSocket;
    private Thread acceptThread;

    public TcpEchoServer(InetSocketAddress address) {
        this.address = address;
    }

    public synchronized void start() throws IOException {
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
        var host = args.length >= 1 ? args[0] : "127.0.0.1";
        var port = args.length >= 2 ? Integer.parseInt(args[1]) : 5000;
        var server = new TcpEchoServer(new InetSocketAddress(InetAddress.getByName(host), port));
        Runtime.getRuntime().addShutdownHook(Thread.ofPlatform().unstarted(server::close));
        server.start();
        server.awaitTermination();
    }
}

