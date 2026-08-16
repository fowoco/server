# File Storage rollback 보상 운영 가이드

## 목적과 보장 범위

로컬 파일시스템은 PostgreSQL 트랜잭션에 참여하지 않는다. 파일을 먼저 저장한 뒤
`stored_file` 또는 감사로그 영속화가 실패하면 DB는 rollback되지만 파일만 남을 수 있다.
Server는 파일 저장 전에 transaction synchronization을 등록하고, transaction 결과가
`ROLLED_BACK`일 때 서버가 생성한 `storage_key`를 `deleteIfExists`로 정리한다.

현재 보장 범위는 다음과 같다.

- 최종 파일은 storage root 안의 임시 파일에 완전히 기록한 후 atomic move로 공개한다.
- 배포 volume이 atomic move를 지원하지 않으면 일반 move로 대체하지 않고 저장을
  실패시킨다. 불완전한 최종 파일을 노출하지 않는 것이 저장 성공보다 우선한다.
- DB rollback callback은 같은 `storage_key`를 여러 번 정리해도 성공하도록 멱등 삭제한다.
- `FileService` 업로드와 Worker Link 문서 업로드 모두 같은 rollback 보상을 사용한다.
- Worker Link 재시도 key는 원문 대신 SHA-256 hash만 DB에 저장한다. 같은 key와 같은
  요청은 기존 `upload_id`로 수렴하고, 같은 key의 다른 요청은
  `IDEMPOTENCY_CONFLICT`로 거부한다.

이 보상은 DB와 파일시스템을 하나의 원자적 transaction으로 바꾸지 않는다. 프로세스가
파일 finalize 직후 강제 종료되거나 transaction 완료 결과가 `UNKNOWN`이면 운영 확인이
필요하다.

## 구조화 로그

rollback 보상은 다음 event를 남긴다.

| event | status | 의미 | 운영 조치 |
| --- | --- | --- | --- |
| `file_storage_cleanup` | `ATTEMPT` | rollback 파일 삭제 시작 | 뒤따르는 동일 `request_id`·`storage_key` 결과 확인 |
| `file_storage_cleanup` | `SUCCEEDED` | 파일이 삭제됐거나 이미 없음 | 별도 조치 없음 |
| `file_storage_cleanup` | `FAILED` | 삭제 중 예외 발생 | DB와 파일을 대조해 orphan 여부 확인 |
| `file_storage_transaction_completion` | `UNKNOWN` | commit/rollback 결과를 확정하지 못함 | 자동 삭제 금지, reconciliation 수행 |

로그에는 `request_id`, `action`, `storage`, `phase`, 서버 생성 `storage_key`만 사용한다.
원본 Worker Link token, `Idempotency-Key`, 파일명, 파일 내용과 사용자 개인정보를
추가하지 않는다.

우선 확인할 검색 조건은 다음과 같다.

```text
event=file_storage_cleanup status=FAILED
event=file_storage_transaction_completion status=UNKNOWN reconciliation_required=true
```

## FAILED와 UNKNOWN 대응

`FAILED` 또는 `UNKNOWN` 한 건마다 로그의 `request_id`와 `storage_key`를 기준으로
`stored_file.storage_key` 행과 실제 storage volume의 최종 파일을 대조한다.

| DB 행 | 최종 파일 | 판정과 조치 |
| --- | --- | --- |
| 있음 | 있음 | commit된 정상 파일이다. 삭제하지 않는다. |
| 없음 | 있음 | orphan 후보이다. 동일 key가 서버 생성 UUID인지, 연결된 업무 행이 없는지 재확인한 뒤 승인된 운영 절차로 파일만 멱등 삭제한다. |
| 있음 | 없음 | DB가 가리키는 파일이 유실된 상태다. 자동 DB 삭제를 금지하고 복구 또는 재업로드를 결정한다. |
| 없음 | 없음 | rollback 정리가 완료된 상태다. |

