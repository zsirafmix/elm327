# Contributing

## Ground rules

1. **Never weaken `SafetyGate`** without an explicit, reviewed design for non-READ-ONLY builds and legal review.
2. **No secrets in git** (API keys, keystores, tokens).
3. Prefer small, focused commits.

## Commit style

```
type(scope): short imperative summary

# types: feat, fix, docs, refactor, test, chore, safety
# examples:
feat(obd): add Mode 09 CALID parser
docs(safety): clarify UDS 0x31 block message
test(scoring): cover star boundaries
```

## Pull requests

- Describe mock vs hardware impact
- Note any new permissions
- Add/adjust unit tests for parsers and scoring
- Update `docs/` when behavior or schema changes
- Run `./gradlew :app:testDebugUnitTest` before requesting review

## Code layout

Follow existing packages (`domain` free of Android; UI thin; safety in `obd.safety`).
