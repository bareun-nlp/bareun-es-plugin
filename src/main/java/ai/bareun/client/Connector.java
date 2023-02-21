package ai.bareun.client;

// import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.protobuf.util.JsonFormat;

import ai.bareun.protos.AnalyzeSyntaxRequest;
import ai.bareun.protos.AnalyzeSyntaxResponse;
import ai.bareun.protos.Document;
import ai.bareun.protos.LanguageServiceGrpc;
import io.grpc.CallOptions;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.StatusRuntimeException;
import io.grpc.Channel;
import io.grpc.MethodDescriptor;

import static io.grpc.Metadata.ASCII_STRING_MARSHALLER;

public class Connector {
    LanguageServiceGrpc.LanguageServiceBlockingStub client;
    private final static Logger LOGGER = Logger.getGlobal();
    protected String apiKey;
    protected String ip;
    protected int port;
    protected ManagedChannel channel;
    protected AnalyzeSyntaxResponse lastResponse;

    public final static int DEF_PORT = 5656;
    public final static String DEF_ADDRESS = "nlp.bareun.ai"; // "10.3.8.44";
    public final static String DEF_APIKEY = "koba-YOUR-KEY";

    public Connector() {
        this("INVALID API KEY", DEF_ADDRESS, DEF_PORT);
    }

    public Connector(String apiKey, String ip) {
        this(apiKey, ip, DEF_PORT);
    }

    public Connector(String apiKey, String ip, int port) {
        this.apiKey = apiKey;
        this.ip = ip;
        this.port = port;
    }

    protected class serviceClientInterceptor implements ClientInterceptor {
        @Override
        public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(MethodDescriptor<ReqT, RespT> method,
                CallOptions callOptions, Channel next) {
            return new ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>(next.newCall(method, callOptions)) {
                @Override
                public void start(Listener<RespT> responseListener, Metadata headers) {
                    headers.put(Metadata.Key.of("api-key", ASCII_STRING_MARSHALLER), apiKey);
                    super.start(responseListener, headers);
                }
            };
        }
    }


    public AnalyzeSyntaxResponse send(String text) {
        lastResponse = null;
        if (text == null || text.isEmpty())
            return lastResponse;
        lastResponse = AccessController.doPrivileged((PrivilegedAction<AnalyzeSyntaxResponse>) () -> {
            AnalyzeSyntaxResponse response = null;
            try {
                channel = ManagedChannelBuilder.forAddress(ip, port).usePlaintext()
                        .intercept(new serviceClientInterceptor()).build();
                        client = LanguageServiceGrpc.newBlockingStub(channel);
                LOGGER.setLevel(Level.INFO);
                LOGGER.info("analyze - '" + text + "'");
                Document document = Document.newBuilder().setContent(text).setLanguage("ko-KR").build();
                AnalyzeSyntaxRequest request = AnalyzeSyntaxRequest.newBuilder()
                        .setDocument(document)
                        .setAutoSplitSentence(true)
                        .setAutoSpacing(true)
                        .build();

                CallOptions.Key<String> metaDataKey = CallOptions.Key.create("api-key");
                response = client.withOption(metaDataKey, apiKey).analyzeSyntax(request);
            } catch (StatusRuntimeException e) {
                e.printStackTrace();
                LOGGER.warning(e.getMessage());
                LOGGER.warning(text);
                return null;
            } finally {
                channel.shutdown();
            }
            return response;
        });
        return lastResponse;
    }

    public AnalyzeSyntaxResponse get() {
        return lastResponse;
    }

    public void shutdownChannel() {
        channel.shutdown();
    }

    public String toJson() {
        String jsonString = "";

        if (lastResponse == null)
            return jsonString;
        try {
            jsonString = JsonFormat.printer().includingDefaultValueFields().print(lastResponse);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return jsonString;
    }
}
