package ai.bareun.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import ai.bareun.protos.AnalyzeSyntaxResponse;

/**
 * {@link BareunConnect} 단위 테스트.
 *
 * <p>JDK 에 딸린 {@link HttpServer} 로 응답을 흉내 낸다.
 */
class BareunConnectTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    private String startServer(int status, byte[] body) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            exchange.sendResponseHeaders(status, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @Test
    void 정상_응답을_파싱한다() throws IOException {
        AnalyzeSyntaxResponse expected = AnalyzeSyntaxResponse.newBuilder()
                .setLanguage("ko_KR").build();
        String url = startServer(200, expected.toByteArray());

        AnalyzeSyntaxResponse got =
                new BareunConnect(url, "koba-TEST", 5000).analyze("가", List.of());
        assertEquals("ko_KR", got.getLanguage());
    }

    @Test
    void 끝_슬래시가_있어도_경로가_겹치지_않는다() throws IOException {
        AnalyzeSyntaxResponse expected = AnalyzeSyntaxResponse.newBuilder()
                .setLanguage("ko_KR").build();
        String url = startServer(200, expected.toByteArray());
        assertEquals("ko_KR",
                new BareunConnect(url + "///", "koba-TEST", 5000)
                        .analyze("가", List.of()).getLanguage());
    }

    @Test
    void 서버에_닿지_못하면_상태_0_으로_떨어진다() throws IOException {
        int port;
        try (ServerSocket s = new ServerSocket(0)) {
            port = s.getLocalPort();
        }
        BareunClientException e = assertThrows(BareunClientException.class,
                () -> new BareunConnect("http://127.0.0.1:" + port, "k", 2000)
                        .analyze("가", List.of()));
        assertEquals(0, e.getHttpStatus());
        assertTrue(e.getMessage().contains("접속하지 못했습니다"));
    }

    @Test
    void 인증_실패를_구분한다() throws IOException {
        String json = "{\"code\":\"permission_denied\",\"message\":\"API키가 유효하지 않습니다\"}";
        String url = startServer(403, json.getBytes(StandardCharsets.UTF_8));

        BareunClientException e = assertThrows(BareunClientException.class,
                () -> new BareunConnect(url, "k", 5000).analyze("가", List.of()));
        assertTrue(e.isAuthFailure());
        assertTrue(e.getMessage().contains("API키가 유효하지 않습니다"));
    }

    @Test
    void 오류_본문이_JSON이_아니어도_메시지를_만든다() throws IOException {
        String url = startServer(404, "404 page not found".getBytes(StandardCharsets.UTF_8));
        BareunClientException e = assertThrows(BareunClientException.class,
                () -> new BareunConnect(url, "k", 5000).analyze("가", List.of()));
        assertEquals(404, e.getHttpStatus());
        assertTrue(e.getMessage().contains("제공하지 않습니다"));
    }

    @Test
    void 응답이_깨지면_알린다() throws IOException {
        String url = startServer(200, new byte[] {(byte) 0xff, (byte) 0xff});
        BareunClientException e = assertThrows(BareunClientException.class,
                () -> new BareunConnect(url, "k", 5000).analyze("가", List.of()));
        assertTrue(e.getMessage().contains("해석하지 못했습니다"));
    }

    @Test
    void JSON_문자열_추출() {
        assertEquals("abc", BareunConnect.extractJsonString("{\"m\":\"abc\"}", "m"));
        assertEquals("한글", BareunConnect.extractJsonString("{\"m\": \"한글\"}", "m"));
        assertEquals("a\"b", BareunConnect.extractJsonString("{\"m\":\"a\\\"b\"}", "m"));
        assertEquals("A", BareunConnect.extractJsonString("{\"m\":\"\\u0041\"}", "m"));
        assertNull(BareunConnect.extractJsonString("{\"x\":\"1\"}", "m"));
        assertNull(BareunConnect.extractJsonString("{\"m\":1}", "m"));
    }
}
