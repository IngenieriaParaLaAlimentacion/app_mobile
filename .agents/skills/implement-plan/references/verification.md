# Verification Reference

## Automated Checks

Run `scripts/verify.sh` from the project root after completing each phase. It handles:

1. **Kotlin compilation** — catches type errors, missing imports, and broken references
2. **Proto generation** — only runs if `.proto` files changed, regenerates Kotlin bindings

## Common Compilation Failures

| Error | Likely Cause | Fix |
|---|---|---|
| Unresolved reference | Missing import or renamed symbol | Check actual import paths in similar files |
| Type mismatch | Wrong type in state/action/destination | Verify against the pattern in neighboring features |
| No value passed for parameter | Constructor changed | Check the current constructor signature |
| Overload resolution ambiguity | Multiple matching functions | Add explicit type annotations |

## Manual Verification Checklist

After automated checks pass, these need human testing:
- Screen renders correctly on iOS and Android
- Navigation flows work (forward and back)
- Loading/error states display properly
- No regressions in related screens

## Proto-Specific

If modifying `.proto` files:
- Regenerate before compiling Kotlin: `./gradlew generateProtos --no-daemon`
- Check that DTOs and mappers align with new proto fields
- Verify serialization round-trips if changing field types
