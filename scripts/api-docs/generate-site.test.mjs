import assert from 'node:assert/strict'
import { mkdtemp, readFile, rm, writeFile } from 'node:fs/promises'
import os from 'node:os'
import path from 'node:path'
import { spawnSync } from 'node:child_process'
import test from 'node:test'

const script = path.resolve('scripts/api-docs/generate-site.mjs')

test('OpenAPI JSON을 읽기 전용 Swagger HTML로 변환한다', async () => {
  const temporaryRoot = await mkdtemp(path.join(os.tmpdir(), 'fowoco-api-docs-'))
  try {
    const openApiFile = path.join(temporaryRoot, 'openapi.json')
    const output = path.join(temporaryRoot, 'site')
    await writeFile(openApiFile, JSON.stringify({
      openapi: '3.1.0',
      info: {
        title: 'FOWOCO <Server> API',
        version: '0.1.0',
        description: '</script><script>alert("unsafe")</script>',
      },
      servers: [{ url: 'http://127.0.0.1:18080' }],
      paths: {
        '/health': {
          get: {
            summary: '서버 상태 확인',
            responses: { 200: { description: '정상' } },
          },
        },
      },
    }))

    const result = spawnSync(process.execPath, [
      script,
      '--openapi', openApiFile,
      '--output', output,
      '--commit', '1234567890abcdef1234567890abcdef12345678',
      '--generated-at', '2026-07-24T01:02:03Z',
      '--repository-url', 'https://github.com/fowoco/server',
    ], { encoding: 'utf8' })

    assert.equal(result.status, 0, result.stderr)
    const index = await readFile(path.join(output, 'index.html'), 'utf8')
    const init = await readFile(path.join(output, 'assets', 'swagger-init.js'), 'utf8')
    const copiedSpecification = JSON.parse(await readFile(path.join(output, 'openapi.json'), 'utf8'))
    const metadata = JSON.parse(await readFile(path.join(output, 'metadata.json'), 'utf8'))

    assert.match(index, /FOWOCO &lt;Server&gt; API/)
    assert.match(index, /읽기 전용 API 계약/)
    assert.doesNotMatch(index, /<\/script><script>alert/)
    assert.match(index, /\\u003c\/script\\u003e/)
    assert.match(init, /supportedSubmitMethods: \[\]/)
    assert.equal(copiedSpecification.info.description, '</script><script>alert("unsafe")</script>')
    assert.equal(copiedSpecification.servers, undefined)
    assert.equal(metadata.path_count, 1)
    assert.equal(metadata.try_it_out_enabled, false)
  } finally {
    await rm(temporaryRoot, { recursive: true, force: true })
  }
})

test('유효한 OpenAPI 문서가 아니면 사이트 생성을 거부한다', async () => {
  const temporaryRoot = await mkdtemp(path.join(os.tmpdir(), 'fowoco-api-docs-'))
  try {
    const openApiFile = path.join(temporaryRoot, 'openapi.json')
    await writeFile(openApiFile, JSON.stringify({
      info: { title: 'broken' },
      paths: {},
    }))

    const result = spawnSync(process.execPath, [
      script,
      '--openapi', openApiFile,
      '--output', path.join(temporaryRoot, 'site'),
    ], { encoding: 'utf8' })

    assert.notEqual(result.status, 0)
    assert.match(result.stderr, /\[api-docs\]/)
  } finally {
    await rm(temporaryRoot, { recursive: true, force: true })
  }
})

test('HTTPS Server URL이 있으면 Swagger 실제 호출을 활성화한다', async () => {
  const temporaryRoot = await mkdtemp(path.join(os.tmpdir(), 'fowoco-api-docs-'))
  try {
    const openApiFile = path.join(temporaryRoot, 'openapi.json')
    const output = path.join(temporaryRoot, 'site')
    await writeFile(openApiFile, JSON.stringify({
      openapi: '3.1.0',
      info: { title: 'FOWOCO Server API', version: '0.1.0' },
      paths: { '/api/v1/auth/login': { post: { responses: { 200: { description: '정상' } } } } },
    }))

    const result = spawnSync(process.execPath, [
      script,
      '--openapi', openApiFile,
      '--output', output,
      '--server-url', 'https://demo.fowoco.example/',
    ], { encoding: 'utf8' })

    assert.equal(result.status, 0, result.stderr)
    const index = await readFile(path.join(output, 'index.html'), 'utf8')
    const init = await readFile(path.join(output, 'assets', 'swagger-init.js'), 'utf8')
    const copiedSpecification = JSON.parse(await readFile(path.join(output, 'openapi.json'), 'utf8'))
    const metadata = JSON.parse(await readFile(path.join(output, 'metadata.json'), 'utf8'))

    assert.match(index, /실제 데모 Server/)
    assert.match(index, /connect-src 'self' https:\/\/demo\.fowoco\.example/)
    assert.doesNotMatch(index, /\.swagger-ui \.auth-wrapper \{ display: none; \}/)
    assert.match(init, /withCredentials: false/)
    assert.match(init, /supportedSubmitMethods: \['get', 'post'/)
    assert.deepEqual(copiedSpecification.servers, [{
      url: 'https://demo.fowoco.example',
      description: 'FOWOCO HTTPS 데모 Server',
    }])
    assert.equal(metadata.try_it_out_enabled, true)
    assert.equal(metadata.server_url, 'https://demo.fowoco.example')
  } finally {
    await rm(temporaryRoot, { recursive: true, force: true })
  }
})

test('GitHub Pages 실제 호출 URL은 HTTPS가 아니면 거부한다', async () => {
  const temporaryRoot = await mkdtemp(path.join(os.tmpdir(), 'fowoco-api-docs-'))
  try {
    const openApiFile = path.join(temporaryRoot, 'openapi.json')
    await writeFile(openApiFile, JSON.stringify({
      openapi: '3.1.0',
      info: { title: 'FOWOCO Server API', version: '0.1.0' },
      paths: {},
    }))

    const result = spawnSync(process.execPath, [
      script,
      '--openapi', openApiFile,
      '--output', path.join(temporaryRoot, 'site'),
      '--server-url', 'http://fowoco.example',
    ], { encoding: 'utf8' })

    assert.notEqual(result.status, 0)
    assert.match(result.stderr, /HTTPS만 허용/)
  } finally {
    await rm(temporaryRoot, { recursive: true, force: true })
  }
})
