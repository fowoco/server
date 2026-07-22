## 왜 필요한가요?

관련 Issue와 사용자가 겪던 문제를 적어 주세요.

- Closes #

## 무엇이 바뀌나요?

- API·도메인·DB 변경:
- 권한·Workflow 변경:
- AI·외부 연동 변경:
- 문서·배포 변경:

## 어떻게 검증했나요?

- [ ] `./gradlew clean test`
- [ ] `./gradlew build`
- [ ] `/health`와 Swagger UI 확인
- [ ] 정상 요청
- [ ] 잘못된 입력
- [ ] 권한 부족
- [ ] 다른 사업장 접근 차단
- [ ] 필요한 상태 전이·Idempotency

테스트 명령과 결과를 짧게 적어 주세요.

## 보안·개인정보

- [ ] DTO·로그·AI 입력에 불필요한 개인정보가 없습니다.
- [ ] JWT, Worker Link 원본 토큰, API Key, 비밀번호가 없습니다.
- [ ] 모든 사업장 데이터 접근에 `company_id` 범위를 검사합니다.
- [ ] AI 결과가 자동 승인·발송되지 않습니다.
- [ ] 중요한 변경이 AuditLog와 `request_id`로 추적됩니다.
- [ ] 관련 Accepted ADR을 지켰거나 필요한 새 ADR을 이 PR에서 `Proposed`로 작성했습니다.
- [ ] Server에 Prompt Builder·Provider SDK·모델 routing을 추가하지 않았습니다.

## API·DB·운영 영향

- [ ] Swagger/OpenAPI와 Notion 계약을 갱신했습니다.
- [ ] Client에 알려야 할 호환성 변경을 적었습니다.
- [ ] DB 변경에 Flyway migration이 있습니다.
- [ ] migration 번호와 소유 Issue를 확인했고 다른 기능의 테이블을 미리 만들지 않았습니다.
- [ ] 환경변수는 이름만 `.env.example`에 적었습니다.
- [ ] 배포 후 Smoke Test와 롤백 방법을 적었습니다.

## 화면 또는 응답 예시

개인정보를 제거한 예시만 첨부해 주세요.
