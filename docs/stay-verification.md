# 체류기간 만료 경과 긴급 확인

`stay_expiry_date`는 Server에 저장된 마지막 기록일 뿐, 근로자의 적법 체류 여부나 고용 종료를
자동으로 판정하는 값이 아닙니다. FOWOCO는 날짜가 지난 재직 근로자를 삭제하거나 퇴사 처리하지
않고 `WF-STY-EXC-001` 긴급 확인 Case로 분리합니다.

## 처리 흐름

```text
매일 02:10 또는 HR 수동 스캔
→ stay_expiry_date < 오늘인 ACTIVE·ON_LEAVE 근로자 탐색
→ (company_id, worker_id, source_stay_expiry_date) 중복 차단
→ UNKNOWN 확인 Case 생성
→ HR이 공식 결과·접수 증빙·확인 메모를 기록
→ APPROVED이면 새 체류만료일만 갱신
→ EMPLOYMENT_ENDED이면 WF-CHG-001 후보만 제시
```

날짜 경과만으로 `Worker.work_status`를 변경하지 않습니다. `EMPLOYMENT_ENDED` 역시 HR이 확인
시각과 공식 확인 메모를 입력해야 선택할 수 있으며, Server는 고용변동 Workflow를 자동 실행하지
않고 후보로만 반환합니다.

## API

| API | 역할 |
| --- | --- |
| `POST /api/v1/stay-verifications/scan` | 현재 사업장을 즉시 멱등 스캔 |
| `GET /api/v1/stay-verifications` | 긴급 확인 Case 목록 조회 |
| `PATCH /api/v1/stay-verifications/{id}` | 상태·증빙·재확인일 기록 |

상태 변경은 `expected_version`으로 동시 수정을 차단합니다. 승인 완료는 기존 만료일보다 늦은
`new_stay_expiry_date`와 승인 결과 문서 또는 공식 확인 메모가 필요합니다. 심사 중은 신청일,
재확인일, 접수 문서 또는 공식 확인 메모가 필요합니다.

## 운영 설정

| 환경변수 | 기본값 | 설명 |
| --- | --- | --- |
| `STAY_VERIFICATION_SCHEDULER_ENABLED` | `true` | 일일 스캔 사용 여부 |
| `STAY_VERIFICATION_SCAN_CRON` | `0 10 2 * * *` | Asia/Seoul 기준 실행 Cron |

PostgreSQL에서는 `bootstrap_expired_stay_candidates(date)` SECURITY DEFINER 함수가 RLS 밖에서
최소 후보 식별자만 읽고, 각 Case 저장은 해당 사업장 tenant context를 다시 설정한 독립
트랜잭션에서 수행합니다.
