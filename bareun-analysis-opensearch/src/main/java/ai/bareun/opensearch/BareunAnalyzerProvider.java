package ai.bareun.opensearch;

import org.opensearch.common.settings.Settings;
import org.opensearch.env.Environment;
import org.opensearch.index.IndexSettings;
import org.opensearch.index.analysis.AnalyzerProvider;
import org.opensearch.index.analysis.AnalyzerScope;

import ai.bareun.analysis.BareunAnalyzer;
import ai.bareun.analysis.BareunConfig;
import ai.bareun.client.BareunConnect;

/**
 * {@link BareunAnalyzer} 를 만드는 제공자.
 *
 * <p>추상 클래스 {@code AbstractIndexAnalyzerProvider} 를 상속하지 않고 인터페이스를
 * 직접 구현한다. 그 추상 클래스의 생성자가 OpenSearch 2 와 3 에서 달라질 수 있어
 * 버전 사이에 변하지 않는 인터페이스만 쓴다. 소스 하나로 2.x 와 3.x 를 모두 빌드한다.
 */
public class BareunAnalyzerProvider implements AnalyzerProvider<BareunAnalyzer> {

    private final String name;
    private final BareunAnalyzer analyzer;

    /**
     * @param indexSettings 인덱스 설정
     * @param environment 노드 환경. 노드 설정({@code opensearch.yml} + keystore)이 여기 있다.
     * @param name analyzer 설정에서 준 이름
     * @param settings 이 analyzer 의 설정
     * @param apiKey 플러그인 생성자가 keystore 에서 읽어 둔 API 키
     */
    public BareunAnalyzerProvider(IndexSettings indexSettings, Environment environment,
            String name, Settings settings, String apiKey) {
        this.name = name;
        BareunConfig resolved = BareunSettings.resolve(environment.settings(), settings, apiKey);
        this.analyzer = new BareunAnalyzer(
                new BareunConnect(resolved.baseUrl, resolved.apiKey, resolved.timeoutMillis),
                resolved);
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public AnalyzerScope scope() {
        return AnalyzerScope.INDEX;
    }

    @Override
    public BareunAnalyzer get() {
        return analyzer;
    }
}
