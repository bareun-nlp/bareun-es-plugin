package ai.bareun.es;

import java.util.List;

import ai.bareun.analysis.BareunConfig;
import ai.bareun.analysis.BareunConfig.DecompoundMode;

import org.elasticsearch.common.settings.SecureSetting;
import org.elasticsearch.common.settings.SecureString;
import org.elasticsearch.common.settings.Setting;
import org.elasticsearch.common.settings.Settings;

/**
 * 플러그인 설정.
 *
 * <p>노드 단위 기본값은 {@code elasticsearch.yml} 에 적고, API 키는 노드 keystore 에
 * 넣는다. 인덱스의 analyzer 설정에서 항목별로 덮어쓸 수 있다.
 *
 * <pre>
 * # elasticsearch.yml
 * bareun.host: nlp.bareun.ai
 * bareun.port: 5656
 * bareun.tls: false
 * bareun.timeout: 10s
 * bareun.stoptags: [E, IC, J, MAG, ...]
 *
 * # keystore
 * bin/elasticsearch-keystore add bareun.api_key
 * </pre>
 *
 * <h2>왜 바꿨나</h2>
 *
 * <p>예전에는 {@code /usr/share/elasticsearch/data/config.properties} 를 손으로
 * 복사해 두어야 동작했다. 데이터 디렉토리에 설정을 두면 컨테이너를 다시 만들 때마다
 * 사라지고, 노드를 늘리면 노드마다 흩어진다. API 키가 평문으로 데이터 디렉토리에
 * 남는 것도 문제였다. Elasticsearch 에는 이런 값을 넣는 자리가 이미 있다.
 */
public final class BareunSettings {

    /** 바른 서버 호스트. 이름이나 IP 만 준다(URL 이 아니다). */
    public static final Setting<String> HOST =
            Setting.simpleString("bareun.host", "localhost", Setting.Property.NodeScope);

    /** 바른 서버 포트. 도커는 5656, 네이티브 설치본은 5658 이 관례다. */
    public static final Setting<Integer> PORT =
            Setting.intSetting("bareun.port", 5656, 1, 65535, Setting.Property.NodeScope);

    /** TLS 사용 여부. 공개 서비스(api.bareun.ai:443)에 붙을 때 켠다. */
    public static final Setting<Boolean> TLS =
            Setting.boolSetting("bareun.tls", false, Setting.Property.NodeScope);

    /** 요청 하나의 제한 시간. */
    public static final Setting<org.elasticsearch.core.TimeValue> TIMEOUT =
            Setting.timeSetting("bareun.timeout",
                    org.elasticsearch.core.TimeValue.timeValueSeconds(10),
                    Setting.Property.NodeScope);

    /**
     * API 키. 노드 keystore 에 넣는다.
     *
     * <p>{@code bin/elasticsearch-keystore add bareun.api_key}
     */
    public static final Setting<SecureString> API_KEY =
            SecureSetting.secureString("bareun.api_key", null);

    /**
     * 색인에서 빼는 품사 태그의 접두사 목록.
     *
     * <p>접두사로 비교하므로 {@code J} 하나로 모든 조사(JKS·JKB·JX …)가 빠진다.
     */
    public static final Setting<List<String>> STOPTAGS =
            Setting.stringListSetting("bareun.stoptags", BareunConfig.DEFAULT_STOPTAGS,
                    Setting.Property.NodeScope);

    /** 쓸 사용자 사전 이름들. 앞에 온 것이 우선한다. */
    public static final Setting<List<String>> CUSTOM_DICT_NAMES =
            Setting.stringListSetting("bareun.custom_dict_names", List.of(),
                    Setting.Property.NodeScope);

    private BareunSettings() {
    }

    /**
     * keystore 에서 API 키를 읽는다.
     *
     * <p><b>플러그인 생성자에서 한 번만 부를 수 있다.</b> Elasticsearch 는 부팅이
     * 끝나면 keystore 를 닫으므로, 인덱스를 열 때(=analyzer 공장을 만들 때) 읽으면
     * {@code IllegalStateException: Keystore is closed} 로 죽는다. 게다가 그 예외가
     * analyzer 공장 생성 중에 나면 ES 자신의 인덱스 템플릿 생성까지 함께 실패한다
     * (실측: 8.19.21 에서 .ml-state 등 여러 템플릿이 무더기로 깨졌다).
     *
     * @param nodeSettings 노드 설정. 플러그인 생성자가 받는 것이어야 한다.
     * @return API 키. 설정되지 않았으면 빈 문자열.
     */
    public static String readApiKey(Settings nodeSettings) {
        try (SecureString s = API_KEY.get(nodeSettings)) {
            return (s == null) ? "" : s.toString();
        }
    }

    /**
     * 노드 설정과 analyzer 설정을 합쳐 실제로 쓸 값을 만든다.
     *
     * <p>analyzer 쪽에 값이 있으면 그것을 쓰고, 없으면 노드 설정을 쓴다.
     *
     * <p>API 키만 따로 받는 것은 위 {@link #readApiKey} 의 제약 때문이다 —
     * 여기서 keystore 를 열 수 없다. 인덱스 설정은 클러스터 상태에 평문으로
     * 저장되므로 어차피 거기에 키를 두면 안 된다.
     *
     * @param nodeSettings 노드 설정({@code elasticsearch.yml})
     * @param analyzerSettings 인덱스의 analyzer 설정. 없으면 {@link Settings#EMPTY}
     * @param apiKey 플러그인 생성자가 미리 읽어 둔 API 키
     * @return 합쳐진 설정
     */
    public static BareunConfig resolve(Settings nodeSettings, Settings analyzerSettings, String apiKey) {
        String host = analyzerSettings.get("host", HOST.get(nodeSettings));
        int port = analyzerSettings.getAsInt("port", PORT.get(nodeSettings));
        boolean tls = analyzerSettings.getAsBoolean("tls", TLS.get(nodeSettings));
        long timeoutMillis = analyzerSettings
                .getAsTime("timeout", TIMEOUT.get(nodeSettings)).millis();

        List<String> stoptags = analyzerSettings.getAsList("stoptags", null);
        if (stoptags == null) {
            stoptags = STOPTAGS.get(nodeSettings);
        }
        List<String> dicts = analyzerSettings.getAsList("custom_dict_names", null);
        if (dicts == null) {
            dicts = CUSTOM_DICT_NAMES.get(nodeSettings);
        }
        DecompoundMode mode = DecompoundMode.from(analyzerSettings.get("decompound_mode", null));

        return new BareunConfig(BareunConfig.baseUrl(tls, host, port), apiKey,
                (int) timeoutMillis, stoptags, dicts, mode);
    }
}
