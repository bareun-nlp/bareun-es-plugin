package ai.bareun.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 실제 Elasticsearch·OpenSearch 노드에 플러그인 zip 을 설치하고 분석까지 돌려 본다.
 *
 * <p>대상은 시스템 속성으로 정한다({@code it.product}: elasticsearch|opensearch,
 * {@code it.version}, {@code it.zip}). 바른 서버는 {@link MockBareunServer} 로 갈음하고,
 * API 키는 환경변수 {@code BAREUN_TEST_API_KEY} 에서 읽는다(없으면 모의 서버 전용 더미).
 * 키는 노드 keystore 에만 넣는다.
 */
class PluginContainerIT {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();

    private static final String PRODUCT = System.getProperty("it.product", "elasticsearch");
    private static final String VERSION = System.getProperty("it.version");
    private static final String ZIP = System.getProperty("it.zip");
    private static final String API_KEY = System.getenv().getOrDefault(
            "BAREUN_TEST_API_KEY", "koba-MOCK-ONLY-0000");
    private static final boolean OS = PRODUCT.equals("opensearch");

    private static MockBareunServer bareun;
    private static GenericContainer<?> node;
    private static String base;

    @BeforeAll
    static void start() throws Exception {
        assertTrue(ZIP != null && !ZIP.isEmpty() && Files.exists(Path.of(ZIP)),
                "it.zip 으로 플러그인 zip 경로를 주세요: " + ZIP);
        bareun = new MockBareunServer();

        String home = OS ? "/usr/share/opensearch" : "/usr/share/elasticsearch";
        String cli = OS ? "opensearch" : "elasticsearch";
        String image = OS ? "opensearchproject/opensearch:" + VERSION
                : "docker.elastic.co/elasticsearch/elasticsearch:" + VERSION;
        // 설치 → keystore 에 키 → 원래 진입점. 키는 이미지에 굽지 않고 환경변수로만 받는다.
        String launch = OS ? "exec ./opensearch-docker-entrypoint.sh opensearch"
                : "exec /bin/tini -- /usr/local/bin/docker-entrypoint.sh eswrapper";
        String script = "set -e; bin/" + cli + "-plugin install --batch file:///tmp/plugin.zip; "
                + "bin/" + cli + "-keystore create >/dev/null 2>&1 || true; "
                + "printf %s \"$BAREUN_KEY\" | bin/" + cli + "-keystore add -x -f bareun.api_key; "
                + launch;

        node = new GenericContainer<>(DockerImageName.parse(image))
                .withCopyFileToContainer(MountableFile.forHostPath(ZIP), "/tmp/plugin.zip")
                .withCreateContainerCmdModifier(c -> c.withEntrypoint("sh", "-c", script)
                        .withWorkingDir(home))
                .withExtraHost("host.docker.internal", "host-gateway")
                .withEnv("BAREUN_KEY", API_KEY)
                .withEnv("discovery.type", "single-node")
                .withEnv("bareun.host", "host.docker.internal")
                .withEnv("bareun.port", String.valueOf(bareun.port()))
                .withExposedPorts(9200)
                .waitingFor(Wait.forHttp("/").forPort(9200).forStatusCodeMatching(c -> c == 200)
                        .withStartupTimeout(Duration.ofMinutes(4)));
        if (OS) {
            node.withEnv("DISABLE_SECURITY_PLUGIN", "true")
                    .withEnv("DISABLE_INSTALL_DEMO_CONFIG", "true")
                    .withEnv("OPENSEARCH_JAVA_OPTS", "-Xms512m -Xmx512m");
        } else {
            node.withEnv("xpack.security.enabled", "false")
                    .withEnv("ES_JAVA_OPTS", "-Xms512m -Xmx512m");
        }
        node.start();
        base = "http://" + node.getHost() + ":" + node.getMappedPort(9200);
    }

    @AfterAll
    static void stop() {
        if (node != null) {
            node.stop();
        }
        if (bareun != null) {
            bareun.close();
        }
    }

