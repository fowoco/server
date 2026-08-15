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
- `cleanup`: 이 기능이 예약한 ID와 해시가 모두 일치할 때만 OCR·신규 문서·생성 파일을
  제거한다. 기존 `LEGACY` 문서 83건은 삭제하지 않고, 이 명령이 추가한 파일 연결만
  원래 상태로 복원한다. 기존 Demo Company, Worker, Task와 Showcase 메타데이터는 유지한다.

같은 `import`를 다시 실행하면 DB row와 파일 수가 증가하지 않는다. 예약 ID 또는 저장 키에
다른 내용이 있으면 덮어쓰지 않고 즉시 실패한다. 서버 재시작 때 Operational Seed가 다시
검증되어도 결정적 파일 ID를 허용하므로 문서 연결이 원복되거나 시작이 실패하지 않는다.

## 메타데이터와 파일 본문 일치

`import`는 신규 `DEMO_SEED` 문서 43건뿐 아니라 기존 문서함의 `LEGACY` 메타데이터
83건도 함께 읽는다. 이 중 `MISSING` 16건은 파일이 없는 상태를 의도적으로 유지하고,
나머지 67건에는 근로자와 문서 행을 기준으로 합성 이미지 또는 PDF를 생성해 연결한다.

적재 완료 기준은 다음과 같다.

```text
문서 메타데이터 126건 = DEMO_SEED 43 + LEGACY 83
파일 연결 109건 = DEMO_SEED 42 + LEGACY 비누락 67
파일 형식 = 이미지 71 + PDF 34 + HWP 1 + HWPX 3
파일 없는 누락 상태 = 17건
```

모든 파일은 연결된 `worker_id`의 표시 이름, 영문 이름, 국적·국가 코드, 선호 언어,
체류자격, 합성 생년월일·문서번호·주소를 사용한다. 문서 행의 `document_type`,
`submission_status`, `issue_date`, `expiry_date`, `worker_document_id`도 파일 본문 또는
컨테이너 메타데이터에 그대로 기록한다. Import 전에 실제 Worker 행의 이름·국적 코드·언어·
체류자격이 fixture와 일치하는지 확인하므로 서로 다른 사람의 파일을 잘못 연결할 수 없다.

PDF와 이미지는 결정적으로 생성하고, HWP/HWPX는 AI 저장소에서 사용하는 실제 템플릿
구조에 합성값을 주입한다. HWP에는 `FOWOCO-Metadata` 스트림, HWPX에는 미리보기 텍스트와
본문 XML에 연결 정보를 남겨 원본 문서까지 역추적할 수 있다.

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
각 파일은 영문 이름, 국적, 생년월일, 무효 문서번호, 발급·만료일, 실사형 합성 증명사진과
저장 키가 서로 다르며 SHA-256도 27개 모두 달라야 한다. 응웬반A의 기존 유효 여권과
아르준 타파의 과거 만료 파일도 해당 근로자 DB 값과 동일한 합성 정보로 생성한다.
기존 `LEGACY` 메타데이터 자체는 덮어쓰지 않고 비누락 행에만 결정적 파일 연결을 추가한다.

```text
Demo Company Worker 28명
= 응웬반A 기존 유효 여권 1명
+ 신규 합성 여권 27명(활성 24명, 휴직 3명 포함)
```

신규 파일명은 `여권사본_{합성이름}.png`, 저장 파일명은
`passport-copy-worker-{worker_number}.png` 형식이다. 모든 이미지는 실제 여권 문양 대신
FOWOCO QA 레이아웃과 `DEMO SAMPLE / NOT A TRAVEL DOCUMENT` 표식을 사용한다. 정보면은
상단 제목, 좌측 증명사진, 우측의 조밀한 영문 필드, 하단 2줄 판독 영역으로 구성하되
공식 국가명·국가 문장·보안문양은 포함하지 않는다. 증명사진
원본 27장은 `demo-data/passport-portraits/worker-{worker_number}.png`에 포함되며, 모두
이미지 생성 모델로 만든 완전한 합성 성인이다. 생성기에서 사진을 여권형 정보면 비율로
중앙 크롭하므로 Seed를 다시 실행해도 동일한 파일과 SHA-256이 만들어진다.

여권 인적사항면은 실제 인물이나 실제 여권 원본을 사용하지 않는다. 프로젝트에 포함된
`demo-data/nguyen-van-an-portrait.png`와 `demo-data/passport-portraits/*.png`는 이미지 생성
모델로 만든 완전한 합성 인물이며, 생성기에서 사진·영문 인적사항·발급일·만료일을 조합한다.
실제 여권의 사진·정보 필드·MRZ 배치 비율만 참고하고 실제 국가 문장과 보안문양은
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
