package ai.bareun.es;

import java.util.List;
import java.util.Locale;

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
            Setting.stringListSetting("bareun.stoptags", DEFAULT_STOPTAGS(),
                    Setting.Property.NodeScope);

    /** 쓸 사용자 사전 이름들. 앞에 온 것이 우선한다. */
    public static final Setting<List<String>> CUSTOM_DICT_NAMES =
            Setting.stringListSetting("bareun.custom_dict_names", List.of(),
                    Setting.Property.NodeScope);

    /**
     * 기본 불용 태그.
     *
     * <p>어미·감탄사·조사·부사·관형사·기호 등 검색어가 되기 어려운 품사를 뺀다.
     * 상수 대신 메서드로 둔 것은 {@link Setting} 초기화 순서에 얽매이지 않기 위해서다.
     *
     * @return 기본 불용 태그 목록
     */
    static List<String> DEFAULT_STOPTAGS() {
        return List.of("E", "IC", "J", "MAG", "MAJ", "MM", "NA", "NF", "NV",
                "SE", "SF", "SO", "SP", "SS", "SW", "VC", "VX", "XPN", "XS");
    }

    private BareunSettings() {
    }

    /** 형태소를 어떻게 토큰으로 낼지. nori 의 {@code decompound_mode} 와 같은 자리다. */
    public enum DecompoundMode {
        /** 어절만 낸다. 형태소로 쪼개지 않는다. */
        NONE,
        /** 형태소만 낸다. 어절은 내지 않는다. */
        DISCARD,
        /** 형태소를 내고, 용언 어절은 원형과 같은 자리에 어절 전체도 함께 낸다(기본). */
        MIXED;

        /**
         * 설정 문자열을 모드로 바꾼다.
         *
         * @param s 설정값. 대소문자를 가리지 않는다.
         * @return 모드
         * @throws IllegalArgumentException 모르는 값이면. 오타를 조용히 기본값으로
         *         떨어뜨리면 색인이 의도와 다르게 만들어져도 드러나지 않는다.
         */
        public static DecompoundMode from(String s) {
            if (s == null || s.isEmpty()) {
                return MIXED;
            }
            try {
                return valueOf(s.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        "decompound_mode 는 none·discard·mixed 중 하나여야 합니다: " + s);
            }
        }
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
    public static Resolved resolve(Settings nodeSettings, Settings analyzerSettings, String apiKey) {
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

        String baseUrl = (tls ? "https://" : "http://") + host + ":" + port;
        return new Resolved(baseUrl, apiKey, (int) timeoutMillis, stoptags, dicts, mode);
    }

    /**
     * 합쳐진 설정.
     *
     * <p>불변이다. 토크나이저마다 이 값을 들고 다닌다.
     */
    public static final class Resolved {
        /** 서버 기준 주소. 예: {@code http://localhost:5656} */
        public final String baseUrl;
        /** API 키 */
        public final String apiKey;
        /** 요청 제한 시간(밀리초) */
        public final int timeoutMillis;
        /** 색인에서 뺄 품사 태그 접두사들 */
        public final List<String> stoptags;
        /** 쓸 사용자 사전 이름들 */
        public final List<String> customDictNames;
        /** 형태소를 어떻게 토큰으로 낼지 */
        public final DecompoundMode decompoundMode;

        Resolved(String baseUrl, String apiKey, int timeoutMillis, List<String> stoptags,
                List<String> customDictNames, DecompoundMode decompoundMode) {
            this.baseUrl = baseUrl;
            this.apiKey = apiKey;
            this.timeoutMillis = timeoutMillis;
            this.stoptags = List.copyOf(stoptags);
            this.customDictNames = List.copyOf(customDictNames);
            this.decompoundMode = decompoundMode;
        }

        /**
         * 태그가 불용 목록에 걸리는지 본다.
         *
         * <p>접두사로 비교한다. {@code J} 하나로 모든 조사가 빠지게 하려는 것이다.
         *
         * @param tag 품사 태그. 예: {@code JKS}
         * @return 빼야 하면 true
         */
        public boolean isStopTag(String tag) {
            if (tag == null || tag.isEmpty()) {
                return true;
            }
            for (String s : stoptags) {
                if (tag.startsWith(s)) {
                    return true;
                }
            }
            return false;
        }
    }
}
