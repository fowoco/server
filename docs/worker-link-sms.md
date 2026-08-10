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
→ Server SMS 발송 API
→ SOLAPI
→ Provider 접수 성공
→ worker_link.delivery_status = SENT
→ WORKER_LINK_SENT 감사로그
```

Provider 오류 또는 timeout이면 `NOT_SENT`를 유지합니다. 여기서 `SENT`는 통신사의 최종
수신 확인이 아니라 **SMS Provider가 발송 요청을 접수했다는 뜻**입니다.

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

응답의 `worker_link_id`와 `worker_url`을 발송 요청에 사용합니다. 기존 계약에서
`worker_url`은 URL 전체가 아니라 원본 링크 token입니다.

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
  "worker_link_token": "{worker_url 응답값}"
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

이미 성공한 링크에 같은 요청을 다시 보내면 문자를 중복 발송하지 않고 기존 `SENT`
결과를 반환합니다.

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
직접 전달했다면 기존 `POST /api/v1/worker-links/{workerLinkId}/sent`로 전송 완료를 기록할
수 있습니다.

## 개인정보와 로그

- 전화번호는 요청 순간에만 사용하며 DB에 저장하지 않습니다.
- 원본 link token과 전화번호를 감사로그·애플리케이션 로그에 기록하지 않습니다.
- 감사로그에는 링크 ID, 사업장, 실행 담당자와 `WORKER_LINK_SENT` 행동만 남습니다.
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
