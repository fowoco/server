# 합성 근로자 문서 영구 적재

이 기능은 기존 Demo Company·Worker Seed 위에 합성 문서 파일과 메타데이터를 추가한다.
실제 개인정보나 행정기관 원본 문서를 사용하지 않으며, 모든 파일에는
`DEMO / SAMPLE - NOT FOR OFFICIAL SUBMISSION` 문구가 포함된다.

## 저장 구조

- DB: PostgreSQL 16의 `worker_document`, `stored_file`, `document_ocr_run`
- 원본: `app.file-storage.local-path` 아래 파일시스템
- Kubernetes: `server-data` PVC의 `/app/data/files`
- 로컬 Compose: `fowoco-server-data` named volume의 `/app/data/files`

DB에는 개발자 개인 절대경로를 저장하지 않는다. 저장 키는 다음 형태다.

```text
demo/{company_id}/workers/{worker_id}/documents/{document_id}/{filename}
```

## 준비

다음 값은 로컬 Secret으로만 설정하고 터미널 출력, Issue, PR, 문서에 실제 값을 남기지 않는다.

```bash
export DEMO_DB_PASSWORD='<local-secret>'
export JWT_SECRET_BASE64='<32-byte-base64-secret>'
export DEMO_SEED_ADMIN_PASSWORD='<12-or-more-character-local-secret>'
export OCR_RESULT_ENCRYPTION_KEY_BASE64='<32-byte-base64-secret>'
```

## 명령

```bash
./scripts/demo-data import
./scripts/demo-data verify
./scripts/demo-data cleanup
```

- `import`: PostgreSQL과 파일 volume을 준비하고, 기존 Demo Seed를 멱등 실행한 뒤 문서
  fixture를 적재·검증한다. 성공하면 API Server도 기동한다.
- `verify`: 고정 ID, 회사·근로자·업무 연결, 날짜·상태, MIME·크기·SHA-256, 실제 파일,
  OCR 원본 계보를 읽기 검증한다.
- `cleanup`: 이 기능이 예약한 ID와 해시가 모두 일치할 때만 OCR·문서·파일을 제거한다.
  기존 Demo Company, Worker, Task와 기존 Showcase 문서는 삭제하지 않는다.

같은 `import`를 다시 실행하면 DB row와 파일 수가 증가하지 않는다. 예약 ID 또는 저장 키에
다른 내용이 있으면 덮어쓰지 않고 즉시 실패한다.

## 대표 fixture

대표 근로자는 `응웬반A / NGUYEN VAN AN`, `VN`, `vi`, `E-9`이다. 숫자·주소는
`SYNTHETIC`, `DEMO`, `SAMPLE` 표식을 포함해 실제 식별자로 사용할 수 없게 만들었다.

대표 근로자 파일:

- 여권 인적사항면 PNG, 여권 사본 PDF
- 외국인등록증 앞면 PNG, 뒷면 JPG, 통합 사본 PDF
- 고용허가서 PDF
- 표준근로계약서 PDF·HWPX·HWP
- 취업활동기간 연장신청서 초안 HWPX
- 통합신청서 초안 HWPX
- 체류지 입증자료 PDF

Demo Company의 나머지 근로자 27명에게는 각각 다른 합성 여권 사본 PNG를 추가한다.
각 파일은 영문 이름, 국적, 생년월일, 무효 문서번호, 발급·만료일, 합성 인물 도형과
저장 키가 서로 다르며 SHA-256도 27개 모두 달라야 한다. 응웬반A의 기존 유효 여권은
그대로 유지하고, 아르준 타파의 과거 만료 파일과 기존 `LEGACY` 메타데이터도 덮어쓰지 않는다.

```text
Demo Company Worker 28명
= 응웬반A 기존 유효 여권 1명
+ 신규 합성 여권 27명(활성 24명, 휴직 3명 포함)
```

신규 파일명은 `여권사본_{합성이름}.png`, 저장 파일명은
`passport-copy-worker-{worker_number}.png` 형식이다. 모든 이미지는 실제 여권 문양 대신
FOWOCO QA 레이아웃과 `DEMO SAMPLE / NOT A TRAVEL DOCUMENT` 표식을 사용한다.

여권 인적사항면은 실제 인물이나 실제 여권 원본을 사용하지 않는다. 프로젝트에 포함된
`demo-data/nguyen-van-an-portrait.png`는 이미지 생성 모델로 만든 완전한 합성 인물이며,
생성기에서 사진·영문 인적사항·발급일·만료일을 조합한다. 실제 국가 문장과 보안문양은
포함하지 않고, 국가 코드는 비실재 코드 `XDM`, 문서번호와 기계 판독 영역은 명시적으로
무효인 값만 사용한다. 이미지 전체에는 `DEMO SAMPLE`과 `NOT A TRAVEL DOCUMENT` 표시가
들어간다. 발급일과 만료일은 DB에 적재되는 날짜를 그대로 렌더링해 본문과 메타데이터가
어긋나지 않게 한다.

추가 근로자 4명에게 정상·임박·만료·필수 문서 누락 상태를 연결한다. 대표 근로자의
ARC 앞면에는 암호화된 합성 OCR 결과와 `REVIEW_REQUIRED` 상태를 연결한다.

## API 확인

로그인 후 다음 API를 사용한다. `company_id`는 요청에서 받지 않고 인증 Context에서 결정한다.

```text
GET /api/v1/documents
GET /api/v1/documents?workerId={worker_id}
GET /api/v1/documents/{worker_document_id}
GET /api/v1/files/{file_id}/content
GET /api/v1/documents/{worker_document_id}/ocr-runs/latest
```

다른 Demo Test Company 계정으로 같은 ID를 조회하면 파일·문서 존재 여부를 감춘 `404`가
반환되어야 한다.

## 배포 환경 실행

Kubernetes Secret 값을 로컬로 출력하거나 복사하지 않는다. 권한이 있는 운영자가 현재
Server image에 이 변경이 배포된 뒤 Pod 내부에서 one-shot command를 실행하고, 별도 Pod의
동시 실행이 없도록 확인해야 한다. 정확한 명령은 배포 시스템의 Secret 주입 방식을 유지한 채
다음 Spring argument를 사용한다.

```text
--server.port=0
--app.demo-document-data.command=import|verify|cleanup
--app.reliability.outbox.enabled=false
```

`DEMO_SEED_ENABLED=true`, `DOCUMENT_OCR_ENABLED=true`, 영구 `FILE_STORAGE_LOCAL_PATH`, OCR
암호화 키가 필요하다. 명령 완료 후 runner가 Application Context를 닫으므로 별도 Pod는 종료된다.
기존 운영·개발 데이터가 섞인 DB에서는 실행하지 않고 전용 Demo Company와 volume에서만
실행한다.
