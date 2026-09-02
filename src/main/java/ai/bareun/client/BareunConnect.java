package ai.bareun.client;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.google.protobuf.InvalidProtocolBufferException;

import ai.bareun.protos.AnalyzeSyntaxRequest;
import ai.bareun.protos.AnalyzeSyntaxResponse;
import ai.bareun.protos.Document;
import ai.bareun.protos.EncodingType;

/**
 * 바른 서버에 Connect 프로토콜로 형태소 분석을 요청하는 최소 클라이언트.
 *
 * <p>Connect 의 단항(unary) 호출은 평범한 HTTP POST 다.
 *
 * <pre>
 *   POST /bareun.LanguageService/AnalyzeSyntax
 *   Content-Type: application/proto
 *   api-key: koba-...
 *   &lt;직렬화된 요청 메시지&gt;
 * </pre>
 *
 * <h2>왜 {@link HttpURLConnection} 인가</h2>
 *
 * <p>더 현대적인 {@code java.net.http.HttpClient} 를 쓰지 않았다. 그쪽은 셀렉터
 * 스레드를 스스로 만드는데, Elasticsearch 8 은 SecureSM 아래에서 돌면서 플러그인
 * 코드의 스레드 생성을 막는다({@code RuntimePermission "modifyThread"}). 반면
 * {@link HttpURLConnection} 은 부르는 스레드에서 그대로 블로킹하므로 스레드를
 * 만들지 않는다. Lucene 의 토크나이저는 어차피 동기 호출이라 이쪽이 맞다.
 *
 * <p>옛 구현은 gRPC(grpc-netty-shaded)를 썼다. Elasticsearch 자체가 Netty 를 쓰기
 * 때문에 클래스로더가 갈려도 위험하고, 의존이 20여 개 딸려 왔다. 지금 이 플러그인이
 * 담는 것은 {@code protobuf-java} 하나다.
 *
 * <p>이 클래스는 불변이고 스레드 안전하다. 연결은 JDK 의 keep-alive 풀이 재사용한다 —
 * 옛 구현은 요청마다 gRPC 채널을 새로 만들고 닫았다.
 */
public final class BareunConnect {

    /** Connect 바이너리 인코딩. 서버는 application/json 도 받지만 이쪽이 작고 빠르다. */
    private static final String CONTENT_TYPE = "application/proto";

    /** 오류 본문을 읽어 올 때의 상한. 서버가 큰 본문을 보내도 메모리를 물지 않게 한다. */
    private static final int MAX_ERROR_BODY = 8 * 1024;

    private final String endpoint;
    private final String apiKey;
    private final int timeoutMillis;

    /**
     * @param baseUrl 서버 기준 주소. 예: {@code http://localhost:5656}
     * @param apiKey API 키. 모든 요청의 {@code api-key} 헤더로 실린다.
     * @param timeoutMillis 접속·읽기 제한 시간
     */
    public BareunConnect(String baseUrl, String apiKey, int timeoutMillis) {
        // 끝 슬래시가 붙으면 경로가 "//bareun.LanguageService/..." 가 되어 404 가 난다.
        this.endpoint = baseUrl.replaceAll("/+$", "") + "/bareun.LanguageService/AnalyzeSyntax";
        this.apiKey = apiKey;
        this.timeoutMillis = timeoutMillis;
    }

    /**
     * 문장을 형태소 분석한다.
     *
     * <p>오프셋을 UTF-16 으로 요청한다. 자바 문자열과 Lucene 의 {@code OffsetAttribute}
     * 가 모두 UTF-16 문자 단위라, 서버가 준 위치를 그대로 쓸 수 있어야 한다. UTF8 로
     * 받으면 한글이 섞인 문장에서 하이라이팅 위치가 어긋나는데, 오류가 아니라 엉뚱한
     * 구간이 강조되므로 알아채기 어렵다.
     *
     * @param text 분석할 문장
     * @param customDictNames 쓸 사용자 사전 이름들. 비어 있으면 싣지 않는다.
     * @return 분석 결과
     * @throws BareunClientException 접속 실패·서버 오류·응답 파싱 실패
     */
    public AnalyzeSyntaxResponse analyze(String text, List<String> customDictNames) {
        AnalyzeSyntaxRequest.Builder req = AnalyzeSyntaxRequest.newBuilder()
                .setDocument(Document.newBuilder().setContent(text).setLanguage("ko_KR"))
                .setEncodingType(EncodingType.UTF16)
                .setAutoSplitSentence(true)
                .setAutoSpacing(true);
        if (customDictNames != null && !customDictNames.isEmpty()) {
            req.addAllCustomDictNames(customDictNames);
        }
        byte[] body = req.build().toByteArray();

        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(endpoint).openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(timeoutMillis);
            conn.setReadTimeout(timeoutMillis);
            conn.setRequestProperty("Content-Type", CONTENT_TYPE);
            conn.setRequestProperty("api-key", apiKey);
            conn.setFixedLengthStreamingMode(body.length);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(body);
            }

