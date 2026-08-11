# AI 파이프라인 소요시간 측정 가이드

## 목적

이 문서는 FOWOCO의 `PLAN → Slot 조회 → ANALYZE → Renewal → 문서 생성` 흐름을
Server 경계에서 반복 측정하고, 데모 발표의 정량 평가 근거로 정리하는 방법을
설명합니다.

Server는 AI Runtime HTTP 왕복시간을 측정합니다. BERT·A.X·LangGraph 내부 Node의
실행시간과 모델 정확도는 AI 팀의 평가 결과를 사용합니다. 두 값을 같은 지표로
혼합하지 않습니다.

## 기록되는 단계

### AiRun

| Phase | Stage | 의미 |
| --- | --- | --- |
| `PLAN` | `PLAN_RUNTIME_CALL` | AI Runtime의 PLAN 요청·응답과 Server 계약 검증 |
| `ANALYZE` | `SLOT_RESOLUTION` | tenant와 allow-list를 적용한 DB 업무정보 조회 |
| `ANALYZE` | `ANALYZE_RUNTIME_CALL` | PLAN 결정을 재사용한 ANALYZE 요청·응답과 검증 |
| `PLAN/ANALYZE` | `RESULT_PERSIST` | 검증된 결과 저장과 공개 상태 Event 발행 |
| `PIPELINE/ANALYZE` | `TOTAL` | 자동 실행 묶음의 전체 시간. HR 답변 대기시간은 포함하지 않음 |

### Renewal

| Stage | 의미 |
| --- | --- |
| `CONTEXT_LOAD` | Task·Worker·Company·OCR·Slot Context 조회 |
| `RENEWAL_RUNTIME_CALL` | Renewal Agent HTTP 왕복과 계약 검증 |
| `DOCUMENT_GENERATION` | HWP/HWPX 생성 결과 준비 |
| `RESULT_APPLY` | 안내 초안·Task 상태·생성 파일 반영 |
| `TOTAL` | Renewal 자동 실행 전체 시간 |

구조화 로그에는 추적용 `request_id`, `attempt_id`, 단계, 성공·실패, 소요시간과
안전한 오류 코드만 남습니다. 발화문, 실명, Slot 실제 값, Token과 AI 응답 본문은
남기지 않습니다.

## 로컬 실행

### 1. Server 실행

Prometheus endpoint는 기본적으로 인증 없이 접근할 수 없습니다. 로컬 측정할 때만
`observability` profile을 함께 활성화합니다.

```bash
SERVER_ADDRESS=0.0.0.0 SPRING_PROFILES_ACTIVE=local,observability ./gradlew bootRun
```

Docker Desktop의 Prometheus가 호스트의 Server에 접근하도록 로컬 실행 주소만
`0.0.0.0`으로 변경합니다. 신뢰할 수 있는 개발 네트워크에서만 사용하고 측정 후
Server를 종료합니다. 이 값을 주지 않으면 Server는 계속 `127.0.0.1`에만 바인딩됩니다.

다음 주소에서 Prometheus 형식의 원시 지표를 확인합니다.

```text
http://127.0.0.1:8080/actuator/prometheus
```

`observability` profile은 `prod`와 함께 활성화해도 공개 Security Chain이 생성되지
않도록 막혀 있습니다. 운영 환경에서 Prometheus를 연결할 때는 별도 내부망 인증
정책을 먼저 정합니다.

### 2. Prometheus 실행

Server가 실행된 상태에서 다음 명령을 사용합니다.

```bash
docker compose -f compose.observability.yml up -d
```

- Prometheus: http://127.0.0.1:9090
- 수집 상태: http://127.0.0.1:9090/targets

측정을 마치고 컨테이너만 중지할 때는 다음을 사용합니다.

```bash
docker compose -f compose.observability.yml down
```

로컬 측정 데이터까지 삭제하려면 명시적으로 `down -v`를 사용합니다.

## 주요 Metric

| Metric | 설명 |
| --- | --- |
| `fowoco_ai_pipeline_stage_seconds` | AiRun 단계별 Server 관측시간 |
| `fowoco_ai_analysis_outcomes_total` | 검증된 분석 응답 Outcome 횟수 |
| `fowoco_ai_pipeline_failures_total` | 단계와 안전한 오류 코드별 실패 횟수 |
| `fowoco_renewal_stage_seconds` | Renewal 단계별 Server 관측시간 |
| `fowoco_renewal_failures_total` | Renewal 오류 코드별 실패 횟수 |

