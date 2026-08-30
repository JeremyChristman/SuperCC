# 0009 — The error log is always on, and is not a setting

**Status:** Accepted (jc-11, 2026-08-29) · **Carves out an exception to** ADR 0005 · **Applies to:** `java/io/ErrorLog.java`

## Context

SuperCC is launched by double-clicking the jar, which runs it under `javaw` — and `javaw` discards
stderr entirely. So all 27 `printStackTrace` calls in the program, and every uncaught exception,
went nowhere at all. A user reporting "it just closed" had nothing to send, and whoever tried to fix
it had nothing to read. For a project whose whole debugging culture is measurement, that was the
largest hole in it.

jc-11 tees `System.err` to a log file, which captures every existing call site without editing any
of them.

ADR 0005 says every opt-in switch goes through the single `optedIn()` predicate and defaults **off**,
so that downloaders get stock behavior. Read literally, this feature should be a setting that
defaults off.

## Decision

**The error log is always on and has no setting.**

An opt-in crash log is close to useless. To benefit from it you would have to already know the
program was crashing, find the setting, turn it on, and reproduce the crash — at which point you no
longer need the log. The one case it exists for is the crash that already happened, on a machine
that is not yours, to someone who will not reproduce it on request.

Two properties make "always on" acceptable against ADR 0005's intent:

- **It creates no file unless something is actually written.** The log opens lazily on the first
  byte. A clean launch leaves nothing behind — verified: a jc-11 launch produces an empty stderr and
  no log file. That is only true because jc-11 also fixed the startup `NullPointerException` that
  used to fire on every launch (see below).
- **It is bounded.** 512 KB, rotating once to `succ_error-<machine>.prev.log`, counted per write
  rather than checked at startup, because a repaint loop throwing every frame can produce megabytes
  in seconds.

## Consequences

- The four-file release zip is unchanged; the log is a runtime artifact, not a shipped file. README
  section 5 documents it so nobody is surprised by a file they did not download.
- **The startup NPE had to be fixed first.** `Gui.repaint(boolean)` dereferences `getSavestates()`
  unguarded, so every launch with no level open threw. Harmless and invisible while stderr went
  nowhere — but with a log, an error on every single launch would train everyone to ignore the
  artifact. `Gui` is form-based and cannot be recompiled here, so the fix is at the only reachable
  end: the startup repaint call site in `java/emulator/SuperCC.java`, which now passes through the
  full repaint only when a level is actually loaded.
- **The file name carries the machine name.** The Chip's Challenge folder is Dropbox-synced between
  two PCs (ADR 0004). A single shared log would be appended by both and come back as a conflicted
  copy; worse, rotation cannot work while another process holds the file, because Windows refuses to
  move an open file. One file per machine removes both problems without a special case.
- **`ErrorLog` must never throw and must never print.** It sits underneath `System.err`: an
  exception escaping it surfaces inside code that was merely reporting a problem, and anything it
  prints recurses into itself. Every path catches `Throwable`, and a failed open disables it
  permanently rather than retrying per write.
- **The console keeps its stack traces.** On Java 9+, AWT routes EDT exceptions to the default
  uncaught-exception handler, and `ThreadGroup` prints to `System.err` *only when no handler is
  installed*. Installing one would therefore have made jc-11 quieter than jc-10. The handler prints
  through `System.err` — which is the tee — so the console keeps what it had and the file is written
  exactly once.
- If a future maintainer wants this switchable, that is a new decision that supersedes this record.
  Do not add it to `optedIn()` on the strength of ADR 0005 alone; ADR 0005 is about *features*, and
  this is diagnostics.
