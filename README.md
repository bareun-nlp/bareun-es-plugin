# bareun-es-plugin

바른(bareun) 한국어 형태소 분석기를 Elasticsearch 분석기로 쓰는 플러그인입니다.

바른에 대해서는 [bareun.ai](https://bareun.ai) 를 보세요.

## 지원 버전

Elasticsearch 는 플러그인의 `elasticsearch.version` 이 노드 버전과 **정확히 같아야**
로드합니다. 그래서 대상 버전마다 zip 이 따로 나옵니다.

| Elasticsearch | 산출물 | 빌드 |
| --- | --- | --- |
| 8.19.21 | `elasticsearch-analysis-bareun-1.0.0-es8.19.21.zip` | `mvn package` |
| 9.5.2 | `elasticsearch-analysis-bareun-1.0.0-es9.5.2.zip` | `mvn -Pes9 package` |

다른 버전이 필요하면 `mvn -Delasticsearch.version=9.4.6 package` 처럼 지정합니다.
8.19 는 JDK 17, 9.x 는 JDK 21 이 필요합니다.

## 설치

[Releases](https://github.com/bareun-nlp/bareun-es-plugin/releases) 에서 노드 버전에
맞는 zip 을 받아 설치합니다.

```bash
bin/elasticsearch-plugin install file:///path/to/elasticsearch-analysis-bareun-1.0.0-es8.19.21.zip
```

## 설정

노드 단위 기본값은 `elasticsearch.yml` 에, API 키는 keystore 에 넣습니다.

```yaml
# elasticsearch.yml
bareun.host: nlp.bareun.ai
bareun.port: 5656
bareun.tls: false
bareun.timeout: 10s
bareun.stoptags: [E, IC, J, MAG, MAJ, MM, NA, NF, NV, SE, SF, SO, SP, SS, SW, VC, VX, XPN, XS]
bareun.custom_dict_names: []
```

```bash
bin/elasticsearch-keystore add bareun.api_key
```

설정을 바꾸면 노드를 다시 시작합니다.

> API 키를 인덱스 설정에 넣을 수는 없습니다. 인덱스 설정은 클러스터 상태에 평문으로
> 저장되기 때문입니다. keystore 만 받습니다.

도커에서는 설정을 환경변수로 줄 수 있습니다(`-e bareun.host=...`). 키는 컨테이너를
띄운 뒤 keystore 에 넣습니다. `examples/docker/` 에 예시가 있습니다.

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

옛 이름도 그대로 동작합니다. 이미 만들어진 인덱스가 깨지지 않게 한동안 함께 둡니다.
새 인덱스에는 `bareun_*` 를 쓰세요.

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
스스로 만드는데 Elasticsearch 는 플러그인의 스레드 생성을 막으므로 쓸 수 없습니다.

오프셋은 UTF-16 으로 받습니다. Lucene 의 `OffsetAttribute` 도 UTF-16 문자 단위라
변환 없이 그대로 씁니다. 하이라이팅 위치가 맞으려면 필요합니다.

플러그인이 담는 의존은 `protobuf-java` 하나입니다.

## 개발

```bash
mvn -B verify              # 8.19.21 대상, 단위 테스트 포함
mvn -B -Pes9 verify        # 9.5.2 대상 (JDK 21 필요)
```

실제 Elasticsearch 에 올려 확인하려면 `examples/docker/` 를 보세요.

## 라이선스

BSD 3-Clause
