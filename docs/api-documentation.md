# API 문서 사용법

FOWOCO Server의 공유용 Swagger HTML은 `main`에서 실제로 생성되는 OpenAPI
JSON을 읽기 쉬운 화면으로 변환한 문서입니다.

- 팀 공유 사이트: <https://fowoco.github.io/server/api/>
- OpenAPI JSON: <https://fowoco.github.io/server/api/openapi.json>
- 로컬 Swagger UI: <http://localhost:8080/swagger-ui.html>
- 파일 미리보기·합성 문서 확인: [file-preview.md](file-preview.md)

## 어떤 문서인가요?

| 문서 | 용도 |
| --- | --- |
| 공유용 Swagger HTML | 서버를 실행하지 않고 현재 `main`의 API를 확인 |
| OpenAPI JSON | Client 코드 생성, 계약 비교, 다른 도구에서 불러오기 |
| 로컬 Swagger UI | 개발 중인 브랜치의 API 확인과 직접 요청 테스트 |

공유 사이트는 기본적으로 `Try it out`을 비활성화한 읽기 전용 문서입니다.
저장소의 `SERVER_PUBLIC_URL` 변수에 **HTTPS 데모 Server 주소**가 설정된 경우에만
실제 호출 기능과 `Authorize` 버튼이 활성화됩니다.

## 공유 Swagger에서 실제 호출하기

다음 세 조건이 모두 충족돼야 합니다.

1. 배포 Server가 `https://` 주소를 제공합니다.
2. GitHub 저장소 `Settings → Secrets and variables → Actions → Variables`에
   `SERVER_PUBLIC_URL=https://...`을 등록합니다.
3. Server의 `CORS_ALLOWED_ORIGINS`에 `https://fowoco.github.io`를 추가합니다.

그 뒤 `Database Documentation` Workflow가 `main`에서 다시 실행되면 Swagger의
`Try it out`이 열립니다.

```text
POST /api/v1/auth/login
→ 응답의 Access Token 복사
→ Swagger 우측 상단 Authorize
→ Bearer Token 입력
→ 보호 API Execute
```

GitHub Pages는 HTTPS이므로 HTTP Server는 브라우저의 mixed content 정책에 의해
차단됩니다. 생성기도 잘못된 HTTP 주소를 받으면 실패하도록 구성했습니다.
Refresh Token은 HttpOnly·SameSite Cookie이므로 로그인 이후의 Refresh·Logout 흐름은
공유 Swagger보다 실제 Client에서 확인합니다.

## 언제 갱신되나요?

Controller, 요청·응답 DTO, OpenAPI 설정 또는 문서 생성기가 변경되어 `main`에
병합되면 GitHub Actions가 다음 순서로 갱신합니다.

```text
Spring Boot test profile 실행
→ /v3/api-docs에서 OpenAPI JSON 추출
→ JSON 기본 구조 검증
→ Swagger HTML 생성
→ DB 문서와 하나의 GitHub Pages 사이트로 배포
```

운영 서버의 내장 Swagger는 보안상 계속 비활성화합니다. 공유 사이트의 명세 자체는
test profile과 메모리 DB에서 만들며 운영 DB를 조회하지 않습니다. 실제 호출을 켠 경우에만
사용자가 누른 요청이 지정된 HTTPS 데모 Server로 전송됩니다.

## PR에서 먼저 확인하기

1. PR의 `Checks`에서 `Database Documentation` Workflow를 엽니다.
2. `Build API documentation` 결과가 성공했는지 확인합니다.
3. 실행 결과 아래 `api-docs-site` Artifact를 내려받습니다.
4. 압축을 풀고 `index.html`을 브라우저로 엽니다.

Artifact는 외부 CDN에서 Swagger UI 정적 파일을 불러오므로 인터넷 연결이
필요합니다. API 명세 자체는 HTML 안에도 포함되어 있어 별도 서버가 필요하지
않습니다.

## 로컬에서 생성하기

Java 17, Node.js와 `curl`이 필요합니다.

```bash
./scripts/api-docs/generate.sh
open build/api-docs/site/index.html
```

기본 `18080` 포트가 사용 중이면 다른 포트를 지정할 수 있습니다.

```bash
API_DOCS_PORT=18081 ./scripts/api-docs/generate.sh
```

로컬에서 실제 호출용 정적 사이트 결과를 확인할 때는 HTTPS 테스트 주소를 지정합니다.

```bash
API_DOCS_SERVER_URL=https://demo.example.com ./scripts/api-docs/generate.sh
```

## 보안 원칙

- 운영·Staging DB와 운영 API에 연결하지 않습니다.
- 실제 사용자·근로자 데이터나 Access·Refresh Token을 포함하지 않습니다.
- 기본은 `Try it out` 비활성화이며, 검증된 HTTPS origin 하나만 Content Security
  Policy의 연결 대상으로 허용합니다.
- GitHub Pages에는 DB·JWT·AI·SMTP Secret을 저장하지 않습니다.
- 공유 Swagger에서 입력한 Access Token은 브라우저 새로고침 뒤 보존하지 않습니다.
- HTML에 표시하는 것은 API 경로, DTO Schema, 예시값과 비민감 build metadata뿐입니다.
- 배포 전에 생성기 테스트와 OpenAPI 기본 구조 검증을 통과해야 합니다.
