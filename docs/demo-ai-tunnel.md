# Mac AI Quick Tunnel 임시 전환

이 문서는 Kubernetes AI Agent가 준비되기 전, 오늘·내일 시연에 한해 배포 Server가
Mac의 AI Agent를 호출하도록 전환하고 원래 설정으로 복구하는 절차를 설명한다.

## 안전장치

- 실제 값은 GitHub Actions Secret으로만 전달하고 Git·PR·로그에 기록하지 않는다.
- `apply` 최초 실행은 현재 `server-env` 전체 data를 Kubernetes 내부의
  `server-env-before-mac-demo` Secret으로 백업한다.
- `apply`를 다시 실행해도 최초 백업을 덮어쓰지 않는다.
- Server rollout이 실패하면 Workflow가 원래 Secret으로 자동 복구하고 다시 기동한다.
- `restore`는 백업 전체 data를 되돌린 뒤 백업 Secret을 삭제한다.
- Quick Tunnel URL은 프로세스가 재시작되면 바뀌므로 변경될 때마다 Secret과 Workflow를
  다시 적용해야 한다.

## GitHub Actions Secret

Server 저장소 Actions Secret에 다음 이름으로 등록한다.

| 이름 | 값 |
| --- | --- |
| `KUBE_CONFIG` | 기존에 등록된 k3s kubeconfig Base64 |
| `AI_DEMO_BASE_URL` | `https://...trycloudflare.com` 형식의 현재 Quick Tunnel 주소 |
| `AI_DEMO_INTERNAL_TOKEN` | Mac AI의 `FOWOCO_INTERNAL_API_TOKEN`과 동일한 32자 이상 값 |

Workflow는 신·구 Server 버전 모두를 위해 다음 변수를 함께 설정한다.

- `FOWOCO_AI_BASE_URL`
- `FOWOCO_AI_INTERNAL_TOKEN`
- `AI_RUNTIME_ENDPOINT`
- `AI_RUNTIME_SERVICE_CREDENTIAL`
- `AI_RUNTIME_ENABLED=true`
- `AI_RUNTIME_OVERALL_TIMEOUT=240s`

## 적용

1. Mac AI Agent readiness가 성공한 상태에서 Quick Tunnel을 실행한다.
2. 현재 Tunnel 주소와 같은 Internal Token을 GitHub Actions Secret에 등록한다.
3. GitHub Actions에서 `Demo AI Tunnel switch`를 선택한다.
4. `Run workflow`의 `operation=apply`를 실행한다.
5. Workflow의 Server readiness 및 Server Pod→Mac AI readiness 검증이 성공했는지 확인한다.
6. 배포 Client에서 AiRun을 생성해 PLAN→ANALYZE 전체 왕복을 확인한다.

## 시연 종료 후 복구

1. `Demo AI Tunnel switch`의 `operation=restore`를 실행한다.
2. Server rollout과 readiness 성공을 확인한다.
3. GitHub Actions Secret `AI_DEMO_BASE_URL`, `AI_DEMO_INTERNAL_TOKEN`을 삭제한다.
4. Quick Tunnel과 Mac AI Agent를 종료한다.
5. 임시 Workflow와 이 문서를 제거한다.

`restore` 전에 Quick Tunnel을 먼저 종료하면 배포 Server가 AI 호출에 실패할 수 있으므로
반드시 Server 설정을 먼저 복구한다.
