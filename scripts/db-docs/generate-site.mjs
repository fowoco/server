import { readFile, writeFile, mkdir, access } from 'node:fs/promises'
import path from 'node:path'
import process from 'node:process'

function parseArguments(argv) {
  const result = {}
  for (let index = 0; index < argv.length; index += 2) {
    const key = argv[index]
    const value = argv[index + 1]
    if (!key?.startsWith('--') || value === undefined) {
      throw new Error(`잘못된 인자입니다: ${key ?? '(없음)'}`)
    }
    result[key.slice(2)] = value
  }
  return result
}

function escapeHtml(value) {
  return String(value ?? '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;')
}

function stateClass(state) {
  const normalized = String(state ?? '').toLowerCase()
  if (normalized === 'success') return 'success'
  if (normalized === 'pending') return 'pending'
  return 'warning'
}

function displayValue(value, fallback = '—') {
  return value === null || value === undefined || value === '' ? fallback : String(value)
}

function validateGeneratedAt(value) {
  const parsed = new Date(value)
  if (Number.isNaN(parsed.getTime())) {
    throw new Error(`생성 시각이 ISO-8601 형식이 아닙니다: ${value}`)
  }
  return parsed.toISOString()
}

function migrationRows(migrations) {
  return migrations
    .map((migration) => {
      const executionTime = Number.isFinite(migration.executionTime)
        ? `${migration.executionTime} ms`
        : '—'
      return `
        <tr>
          <td><code>${escapeHtml(displayValue(migration.version, 'Repeatable'))}</code></td>
          <td>${escapeHtml(displayValue(migration.description))}</td>
          <td>${escapeHtml(displayValue(migration.type))}</td>
          <td><span class="badge ${stateClass(migration.state)}">${escapeHtml(displayValue(migration.state))}</span></td>
          <td>${escapeHtml(displayValue(migration.installedOnUTC))}</td>
          <td>${escapeHtml(executionTime)}</td>
        </tr>`
    })
    .join('')
}

function pageShell({ title, description, body, assetPrefix = '' }) {
  return `<!doctype html>
<html lang="ko">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <meta name="description" content="${escapeHtml(description)}">
  <title>${escapeHtml(title)}</title>
  <link rel="stylesheet" href="${assetPrefix}assets/styles.css">
</head>
<body>
  <header class="topbar">
    <a class="brand" href="${assetPrefix}index.html">FOWOCO Database</a>
    <nav aria-label="주요 문서">
      <a href="${assetPrefix}schema/index.html">테이블 구조</a>
      <a href="${assetPrefix}migrations/index.html">Migration 이력</a>
    </nav>
  </header>
  <main>${body}</main>
  <footer>실제 데이터가 아닌 일회용 빈 PostgreSQL의 구조만 문서화합니다.</footer>
</body>
</html>`
}

const styles = `
:root {
  color-scheme: light;
  --ink: #172b2d;
  --muted: #5f7375;
  --line: #dbe5e4;
  --surface: #f5f9f8;
  --brand: #0c6a65;
  --brand-dark: #084d49;
  --success: #16794b;
  --pending: #936300;
  --warning: #a33a2b;
}
* { box-sizing: border-box; }
body {
  margin: 0;
  color: var(--ink);
  background: #fff;
  font-family: Inter, Pretendard, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
  line-height: 1.6;
}
.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  padding: 1rem clamp(1rem, 5vw, 4rem);
  color: #fff;
  background: var(--brand-dark);
}
.topbar a { color: inherit; text-decoration: none; }
.brand { font-size: 1.1rem; font-weight: 800; letter-spacing: .02em; }
nav { display: flex; gap: 1rem; font-size: .95rem; }
main { width: min(1120px, calc(100% - 2rem)); margin: 0 auto; padding: 3.5rem 0; }
.hero { max-width: 760px; margin-bottom: 2rem; }
.eyebrow { color: var(--brand); font-weight: 800; text-transform: uppercase; letter-spacing: .08em; }
h1 { margin: .25rem 0 1rem; font-size: clamp(2rem, 5vw, 3.2rem); line-height: 1.18; }
h2 { margin-top: 2.25rem; }
.lead { color: var(--muted); font-size: 1.08rem; }
.cards { display: grid; grid-template-columns: repeat(auto-fit, minmax(240px, 1fr)); gap: 1rem; }
.card {
  display: block;
  padding: 1.3rem;
  color: inherit;
  text-decoration: none;
  border: 1px solid var(--line);
  border-radius: 14px;
  background: var(--surface);
}
.card:hover { border-color: var(--brand); transform: translateY(-1px); }
.card h2 { margin: 0 0 .35rem; }
.meta {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: .75rem;
  margin: 2rem 0;
}
.meta div { padding: 1rem; border-left: 3px solid var(--brand); background: var(--surface); }
.meta dt { color: var(--muted); font-size: .85rem; }
.meta dd { margin: .25rem 0 0; font-weight: 700; overflow-wrap: anywhere; }
.table-wrap { overflow-x: auto; border: 1px solid var(--line); border-radius: 12px; }
table { width: 100%; border-collapse: collapse; font-size: .92rem; }
th, td { padding: .8rem; border-bottom: 1px solid var(--line); text-align: left; white-space: nowrap; }
th { background: var(--surface); }
tbody tr:last-child td { border-bottom: 0; }
.badge { display: inline-block; padding: .15rem .55rem; border-radius: 999px; font-weight: 700; }
.badge.success { color: var(--success); background: #def4e8; }
.badge.pending { color: var(--pending); background: #fff0bd; }
.badge.warning { color: var(--warning); background: #ffe2dd; }
.notice { padding: 1rem 1.2rem; border-radius: 10px; background: #e9f5f3; }
code { font-family: "SFMono-Regular", Consolas, monospace; }
footer { padding: 2rem 1rem; color: var(--muted); text-align: center; border-top: 1px solid var(--line); }
@media (max-width: 640px) {
  .topbar { align-items: flex-start; flex-direction: column; }
  main { padding-top: 2rem; }
}
`

