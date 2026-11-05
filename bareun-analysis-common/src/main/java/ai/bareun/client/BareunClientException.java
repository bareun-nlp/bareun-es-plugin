package ai.bareun.client;

/**
 * 바른 서버 호출이 실패했을 때 던지는 예외.
 *
 * <p>분석 도중에 나므로 Lucene 의 {@code incrementToken} 밖으로 나가면 색인·검색이
 * 실패한다. 호출하는 쪽에서 잡아 빈 토큰 스트림으로 떨어뜨릴지, 그대로 올릴지
 * 정한다 — 조용히 삼키면 "색인은 됐는데 아무것도 검색되지 않는" 상태가 되므로
 * 기본은 올리는 쪽이다.
 */
public class BareunClientException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int httpStatus;

    /**
     * @param message 사람이 읽는 오류 메시지
     * @param httpStatus HTTP 상태 코드. 접속 자체가 안 된 경우는 0.
     * @param cause 원인 예외. 없으면 null.
     */
    public BareunClientException(String message, int httpStatus, Throwable cause) {
        super(message, cause);
        this.httpStatus = httpStatus;
    }

    /** @return HTTP 상태 코드. 서버에 닿지도 못한 경우는 0 이다. */
    public int getHttpStatus() {
        return httpStatus;
    }

    /** @return 인증·라이선스 문제로 거부당한 것이면 true */
    public boolean isAuthFailure() {
        return httpStatus == 401 || httpStatus == 403;
    }
}
