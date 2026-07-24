import { access, mkdir, readFile, writeFile } from 'node:fs/promises'
import path from 'node:path'
import process from 'node:process'

const SWAGGER_UI_VERSION = '5.32.2'

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

function safeEmbeddedJson(value) {
  return JSON.stringify(value)
    .replaceAll('&', '\\u0026')
    .replaceAll('<', '\\u003c')
    .replaceAll('>', '\\u003e')
    .replaceAll('\u2028', '\\u2028')
    .replaceAll('\u2029', '\\u2029')
}

function validateGeneratedAt(value) {
  const parsed = new Date(value)
  if (Number.isNaN(parsed.getTime())) {
    throw new Error(`생성 시각이 ISO-8601 형식이 아닙니다: ${value}`)
  }
  return parsed.toISOString()
}

function validateOpenApi(specification) {
  if (typeof specification !== 'object' || specification === null) {
    throw new Error('OpenAPI 문서가 JSON 객체가 아닙니다.')
  }
  if (!String(specification.openapi ?? '').startsWith('3.')) {
    throw new Error(`지원하지 않는 OpenAPI 버전입니다: ${specification.openapi ?? '(없음)'}`)
  }
  if (typeof specification.info?.title !== 'string' || specification.info.title.trim() === '') {
    throw new Error('OpenAPI info.title이 없습니다.')
  }
  if (typeof specification.paths !== 'object' || specification.paths === null) {
    throw new Error('OpenAPI paths가 없습니다.')
  }
}

function commitLink(repositoryUrl, commit) {
  return /^[0-9a-f]{7,40}$/i.test(commit)
    ? `${repositoryUrl}/commit/${commit}`
    : repositoryUrl
}

const swaggerInit = `
window.addEventListener('DOMContentLoaded', () => {
  const specification = JSON.parse(document.getElementById('openapi-specification').textContent)
  window.ui = SwaggerUIBundle({
    spec: specification,
    dom_id: '#swagger-ui',
    deepLinking: true,
    displayRequestDuration: true,
    filter: true,
    persistAuthorization: false,
    supportedSubmitMethods: [],
    presets: [
      SwaggerUIBundle.presets.apis,
      SwaggerUIStandalonePreset,
    ],
    layout: 'BaseLayout',
  })
})
`.trimStart()

function page({ specification, generatedAt, commit, repositoryUrl }) {
  const title = specification.info.title
  const version = specification.info.version ?? 'unknown'
  const commitHref = commitLink(repositoryUrl, commit)

  return `<!doctype html>
<html lang="ko">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <meta name="description" content="FOWOCO Server main 브랜치에서 자동 생성한 OpenAPI 문서">
  <meta http-equiv="Content-Security-Policy" content="default-src 'self'; script-src 'self' https://cdn.jsdelivr.net; style-src 'self' https://cdn.jsdelivr.net 'unsafe-inline'; img-src 'self' data:; font-src 'self' https://cdn.jsdelivr.net; connect-src 'self'; object-src 'none'; base-uri 'none'; frame-ancestors 'none'">
  <title>${escapeHtml(title)} · Swagger</title>
  <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/swagger-ui-dist@${SWAGGER_UI_VERSION}/swagger-ui.css">
  <style>
    :root { color-scheme: light; --brand: #0c6a65; --brand-dark: #084d49; }
    * { box-sizing: border-box; }
    body { margin: 0; background: #fafafa; }
    .api-docs-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 1rem;
      padding: 1rem clamp(1rem, 4vw, 3rem);
      color: #fff;
      background: var(--brand-dark);
      font-family: Inter, Pretendard, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
    }
    .api-docs-header strong { font-size: 1.05rem; }
    .api-docs-header nav { display: flex; flex-wrap: wrap; gap: 1rem; }
    .api-docs-header a { color: #fff; }
    .api-docs-notice {
      margin: 1rem auto 0;
      width: min(1280px, calc(100% - 2rem));
      padding: .85rem 1rem;
      border-left: 4px solid var(--brand);
      background: #e9f5f3;
      font: 14px/1.6 Inter, Pretendard, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
    }
    .api-docs-meta { color: #d5e8e6; font-size: .85rem; }
    .swagger-ui .topbar,
    .swagger-ui .auth-wrapper { display: none; }
    @media (max-width: 720px) {
      .api-docs-header { align-items: flex-start; flex-direction: column; }
    }
  </style>
</head>
<body>
  <header class="api-docs-header">
    <div>
      <strong>FOWOCO Server API</strong>
      <div class="api-docs-meta">API ${escapeHtml(version)} · 생성 ${escapeHtml(generatedAt)} · <a href="${escapeHtml(commitHref)}">commit ${escapeHtml(commit)}</a></div>
    </div>
    <nav aria-label="관련 문서">
      <a href="../index.html">Database 문서</a>
      <a href="openapi.json" download>OpenAPI JSON</a>
      <a href="${escapeHtml(repositoryUrl)}">GitHub</a>
    </nav>
  </header>
  <p class="api-docs-notice">
    main 코드에서 자동 생성한 읽기 전용 API 계약입니다. 실제 요청 전송은 비활성화되어 있습니다.
    로컬에서 API를 시험하려면 서버의 <code>/swagger-ui.html</code>을 사용하세요.
  </p>
  <main id="swagger-ui"></main>
  <script id="openapi-specification" type="application/json">${safeEmbeddedJson(specification)}</script>
  <script src="https://cdn.jsdelivr.net/npm/swagger-ui-dist@${SWAGGER_UI_VERSION}/swagger-ui-bundle.js"></script>
  <script src="https://cdn.jsdelivr.net/npm/swagger-ui-dist@${SWAGGER_UI_VERSION}/swagger-ui-standalone-preset.js"></script>
  <script src="assets/swagger-init.js"></script>
</body>
</html>`
}

async function main() {
  const args = parseArguments(process.argv.slice(2))
  if (!args.openapi || !args.output) {
    throw new Error('--openapi와 --output은 필수입니다.')
  }

  const input = path.resolve(args.openapi)
  const output = path.resolve(args.output)
  const generatedAt = validateGeneratedAt(args['generated-at'] ?? new Date().toISOString())
  const commit = args.commit ?? 'unknown'
  const repositoryUrl = args['repository-url'] ?? 'https://github.com/fowoco/server'

  await access(input)
  const specification = JSON.parse(await readFile(input, 'utf8'))
  validateOpenApi(specification)
  delete specification.servers

  await mkdir(path.join(output, 'assets'), { recursive: true })
  await writeFile(path.join(output, 'index.html'), page({
    specification,
    generatedAt,
    commit,
    repositoryUrl,
  }))
  await writeFile(path.join(output, 'openapi.json'), `${JSON.stringify(specification, null, 2)}\n`)
  await writeFile(path.join(output, 'assets', 'swagger-init.js'), swaggerInit)
  await writeFile(path.join(output, 'metadata.json'), `${JSON.stringify({
    generated_at: generatedAt,
    git_commit: commit,
    openapi_version: specification.openapi,
    api_version: specification.info.version ?? null,
    path_count: Object.keys(specification.paths).length,
    swagger_ui_version: SWAGGER_UI_VERSION,
    try_it_out_enabled: false,
  }, null, 2)}\n`)
}

main().catch((error) => {
  console.error(`[api-docs] ${error.message}`)
  process.exitCode = 1
})