Timer는 Prometheus에서 `_count`, `_sum`, `_max`, `_bucket` 시계열로 노출됩니다.
Prometheus 시간 단위는 초이고 구조화 로그의 `duration_ms`는 밀리초입니다.

## PromQL 예시

최근 30분 PLAN 평균시간:

```promql
sum(increase(fowoco_ai_pipeline_stage_seconds_sum{phase="PLAN",stage="PLAN_RUNTIME_CALL",status="SUCCESS"}[30m]))
/
sum(increase(fowoco_ai_pipeline_stage_seconds_count{phase="PLAN",stage="PLAN_RUNTIME_CALL",status="SUCCESS"}[30m]))
```

최근 30분 단계별 중앙값:

```promql
histogram_quantile(
  0.50,
  sum by (le, phase, stage) (
    increase(fowoco_ai_pipeline_stage_seconds_bucket{status="SUCCESS"}[30m])
  )
)
```

최근 30분 단계별 95백분위:

```promql
histogram_quantile(
  0.95,
  sum by (le, phase, stage) (
    increase(fowoco_ai_pipeline_stage_seconds_bucket{status="SUCCESS"}[30m])
  )
)
```

분석 결과별 횟수:

```promql
sum by (outcome) (fowoco_ai_analysis_outcomes_total)
```

오류 코드별 실패 단계 횟수:

```promql
sum by (phase, stage, failure_code) (fowoco_ai_pipeline_failures_total)
```

한 번의 오류는 실제 실패 단계와 이를 감싼 `TOTAL` 단계에 각각 기록될 수 있습니다.
실패한 요청 수만 확인할 때는 `stage="TOTAL"`을, 원인 단계를 확인할 때는
`stage!="TOTAL"`을 사용합니다.

## 정량 평가 절차

1. Server와 AI Runtime을 기동합니다.
2. 모델 최초 로딩을 위한 워밍업 요청을 2회 실행하고 결과에서 제외합니다.
3. 동일한 합성 발화와 Demo Worker를 사용해 시나리오별 최소 10회 실행합니다.
4. 각 실행의 `request_id`, cold/warm 여부, Outcome과 모델 버전을 별도 표에 기록합니다.
5. Prometheus에서 단계별 중앙값·최댓값·실패 횟수를 조회합니다.
6. AI 팀의 모델 내부 추론시간·정확도와 Server E2E 시간을 구분해 보고합니다.

권장 시나리오:

| 시나리오 | 확인할 결과 |
| --- | --- |
| 정상 체류연장 | `PLAN → Slot → ANALYZE → REVIEW_REQUIRED` |
| 누락 정보 | `NEEDS_INFO → HR 답변 → ANALYZE` |
| 범위 밖 발화 | `OUT_OF_SCOPE`, Slot·ANALYZE 미실행 |
| Runtime 지연 | 240초 제한과 `DEADLINE_EXCEEDED` |
| Renewal 문서 생성 | Context·Runtime·문서 생성·결과 반영 |

평가표 예시:

| 시나리오 | 반복 | PLAN | Slot | ANALYZE | 전체 | Outcome | 비고 |
| --- | ---: | ---: | ---: | ---: | ---: | --- | --- |
| 정상 체류연장 | 1 | 0.84s | 0.02s | 0.41s | 1.34s | REVIEW_REQUIRED | warm |

Server 지표만으로 `HR 업무시간이 몇 % 절감됐다`고 결론 내리지 않습니다. 이 효과를
제시하려면 동일 업무의 수작업 시간과 FOWOCO 사용시간을 별도로 측정해야 합니다.

## 보안과 Metric tag

허용 tag는 `phase`, `stage`, `status`, `outcome`, `failure_code`처럼 값의 종류가
제한된 항목뿐입니다. `requestId`, `attemptId`, `companyId`, `workerId`, `taskId`,
실명과 연락처를 Metric tag에 넣지 않습니다. 추적 ID는 구조화 로그에서만 사용합니다.
