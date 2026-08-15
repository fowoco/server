# 문서 미리보기와 합성 데이터 품질 확인

## 한눈에 보기

HR 담당자가 문서함에서 파일을 내려받기 전에 내용을 확인할 수 있도록 다음 API를 사용한다.

```http
GET /api/v1/files/{fileId}/preview
Authorization: Bearer <access-token>
```

| 원본 형식 | Server 동작 | 응답 형식 |
| --- | --- | --- |
| PDF | 원본을 브라우저에 바로 표시 | `application/pdf` |
| JPG·PNG·WEBP | 원본을 브라우저에 바로 표시 | 원본 이미지 MIME |
| HWP·HWPX | AI 문서 변환 API로 PDF 변환 | `application/pdf` |
| 그 외 형식 | 미리보기 거부 | `415 FILE_PREVIEW_UNSUPPORTED` |

원본 다운로드는 기존 `GET /api/v1/files/{fileId}/content`를 그대로 사용한다. 미리보기는
원본 파일이나 DB 레코드를 변경하지 않고 필요할 때만 변환하므로 별도 Flyway Migration이 없다.

## 처리 흐름

```text
Client 미리보기 클릭
→ Server가 JWT 역할과 companyId 확인
→ stored_file과 저장소 원본 조회
→ PDF·이미지는 inline 반환
→ HWP·HWPX는 AI /api/v1/documents/convert 호출
→ PDF signature와 응답 크기 검증
→ Client에 inline PDF 반환
```

다른 사업장의 fileId는 파일 존재 여부가 드러나지 않도록 `404`로 처리한다. 응답에는
`Cache-Control: no-store`, `Content-Disposition: inline`, `X-Content-Type-Options: nosniff`를
적용한다. AI URL·인증 토큰·오류 본문은 Client 응답과 일반 로그에 노출하지 않는다.

## 로컬에서 확인하기

PDF와 이미지 미리보기는 AI 없이 확인할 수 있다. HWP·HWPX 미리보기까지 확인하려면 AI
문서 변환 서버와 LibreOffice 변환 기능이 실행 중이어야 한다.

```dotenv
AI_RUNTIME_ENABLED=true
AI_DOCUMENT_CONVERSION_ENDPOINT=http://127.0.0.1:8000/api/v1/documents/convert
AI_DOCUMENT_CONVERSION_TIMEOUT=60s
AI_RUNTIME_SERVICE_CREDENTIAL=<AI의 FOWOCO_INTERNAL_API_TOKEN과 같은 값>
```

1. AI와 Server를 실행한다.
2. <http://localhost:8080/swagger-ui.html>에서 로그인한다.
3. 반환된 Access Token을 `Authorize`에 입력한다.
4. `GET /api/v1/files/{fileId}/preview`에 문서의 `fileId`를 넣어 실행한다.
5. `200`, `Content-Type: application/pdf`, `Content-Disposition: inline`을 확인한다.

AI가 꺼져 있어도 PDF·이미지 미리보기는 정상 동작한다. 이 상태에서 HWP·HWPX를 요청하면
무한 대기나 빈 파일 대신 `503 FILE_PREVIEW_UNAVAILABLE`을 반환한다.

## 오류 기준

| HTTP | 의미 | 담당자 행동 |
| --- | --- | --- |
| 404 | 파일이 없거나 다른 사업장 파일 | 올바른 문서인지 확인 |
| 415 | 지원하지 않는 형식 | 원본 다운로드 사용 |
| 422 | 손상됐거나 변환할 수 없는 HWP·HWPX | 원본 파일 교체·재생성 |
| 503 | AI 변환 기능 비활성 또는 장애 | AI·LibreOffice 설정 확인 후 재시도 |

## 합성 문서 품질 후속 확인

PR #183의 합성 문서가 병합된 뒤 아래 항목을 문서별로 확인한다. 이 작업은 Preview API와
분리하여 진행해 다른 팀의 Seed 파일 변경과 충돌하지 않게 한다.

- Worker DB의 표시 이름·국적·체류기간과 문서 본문·메타데이터가 일치하는가?
- 텍스트 잘림, 빈 페이지, 겹침, 깨진 글꼴이 없는가?
- 실존 개인정보·기관 직인·공식 문서로 오인할 요소가 없고 `DEMO`·`NOT VALID` 표시가 있는가?
- OCR로 읽을 핵심 칸을 워터마크가 가리지 않는가?
- PDF·이미지는 Preview API에서 열리고 HWP·HWPX는 PDF로 변환되는가?
- 여권번호·외국인등록번호 같은 값이 일반 로그와 오류 응답에 남지 않는가?

품질이 부족한 문서는 원본 생성 규칙을 수정하고, 수정 전후 Preview 화면과 확인한 필드 목록을
#186에 남긴다.
