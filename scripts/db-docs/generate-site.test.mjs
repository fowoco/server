import assert from 'node:assert/strict'
import { mkdtemp, mkdir, readFile, rm, writeFile } from 'node:fs/promises'
import os from 'node:os'
import path from 'node:path'
import { spawnSync } from 'node:child_process'
import test from 'node:test'

const script = path.resolve('scripts/db-docs/generate-site.mjs')

test('Flyway JSON을 안전한 DB 문서 사이트로 변환한다', async () => {
  const temporaryRoot = await mkdtemp(path.join(os.tmpdir(), 'fowoco-db-docs-'))
  try {
    const infoFile = path.join(temporaryRoot, 'flyway-info.json')
    const output = path.join(temporaryRoot, 'site')
    await mkdir(path.join(output, 'schema'), { recursive: true })
    await writeFile(path.join(output, 'schema', 'index.html'), '<html>SchemaSpy</html>')
    await writeFile(infoFile, JSON.stringify({
      schemaVersion: '5',
      schemaName: 'public',
      flywayVersion: '12.4.0',
      migrations: [
        {
          version: '1',
          description: 'baseline <safe>',
          type: 'SQL',
          state: 'Success',
          installedOnUTC: '2026-07-24T00:00:00Z',
          executionTime: 14,
          filepath: '/flyway/sql/V1__baseline.sql',
        },
        {
          version: '6',
          description: 'next',
          type: 'SQL',
          state: 'Pending',
          installedOnUTC: '',
          executionTime: 0,
          filepath: '/private/path/V6__next.sql',
        },
      ],
    }))

    const result = spawnSync(process.execPath, [
      script,
      '--flyway-info', infoFile,
      '--output', output,
      '--commit', '1234567890abcdef1234567890abcdef12345678',
      '--generated-at', '2026-07-24T01:02:03Z',
      '--repository-url', 'https://github.com/fowoco/server',
    ], { encoding: 'utf8' })

    assert.equal(result.status, 0, result.stderr)
    const index = await readFile(path.join(output, 'index.html'), 'utf8')
    const migrations = await readFile(path.join(output, 'migrations', 'index.html'), 'utf8')
    const metadata = JSON.parse(await readFile(path.join(output, 'metadata.json'), 'utf8'))

    assert.match(index, /현재 Schema Version/)
    assert.match(index, /성공 1개 · 대기 1개/)
    assert.match(migrations, /baseline &lt;safe&gt;/)
    assert.doesNotMatch(migrations, /private\/path/)
    assert.equal(metadata.schema_version, '5')
    assert.deepEqual(metadata.migration_counts, {
      success: 1,
      pending: 1,
      attention_required: 0,
    })
  } finally {
    await rm(temporaryRoot, { recursive: true, force: true })
  }
})

test('SchemaSpy 결과가 없으면 불완전한 사이트 생성을 거부한다', async () => {
  const temporaryRoot = await mkdtemp(path.join(os.tmpdir(), 'fowoco-db-docs-'))
  try {
    const infoFile = path.join(temporaryRoot, 'flyway-info.json')
    await writeFile(infoFile, JSON.stringify({ migrations: [] }))

    const result = spawnSync(process.execPath, [
      script,
      '--flyway-info', infoFile,
      '--output', path.join(temporaryRoot, 'site'),
    ], { encoding: 'utf8' })

    assert.notEqual(result.status, 0)
    assert.match(result.stderr, /\[db-docs\]/)
  } finally {
    await rm(temporaryRoot, { recursive: true, force: true })
  }
})
