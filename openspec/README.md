# OpenSpec — StaffFlow

This directory is the **SDD (Spec-Driven Development) artifact store** for the StaffFlow project.

## Structure

```
openspec/
├── config.yaml          ← SDD config: stack, testing, phase rules
├── specs/               ← Source-of-truth domain specs (merged after archive)
│   └── {domain}/
│       └── spec.md
├── changes/             ← Active change folders
│   ├── {change-name}/
│   │   ├── state.yaml
│   │   ├── proposal.md
│   │   ├── specs/
│   │   ├── design.md
│   │   ├── tasks.md
│   │   └── verify-report.md
│   └── archive/         ← Completed changes (YYYY-MM-DD-{change-name}/)
└── README.md            ← This file
```

## SDD Cycle

Each change goes through: **explore → propose → spec → design → tasks → apply → verify → archive**

The orchestrator manages DAG state via `changes/{change-name}/state.yaml`.

## Active Scope

This SDD cycle covers **staffflow-backend/** only.
Android (`staffflow-android/`) is out of scope.

## Testing Gate

The full backend suite runs without a database or Spring context (341 tests):
```bash
cd staffflow-backend
./mvnw -B test
```

CI (`.github/workflows/ci.yml`) runs this same command on every push and pull request.