            int status = conn.getResponseCode();
            if (status / 100 != 2) {
                throw new BareunClientException(errorMessage(conn, status), status, null);
            }
            try (InputStream is = conn.getInputStream()) {
                return AnalyzeSyntaxResponse.parseFrom(readAll(is, Integer.MAX_VALUE));
            }
        } catch (InvalidProtocolBufferException e) {
            // 2xx 인데 파싱이 안 된다면 서버와 플러그인의 proto 가 어긋난 것이다.
            throw new BareunClientException(
                    "바른 서버의 응답을 해석하지 못했습니다. 서버와 플러그인의 버전이 다를 수 있습니다: "
                            + e.getMessage(), 0, e);
        } catch (IOException e) {
            throw new BareunClientException(
                    "바른 서버에 접속하지 못했습니다 (" + endpoint + "): " + e.getMessage(), 0, e);
        } finally {
            if (conn != null) {
                // disconnect() 는 keep-alive 연결을 버리므로 부르지 않는다. 스트림을
                // 끝까지 읽어 닫아 두면 JDK 가 연결을 풀에 돌려놓는다.
                conn.getHeaderFields();
            }
        }
    }

    /**
     * 실패 응답에서 사람이 읽을 메시지를 만든다.
     *
     * <p>Connect 는 오류를 {@code {"code":"...","message":"..."}} JSON 으로 돌려준다.
     * 다만 라우팅 단계에서 떨어지면 평문이 오므로, 파싱에 실패해도 상태 코드만으로
     * 쓸 만한 문장을 만든다 — 오류 처리 도중에 다시 죽지 않게 하는 것이 목적이다.
     *
     * @param conn 응답을 받은 연결
     * @param status HTTP 상태 코드
     * @return 오류 메시지
     */
    private String errorMessage(HttpURLConnection conn, int status) {
        String body = "";
        try (InputStream es = conn.getErrorStream()) {
            if (es != null) {
                body = new String(readAll(es, MAX_ERROR_BODY), StandardCharsets.UTF_8);
            }
        } catch (IOException ignored) {
            // 본문을 못 읽어도 상태 코드만으로 메시지를 만든다.
        }
        String detail = extractJsonString(body, "message");
        if (detail == null || detail.isEmpty()) {
            detail = body.trim();
        }
        String prefix;
        if (status == 401 || status == 403) {
            prefix = "바른 서버가 요청을 거부했습니다. API 키와 라이선스를 확인하세요";
        } else if (status == 404 || status == 501) {
            prefix = "바른 서버가 이 기능을 제공하지 않습니다";
        } else {
            prefix = "바른 서버가 오류를 돌려주었습니다 (HTTP " + status + ")";
        }
        return detail.isEmpty() ? prefix : prefix + ": " + detail;
    }

    /**
     * 스트림을 끝까지 읽는다.
     *
     * @param is 읽을 스트림
     * @param limit 최대 바이트 수. 넘으면 거기서 멈춘다.
     * @return 읽은 바이트
     * @throws IOException 읽기 실패
     */
    private static byte[] readAll(InputStream is, int limit) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) > 0) {
            out.write(buf, 0, n);
            if (out.size() >= limit) {
                break;
            }
        }
        return out.toByteArray();
    }

    /**
     * 평평한 JSON 오브젝트에서 문자열 필드 하나를 꺼낸다.
     *
     * <p>오류 본문의 한 필드를 읽자고 JSON 라이브러리를 플러그인에 넣지 않으려고
     * 직접 읽는다. Connect 오류 본문은 최상위에 {@code code}·{@code message} 가 있는
     * 단순한 모양이고, 여기서 잘못 읽어도 최악이 "메시지가 덜 예쁘다" 이므로 충분하다.
     * 다만 JSON 이스케이프는 풀어야 한국어 메시지가 제대로 보인다.
     *
     * @param json JSON 문자열
     * @param field 꺼낼 필드 이름
     * @return 값. 없으면 null.
     */
    static String extractJsonString(String json, String field) {
        String needle = "\"" + field + "\"";
        int k = json.indexOf(needle);
        if (k < 0) {
            return null;
        }
        int i = json.indexOf(':', k + needle.length());
        if (i < 0) {
            return null;
        }
        i++;
        while (i < json.length() && Character.isWhitespace(json.charAt(i))) {
            i++;
        }
        if (i >= json.length() || json.charAt(i) != '"') {
            return null;
        }
        i++;
        StringBuilder sb = new StringBuilder();
        while (i < json.length()) {
            char c = json.charAt(i);
            if (c == '"') {
                return sb.toString();
            }
            if (c == '\\' && i + 1 < json.length()) {
                char n = json.charAt(++i);
                switch (n) {
                    case 'n': sb.append('\n'); break;
                    case 't': sb.append('\t'); break;
                    case 'r': sb.append('\r'); break;
                    case 'b': sb.append('\b'); break;
                    case 'f': sb.append('\f'); break;
                    case 'u':
                        if (i + 4 < json.length()) {
                            sb.append((char) Integer.parseInt(json.substring(i + 1, i + 5), 16));
                            i += 4;
                        }
                        break;
                    default: sb.append(n); break;
                }
            } else {
                sb.append(c);
            }
            i++;
        }
        // 닫는 따옴표를 못 찾았다 — 잘린 본문이다. 읽은 데까지 돌려준다.
        return sb.toString();
    }
}
