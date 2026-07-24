# 데이터베이스 문서 사용법

FOWOCO Server의 데이터베이스 문서는 `main`의 Flyway Migration을 일회용
PostgreSQL에 처음부터 적용한 결과로 생성합니다.

- 팀 공유 사이트: <https://fowoco.github.io/server/>
- 변경의 원본: `src/main/resources/db/migration`
- 구조 결정의 원본: `docs/adr`

문서는 구조를 쉽게 찾기 위한 보조 수단입니다. 문서 화면에서 DB를 변경할 수
없으며, Flyway SQL을 거치지 않은 변경은 정식 변경으로 인정하지 않습니다.

## 무엇을 볼 수 있나요?

| 메뉴 | 확인할 수 있는 내용 |
| --- | --- |
| 테이블 구조 | 전체 ERD, 컬럼 타입, Nullable, 기본값 |
| 관계 | PK, FK, UNIQUE, CHECK, INDEX |
| Migration 이력 | 적용 버전, 상태, 적용 시각, 실행 시간 |
| 생성 정보 | 기준 Git commit, Flyway·Schema version, 생성 시각 |

운영 DB의 데이터, 계정, 접속 주소, 비밀번호는 문서에 포함하지 않습니다.

## 언제 갱신되나요?

다음 경로가 변경되어 `main`에 병합되면 `Database Documentation` Workflow가
자동 실행됩니다.

```text
src/main/resources/db/migration/**
scripts/db-docs/**
.github/workflows/database-docs.yml
```

Workflow는 다음 순서로 동작합니다.

```text
빈 PostgreSQL 시작
→ Flyway migrate
→ Flyway validate
→ SchemaSpy HTML 생성
→ Migration 이력 페이지 생성
→ GitHub Pages 배포
```

Migration 적용이나 `validate`가 실패하면 기존 Pages를 덮어쓰지 않습니다.

## PR에서 먼저 확인하기

Migration을 바꾼 PR에서는 Pages를 배포하지 않습니다.

1. PR의 `Checks`에서 `Database Documentation`을 엽니다.
2. 실행 결과 아래의 `Artifacts`로 이동합니다.
3. `database-docs-site`를 내려받습니다.
4. 압축을 풀고 `index.html`을 브라우저로 엽니다.

Artifact는 14일 동안 보관합니다. PR 작성자는 ERD 변경이 의도한 구조인지
확인한 뒤 리뷰를 요청합니다.

## 로컬에서 생성하기

Docker Desktop 또는 Docker Engine과 Node.js 24 이상이 필요합니다.

```bash
./scripts/db-docs/generate-local.sh
```

이 명령은 작업마다 별도의 Docker network와 PostgreSQL container를 만들고,
완료되거나 실패하면 정확히 그 임시 자원만 제거합니다.

생성 결과는 Git에 포함되지 않는 아래 경로에 있습니다.

```text
build/db-docs/site/index.html
```

macOS에서는 다음 명령으로 열 수 있습니다.

```bash
open build/db-docs/site/index.html
```

## 실패했을 때 확인할 것

| 증상 | 확인 |
| --- | --- |
| Docker를 찾지 못함 | Docker Desktop 설치·실행 여부 |
| PostgreSQL 준비 실패 | 기존 container와 Docker 자원 상태 |
| Flyway migrate 실패 | 가장 최근 Migration SQL과 PostgreSQL 문법 |
| Flyway validate 실패 | 이미 적용된 Migration을 수정·삭제했는지 |
| SchemaSpy 실패 | 테이블·FK·제약조건 오류와 container 로그 |
| Pages 배포 실패 | Repository Pages Source와 `github-pages` Environment |

적용된 Migration을 수정하거나 `flyway repair`로 실패를 숨기지 않습니다. 새
버전의 Migration을 추가해 정정하고, 위험한 변경은 ADR과 리뷰를 먼저 거칩니다.

## 보안 원칙

- 운영·Staging DB에 연결하지 않습니다.
- 실제 사용자·근로자·Demo Seed 데이터를 넣지 않습니다.
- 임시 DB 값은 Workflow 내부에서만 사용합니다.
- Personal Access Token과 별도 Cloud Secret은 사용하지 않습니다.
- Workflow Action은 full-length commit SHA로 고정합니다.
- 생성 사이트에는 Schema 구조와 비민감 build metadata만 게시합니다.
