#!/usr/bin/env bash
# 실제 컨테이너에 플러그인을 설치해 로드·분석을 확인한다(도커 필요).
#
#   scripts/it.sh elasticsearch 8.19.21
#   scripts/it.sh opensearch 2.19.7
#
# ES 9.x·OpenSearch 3.x 용 zip 은 JDK 21 로 만들어야 한다. JDK 21 이 없으면 먼저
# 도커로 zip 을 만들고, 그 경로를 세 번째 인자로 주면 JDK 17 으로 시험만 돌린다.
#
#   scripts/it.sh opensearch 3.9.0 bareun-analysis-opensearch/target/releases/opensearch-analysis-bareun-1.1.0-os3.9.0.zip
#
# 바른 서버는 시험 안의 모의 서버를 쓴다. API 키가 필요하면 환경변수
# BAREUN_TEST_API_KEY 로만 준다(없으면 모의 서버 전용 더미 값).
# MVN_ARGS 로 maven 인자를 덧붙일 수 있다(예: MVN_ARGS=-Dmaven.repo.local=/tmp/m2).
set -euo pipefail

product="${1:?제품(elasticsearch|opensearch)}"
version="${2:?제품 버전}"
zip_arg="${3:-}"
cd "$(dirname "$0")/.."

case "$product" in
  elasticsearch) mod=bareun-analysis-elasticsearch; prefix=elasticsearch-analysis-bareun; tag=es ;;
  opensearch)    mod=bareun-analysis-opensearch;    prefix=opensearch-analysis-bareun;    tag=os ;;
  *) echo "모르는 제품: $product" >&2; exit 2 ;;
esac

if [ -n "$zip_arg" ]; then
  zip="$(realpath "$zip_arg")"
else
  # 대상 버전을 속성으로 넘겨 그 버전용 zip 을 만든다.
  major="${version%%.*}"
  rel=17
  if { [ "$product" = elasticsearch ] && [ "$major" -ge 9 ]; } || { [ "$product" = opensearch ] && [ "$major" -ge 3 ]; }; then
    rel=21
  fi
  mvn -B -q ${MVN_ARGS:-} -pl "$mod" -am "-D${product}.version=${version}" \
    -Dmaven.compiler.release="$rel" -Dmaven.compiler.target="$rel" -DskipTests package
  zip="$PWD/$mod/target/releases/${prefix}-$(mvn -B -q help:evaluate -Dexpression=project.version -DforceStdout -N)-${tag}${version}.zip"
fi

mvn -B ${MVN_ARGS:-} -Pit -pl bareun-analysis-it -am \
  -Dit.product="$product" -Dit.version="$version" -Dit.zip="$zip" \
  -Dsurefire.failIfNoSpecifiedTests=false -Dtest='!*' verify

# 결과 요약. failsafe 가 verify 단계에서 실패를 알려 주지만, 몇 개가 돌았는지도 남긴다.
sed -n 's/.*<\(completed\|errors\|failures\|skipped\)>\([0-9]*\)<.*/\1=\2/p' \
  bareun-analysis-it/target/failsafe-reports/failsafe-summary.xml | tr '\n' ' '
echo
