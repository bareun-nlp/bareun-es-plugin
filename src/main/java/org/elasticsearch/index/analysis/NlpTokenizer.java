package org.elasticsearch.index.analysis;

import java.io.IOException;
import java.nio.Buffer;
import java.nio.CharBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;
// import java.util.stream.Stream;

import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.analysis.tokenattributes.TypeAttribute;
import org.apache.lucene.util.AttributeFactory;
// import static java.util.stream.Collectors.toList;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.env.Environment;
import org.elasticsearch.index.IndexSettings;

import ai.bareun.protos.AnalyzeSyntaxResponse;
import ai.bareun.protos.Morpheme;
import ai.bareun.protos.Sentence;
import ai.bareun.protos.Token;

final class MyToken {
    public String text;
    public int beginOffset;
    public int length;
    public int positionInc;
    public String type;
    //public boolean isLast;
    //public boolean isEmpty;
}

public final class NlpTokenizer extends Tokenizer {
    private final static Logger LOGGER = Logger.getGlobal();

    private final CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);
    private final OffsetAttribute offsetAtt = addAttribute(OffsetAttribute.class);
    private final TypeAttribute typeAtt = addAttribute(TypeAttribute.class);
    private final PositionIncrementAttribute posIncAtt = addAttribute(PositionIncrementAttribute.class);

    private Boolean doneGetInputString = false;
    //private String usingRawString = "";
    //private int tokenIndex = 0;
    //private int sentenceIndex = 0;
    //private Boolean morphemeCheckDoneOnTokens = false;
    BareunCaller caller = null;
    //List<Sentence> sentences;
    //ArrayList<MyToken> tokens;
    Iterator<MyToken> itMyToken = null;


    public NlpTokenizer(IndexSettings indexSettings, Environment environment,Settings settings) {
        
        this(getSettings(indexSettings, environment, settings));
    }

    public NlpTokenizer() {
        this(BareunCaller.getSettingsFromConfig());
    }

    private NlpTokenizer(BareunCaller.NlpSettings settings) {
        super(AttributeFactory.DEFAULT_ATTRIBUTE_FACTORY);
        caller = new BareunCaller(settings);
    }


    public AnalyzeSyntaxResponse getInputString() throws IOException {
        int readDone = -1;
        String text = "";
        do {
            final CharBuffer buffer = CharBuffer.allocate(1024);
            buffer.compact();
            ((Buffer) buffer).clear();
            readDone = input.read(buffer);
            doneGetInputString = true;
            if( readDone > 0 )
                text += new String(buffer.array(), 0, readDone);
        } while (readDone != -1);

        //text = text.trim();
        //usingRawString = text;
        if( text.isEmpty() ) return null;
        return caller.send(text);
    }

    private static BareunCaller.NlpSettings getSettings(IndexSettings indexSettings, Environment environment,Settings settings) {
        BareunCaller.NlpSettings s = BareunCaller.getSettingsFromConfig();

        List<String> stopTokens = Analysis.getWordList(environment, settings, "stoptags");
        if( stopTokens != null ) {
            s.stopTokens = stopTokens;
            LOGGER.info("stoptags re setting.");
        }
        s.ip = settings.get("bareun_server_address", s.ip);
        LOGGER.info("bareun_server_address = " + s.ip);
        s.port = settings.getAsInt("bareun_server_port", s.port);
        LOGGER.info("bareun_server_port = " + s.port);
        return s;
    }
/*
    private boolean isLastSentence() {
        if (sentences.size() == sentenceIndex + 1) {
            return true;
        }
        return false;
    }

    private boolean isLastToken() {
        if (tokens.size() <= tokenIndex) {
            return true;
        }
        return false;
    }
 */
    private ArrayList<MyToken> getWords(List<Sentence> sentences) {
        ArrayList<MyToken> tokens = new ArrayList<MyToken>();

        for( Sentence sentence : sentences) {
            for( Token t : sentence.getTokensList() ) {
                int iMorpheme = 0;
                for( Morpheme morpheme : t.getMorphemesList()) {                    
                    String text = morpheme.getText().getContent();
                    String tag = morpheme.getTag().name();
                    //System.out.println(text + " " + tag);
                    String tokenName = caller.isEsToken(tag);
                    if (!tokenName.isEmpty()) {
                        MyToken temp2 = new MyToken();
                        temp2.text = text;
                        temp2.beginOffset = morpheme.getText().getBeginOffset();
                        temp2.length = temp2.text.length();
                        temp2.type = tokenName;
                        temp2.positionInc = 1;
                        //temp2.isLast = false;
                        //temp2.isEmpty = false;
                        tokens.add(temp2);

                        // TODO : VV나 VA인경우 어절 전체를 등록한다.
                        if( iMorpheme == 0 && (tokenName == "VV" ||  tokenName == "VA") && 
                            t.getText().getContent() != text ) {
                            MyToken token = new MyToken();
                            token.text = t.getText().getContent();
                            token.beginOffset = t.getText().getBeginOffset();
                            token.length = token.text.length();
                            token.type = tokenName;
                            token.positionInc = 0;
                            tokens.add(token);
                        }
                    }
                    iMorpheme++;
                }
            }
        }
        return tokens;
    }
