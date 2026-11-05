package ai.bareun.es;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.elasticsearch.common.settings.Settings;
import org.junit.jupiter.api.Test;

import ai.bareun.analysis.BareunConfig;
import ai.bareun.analysis.BareunConfig.DecompoundMode;

/** 노드 설정과 analyzer 설정을 합치는 규칙 확인. */
class BareunSettingsTest {

    @Test
    void 아무것도_안_주면_기본값을_쓴다() {
        BareunConfig c = BareunSettings.resolve(Settings.EMPTY, Settings.EMPTY, "k");
        assertEquals("http://localhost:5656", c.baseUrl);
        assertEquals(10_000, c.timeoutMillis);
        assertEquals(BareunConfig.DEFAULT_STOPTAGS, c.stoptags);
        assertEquals(List.of(), c.customDictNames);
        assertEquals(DecompoundMode.MIXED, c.decompoundMode);
        assertEquals("k", c.apiKey);
    }

    @Test
    void 노드_설정이_기본값을_덮는다() {
        Settings node = Settings.builder()
                .put("bareun.host", "nlp.example")
                .put("bareun.port", 443)
                .put("bareun.tls", true)
                .put("bareun.timeout", "3s")
                .putList("bareun.stoptags", "J")
                .putList("bareun.custom_dict_names", "a", "b")
                .build();
        BareunConfig c = BareunSettings.resolve(node, Settings.EMPTY, "k");
        assertEquals("https://nlp.example:443", c.baseUrl);
        assertEquals(3_000, c.timeoutMillis);
        assertEquals(List.of("J"), c.stoptags);
        assertEquals(List.of("a", "b"), c.customDictNames);
    }

    @Test
    void analyzer_설정이_노드_설정을_덮는다() {
        Settings node = Settings.builder().put("bareun.host", "node-host")
                .putList("bareun.custom_dict_names", "node").build();
        Settings analyzer = Settings.builder().put("host", "idx-host")
                .putList("custom_dict_names", "idx")
                .put("decompound_mode", "discard").build();
        BareunConfig c = BareunSettings.resolve(node, analyzer, "k");
        assertEquals("http://idx-host:5656", c.baseUrl);
        assertEquals(List.of("idx"), c.customDictNames);
        assertEquals(DecompoundMode.DISCARD, c.decompoundMode);
    }

    @Test
    void 모르는_decompound_mode_는_설정_단계에서_막는다() {
        Settings analyzer = Settings.builder().put("decompound_mode", "typo").build();
        assertThrows(IllegalArgumentException.class,
                () -> BareunSettings.resolve(Settings.EMPTY, analyzer, "k"));
    }
}