`UNKNOWN`은 실제로 commit됐을 가능성이 있으므로 파일부터 지우지 않는다. 확인 중에는
원본 파일명이나 token을 로그에 복사하지 않고, 접근이 제한된 DB와 volume에서 서버 생성
UUID key만 사용한다. 수동 삭제를 재시도한 뒤에는 같은 key의 파일 부재와 DB 행 부재를
다시 확인하고 incident 기록에 `request_id`와 판정만 남긴다.

## 배포 전후 Smoke

`FILE_STORAGE_LOCAL_PATH`가 실제 배포 Pod에 mount된 경로인지 먼저 확인한다. 임시
container filesystem을 가리키거나 여러 Pod가 서로 다른 local volume을 사용하면 이
구현의 범위를 벗어난다.

1. 실제 mount에서 허용 MIME 파일을 업로드하고 다운로드 내용이 같은지 확인한다.
2. Worker Link 문서 제출에 동일한 `Idempotency-Key`와 동일 payload를 두 번 보내 두
   응답의 `upload_id`가 같은지 확인한다.
3. 같은 key로 내용만 바꾼 요청이 `409 IDEMPOTENCY_CONFLICT`인지 확인한다.
4. DB에는 key별 idempotency 행과 `stored_file` 행이 각각 한 건이고, volume에는 대응하는
   최종 파일이 한 개뿐인지 확인한다.
5. storage root에 `.fowoco-upload-*.tmp`가 남지 않았는지 확인한다.
6. 검증 환경에서 파일 저장 이후 transaction rollback을 강제로 발생시켜 DB 행, 감사로그,
   최종 파일과 임시 파일이 모두 남지 않는지 확인한다.
7. mount가 atomic move를 지원하지 않으면 배포를 중단한다. 일반 move fallback을
   추가하지 말고 atomic rename이 가능한 volume 또는 별도 object storage 구현을 선택한다.

PostgreSQL 16 동시성 검증은
`WorkerLinkDocumentPostgreSqlIntegrationTest`가 두 HTTP 요청을 Worker Link 행 잠금
직전에 겹치게 한 뒤 DB 행과 실제 LocalFileStorage 파일이 하나로 수렴하는지 반복
확인한다. 환경변수가 없으면 테스트가 skip되므로 결과에서 실제 실행 건수를 반드시
확인한다.

## Rollback 원칙

- 적용된 Flyway migration을 수정하거나 schema history를 조작하지 않는다.
- V51의 hash column은 nullable이므로 이전 image가 같은 schema에서 기동하고 legacy 행을
  기록할 수 있다. 이는 schema 하위 호환을 의미하며, 신·구 version 사이의 멱등성 결과
  재사용까지 보장한다는 뜻은 아니다.
- 새 version은 `client_request_id`에 `canonical:<stored_file_id>`를 기록하지만 이전
  version은 multipart `clientRequestId` 원문으로 기존 결과를 조회한다. 따라서 새 version이
  성공시킨 업로드를 이전 image로 rollback한 뒤 재시도하면 기존 결과를 찾지 못하고 중복
  업로드할 수 있다.
- rollback 가능한 배포 기간에는 Client가 `Idempotency-Key`와 `clientRequestId`를 함께
  보내는 현재 동작을 유지한다. 이전 Server version을 지원하지 않기로 확정하기 전에는
  `clientRequestId` 전송을 제거하지 않는다.
- 이전 image로 rollback한 경우 새 version에서 성공한 Worker Link 문서 요청의 자동 재시도를
  피하고, 재시도가 발생했다면 `worker_document_upload_idempotency`, `stored_file`과 실제
  volume을 대조해 중복 여부를 확인한다.
- 이전 image로 되돌린 뒤에도 orphan 후보는 위 reconciliation 표로 판단한다.
- cleanup 실패를 숨기기 위해 `stored_file` 행이나 파일을 일괄 삭제하지 않는다.
