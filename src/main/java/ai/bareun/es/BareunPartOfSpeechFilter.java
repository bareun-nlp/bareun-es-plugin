package ai.bareun.es;

import java.io.IOException;
import java.util.List;

import org.apache.lucene.analysis.FilteringTokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.TypeAttribute;

/**
 * 품사로 토큰을 거르는 필터.
 *
 * <p>토크나이저가 토큰의 {@code type} 에 품사 태그를 넣어 두므로 그것을 본다.
 * 토크나이저 쪽 {@code stoptags} 로도 같은 일을 할 수 있지만, 색인용과 검색용
 * analyzer 에서 다른 태그를 거르고 싶을 때는 이 필터가 필요하다.
 *
 * <p>비교는 접두사로 한다. {@code J} 하나로 모든 조사(JKS·JKB·JX …)가 빠진다.
 */
public final class BareunPartOfSpeechFilter extends FilteringTokenFilter {

    private final TypeAttribute typeAtt = addAttribute(TypeAttribute.class);
    private final List<String> stoptags;

    /**
     * @param input 앞 단계 토큰 스트림
     * @param stoptags 뺄 품사 태그 접두사들
     */
    public BareunPartOfSpeechFilter(TokenStream input, List<String> stoptags) {
        super(input);
        this.stoptags = List.copyOf(stoptags);
    }

    @Override
    protected boolean accept() throws IOException {
        String type = typeAtt.type();
        if (type == null) {
            // 다른 토크나이저가 앞에 있으면 품사가 없다. 거르지 않고 통과시킨다 —
            // 품사를 모르는 토큰을 조용히 버리면 색인이 비는 이유를 찾기 어렵다.
            return true;
        }
        for (String s : stoptags) {
            if (type.startsWith(s)) {
                return false;
            }
        }
        return true;
    }
}
