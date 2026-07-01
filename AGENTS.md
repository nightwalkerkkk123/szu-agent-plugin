# AGENTS

> AI agent entry file for this project (Hermes, Claude Code, Codex).
> **Project-specific rules live in `CLAUDE.md`** — read that first; this file only adds the cross-agent context.

## What this project is

- **Type**: CLI tool + Skill/MCP plugin (Java 21, Maven)
- **For**: External AI agents to call as a tool over MCP (HTTP transport on `localhost:8765/mcp` by default, started via `scripts/serve.sh --background`; stdio command transport also available via `mcp serve`)
- **Not**: an AI agent, a web app, a database-backed service
- **Business reference**: `E:\CODE\szu-sports-booking\` (Python backend this CLI talks to)
- **Full description**: see `CLAUDE.md`, `SERVICE.md`, `WORKING-CONTEXT.md`

## Build & test

```bash
mvn -q -DskipTests package        # build jar
mvn test                          # run unit + integration tests
java -jar target/szu-agent-plugin.jar skill list --format json   # smoke test
```

Maven: `E:\tools\apache-maven-3.9.16\bin\mvn` · JDK 21: `E:\tools\jdk-21` (see `docs/setup/windows-maven.md`).

## Architecture (one-line)

picocli CLI commands → Skill/MCP tool registry → `BrowserLifecycle` (Playwright adapter) → target booking system.
Java sources under `src/main/java`, tests under `src/test/java`.

For the full map see `docs/system-map.md` and `docs/design-patterns.md` (referenced by `CLAUDE.md`).

## Security baseline

- Public methods require Javadoc with `@since` and `@author` (project rule — see `CLAUDE.md`)
- Never log credentials, cookies, or tokens — use `LogMasker` for redaction
- Validate all CLI args server-side / library-side; never trust caller input
- No `System.out.println` in production code; use SLF4J/Logback
- Follow OWASP Top 10
- Pre-commit gate: `mvn test` must pass

## Engine guidance (for oh-my-hermes / multi-agent routing)

- **Multi-file feature work** (new command, new tool, refactor across modules) → `implement-with-claude-code`
- **Single-file targeted fix** (typo, simple bug, one method change) → `implement-with-codex`
- **Issue triage, PR creation, security scan** → Hermes-side skills (`auto-issue-triage`, `create-github-pr`, `security-review`)
- **Architecture / pattern decisions** → talk to a human or use a planner agent first; do not silently pick

## Do **not** apply these oh-my-hermes skills here

They assume a web app stack and will mislead the agent:

- `deploy-to-vercel`, `connect-supabase`, `setup-monitoring`, `health-check` (no HTTP `/api/health` endpoint, no Vercel, no Supabase)
- `design-handoff`, `product-brief` (project is already specified — see `CLAUDE.md` and `docs/PRD.md`)

## Commit conventions

- One commit per meaningful change
- Conventional Commits format (`feat:`, `fix:`, `refactor:`, `docs:`, `test:`, `chore:`)
- Never commit `.env`, `.env.local`, credentials, or generated `target/` artifacts
- See `CLAUDE.md` for the full rule set (harness traces, feature intake, etc.)

## When stuck

1. Read `CLAUDE.md` first.
2. If it's a coding question, check `docs/CONTEXT_RULES.md` for the per-phase reading list.
3. If it's about the architecture, see `docs/system-map.md` or `docs/design-patterns.md`.
4. If still unclear, **stop and ask** — do not guess (per the "Think Before Coding" guideline in `CLAUDE.md`).
