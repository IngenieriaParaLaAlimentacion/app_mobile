# Contributing

## Development setup

Follow the [installation and configuration in the README](README.md#installation). There are no Git hooks or linters to install.

Before opening a PR, check that the code compiles and the tests pass:

```bash
# Quick type check (commonMain)
./scripts/compile.sh

# Tests (the same ones CI runs)
./gradlew :composeApp:testProdDebugUnitTest -PtargetEnvironment=prod --no-daemon
```

The `commonMain` check does not replace an Android or iOS build. If you changed platform code, also build the matching variant (see [Running](README.md#running)).

## Branching

We use two long-lived branches:

- `develop`: integration branch. Every working branch starts from `develop` and goes back into `develop` through a PR.
- `main`: release branch. It only receives PRs from `develop`. Never merge working branches into it or commit directly to `main`.

CI ([`pr-branch-policy.yml`](.github/workflows/pr-branch-policy.yml)) enforces this on every PR:

- PRs into `develop` must come from `feat/*`, `feature/*`, `fix/*`, `refactor/*`, `docs/*` or `chore/*`.
- PRs into `main` are only accepted from `develop`.

Examples: `feat/food-origin`, `fix/scroll-focused-inputs`, `docs/improve-docs`.

## Commits

We follow [Conventional Commits](https://www.conventionalcommits.org/). Scope is optional.

```
<type>(<scope>): <description>

feat(measurements): allow backdated date and time entry
fix: scroll focused inputs relative to form viewport
docs: refresh setup and technical guides
ci: run shared and Android unit tests on pull requests
```

Common types: `feat`, `fix`, `docs`, `refactor`, `ci`, `chore`.

## Pull Requests

- Target `develop`. Only releases go from `develop` to `main`.
- Keep PRs small and focused. If a task is large, split it into incremental PRs.
- Delete the remote branch after merging.

## Code conventions

Conventions for architecture, theme, icons, form validation and navigation are in [`CLAUDE.md`](CLAUDE.md). The key points:

- Clean Architecture with MVVM. Screens follow the `Action`, `Destination`, `Navigation`, `Screen`, `State` and `ViewModel` structure.
- Colors and components come from `core/design_system/`. Literal hex values only exist in `theme/Palette.kt`.
- Icons are MingCute (`ic_mingcute_<name>.xml`). Material icons are not used.
- UI text is in Spanish.

## CI

GitHub Actions runs [`ci.yml`](.github/workflows/ci.yml) on pull requests and on pushes to `develop` and `main`:

- `commonTest` and `androidUnitTest` tests on the Android JVM target.
- HTML and JUnit XML reports as artifacts, even when tests fail.

Other workflows:

- [`pr-branch-policy.yml`](.github/workflows/pr-branch-policy.yml) validates the source branch of every PR into `develop` or `main`.
- [`manual-pages.yml`](.github/workflows/manual-pages.yml) publishes the user manual to GitHub Pages.

## Tests

Tests run on the Android JVM target, with no emulator or backend credentials.

```bash
./gradlew :composeApp:testProdDebugUnitTest -PtargetEnvironment=prod --no-daemon
```

The suite does not run UI tests or tests on the iOS simulator.
