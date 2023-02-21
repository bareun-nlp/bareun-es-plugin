package org.elasticsearch.index.analysis;

import org.apache.lucene.analysis.*;

//import java.io.Reader;

public class BareunAnalyzer extends Analyzer {
    public BareunAnalyzer() {
        super();
    }

    @Override
    protected TokenStreamComponents createComponents(final String fieldName) {
        Tokenizer tokenizer = new NlpTokenizer();
        TokenStream stream = new BareunTokenFilter(tokenizer);
        return new TokenStreamComponents(tokenizer, stream);
    }
}
