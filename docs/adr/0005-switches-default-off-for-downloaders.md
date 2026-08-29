# 0005 — Every opt-in switch defaults to off

**Status:** Accepted (jc-8, 2026-08-14) · **Applies to:** `SuccPaths.optedIn()` and every setting routed through it

## Context

This fork adds settings that exist for the maintainer's own workflow. The first was `ShowBuildTag`,
which puts `[jc-N]` in the window title so the running build is identifiable during development. It
originally defaulted **on**, with the reasoning that a typo should not silently disable it.

That reasoning is right for the maintainer and wrong for everyone else: the release is a public
download, and a stranger's title bar should not carry a private build number. `AlwaysOpenInMS` (jc-9)
raised the same question, and forcing a ruleset by default would be a far worse surprise.

Each switch had also been parsing its own value, which is how a family of subtly different truthiness
rules gets started.

## Decision

**One predicate, `optedIn()`, decides every switch**, and it is strict: only `true` (any casing) or
`1` turn a switch on. An absent key, an absent file, a typo, `yes`, `on`, and any garbage all mean
**off**. Any future switch goes through it.

`ShowBuildTag` is emitted through its *getter*, not the raw map, so an older settings file cannot
write the literal text `null` into it.

## Consequences

- A downloader gets stock behavior from a stock file. Nothing surprising is on by default.
- **A typo now silently disables the feature** rather than silently enabling it. That is the correct
  direction for a public build, and it is why the four `succ_settings.ini` parser traps in
  `CLAUDE.md` §5 matter so much: `ShowBuildTag=false` without the space parses as a different key
  entirely, and under this ADR the result is "off", which is at least the safe answer.
- The build tag cannot be used to confirm which build is running when it is off. Verify a build by
  the jar's SHA-256, or with `javap -p -constants -classpath SuperCC.jar emulator.SuperCC` —
  `BUILD_TAG` is still in the class file either way.
- The toggle is account-scoped, not machine-scoped, because the settings file is synced. If
  per-machine ever matters, the fix is a `-D` JVM property read in preference to the key.
