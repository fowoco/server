# 근로자 안전 보관 운영 가이드

## 목적

퇴사하거나 고용이 종료된 근로자를 물리적으로 삭제하지 않고 운영 목록에서 분리합니다.
과거 Task·문서·근로자 응답·감사로그의 참조는 그대로 보존합니다.

체류기간이 지났다는 사실만으로 퇴사나 불법체류를 판단하거나 자동 보관하지 않습니다.
체류 만료 경과 확인은 `stay_verification_case`에서 먼저 처리하고, 실제 근무상태와
남은 업무가 정리된 뒤 HR 또는 ADMIN이 사유를 입력해 보관합니다.

## API 흐름

```text
GET /api/v1/workers/{workerId}/archive-eligibility
→ 보관 차단 사유 확인
→ HR이 남은 Task·승인·Worker Link 정리
→ POST /api/v1/workers/{workerId}/archive
→ worker_archive와 WORKER_ARCHIVED 감사로그 저장
→ 기본 목록·AI 대상 탐색·신규 Task 대상에서 제외
```

보관 요청 예시:

```json
{
  "reason": "퇴사 및 진행 업무 종료 확인",
  "expected_version": 3
}
```

## 보관 차단 조건

| 코드 | 의미 | 담당자 행동 |
| --- | --- | --- |
| `ACTIVE_EMPLOYMENT_STATUS` | `ACTIVE` 또는 `ON_LEAVE` 상태 | 실제 근무상태를 먼저 확인 |
| `OPEN_TASK` | 완료·취소되지 않은 Task 존재 | 업무를 완료하거나 취소 |
| `PENDING_APPROVAL` | 결정을 기다리는 승인 존재 | 승인 또는 반려 처리 |
| `ACTIVE_WORKER_LINK` | 아직 유효한 근로자 링크 존재 | 응답을 마치거나 링크 만료·폐기 처리 |
| `ALREADY_ARCHIVED` | 이미 보관됨 | 중복 처리하지 않고 기존 기록 확인 |

## 보존과 차단 범위

- 기본 `GET /workers` 목록과 검색에서는 보관 근로자를 제외합니다.
- 자연어 분석의 근로자 탐색과 신규 Task 대상 확인에서도 제외합니다.
- `GET /workers/{workerId}` 상세와 기존 Task·문서·응답·감사 기록은 삭제하지 않습니다.
- 보관은 복구할 수 없는 삭제가 아니며, 원문 개인정보 자동 삭제 정책을 의미하지 않습니다.
- `expected_version`으로 동시 변경을 막고 보관 시각·처리자·사유를 감사 가능하게 남깁니다.

## DB와 RLS

- `V56__create_worker_archive.sql`: 보관 메타데이터와 복합 FK 생성
- `V57__prepare_worker_archive_rls.sql`: 사업장 격리 정책 준비

RLS 활성화는 공통 RLS Migration PR에서 기존 테이블과 함께 수행합니다.
