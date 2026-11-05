# bareun-es-plugin

바른(bareun) 한국어 형태소 분석기를 Elasticsearch 와 OpenSearch 분석기로 쓰는 플러그인입니다.

바른에 대해서는 [bareun.ai](https://bareun.ai) 를 보세요. 서버 API 는 바른 3.4.0 에 맞춰져 있습니다.

## 지원 버전

두 제품 모두 플러그인의 대상 버전이 노드 버전과 **정확히 같아야** 로드합니다. 그래서
제품마다, 버전마다 zip 이 따로 나옵니다. 파일 이름에 제품과 버전이 들어 있습니다.

| 제품 | 대상 버전 | 산출물 | 빌드 | JDK |
| --- | --- | --- | --- | --- |
| Elasticsearch | 8.19.21 | `elasticsearch-analysis-bareun-1.1.0-es8.19.21.zip` | `-pl bareun-analysis-elasticsearch -am` | 17 |
| Elasticsearch | 9.5.2 | `elasticsearch-analysis-bareun-1.1.0-es9.5.2.zip` | `-pl bareun-analysis-elasticsearch -am -Pes9` | 21 |
| OpenSearch | 2.19.7 | `opensearch-analysis-bareun-1.1.0-os2.19.7.zip` | `-pl bareun-analysis-opensearch -am` | 17 |
| OpenSearch | 3.9.0 | `opensearch-analysis-bareun-1.1.0-os3.9.0.zip` | `-pl bareun-analysis-opensearch -am -Pos3` | 21 |

위 네 버전은 실제 컨테이너에 설치해 로드와 분석까지 확인합니다(CI 통합 시험).
다른 버전이 필요하면 `-Delasticsearch.version=9.4.6` 이나 `-Dopensearch.version=2.18.0`
처럼 지정해 직접 빌드합니다. 이때는 그 버전에서 컴파일되는지 확인이 필요합니다.

### 구성

```
bareun-analysis-common         바른 서버 호출·토크나이저·토큰 필터(제품 무관)
bareun-analysis-elasticsearch  Elasticsearch 플러그인
bareun-analysis-opensearch     OpenSearch 플러그인
bareun-analysis-it             컨테이너 통합 시험(-Pit)
```

## 설치

[Releases](https://github.com/bareun-nlp/bareun-es-plugin/releases) 에서 제품과 노드
버전에 맞는 zip 을 받아 설치합니다.

```bash
# Elasticsearch
bin/elasticsearch-plugin install file:///path/to/elasticsearch-analysis-bareun-1.1.0-es8.19.21.zip

# OpenSearch
bin/opensearch-plugin install file:///path/to/opensearch-analysis-bareun-1.1.0-os2.19.7.zip
```

## 설정

노드 단위 기본값은 `elasticsearch.yml`(OpenSearch 는 `opensearch.yml`)에, API 키는
keystore 에 넣습니다. 설정 이름은 두 제품이 같습니다.

```yaml
# elasticsearch.yml / opensearch.yml
bareun.host: nlp.bareun.ai
bareun.port: 5656
bareun.tls: false
bareun.timeout: 10s
bareun.stoptags: [E, IC, J, MAG, MAJ, MM, NA, NF, NV, SE, SF, SO, SP, SS, SW, VC, VX, XPN, XS]
bareun.custom_dict_names: []
```

```bash
bin/elasticsearch-keystore add bareun.api_key     # OpenSearch: bin/opensearch-keystore
```

설정을 바꾸면 노드를 다시 시작합니다.

> API 키를 인덱스 설정에 넣을 수는 없습니다. 인덱스 설정은 클러스터 상태에 평문으로
> 저장되기 때문입니다. keystore 만 받습니다.

도커에서는 설정을 환경변수로 줄 수 있습니다(`-e bareun.host=...`). 키는 컨테이너를
띄운 뒤 keystore 에 넣습니다. `examples/docker/` 에 Elasticsearch 와 OpenSearch 예시가
있습니다.

## 쓰기

가장 간단한 형태입니다.

```
PUT /my-index
{
  "settings": {
    "analysis": {
      "analyzer": {
        "korean": { "type": "bareun" }
      }
    }
  }
}
```

```
POST /my-index/_analyze
{ "analyzer": "korean", "text": "아버지가 방에 들어가신다." }
```

```
아버지/NNG  방/NNG  들어가/VV  들어가신다./VV
```

## 등록되는 이름

| 종류 | 이름 | 옛 이름 |
| --- | --- | --- |
| analyzer | `bareun` | `baikal_analyzer` |
| tokenizer | `bareun_tokenizer` | `baikal_tokenizer` |
| token filter | `bareun_part_of_speech` | `baikal_token` |

옛 이름은 Elasticsearch 용에서만 동작합니다. 이미 만들어진 인덱스가 깨지지 않게 한동안
함께 둡니다. 새 인덱스에는 `bareun_*` 를 쓰세요. OpenSearch 용은 새로 나온 플러그인이라
옛 이름이 없고, 인덱스 설정은 두 제품 사이에서 그대로 옮길 수 있습니다.

## 토크나이저 옵션

analyzer 설정에서 노드 기본값을 덮어쓸 수 있습니다.

```
PUT /my-index
{
  "settings": {
    "analysis": {
      "tokenizer": {
        "my_bareun": {
          "type": "bareun_tokenizer",
          "decompound_mode": "mixed",
          "stoptags": ["E", "J", "SF"],
          "custom_dict_names": ["mydict"],
          "host": "10.0.0.5",
          "port": 5656
        }
      },
      "analyzer": {
        "korean": { "type": "custom", "tokenizer": "my_bareun" }
      }
    }
  }
}
```

| 옵션 | 뜻 | 기본값 |
| --- | --- | --- |
| `decompound_mode` | `none`·`discard`·`mixed` | `mixed` |
| `stoptags` | 뺄 품사 태그 접두사 | 노드 설정 |
| `custom_dict_names` | 쓸 사용자 사전 이름들 | 노드 설정 |
| `host`·`port`·`tls`·`timeout` | 서버 접속 | 노드 설정 |

### `decompound_mode`

"아버지가 방에 들어가신다." 를 넣었을 때의 차이입니다.

| 모드 | 결과 |
| --- | --- |
| `mixed`(기본) | `아버지` `방` `들어가` `들어가신다.` |
| `discard` | `아버지` `방` `들어가` |
| `none` | `아버지가` `방에` `들어가신다.` |

`mixed` 는 용언 어절을 원형과 **같은 자리**(position)에 함께 넣습니다. `들어가다` 로
찾는 사용자와 `들어가신다` 로 찾는 사용자를 모두 맞추기 위해서입니다.

### `stoptags`

접두사로 비교합니다. `J` 하나로 모든 조사(`JKS`·`JKB`·`JX` …)가 빠집니다.
품사 태그는 [바른 문서](https://docs.bareun.ai/) 를 보세요.

## 품사 필터

색인용과 검색용 analyzer 에서 다른 품사를 거르고 싶을 때 씁니다.

```
"analyzer": {
  "korean": {
    "type": "custom",
    "tokenizer": "bareun_tokenizer",
    "filter": ["bareun_part_of_speech"]
  }
},
"filter": {
  "bareun_part_of_speech": { "type": "bareun_part_of_speech", "stoptags": ["J", "E"] }
}
```

## 어떻게 붙는가

Connect 의 단항 호출은 평범한 HTTP POST 입니다.

```
POST /bareun.LanguageService/AnalyzeSyntax
Content-Type: application/proto
api-key: koba-...
```

그래서 `HttpURLConnection` 으로 붙습니다. `java.net.http.HttpClient` 는 셀렉터 스레드를
스스로 만드는데 Elasticsearch·OpenSearch 는 플러그인의 스레드 생성을 막으므로 쓸 수
없습니다. 접속은 `AccessController.doPrivileged` 안에서 합니다. SecurityManager 를 쓰는
OpenSearch 2.x 는 호출 스택에 Lucene·서버 코드가 섞여 있어, 이렇게 하지 않으면
`plugin-security.policy` 로 권한을 줘도 접속이 막힙니다.

오프셋은 UTF-16 으로 받습니다. Lucene 의 `OffsetAttribute` 도 UTF-16 문자 단위라
변환 없이 그대로 씁니다. 하이라이팅 위치가 맞으려면 필요합니다.

플러그인이 담는 의존은 `protobuf-java` 하나입니다. 다만 OpenSearch 는 코어의 `lib/` 에
같은 jar 를 들고 있어(2.19 는 3.25.5, 3.9 는 3.25.8) zip 에 담으면 설치할 때 jar hell
로 거부됩니다. 그래서 OpenSearch 용 zip 에는 `protobuf-java` 를 담지 않고 코어 것을 씁니다.

## 개발

```bash
mvn -B verify                                                  # 전 모듈 단위 시험, 기본 대상(ES 8.19.21, OS 2.19.7)
mvn -B -pl bareun-analysis-elasticsearch -am -Pes9 verify      # ES 9.5.2 (JDK 21 필요)
mvn -B -pl bareun-analysis-opensearch -am -Pos3 verify         # OS 3.9.0 (JDK 21 필요)
```

실제 컨테이너에 설치해 로드와 분석을 확인하려면(도커 필요):

```bash
scripts/it.sh elasticsearch 8.19.21
scripts/it.sh opensearch 2.19.7
```

바른 서버는 시험 안의 모의 서버로 갈음합니다. API 키가 필요하면 환경변수
`BAREUN_TEST_API_KEY` 로만 줍니다(없으면 모의 서버 전용 더미 값). 9.x·3.x 용 zip 은
JDK 21 로 만들어야 하므로, JDK 21 이 없으면 zip 을 먼저 만들고 경로를 세 번째 인자로
줍니다. 자세한 것은 `scripts/it.sh` 머리말을 보세요.

실제 바른 서버와 함께 띄워 보려면 `examples/docker/` 를 보세요.

## 라이선스

BSD 3-Clause
