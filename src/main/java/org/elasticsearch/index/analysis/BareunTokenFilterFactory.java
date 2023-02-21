package org.elasticsearch.index.analysis;

import org.apache.lucene.analysis.TokenStream;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.env.Environment;
import org.elasticsearch.index.IndexSettings;

public class BareunTokenFilterFactory extends AbstractTokenFilterFactory {
    //public NlpTokenFilterFactory(String name, Settings settings) {
    public BareunTokenFilterFactory(IndexSettings indexSettings, Environment env, String name, Settings settings) {    
        super(name, settings);
    }
    @Override
    public TokenStream create(TokenStream stream) {
        return new BareunTokenFilter(stream);
    }
}
