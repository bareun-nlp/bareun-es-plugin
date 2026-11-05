package ai.bareun.it;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import com.sun.net.httpserver.HttpServer;

import ai.bareun.protos.AnalyzeSyntaxRequest;
import ai.bareun.protos.AnalyzeSyntaxResponse;
import ai.bareun.protos.Morpheme;
import ai.bareun.protos.Sentence;
import ai.bareun.protos.TextSpan;
import ai.bareun.protos.Token;

/**
 * 바른 서버의 {@code AnalyzeSyntax} 만 흉내 내는 모의 서버.
 *
 * <p>컨테이너 안의 플러그인이 호스트로 접속해 오므로 모든 인터페이스에 묶는다.
 * 어절마다 아래 표의 형태소를 돌려주고, 표에 없는 어절은 통째로 일반명사(NNG)로 낸다.
 * 요청의 API 키 헤더와 본문을 {@link #calls()} 에 쌓아 두어, 플러그인이 키와 사용자
 * 사전 이름을 제대로 실어 보냈는지 시험에서 확인할 수 있다.
 */
final class MockBareunServer implements AutoCloseable {

    /** 서버가 받은 요청 하나. */
    record Call(String path, String apiKey, AnalyzeSyntaxRequest request) {
    }

    /** 형태소 하나: 표층형, 어절 안에서의 시작 위치, 품사. */
    private record M(String text, int begin, Morpheme.Tag tag) {
    }

    private static final Map<String, List<M>> TABLE = Map.of(
            "아버지가", List.of(new M("아버지", 0, Morpheme.Tag.NNG), new M("가", 3, Morpheme.Tag.JKS)),
            "방에", List.of(new M("방", 0, Morpheme.Tag.NNG), new M("에", 1, Morpheme.Tag.JKB)),
            "들어가신다.", List.of(
                    new M("들어가", 0, Morpheme.Tag.VV),
                    new M("시", 3, Morpheme.Tag.EP),
                    new M("ㄴ다", 4, Morpheme.Tag.EF),
                    new M(".", 5, Morpheme.Tag.SF)));

    private final HttpServer server;
    private final List<Call> calls = new CopyOnWriteArrayList<>();

    MockBareunServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> {
            try (var in = exchange.getRequestBody()) {
                AnalyzeSyntaxRequest req = AnalyzeSyntaxRequest.parseFrom(in.readAllBytes());
                calls.add(new Call(exchange.getRequestURI().getPath(),
                        exchange.getRequestHeaders().getFirst("api-key"), req));
                byte[] body = respond(req.getDocument().getContent()).toByteArray();
                exchange.getResponseHeaders().add("Content-Type", "application/proto");
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(body);
                }
            }
        });
        server.start();
    }

    int port() {
        return server.getAddress().getPort();
    }

    List<Call> calls() {
        return calls;
    }

    /** 공백으로 어절을 나눠 응답을 만든다. 오프셋은 UTF-16 문자 단위다. */
    private static AnalyzeSyntaxResponse respond(String text) {
        Sentence.Builder sentence = Sentence.newBuilder()
                .setText(TextSpan.newBuilder().setContent(text).setBeginOffset(0));
        int pos = 0;
        for (String eojeol : text.split(" ")) {
            if (eojeol.isEmpty()) {
                pos++;
                continue;
            }
            Token.Builder tok = Token.newBuilder()
                    .setText(TextSpan.newBuilder().setContent(eojeol).setBeginOffset(pos));
            for (M m : TABLE.getOrDefault(eojeol, List.of(new M(eojeol, 0, Morpheme.Tag.NNG)))) {
                tok.addMorphemes(Morpheme.newBuilder()
                        .setText(TextSpan.newBuilder().setContent(m.text()).setBeginOffset(pos + m.begin()))
                        .setTag(m.tag()));
            }
            sentence.addTokens(tok);
            pos += eojeol.length() + 1;
        }
        return AnalyzeSyntaxResponse.newBuilder().setLanguage("ko_KR").addSentences(sentence).build();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
