# 근로자 보안 링크 SMS 발송

## 한 줄 설명

HR이 발급한 근로자 보안 링크를 국내 휴대전화 번호로 전송하고, SMS Provider가 요청을
정상 접수한 뒤에만 링크 상태를 `SENT`로 기록합니다.

## 왜 Server가 발송하나요?

Client에서 SMS Provider를 직접 호출하면 API Secret이 브라우저에 노출됩니다. Server가
Provider 인증과 실패 처리를 맡고, Client에는 링크의 업무 상태만 반환합니다.

```text
HR 화면
→ Worker Link 발급
→ worker_link.delivery_status = SENDING 커밋
→ Server SMS 발송 API
→ SOLAPI
→ Provider 접수 성공
→ worker_link.delivery_status = SENT
→ WORKER_LINK_SENT 감사로그
```

Provider가 명확히 거부하면 `NOT_SENT`로 돌아갑니다. timeout·응답 단절처럼 접수 여부를
확정할 수 없으면 `REVIEW_REQUIRED`로 두고 자동 재발송을 막습니다. 여기서 `SENT`는
통신사의 최종 수신 확인이 아니라 **SMS Provider가 발송 요청을 접수했거나 HR이 수동
전달을 기록했다는 뜻**입니다.

| 상태 | 의미 | 재발송 |
| --- | --- | --- |
| `NOT_SENT` | 아직 발송하지 않았거나 Provider가 명확히 거부함 | 가능 |
| `SENDING` | 발송 요청을 시작했으며 결과 저장 전임 | 자동 재발송 금지 |
| `REVIEW_REQUIRED` | Provider 접수 여부를 확정할 수 없어 담당자 확인이 필요함 | 자동 재발송 금지 |
| `SENT` | Provider 접수 또는 HR 수동 전달이 기록됨 | 발송하지 않고 기존 결과 반환 |

## API 사용 순서

### 1. 링크 발급

```http
POST /api/v1/tasks/{taskId}/worker-link
Authorization: Bearer {accessToken}
Idempotency-Key: worker-link-issue-001
Content-Type: application/json

{
  "expires_in_hours": 72,
  "rotate_existing": false
}
```

응답의 `worker_link_id`와 `worker_link_token`을 발송 요청에 사용합니다.
`worker_url`은 근로자에게 전달할 전체 URL이고, `worker_link_token`은 발급 직후 Server가
SMS 발송 및 공개 API 호출에 사용할 수 있도록 별도로 반환하는 원본 token입니다.

### 2. SMS 발송

링크 발급에 사용한 것과 **동일한 `Idempotency-Key`**를 보냅니다. Server는 key와 token이
현재 링크에 모두 일치하는지 확인하므로, 다른 링크를 잘못 전송할 수 없습니다.

```http
POST /api/v1/worker-links/{workerLinkId}/sms-deliveries
Authorization: Bearer {accessToken}
Idempotency-Key: worker-link-issue-001
Content-Type: application/json

{
  "recipient_phone": "01012345678",
  "worker_link_token": "{worker_link_token 응답값}"
}
```

성공 응답:

```json
{
  "worker_link_id": "...",
  "link_status": "ACTIVE",
  "delivery_status": "SENT",
  "sent_at": "2026-08-11T01:00:00Z",
  "expires_at": "2026-08-14T01:00:00Z"
}
```

이미 `SENT`인 링크에 다시 요청하면 전화번호가 달라도 문자를 추가 발송하지 않고 기존
결과를 반환합니다. `SENT`는 전달 작업이 끝났다는 업무 상태이며 SMS 수신 성공만을 뜻하지
않습니다. 잘못된 번호로 보냈다면 기존 링크를 회전·재발급한 뒤 새 링크를 발송합니다.

Provider 호출이 성공한 뒤 링크가 만료되더라도 접수 결과는 `SENT`로 기록합니다. 발송
시작 시점에 활성 링크였다는 검증을 이미 통과했기 때문에, 외부 호출 후 만료 여부를 다시
검사해 `문자는 발송됐지만 DB는 실패`가 되는 상황을 만들지 않습니다.

## 설정

기본값은 실제 발송을 하지 않는 `none`입니다.

```dotenv
WORKER_PORTAL_BASE_URL=https://client.example.com
WORKER_LINK_SMS_PROVIDER=solapi
SOLAPI_ENDPOINT=https://api.solapi.com/messages/v4/send-many/detail
SOLAPI_API_KEY=Secret으로_주입
SOLAPI_API_SECRET=Secret으로_주입
SOLAPI_SENDER_NUMBER=승인된_발신번호
```

- `WORKER_PORTAL_BASE_URL`: 근로자가 열 Client 주소입니다.
- `SOLAPI_SENDER_NUMBER`: SOLAPI에 등록·승인된 발신번호여야 합니다.
- API Key와 Secret은 Git, Issue, PR, 일반 로그에 남기지 않습니다.
- 운영·데모 배포에서 `WORKER_PORTAL_BASE_URL`은 HTTPS 주소를 사용합니다.

`WORKER_LINK_SMS_PROVIDER=none`에서 SMS API를 호출하면 `503`을 반환합니다. 링크 복사 후
직접 전달했다면 기존 `POST /api/v1/worker-links/{workerLinkId}/sent`로 전달 완료를 기록할
수 있습니다. 이 수동 기록도 같은 `SENT` 상태를 사용하므로 이후 SMS API는 문자를 보내지
않고 기존 결과를 반환합니다.

## 개인정보와 로그

- 전화번호는 요청 순간에만 사용하며 DB에 저장하지 않습니다.
- 원본 link token과 전화번호를 감사로그·애플리케이션 로그에 기록하지 않습니다.
- 감사로그에는 링크 ID, 사업장, 실행 담당자와 발송 시작·접수·실패·확인 필요 행동만 남습니다.
- 현재 MVP는 국내 휴대전화 번호(`010...`, `+82 10...`)만 허용합니다.

## 검증

Provider 계약 테스트는 WireMock을 사용하므로 실제 문자를 보내지 않습니다.

```bash
./gradlew test --tests '*WorkerLinkSms*' \
  --tests 'com.fowoco.server.workerlink.WorkerLinkSecurityIntegrationTest'
```

실제 데모 환경에서는 테스트용 수신번호로 다음을 한 번 확인합니다.

1. 링크 발급
2. SMS 발송 API가 `SENT` 반환
3. 문자에 전화번호나 내부 ID 대신 근로자용 HTTPS URL만 포함
4. 링크 접속·안내 확인·파일 제출
5. 같은 요청 재시도 시 문자가 추가 발송되지 않음
