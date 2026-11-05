package ai.bareun.analysis;

import java.util.List;
import java.util.Locale;

/**
 * 토크나이저가 쓰는 설정. 제품(Elasticsearch·OpenSearch)과 무관한 순수 값이다.
 *
 * <p>노드 설정과 인덱스 설정을 합치는 일은 제품별 모듈이 맡고, 합친 결과를 이 객체에
 * 담아 넘긴다. 불변이고, 토크나이저마다 이 값을 들고 다닌다.
 */
public final class BareunConfig {

    /**
     * 기본 불용 태그.
     *
     * <p>어미·감탄사·조사·부사·관형사·기호 등 검색어가 되기 어려운 품사를 뺀다.
     */
    public static final List<String> DEFAULT_STOPTAGS = List.of("E", "IC", "J", "MAG", "MAJ",
            "MM", "NA", "NF", "NV", "SE", "SF", "SO", "SP", "SS", "SW", "VC", "VX", "XPN", "XS");

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

    public BareunConfig(String baseUrl, String apiKey, int timeoutMillis, List<String> stoptags,
            List<String> customDictNames, DecompoundMode decompoundMode) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.timeoutMillis = timeoutMillis;
        this.stoptags = List.copyOf(stoptags);
        this.customDictNames = List.copyOf(customDictNames);
        this.decompoundMode = decompoundMode;
    }

    /**
     * 호스트·포트·TLS 여부로 서버 기준 주소를 만든다.
     *
     * @param tls true 면 https
     * @param host 호스트 이름이나 IP
     * @param port 포트
     * @return 기준 주소
     */
    public static String baseUrl(boolean tls, String host, int port) {
        return (tls ? "https://" : "http://") + host + ":" + port;
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
