package ai.bareun.es;

import org.apache.lucene.analysis.Analyzer;

import ai.bareun.client.BareunConnect;

/**
 * 바른 토크나이저 하나로 이뤄진 분석기.
 *
 * <p>불용 태그 거르기는 토크나이저가 이미 한다. 그래서 별도의 토큰 필터를 달지 않는다 —
 * 필요하면 인덱스 설정에서 {@code custom} analyzer 를 만들어 필터를 얹는다.
 */
public final class BareunAnalyzer extends Analyzer {

    private final BareunConnect connect;
    private final BareunSettings.Resolved settings;

    /**
     * @param connect 쓸 서버 연결
     * @param settings 합쳐진 설정
     */
    public BareunAnalyzer(BareunConnect connect, BareunSettings.Resolved settings) {
        this.connect = connect;
        this.settings = settings;
    }

    @Override
    protected TokenStreamComponents createComponents(String fieldName) {
        BareunTokenizer tokenizer = new BareunTokenizer(connect, settings);
        return new TokenStreamComponents(tokenizer);
    }
}
