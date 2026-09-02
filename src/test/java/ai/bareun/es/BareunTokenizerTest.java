package ai.bareun.es;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import ai.bareun.es.BareunSettings.DecompoundMode;
import ai.bareun.protos.AnalyzeSyntaxResponse;
import ai.bareun.protos.Morpheme;
import ai.bareun.protos.Sentence;
import ai.bareun.protos.TextSpan;
import ai.bareun.protos.Token;

/**
 * 응답을 토큰으로 바꾸는 부분의 단위 테스트.
 *
 * <p>서버 응답을 손으로 만들어 확인한다. 살아 있는 서버가 필요한 검증은
 * 도커로 Elasticsearch 를 띄워 따로 한다.
 */
class BareunTokenizerTest {

    /**
     * 형태소 하나를 만든다.
     *
     * @param text 표층형
     * @param tag 품사
     * @param begin 시작 위치
     * @return 형태소
     */
    private static Morpheme morph(String text, Morpheme.Tag tag, int begin) {
        return Morpheme.newBuilder()
                .setText(TextSpan.newBuilder().setContent(text).setBeginOffset(begin))
                .setTag(tag)
                .build();
    }

    /** "아버지가 들어가신다" 를 흉내 낸 응답. */
    private static AnalyzeSyntaxResponse sample() {
        Token t1 = Token.newBuilder()
                .setText(TextSpan.newBuilder().setContent("아버지가").setBeginOffset(0))
                .addMorphemes(morph("아버지", Morpheme.Tag.NNG, 0))
                .addMorphemes(morph("가", Morpheme.Tag.JKS, 3))
                .build();
        Token t2 = Token.newBuilder()
                .setText(TextSpan.newBuilder().setContent("들어가신다").setBeginOffset(5))
                .addMorphemes(morph("들어가", Morpheme.Tag.VV, 5))
                .addMorphemes(morph("시", Morpheme.Tag.EP, 8))
                .addMorphemes(morph("ㄴ다", Morpheme.Tag.EF, 9))
                .build();
        return AnalyzeSyntaxResponse.newBuilder()
                .addSentences(Sentence.newBuilder().addTokens(t1).addTokens(t2))
                .build();
    }

    /**
     * 주어진 모드·불용태그로 토큰을 뽑는다.
     *
     * @param mode 분해 모드
     * @param stoptags 불용 태그
     * @return "표층형/품사@위치증가" 목록
     */
    private static List<String> emit(DecompoundMode mode, List<String> stoptags) {
        BareunSettings.Resolved r = new BareunSettings.Resolved(
                "http://x", "k", 1000, stoptags, List.of(), mode);
        return new BareunTokenizer(null, r).collect(sample()).stream()
                .map(e -> e.text + "/" + e.type + "@" + e.positionIncrement)
                .collect(Collectors.toList());
    }

    @Test
    void mixed_는_용언_어절을_원형과_같은_자리에_함께_낸다() {
        assertEquals(List.of("아버지/NNG@1", "들어가/VV@1", "들어가신다/VV@0"),
                emit(DecompoundMode.MIXED, BareunSettings.DEFAULT_STOPTAGS()));
    }

    @Test
    void discard_는_형태소만_낸다() {
        assertEquals(List.of("아버지/NNG@1", "들어가/VV@1"),
                emit(DecompoundMode.DISCARD, BareunSettings.DEFAULT_STOPTAGS()));
    }

    @Test
    void none_은_어절만_낸다() {
        assertEquals(List.of("아버지가/EOJEOL@1", "들어가신다/EOJEOL@1"),
                emit(DecompoundMode.NONE, BareunSettings.DEFAULT_STOPTAGS()));
    }

    @Test
    void 불용태그를_비우면_조사와_어미도_나온다() {
        List<String> out = emit(DecompoundMode.DISCARD, List.of());
        assertEquals(List.of("아버지/NNG@1", "가/JKS@1", "들어가/VV@1", "시/EP@1", "ㄴ다/EF@1"), out);
    }

    @Test
    void 오프셋은_서버가_준_값을_그대로_쓴다() {
        BareunSettings.Resolved r = new BareunSettings.Resolved(
                "http://x", "k", 1000, BareunSettings.DEFAULT_STOPTAGS(), List.of(),
                DecompoundMode.DISCARD);
        List<BareunTokenizer.Emitted> out = new BareunTokenizer(null, r).collect(sample());
        assertEquals(0, out.get(0).begin);
        assertEquals(3, out.get(0).length);
        assertEquals(5, out.get(1).begin);
    }

    @Test
    void 응답이_없으면_토큰도_없다() {
        BareunSettings.Resolved r = new BareunSettings.Resolved(
                "http://x", "k", 1000, List.of(), List.of(), DecompoundMode.MIXED);
        assertTrue(new BareunTokenizer(null, r).collect(null).isEmpty());
        assertTrue(new BareunTokenizer(null, r)
                .collect(AnalyzeSyntaxResponse.getDefaultInstance()).isEmpty());
    }

    @Test
    void 불용태그는_접두사로_비교한다() {
        BareunSettings.Resolved r = new BareunSettings.Resolved(
                "http://x", "k", 1000, List.of("J"), List.of(), DecompoundMode.DISCARD);
        // J 하나로 JKS·JKB·JX 가 모두 빠져야 한다.
        assertTrue(r.isStopTag("JKS"));
        assertTrue(r.isStopTag("JX"));
        assertTrue(r.isStopTag(""));
        assertTrue(r.isStopTag(null));
        assertEquals(false, r.isStopTag("NNG"));
    }

    @Test
    void 모르는_decompound_mode_는_오류로_막는다() {
        // 조용히 기본값으로 떨어뜨리면 색인이 의도와 다르게 만들어져도 드러나지 않는다.
        assertThrows(IllegalArgumentException.class, () -> DecompoundMode.from("wrong"));
        assertEquals(DecompoundMode.MIXED, DecompoundMode.from(null));
        assertEquals(DecompoundMode.MIXED, DecompoundMode.from(""));
        assertEquals(DecompoundMode.DISCARD, DecompoundMode.from(" Discard "));
    }
}