async function main() {
  const args = parseArguments(process.argv.slice(2))
  if (!args['flyway-info'] || !args.output) {
    throw new Error('--flyway-info와 --output은 필수입니다.')
  }

  const generatedAt = validateGeneratedAt(args['generated-at'] ?? new Date().toISOString())
  const commit = args.commit ?? 'unknown'
  const repositoryUrl = args['repository-url'] ?? 'https://github.com/fowoco/server'
  const info = JSON.parse(await readFile(path.resolve(args['flyway-info']), 'utf8'))
  const migrations = Array.isArray(info.migrations) ? info.migrations : null
  if (!migrations) {
    throw new Error('Flyway info JSON에 migrations 배열이 없습니다.')
  }

  const output = path.resolve(args.output)
  await access(path.join(output, 'schema', 'index.html'))
  await mkdir(path.join(output, 'migrations'), { recursive: true })
  await mkdir(path.join(output, 'assets'), { recursive: true })

  const successCount = migrations.filter((migration) => migration.state === 'Success').length
  const pendingCount = migrations.filter((migration) => migration.state === 'Pending').length
  const warningCount = migrations.length - successCount - pendingCount
  const commitHref = /^[0-9a-f]{7,40}$/i.test(commit)
    ? `${repositoryUrl}/commit/${commit}`
    : repositoryUrl

  const homeBody = `
    <section class="hero">
      <p class="eyebrow">Generated from Flyway</p>
      <h1>FOWOCO 데이터베이스 문서</h1>
      <p class="lead">main의 Flyway Migration을 일회용 PostgreSQL에 적용한 뒤 자동 생성한 문서입니다. 실제 구조를 이해하는 보조 자료이며 변경의 원본은 Migration SQL과 ADR입니다.</p>
    </section>
    <section class="cards" aria-label="문서 바로가기">
      <a class="card" href="schema/index.html">
        <h2>테이블 구조 보기</h2>
        <p>전체 ERD, 컬럼, PK·FK·UNIQUE·CHECK·INDEX를 확인합니다.</p>
      </a>
      <a class="card" href="migrations/index.html">
        <h2>Migration 이력 보기</h2>
        <p>적용된 버전, 상태, 실행 시각과 검증 결과를 확인합니다.</p>
      </a>
    </section>
    <dl class="meta">
      <div><dt>현재 Schema Version</dt><dd>${escapeHtml(displayValue(info.schemaVersion))}</dd></div>
      <div><dt>Schema</dt><dd>${escapeHtml(displayValue(info.schemaName))}</dd></div>
      <div><dt>Flyway</dt><dd>${escapeHtml(displayValue(info.flywayVersion))}</dd></div>
      <div><dt>생성 시각</dt><dd>${escapeHtml(generatedAt)}</dd></div>
      <div><dt>Git commit</dt><dd><a href="${escapeHtml(commitHref)}">${escapeHtml(commit)}</a></dd></div>
    </dl>
    <p class="notice">성공 ${successCount}개 · 대기 ${pendingCount}개 · 확인 필요 ${warningCount}개. 문서 생성 전에 <code>flyway validate</code>를 통과했습니다.</p>`

  const migrationBody = `
    <section class="hero">
      <p class="eyebrow">Flyway History</p>
      <h1>Migration 적용 이력</h1>
      <p class="lead">빈 PostgreSQL을 처음부터 구성한 결과입니다. 운영 DB의 데이터나 접속정보는 포함하지 않습니다.</p>
    </section>
    <div class="table-wrap">
      <table>
        <thead><tr><th>Version</th><th>설명</th><th>유형</th><th>상태</th><th>적용 시각(UTC)</th><th>실행 시간</th></tr></thead>
        <tbody>${migrationRows(migrations)}</tbody>
      </table>
    </div>`

  await writeFile(path.join(output, 'index.html'), pageShell({
    title: 'FOWOCO Database Documentation',
    description: 'FOWOCO Server 데이터베이스 ERD와 Flyway Migration 문서',
    body: homeBody,
  }))
  await writeFile(path.join(output, 'migrations', 'index.html'), pageShell({
    title: 'FOWOCO Flyway Migration History',
    description: 'FOWOCO Server Flyway Migration 적용 이력',
    body: migrationBody,
    assetPrefix: '../',
  }))
  await writeFile(path.join(output, 'assets', 'styles.css'), styles.trimStart())
  await writeFile(path.join(output, '.nojekyll'), '')
  await writeFile(path.join(output, 'metadata.json'), JSON.stringify({
    generated_at: generatedAt,
    git_commit: commit,
    schema_version: info.schemaVersion ?? null,
    schema_name: info.schemaName ?? null,
    flyway_version: info.flywayVersion ?? null,
    migration_counts: {
      success: successCount,
      pending: pendingCount,
      attention_required: warningCount,
    },
  }, null, 2))
}

main().catch((error) => {
  console.error(`[db-docs] ${error.message}`)
  process.exitCode = 1
})
