package ai.bareun.es;

import java.util.List;

import org.apache.lucene.analysis.TokenStream;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.env.Environment;
import org.elasticsearch.index.IndexSettings;
import org.elasticsearch.index.analysis.TokenFilterFactory;

/**
 * {@link BareunPartOfSpeechFilter} 를 만드는 공장.
 *
 * <p>추상 클래스 대신 인터페이스를 구현하는 이유는 {@link BareunAnalyzerProvider} 의
 * 설명과 같다.
 */
public class BareunPartOfSpeechFilterFactory implements TokenFilterFactory {

    private final String name;
    private final List<String> stoptags;

    /**
     * @param indexSettings 인덱스 설정
     * @param environment 노드 환경
     * @param name 필터 설정에서 준 이름
     * @param settings 이 필터의 설정
     */
    public BareunPartOfSpeechFilterFactory(IndexSettings indexSettings, Environment environment,
            String name, Settings settings) {
        this.name = name;
        List<String> configured = settings.getAsList("stoptags", null);
        this.stoptags = (configured != null) ? configured
                : BareunSettings.STOPTAGS.get(environment.settings());
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public TokenStream create(TokenStream stream) {
        return new BareunPartOfSpeechFilter(stream, stoptags);
    }
}
