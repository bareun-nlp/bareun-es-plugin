# 플러그인을 미리 설치한 Elasticsearch 이미지.
#
# 빌드하기 전에 플러그인 zip 을 만들어 둔다:
#   mvn -B package            # 8.19.21 용
#   mvn -B -Pes9 package      # 9.5.2 용
#
# 그다음 저장소 루트에서:
#   docker build -f examples/docker/Dockerfile \
#     --build-arg ES_VERSION=8.19.21 -t elasticsearch-bareun:8.19.21 .
ARG ES_VERSION=8.19.21
FROM docker.elastic.co/elasticsearch/elasticsearch:${ES_VERSION}
ARG ES_VERSION

COPY target/releases/elasticsearch-analysis-bareun-1.0.0-es${ES_VERSION}.zip /tmp/plugin.zip

RUN bin/elasticsearch-plugin install --batch file:///tmp/plugin.zip && rm /tmp/plugin.zip

# API 키는 이미지에 굽지 않는다. 컨테이너를 띄울 때 keystore 에 넣는다:
#   docker exec -i <컨테이너> bin/elasticsearch-keystore add -x -f bareun.api_key <<< "koba-..."
# 서버 주소는 환경변수로 준다:
#   -e bareun.host=nlp.bareun.ai -e bareun.port=5656
