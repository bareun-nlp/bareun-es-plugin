package ai.bareun.es;

import org.apache.lucene.analysis.Tokenizer;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.env.Environment;
import org.elasticsearch.index.IndexSettings;
import org.elasticsearch.index.analysis.TokenizerFactory;

import ai.bareun.client.BareunConnect;

/**
 * {@link BareunTokenizer} 를 만드는 공장.
 *
 * <p>노드 설정과 analyzer 설정을 합치는 일은 인덱스를 열 때 한 번만 한다. 토크나이저는
 * 문서마다 새로 만들어지므로 그때마다 설정을 다시 읽으면 낭비다.
 *
 * <p>추상 클래스 대신 인터페이스를 구현하는 이유는
 * {@link BareunAnalyzerProvider} 의 설명과 같다 — ES 8 과 9 에서 추상 클래스의
 * 생성자가 다르다.
 */
public class BareunTokenizerFactory implements TokenizerFactory {

    private final String name;
    private final BareunSettings.Resolved resolved;
    private final BareunConnect connect;

    /**
     * @param indexSettings 인덱스 설정
     * @param environment 노드 환경
     * @param name analyzer 설정에서 준 이름
     * @param settings 이 tokenizer 의 설정
     * @param apiKey 플러그인 생성자가 keystore 에서 읽어 둔 API 키
     */
    public BareunTokenizerFactory(IndexSettings indexSettings, Environment environment,
            String name, Settings settings, String apiKey) {
        this.name = name;
        this.resolved = BareunSettings.resolve(environment.settings(), settings, apiKey);
        this.connect = new BareunConnect(resolved.baseUrl, resolved.apiKey, resolved.timeoutMillis);
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public Tokenizer create() {
        return new BareunTokenizer(connect, resolved);
    }
}
