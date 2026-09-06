# Project-Specific Review Checklist

When analyzing findings, also proactively check for these project-specific concerns in the changed code:

## Database & Performance
- [ ] **N+1 queries**: List fetched, then individual queries per item — should be a JOIN or single query
- [ ] **Missing indexes**: New WHERE/JOIN/ORDER BY columns without corresponding indexes
- [ ] **Sequential scans on large tables**: Use EXPLAIN mentally — will this scan the full table?
- [ ] **Missing SECURITY DEFINER + SET search_path** on new RPC functions

## Architecture (CLEAN)
- [ ] **Layer violations**: Handlers should not contain business logic — that belongs in use-cases/domain
- [ ] **Direct DB access from handlers**: Should go through infrastructure layer
- [ ] **Missing DTOs**: API responses should use proper DTOs, not raw DB rows

## Error Handling
- [ ] **DetailedError pattern**: Error responses must follow `_shared/dtos/detailed-error.ts`
- [ ] **Spanish error messages**: User-facing errors must be in Spanish
- [ ] **No system internals leaked**: Error messages should not expose DB errors, stack traces, or internal paths

## State Machines
- [ ] **Invalid transitions**: Check against `docs/database/state-machines.md`
- [ ] **Missing status checks**: Operations that depend on a specific state should verify it first

## Security
- [ ] **auth.uid() checks**: RPC functions must verify the caller's identity
- [ ] **SQL injection**: Parameterized queries, no string concatenation for SQL
- [ ] **Missing RLS**: New tables must have `ENABLE ROW LEVEL SECURITY`

## Migrations
- [ ] **Idempotency**: All DDL must be safe to re-run (IF NOT EXISTS, DO $$ blocks)
- [ ] **Business error functions**: Use `internal.raise_business_error()` / `internal.raise_auth_error()`