    private static JsonNode call(String method, String path, String body) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base + path))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json");
        b.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body));
        HttpResponse<String> res = HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString());
        assertTrue(res.statusCode() / 100 == 2,
                method + " " + path + " -> " + res.statusCode() + " " + res.body());
        return JSON.readTree(res.body());
    }

    private static List<String> tokens(JsonNode analyze) {
        List<String> out = new ArrayList<>();
        for (JsonNode t : analyze.get("tokens")) {
            out.add(t.get("token").asText() + "/" + t.get("type").asText()
                    + "@" + t.get("position").asInt());
        }
        return out;
    }

    @Test
    void 플러그인이_설치돼_노드에_올라온다() throws Exception {
        String plugins = JSON.writeValueAsString(call("GET", "/_nodes/plugins", null));
        assertTrue(plugins.contains("analysis-bareun"), plugins);
        // 설치된 버전은 zip 의 대상 버전과 같아야 한다(노드가 정확히 일치해야 로드한다).
        assertTrue(plugins.contains("\"" + (OS ? "opensearch_version" : "elasticsearch_version")
                + "\":\"" + VERSION + "\""), plugins);
    }

    @Test
    void 분석기로_형태소를_낸다() throws Exception {
        call("PUT", "/ko1", "{\"settings\":{\"analysis\":{\"analyzer\":{\"korean\":{\"type\":\"bareun\"}}}}}");
        JsonNode r = call("POST", "/ko1/_analyze",
                "{\"analyzer\":\"korean\",\"text\":\"아버지가 방에 들어가신다.\"}");
        // 조사·어미·기호는 기본 불용 태그로 빠지고, 용언은 원형과 같은 자리에 어절도 함께 나온다.
        assertEquals(List.of("아버지/NNG@0", "방/NNG@1", "들어가/VV@2", "들어가신다./VV@2"), tokens(r));
        JsonNode first = r.get("tokens").get(0);
        assertEquals(0, first.get("start_offset").asInt());
        assertEquals(3, first.get("end_offset").asInt());
    }

    @Test
    void 옛_이름도_그대로_동작한다() throws Exception {
        // 옛 baikal_* 이름은 Elasticsearch 용에만 있다. OpenSearch 용은 새로 나온 플러그인이다.
        org.junit.jupiter.api.Assumptions.assumeFalse(OS);
        call("PUT", "/ko2", "{\"settings\":{\"analysis\":{\"analyzer\":{\"old\":{\"type\":\"baikal_analyzer\"}}}}}");
        JsonNode r = call("POST", "/ko2/_analyze", "{\"analyzer\":\"old\",\"text\":\"방에\"}");
        assertEquals(List.of("방/NNG@0"), tokens(r));
    }

    @Test
    void 토크나이저와_토큰필터를_조합한다() throws Exception {
        call("PUT", "/ko3", "{\"settings\":{\"analysis\":{"
                + "\"tokenizer\":{\"t\":{\"type\":\"bareun_tokenizer\",\"stoptags\":[]}},"
                + "\"filter\":{\"pos\":{\"type\":\"bareun_part_of_speech\",\"stoptags\":[\"J\",\"E\",\"SF\"]}},"
                + "\"analyzer\":{\"a\":{\"type\":\"custom\",\"tokenizer\":\"t\",\"filter\":[\"pos\"]}}}}}");
        JsonNode r = call("POST", "/ko3/_analyze", "{\"analyzer\":\"a\",\"text\":\"방에 들어가신다.\"}");
        // 토크나이저는 다 내고, 필터가 J·E·SF 로 시작하는 태그를 거른다. EP·EF 는 E 로 시작해 빠진다.
        // 거른 토큰(에)의 자리는 위치 증가로 남아서 들어가가 2번 자리다.
        assertEquals(List.of("방/NNG@0", "들어가/VV@2", "들어가신다./VV@2"), tokens(r));
    }

    @Test
    void 색인한_문서를_검색한다() throws Exception {
        call("PUT", "/ko4", "{\"settings\":{\"analysis\":{\"analyzer\":{\"korean\":{\"type\":\"bareun\"}}}},"
                + "\"mappings\":{\"properties\":{\"body\":{\"type\":\"text\",\"analyzer\":\"korean\"}}}}");
        call("PUT", "/ko4/_doc/1?refresh=true", "{\"body\":\"아버지가 방에 들어가신다.\"}");
        // 질의도 같은 분석기를 거치므로 "방" 한 글자로 찾아진다.
        JsonNode r = call("POST", "/ko4/_search", "{\"query\":{\"match\":{\"body\":\"방에\"}}}");
        assertEquals(1, r.get("hits").get("total").get("value").asInt());
    }

    @Test
    void 키와_사전이름을_서버로_실어_보낸다() throws Exception {
        call("PUT", "/ko5", "{\"settings\":{\"analysis\":{\"analyzer\":{\"korean\":"
                + "{\"type\":\"bareun\",\"custom_dict_names\":[\"mydict\"]}}}}}");
        int before = bareun.calls().size();
        call("POST", "/ko5/_analyze", "{\"analyzer\":\"korean\",\"text\":\"방에\"}");
        List<MockBareunServer.Call> got = bareun.calls().subList(before, bareun.calls().size());
        assertFalse(got.isEmpty());
        MockBareunServer.Call c = got.get(got.size() - 1);
        assertEquals("/bareun.LanguageService/AnalyzeSyntax", c.path());
        // keystore 로 넣은 키가 api-key 헤더로 간다.
        assertEquals(API_KEY, c.apiKey());
        assertEquals(List.of("mydict"), c.request().getCustomDictNamesList());
        assertEquals(ai.bareun.protos.EncodingType.UTF16, c.request().getEncodingType());
    }

    @Test
    void 키는_인덱스_설정과_클러스터_설정에_새지_않는다() throws Exception {
        String settings = JSON.writeValueAsString(call("GET", "/ko1/_settings", null))
                + JSON.writeValueAsString(call("GET", "/_cluster/settings?include_defaults=true", null));
        assertFalse(settings.contains(API_KEY), "API 키가 설정 응답에 노출됐습니다");
    }
}
