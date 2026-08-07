package example.communication.http;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** HTTP 에코 서버의 정상 응답과 주요 오류 상태를 검증한다. */
class HttpEchoServerTest {
    private HttpEchoServer server;
    private HttpClient client;

    @BeforeEach
    void startServer() throws Exception {
        // 포트 0을 지정해 운영체제가 비어 있는 테스트용 포트를 선택하게 한다.
        server = new HttpEchoServer(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
        server.start();
        client = HttpClient.newHttpClient();
    }

    @AfterEach
    void stopServer() {
        server.close();
    }

    @Test
    void echoesUnicodePostBody() throws Exception {
        // 한글과 특수문자가 UTF-8 인코딩 손실 없이 왕복하는지 확인한다.
        var response = send("POST", "/echo", "한글 echo !@#$%", "text/plain; charset=utf-8");
        assertEquals(200, response.statusCode());
        assertEquals("한글 echo !@#$%", response.body());
        assertEquals("text/plain; charset=utf-8", response.headers().firstValue("Content-Type").orElseThrow());
    }

    @Test
    void returns404ForUnknownPath() throws Exception {
        assertEquals(404, send("POST", "/unknown", "test", "text/plain").statusCode());
    }

    @Test
    void returns405AndAllowHeaderForGet() throws Exception {
        var response = send("GET", "/echo", null, null);
        assertEquals(405, response.statusCode());
        assertEquals("POST", response.headers().firstValue("Allow").orElseThrow());
    }

    private HttpResponse<String> send(String method, String path, String body, String contentType) throws Exception {
        // 각 테스트가 메서드, 경로, 본문만 바꿔 요청할 수 있도록 공통 생성 과정을 모은다.
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.getPort() + path));
        if (contentType != null) builder.header("Content-Type", contentType);
        var publisher = body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8);
        return client.send(builder.method(method, publisher).build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }
}
