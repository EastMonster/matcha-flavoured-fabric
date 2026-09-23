## Contribution Scope

`main` is the stable branch for the current released version. Contributor pull requests should target `main` and should be limited to:

- Bug fixes
- Compatibility fixes
- Small QoL improvements that are appropriate for the current release

New gameplay features, content, balance changes, large refactors, and other work intended for future versions should not target `main`. Bug fixes and small tweaks may be backported from future version when they are safe for the current release; I will make that decision.

`dev` contains ongoing version development. It is a maintainer-owned integration branch that is regularly rebased onto `main` and may be force-pushed. Please do not open pull requests against `dev` or base contributor branches on it.

If you have an idea for the future version, please open an issue first instead of submitting it directly to either branch.

## Development Setup

Install Node.js 22.22.1 or newer and pnpm 11.21 or newer, then install the development dependencies:

```sh
pnpm install
```

This also installs the Husky pre-commit hook. It runs Prettier on staged JSON files before each commit.
