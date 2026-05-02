# Bump Version

You are tasked with bumping the app version for both Android and iOS, and updating the changelog with all changes since the last version.

## Process:

### 1. Pre-flight Checks

Before starting, verify:
```bash
# Check current branch (should be develop)
git branch --show-current

# Check for uncommitted changes
git status --porcelain
```

- If not on `develop`, warn the user and ask if they want to proceed
- If there are uncommitted changes, stop and ask the user to commit or stash them first

### 2. Get Current Version Information

#### Android Version
Read from `composeApp/build.gradle.kts`:
- Find `versionCode = X` (integer, increment by 1)
- Find `versionName = "X.Y.Z"` (semantic version)

#### iOS Version
Read from `iosApp/iosApp.xcodeproj/project.pbxproj`:
- Find `MARKETING_VERSION = X.Y.Z;` (appears multiple times)
- Find `CURRENT_PROJECT_VERSION = N;` (iOS build number, appears multiple times)

**Note**: iOS and Android build numbers may differ. Both should be incremented by 1.

### 3. Find Last Version Bump Commit

Find the last version bump to determine what changes to include in the changelog:

```bash
# Find the commit hash of the last version bump
git log --oneline --grep="chore: bump version" -1 --format="%H"
```

If no previous bump commit exists (first release), use the initial commit or ask the user for a starting point.

### 4. Gather Changes Since Last Version

```bash
# Get all commits since last bump (excluding the bump commit itself)
git log <last-bump-hash>..HEAD --oneline --no-merges
```

### 5. Categorize Changes

Parse commit messages and group by type (conventional commits):
- **Features** (`feat:`) - New functionality
- **Bug Fixes** (`fix:`) - Bug fixes
- **Refactoring** (`refactor:`) - Code changes that neither fix bugs nor add features
- **Performance** (`perf:`) - Performance improvements
- **Chores** (`chore:`) - Build process, dependencies (exclude "bump version" commits)
- **Documentation** (`docs:`) - Documentation changes
- **Other** - Commits not following conventional format

### 6. Ask User for Version Bump Type

Present the categorized changes and ask:

Options:
- **Patch** (X.Y.Z -> X.Y.Z+1): Bug fixes, small changes
- **Minor** (X.Y.Z -> X.Y+1.0): New features, backward compatible
- **Major** (X.Y.Z -> X+1.0.0): Breaking changes

Recommend based on changes:
- If only `fix:` and `chore:` commits -> recommend Patch
- If any `feat:` commits -> recommend Minor
- If breaking changes mentioned -> recommend Major

### 7. Calculate New Version

Based on user selection:
- **Patch**: Increment Z (2.7.0 -> 2.7.1)
- **Minor**: Increment Y, reset Z (2.7.0 -> 2.8.0)
- **Major**: Increment X, reset Y and Z (2.7.0 -> 3.0.0)

Increment both `versionCode` (Android) and `CURRENT_PROJECT_VERSION` (iOS) by 1.

### 8. Update CHANGELOG.md

Create or update `CHANGELOG.md` in the project root:

```markdown
# Changelog

All notable changes to this project will be documented in this file.

## [X.Y.Z] - YYYY-MM-DD

### Features
- Feature description (commit_hash)

### Bug Fixes
- Fix description (commit_hash)

### Refactoring
- Refactor description (commit_hash)

---

## [Previous.Version] - Previous-Date
...
```

Rules:
- Use today's date in YYYY-MM-DD format
- Only include sections that have entries (skip empty categories)
- Clean up commit messages (remove `feat:`, `fix:` prefixes, capitalize first letter)
- Include short commit hash in parentheses
- If CHANGELOG.md exists, prepend the new version section after the header
- Exclude "bump version" commits from the changelog

### 9. Update Version Files

#### Android: Edit `composeApp/build.gradle.kts`
- Update `versionCode = <new_code>`
- Update `versionName = "<new_version>"`

#### iOS: Edit `iosApp/iosApp.xcodeproj/project.pbxproj`
- Update ALL occurrences of `MARKETING_VERSION = <new_version>;`
- Update ALL occurrences of `CURRENT_PROJECT_VERSION = <new_ios_build>;`

**Important for iOS**: Use `replace_all` to update all occurrences (DebugPROD, ReleasePROD, DebugTST, ReleaseTST configurations).

### 10. Commit and Push

After updating all files:

```bash
# Stage all version-related changes
git add composeApp/build.gradle.kts iosApp/iosApp.xcodeproj/project.pbxproj CHANGELOG.md

# Commit with standard message
git commit -m "chore: bump version to X.Y.Z"

# Push to develop
git push origin develop
```

### 11. Create Pull Request

Create a PR from develop to main using GitHub CLI:

```bash
gh pr create --base main --head develop --title "Release vX.Y.Z" --body "$(cat <<'EOF'
## Release vX.Y.Z

### Summary
Version bump from A.B.C to X.Y.Z

### Changes
[Copy the changelog section for this version here]

### Checklist
- [ ] Version numbers updated (Android & iOS)
- [ ] CHANGELOG.md updated
- [ ] Ready for release
EOF
)"
```

The PR body should include:
- The version being released
- A summary of changes (can be copied from CHANGELOG.md)
- The changelog section for this version

### 12. Final Summary

Show the user:
```
Version bump complete!

Changes:
- Android: X.Y.Z (code N) -> A.B.C (code N+1)
- iOS: X.Y.Z (build M) -> A.B.C (build M+1)

Files updated and pushed to develop:
- composeApp/build.gradle.kts
- iosApp/iosApp.xcodeproj/project.pbxproj
- CHANGELOG.md

PR created: [PR URL]
```

## Important Notes:

- **NEVER add co-author information or Claude attribution to commits**
- Preserve existing CHANGELOG.md content when updating
- The "chore: bump version to X.Y.Z" commit message format is required - it's used to find the last version for future changelog generation
- **iOS project.pbxproj**: Match exact format `MARKETING_VERSION = X.Y.Z;` (no quotes)
- Always push to the current branch (typically develop)

## Example Interaction:

**User**: /bump-version

**Assistant**:
"Current versions:
- Android: 2.7.0 (code 47)
- iOS: 2.7.0 (build 4)

**Changes since last version bump:**

**Features (2)**
- Add image preloading for similar listings tab
- Add safeLaunch coroutine extension for iOS crash prevention

**Bug Fixes (4)**
- Add missing space16 padding to profile grid
- Send list properties as native arrays to analytics
- Use dynamic padding for profile grid
- Prevent iOS crashes from uncaught coroutine exceptions

**Refactoring (1)**
- Unify filter tracking into single FiltersAppliedEvent

What type of version bump? (Recommended: **Minor** - new features added)
- Patch (2.7.1)
- Minor (2.8.0)
- Major (3.0.0)"

*User selects Minor*

**Assistant**:
"Updating to version 2.8.0...

✓ Updated composeApp/build.gradle.kts
✓ Updated iosApp/iosApp.xcodeproj/project.pbxproj
✓ Updated CHANGELOG.md
✓ Committed: chore: bump version to 2.8.0
✓ Pushed to develop
✓ Created PR: https://github.com/org/repo/pull/123

Done! Review and merge the PR when ready to release."
