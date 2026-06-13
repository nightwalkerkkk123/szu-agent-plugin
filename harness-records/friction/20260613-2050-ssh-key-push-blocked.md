# Friction: SSH key identity mismatch blocks `git push`

**Date:** 2026-06-13 20:50
**Lane:** normal
**Category:** tool-missing (or: external-credential)

---

## What happened

Tried to push 8 local master commits to `origin/master` at end of
Phase 5 cleanup. The push failed with:

```
ERROR: Permission to nightwalkerkkk123/szu-agent-plugin.git denied to Autur-wang.
fatal: Could not read from remote repository.
```

## Investigation

- `git remote -v` → `git@github.com:nightwalkerkkk123/szu-agent-plugin.git` (SSH)
- `ssh -T git@github.com` → connected, but server identified the
  client as `Autur-wang` (a different GitHub account)
- `~/.ssh/` has two keys: `id_ed25519` (Apr 2025) and `id_rsa` (Nov 2024)
- The active key fingerprint maps to `Autur-wang` on github.com
- The repo owner is `nightwalkerkkk123`

The two GitHub accounts are different people (or same person with
two accounts). The key currently being used is not authorized on
`nightwalkerkkk123/szu-agent-plugin`.

## Why this is friction

- Code is locally committed, tested, fast-forward compatible with
  origin/master — ready to push
- AI agent cannot resolve this safely: trying random SSH keys is
  a credential-stuffing pattern; using a guessed passphrase is
  worse
- AI agent has no way to ask GitHub "which of my keys owns this
  repo" without already having push access
- AI agent has no way to start a browser session for the user to
  log in and rotate

## Workaround applied

- Documented the push as a "user action" in WORKING-CONTEXT.md
- Verified topology (`git merge-base --is-ancestor origin/master
  master` → true) so the push will be a clean fast-forward when
  the user does it
- Did not attempt to push from inside the agent

## Proposed harness improvement

**Option A (lower effort)**: Add a one-line status check to the
session-end summary: "git push needed? Y/N + remote URL + last push
age". This surfaces the drift explicitly so the user knows there's
work waiting.

**Option B (medium effort)**: When a session has unpushed commits
on master/main, automatically generate a one-shot
`/scripts/push.sh` that the user can review and run. Include
`GIT_SSH_COMMAND` hint pointing at the right key, based on
`gh auth status` or known_hosts lookup.

**Option C (high effort)**: Use `gh` CLI exclusively (HTTPS-based
auth flow that goes through the user's `gh auth login` session).
Avoids SSH key ambiguity. Cost: `gh` must be installed and the
user must have already done `gh auth login` once.

## Lesson for future

- The end of any "in master, ready to push" phase should
  **explicitly check** for the push boundary, not assume it
- If push is blocked by credentials, the right output is "user
  action needed at <URL> with <credential>" not "I'll try
  another key"
- Local-master-only state is **not** the same as published
  state; the difference is the user's deployment, not the agent's
