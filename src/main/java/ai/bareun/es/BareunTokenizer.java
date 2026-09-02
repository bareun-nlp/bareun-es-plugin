package ai.bareun.es;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.analysis.tokenattributes.TypeAttribute;

import ai.bareun.client.BareunConnect;
import ai.bareun.es.BareunSettings.DecompoundMode;
import ai.bareun.protos.AnalyzeSyntaxResponse;
import ai.bareun.protos.Morpheme;
import ai.bareun.protos.Sentence;
import ai.bareun.protos.Token;

/**
 * 바른 서버로 형태소 분석을 받아 Lucene 토큰으로 내는 토크나이저.
 *
 * <p>입력 전체를 한 번에 읽어 서버에 한 번 보낸다. 형태소 분석은 문맥이 있어야 하므로
 * 조각으로 나눠 보낼 수 없다.
 *
 * <p>토큰의 오프셋은 서버가 UTF-16 기준으로 준 값을 그대로 쓴다. Lucene 의
 * {@link OffsetAttribute} 도 UTF-16 문자 단위라 변환이 필요 없다.
 */
public final class BareunTokenizer extends Tokenizer {

    /** 한 번에 읽어 들이는 크기. 입력이 크면 여러 번 반복해 이어 붙인다. */
    private static final int READ_CHUNK = 8192;

    private final CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);
    private final OffsetAttribute offsetAtt = addAttribute(OffsetAttribute.class);
    private final TypeAttribute typeAtt = addAttribute(TypeAttribute.class);
    private final PositionIncrementAttribute posIncAtt = addAttribute(PositionIncrementAttribute.class);

    private final BareunConnect connect;
    private final BareunSettings.Resolved settings;

    /** 서버에 이미 보냈는지. {@link #reset()} 마다 되돌린다. */
    private boolean analyzed;
    private Iterator<Emitted> pending;
    /** 입력 길이. {@link #end()} 에서 최종 오프셋을 맞추는 데 쓴다. */
    private int inputLength;

    /**
     * @param connect 쓸 서버 연결
     * @param settings 합쳐진 설정
     */
    public BareunTokenizer(BareunConnect connect, BareunSettings.Resolved settings) {
        this.connect = connect;
        this.settings = settings;
    }

    @Override
    public boolean incrementToken() throws IOException {
        clearAttributes();

        if (!analyzed) {
            analyzed = true;
            String text = readInput();
            inputLength = text.length();
            if (text.isEmpty()) {
                return false;
            }
            AnalyzeSyntaxResponse res = connect.analyze(text, settings.customDictNames);
            pending = collect(res).iterator();
        }

        if (pending == null || !pending.hasNext()) {
            return false;
        }
        Emitted e = pending.next();
        termAtt.append(e.text);
        typeAtt.setType(e.type);
        // 서버가 준 오프셋은 원문 기준이므로 correctOffset 을 거쳐야 char_filter 가
        // 붙은 경우에도 위치가 맞는다.
        offsetAtt.setOffset(correctOffset(e.begin), correctOffset(e.begin + e.length));
        posIncAtt.setPositionIncrement(e.positionIncrement);
        return true;
    }

    @Override
    public void end() throws IOException {
        super.end();
        // 마지막 토큰 뒤의 위치를 입력 끝으로 맞춘다. 하이라이팅과 구문 검색이
        // 문서 끝을 올바로 다루려면 필요하다.
        int finalOffset = correctOffset(inputLength);
        offsetAtt.setOffset(finalOffset, finalOffset);
    }

    @Override
    public void reset() throws IOException {
        super.reset();
        analyzed = false;
        pending = null;
        inputLength = 0;
    }

    /**
     * 입력을 끝까지 읽어 문자열로 만든다.
     *
     * @return 입력 전체
     * @throws IOException 읽기 실패
     */
    private String readInput() throws IOException {
        StringBuilder sb = new StringBuilder();
        char[] buf = new char[READ_CHUNK];
        int n;
        while ((n = input.read(buf, 0, buf.length)) > 0) {
            sb.append(buf, 0, n);
        }
        return sb.toString();
    }

    /**
     * 분석 결과를 낼 토큰 목록으로 바꾼다.
     *
     * @param res 서버 응답
     * @return 순서대로 낼 토큰들
     */
    List<Emitted> collect(AnalyzeSyntaxResponse res) {
        List<Emitted> out = new ArrayList<>();
        if (res == null) {
            return out;
        }
        for (Sentence s : res.getSentencesList()) {
            for (Token t : s.getTokensList()) {
                collectToken(t, out);
            }
        }
        return out;
    }

    /**
     * 어절 하나를 토큰으로 바꾼다.
     *
     * @param t 어절
     * @param out 결과를 담을 목록
     */
    private void collectToken(Token t, List<Emitted> out) {
        String eojeol = t.getText().getContent();
        int eojeolBegin = t.getText().getBeginOffset();

        if (settings.decompoundMode == DecompoundMode.NONE) {
            // 형태소로 쪼개지 않는다. 어절 그대로 하나만 낸다.
            out.add(new Emitted(eojeol, eojeolBegin, eojeol.length(), "EOJEOL", 1));
            return;
        }

        boolean first = true;
        for (Morpheme m : t.getMorphemesList()) {
            String tag = m.getTag().name();
            if (settings.isStopTag(tag)) {
                continue;
            }
            String text = m.getText().getContent();
            out.add(new Emitted(text, m.getText().getBeginOffset(), text.length(), tag, 1));

            // 용언은 어미가 붙어 원형과 표층형이 다르다. "들어가신다" 로 찾는 사용자와
            // "들어가다" 로 찾는 사용자를 모두 맞추려면 어절 전체도 같은 자리에 넣어야
            // 한다. 위치 증가가 0 이라 구문 검색에서 한 자리로 취급된다.
            if (settings.decompoundMode == DecompoundMode.MIXED
                    && first
                    && ("VV".equals(tag) || "VA".equals(tag))
                    && !eojeol.equals(text)) {
                out.add(new Emitted(eojeol, eojeolBegin, eojeol.length(), tag, 0));
            }
            first = false;
        }
    }

    /**
     * 낼 토큰 하나.
     *
     * <p>Lucene 의 속성을 채우기 전 중간 표현이다. 서버 응답을 훑는 일과 토큰을 내는
     * 일을 갈라 두면 {@link #collect} 를 서버 없이 테스트할 수 있다.
     */
    static final class Emitted {
        /** 토큰 문자열 */
        final String text;
        /** 원문에서의 시작 위치(UTF-16 문자 단위) */
        final int begin;
        /** 길이 */
        final int length;
        /** 토큰 종류. 품사 태그를 그대로 쓴다. */
        final String type;
        /** 위치 증가. 0 이면 앞 토큰과 같은 자리다. */
        final int positionIncrement;

        Emitted(String text, int begin, int length, String type, int positionIncrement) {
            this.text = text;
            this.begin = begin;
            this.length = length;
            this.type = type;
            this.positionIncrement = positionIncrement;
        }

        @Override
        public String toString() {
            return text + "/" + type + "@" + begin + "+" + length + (positionIncrement == 0 ? " (same)" : "");
        }
    }
}
