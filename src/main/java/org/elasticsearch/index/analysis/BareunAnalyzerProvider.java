package org.elasticsearch.index.analysis;

// import org.apache.lucene.analysis.Analyzer;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.env.Environment;
import org.elasticsearch.index.IndexSettings;
// import org.elasticsearch.index.analysis.AbstractIndexAnalyzerProvider;

public class BareunAnalyzerProvider extends AbstractIndexAnalyzerProvider<BareunAnalyzer> {
    private final BareunAnalyzer analyzer;
    public BareunAnalyzerProvider(IndexSettings indexSettings, Environment environment, String s, Settings settings) {
        super(s, settings);
        analyzer = new BareunAnalyzer();
    }

    @Override
    public BareunAnalyzer get() {
        return analyzer;
    }
}
