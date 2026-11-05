package ai.bareun.es;

import java.util.List;
import java.util.Map;

import org.apache.lucene.analysis.Analyzer;
import org.elasticsearch.common.settings.Setting;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.analysis.AnalyzerProvider;
import org.elasticsearch.index.analysis.TokenFilterFactory;
import org.elasticsearch.index.analysis.TokenizerFactory;
import org.elasticsearch.indices.analysis.AnalysisModule.AnalysisProvider;
import org.elasticsearch.plugins.AnalysisPlugin;
import org.elasticsearch.plugins.Plugin;

/**
 * 바른 한국어 형태소 분석기 플러그인.
 *
 * <p>등록하는 이름은 아래와 같다. {@code baikal_*} 는 옛 이름이고, 이미 만들어진
 * 인덱스가 깨지지 않도록 한동안 함께 둔다. 새 인덱스에는 {@code bareun_*} 를 쓴다.
 *
 * <table>
 *   <caption>등록 이름</caption>
 *   <tr><th>종류</th><th>이름</th><th>옛 이름</th></tr>
 *   <tr><td>analyzer</td><td>{@code bareun}</td><td>{@code baikal_analyzer}</td></tr>
 *   <tr><td>tokenizer</td><td>{@code bareun_tokenizer}</td><td>{@code baikal_tokenizer}</td></tr>
 *   <tr><td>token filter</td><td>{@code bareun_part_of_speech}</td><td>{@code baikal_token}</td></tr>
 * </table>
 *
 * <p>analyzer 이름을 {@code bareun_analyzer} 가 아니라 {@code bareun} 으로 둔 것은
 * nori 의 관례를 따른 것이다({@code nori} analyzer + {@code nori_tokenizer}).
 */
public class BareunPlugin extends Plugin implements AnalysisPlugin {

    /** 플러그인 이름. 배포 descriptor 의 {@code name} 과 같아야 한다. */
    public static final String PLUGIN_NAME = "analysis-bareun";

    /**
     * keystore 에서 읽어 둔 API 키.
     *
     * <p>여기서 한 번만 읽는다. Elasticsearch 는 부팅이 끝나면 keystore 를 닫으므로,
     * 인덱스를 열 때(=analyzer 공장을 만들 때) 읽으면 늦다. 자세한 사정은
     * {@link BareunSettings#readApiKey}.
     */
    private final String apiKey;

    /**
     * @param settings 노드 설정. 이 시점에는 keystore 가 아직 열려 있다.
     */
    public BareunPlugin(Settings settings) {
        this.apiKey = BareunSettings.readApiKey(settings);
    }

    @Override
    public List<Setting<?>> getSettings() {
        // 여기에 등록하지 않은 설정은 elasticsearch.yml 에 적어도 노드가 뜨지 않는다
        // ("unknown setting"). keystore 설정도 마찬가지다.
        return List.of(
                BareunSettings.HOST,
                BareunSettings.PORT,
                BareunSettings.TLS,
                BareunSettings.TIMEOUT,
                BareunSettings.API_KEY,
                BareunSettings.STOPTAGS,
                BareunSettings.CUSTOM_DICT_NAMES);
    }

    @Override
    public Map<String, AnalysisProvider<TokenizerFactory>> getTokenizers() {
        // 메서드 참조가 아니라 람다다. 공장에 apiKey 를 함께 넘겨야 하기 때문이다.
        AnalysisProvider<TokenizerFactory> provider =
                (indexSettings, env, name, settings) ->
                        new BareunTokenizerFactory(indexSettings, env, name, settings, apiKey);
        return Map.of(
                "bareun_tokenizer", provider,
                "baikal_tokenizer", provider);
    }

    @Override
    public Map<String, AnalysisProvider<AnalyzerProvider<? extends Analyzer>>> getAnalyzers() {
        AnalysisProvider<AnalyzerProvider<? extends Analyzer>> provider =
                (indexSettings, env, name, settings) ->
                        new BareunAnalyzerProvider(indexSettings, env, name, settings, apiKey);
        return Map.of(
                "bareun", provider,
                "baikal_analyzer", provider);
    }

    @Override
    public Map<String, AnalysisProvider<TokenFilterFactory>> getTokenFilters() {
        return Map.of(
                "bareun_part_of_speech", BareunPartOfSpeechFilterFactory::new,
                "baikal_token", BareunPartOfSpeechFilterFactory::new);
    }
}
