# Reset Bump Version Boundary

This command resets the bump version boundary so that commits already on `develop` won't be included in the next version bump changelog. It first records those commits in the changelog so they aren't lost.

## Steps

1. Find the current version from the last bump commit:
```bash
git log --oneline --grep="chore: bump version" -1
```

2. Show commits that would currently be included in the next bump:
```bash
git log <last-bump-hash>..HEAD --oneline --no-merges
```

3. If there are no commits after the boundary, inform the user there's nothing to reset and stop.

4. **Update CHANGELOG.md** with the commits being excluded. Follow the same changelog format used by `/bump-version`:
   - Categorize commits by type (`feat:`, `fix:`, `refactor:`, `perf:`, `chore:`, `docs:`, other)
   - Append entries under the **existing** current version section in `CHANGELOG.md` (do not create a new version heading — these changes belong to the current version)
   - If the current version section doesn't exist yet, create it with today's date
   - Exclude "bump version" commits
   - Clean up commit messages (remove type prefixes, capitalize first letter)
   - Include short commit hash in parentheses

5. Stage and commit the changelog update:
```bash
git add CHANGELOG.md
git commit --allow-empty -m "chore: bump version to <current-version>"
```
Use the version from the last bump commit message (e.g., `2.14.0`). The `--allow-empty` flag ensures this works even if the changelog had no meaningful additions.

**Note**: Do NOT add co-author information or Claude attribution to this commit.

6. Verify the boundary is reset — no commits should appear:
```bash
git log <new-bump-hash>..HEAD --oneline --no-merges
```

7. Inform the user:
   - How many commits were recorded in the changelog and excluded from the next bump
   - That CHANGELOG.md was updated under the current version section