/*
    private void makeTokens() {
        tokens = new ArrayList<MyToken>();
        //System.out.println(sentences);
        
        for (int i = 0; i < sentences.get(sentenceIndex).getTokensCount(); i++) {

            for (int j = 0; j < sentences.get(sentenceIndex).getTokens(i).getMorphemesCount(); j++) {
                Morpheme morpheme = sentences.get(sentenceIndex).getTokens(i).getMorphemes(j);
                String text = morpheme.getText().getContent();
                String tag = morpheme.getTag().name();
                //System.out.println(text + " " + tag);
                String tokenName = caller.isEsToken(tag);
                if (!tokenName.isEmpty()) {
                    MyToken temp2 = new MyToken();
                    temp2.text = morpheme.getText().getContent();
                    temp2.beginOffset = morpheme.getText().getBeginOffset();
                    temp2.length = temp2.text.length();
                    temp2.type = tokenName;
                    temp2.isLast = false;
                    temp2.isEmpty = false;
                    tokens.add(temp2);
                }
            }
        }
    }

    private MyToken nextToken() {
        if (isLastToken()) {
            if (isLastSentence()) {
                MyToken temp = new MyToken();
                temp.isLast = true;
                return temp;
            }
            tokenIndex = 0;
            sentenceIndex++;
            makeTokens();
        }
        if (!tokens.isEmpty() && tokens.size() > tokenIndex) {
            return tokens.get(tokenIndex++);
        } else {
            MyToken temp = new MyToken();
            temp.isEmpty = true;
            return temp;
        }
    }
 */

    @Override
    public boolean incrementToken() throws IOException {
        clearAttributes();
        if (!doneGetInputString) {
            // sentences = null;
            AnalyzeSyntaxResponse response = getInputString();
            if (response == null) return false;

            /*
            try {
                String jsonString = JsonFormat.printer().includingDefaultValueFields().print(response);
                LOGGER.info(jsonString);
            } catch(Exception e) {
                e.printStackTrace();
            }
             */
            List<Sentence> sentences = response.getSentencesList();
            if (sentences == null || sentences.size() == 0) return false;
            List<MyToken> tokens = getWords(sentences);
            this.itMyToken = tokens.iterator();
            
        }
        if( itMyToken != null && itMyToken.hasNext() ) {
            MyToken item = itMyToken.next();
            termAtt.append(item.text);
            typeAtt.setType(item.type);
            offsetAtt.setOffset(item.beginOffset, item.beginOffset + item.length);
            posIncAtt.setPositionIncrement(item.positionInc);
            return true;
        }
        return false;
    }

/* 
    @Override
    public boolean incrementTokenOld() throws IOException {
        // TODO proxy로 grpc 보내고 결과를 tokenizing 해서 token filter로 보내기
        clearAttributes();
        if (!doneGetInputString) {
            // sentences = null;
            AnalyzeSyntaxResponse response = getInputString();
            if (temp == null) return false;

            List<Sentence> tempList = temp.getSentencesList();
            // sentences = tempList;
            if (sentences == null || sentences.size() == 0) return false;
            makeTokens();
        }
        MyToken item = nextToken();
        if (item.isLast) {
            return false;
        } else {
            while(item.isEmpty) {
                item = nextToken();
            }
            if (item.isLast) {
                return false;
            }
            termAtt.append(item.text);
            typeAtt.setType(item.type);
            offsetAtt.setOffset(item.beginOffset, item.beginOffset + item.length);
        }
        return true;
    }
*/
    @Override
    public void close() throws IOException {
        super.close();
        doneGetInputString = false;
        itMyToken = null;
        // tokenIndex = 0;
        // sentenceIndex = 0;
    }

    @Override
    public void reset() throws IOException {
        super.reset();
        doneGetInputString = false;
        itMyToken = null;
        // tokenIndex = 0;
        // sentenceIndex = 0;
    }

    @Override
    public void end() throws IOException {
        super.end();
        doneGetInputString = false;
        itMyToken = null;
        // tokenIndex = 0;
        // sentenceIndex = 0;
    }
}
