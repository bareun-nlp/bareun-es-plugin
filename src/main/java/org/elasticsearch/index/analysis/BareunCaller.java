package org.elasticsearch.index.analysis;

// import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
// import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.logging.Logger;

import ai.bareun.client.Connector;

public class BareunCaller extends Connector {
    // LanguageServiceGrpc.LanguageServiceBlockingStub client;
    private final static Logger LOGGER = Logger.getGlobal();
    private List<String> stopTokens; // tokenMorphemes,
    final static String DEF_STOP_TOKENS = "E,IC,J,MAG,MAJ,MM,NA,NF,NV,SE,SF,SO,SP,SS,SW,VC,VX,XPN,XS";

    String configPath = "";
    // String ip;
    // int port;
    // ManagedChannel channel;
    final static String CONFIG_FILE = "/usr/share/elasticsearch/data/config.properties";

    public static class NlpSettings {
        public String apiKey;
        public String ip;
        public int port;
        public List<String> stopTokens;

        public NlpSettings(String apiKey, String ip, int port, List<String> stopTokens) {
            this.apiKey = apiKey;
            this.ip = ip;
            this.port = port;
            this.stopTokens = stopTokens;
        }
    }

    public static NlpSettings getSettingsFromConfig() {
        return getSettingsFromConfig("");
    }

    /**
     * 설정 파일에서 바른 서버 접속 정보를 읽는다.
     *
     * <p>Elasticsearch 는 SecurityManager 아래에서 돌기 때문에 파일 읽기를
     * {@code doPrivileged} 로 감싸야 한다. 플러그인 정책(plugin-security.policy)에
     * 해당 경로의 read 권한이 선언돼 있다.
     *
     * @param configPath 설정 파일 경로. 빈 문자열이면 {@link #CONFIG_FILE} 을 쓴다.
     * @return 읽어 낸 설정. 파일이 없거나 읽지 못하면 기본값으로 채운 설정을 돌려준다.
     *         이때 API 키는 유효하지 않은 자리표시자이므로 실제 호출은 인증에서 막힌다 —
     *         노드 기동을 막지 않으면서 설정 누락을 로그로 드러내려는 의도다.
     */
    public static NlpSettings getSettingsFromConfig(String configPath) {
        return AccessController.doPrivileged((PrivilegedAction<NlpSettings>) () -> {
            List<String> defaultStopTokens = new ArrayList<String>(Arrays.asList(DEF_STOP_TOKENS.split(",")));
            String pathToRead = configPath.isEmpty() ? CONFIG_FILE : configPath;
            try {
                File path = new File(pathToRead);
                FileReader file = new FileReader(path);

                Properties p = new Properties();
                p.load(file); // 파일 열어줌
                String apiKey = p.getProperty("bareun_api_key", DEF_APIKEY);
                String ip = p.getProperty("bareun_server_address", DEF_ADDRESS);
                int port = Integer.parseInt(p.getProperty("bareun_server_port", String.valueOf(DEF_PORT)));
                String strs = p.getProperty("stoptags", DEF_STOP_TOKENS);
                ArrayList<String> stopTokens = new ArrayList<String>(Arrays.asList(strs.split(",")));
                return new NlpSettings(apiKey, ip, port, stopTokens);
            } catch (Exception e) {
                // 설정을 못 읽어도 노드는 떠야 하므로 기본값으로 계속한다.
                LOGGER.warning(String.format("cannot read %s (%s), falling back to defaults", pathToRead, e.getMessage()));
                return new NlpSettings(DEF_APIKEY, DEF_ADDRESS, DEF_PORT, defaultStopTokens);
            }
        });
    }

    public BareunCaller() {
        this(getSettingsFromConfig());
    }

    public BareunCaller(NlpSettings settings) {
        super(settings.apiKey, settings.ip, settings.port);
        stopTokens = settings.stopTokens;
        LOGGER.info(String.format("SETTINGS - %s %s:%d", apiKey, ip, port));
    }

    /*
     * public AnalyzeSyntaxResponse send(String text) {
     * 
     * return
     * AccessController.doPrivileged((PrivilegedAction<AnalyzeSyntaxResponse>) () ->
     * {
     * AnalyzeSyntaxResponse response = null;
     * try {
     * channel = ManagedChannelBuilder.forAddress(ip, port).usePlaintext().build();
     * client = LanguageServiceGrpc.newBlockingStub(channel);
     * LOGGER.setLevel(Level.INFO);
     * LOGGER.info("analyze - '"+text+"'");
     * Document document =
     * Document.newBuilder().setContent(text).setLanguage("ko-KR").build();
     * AnalyzeSyntaxRequest request =
     * AnalyzeSyntaxRequest.newBuilder().setDocument(document).build();
     * response = client.analyzeSyntax(request);
     * 
     * } catch (StatusRuntimeException e) {
     * LOGGER.warning(e.getMessage());
     * LOGGER.warning(text);
     * return null;
     * } finally {
     * channel.shutdown();
     * }
     * return response;
     * });
     * }
     */

    boolean inIn(String s, List<String> list) {
        if (s == null || s == "")
            return false;
        for (String s2 : list) {
            if (s.startsWith(s2))
                return true;
        }
        return false;
    }

    public String isEsToken(String morpheme) {
        if (morpheme == null || morpheme == "")
            return "UNKOWN";
        return stopTokens == null || !inIn(morpheme, stopTokens) ? morpheme : "";
    }

}
