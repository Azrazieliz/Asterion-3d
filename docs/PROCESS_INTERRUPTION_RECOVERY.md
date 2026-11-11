# Process-interruption recovery — 2026-11-11

When the user reopens Foundry after its process was terminated unexpectedly,
startup marks any `running` jobs as interrupted and **only** requeues jobs with
that exact interruption marker. Deliberate pauses, resource/thermal pauses,
service timeouts and failed jobs remain paused or failed. Resumed jobs keep
their job ID, request, original source files and existing stage directory.

Before reusing its local checkpoint, the production service verifies all
recorded stage outputs against the persisted SHA-256 stage ledger. Tampered
evidence blocks the job rather than being silently treated as approved. Old
editable projects with no stage ledger retain their existing load path.

This is foreground-app-reentry recovery, not automatic background launch after
Android reboot. It does not bypass Android foreground-service start restrictions
or claim to recover unfinished sub-stages within third-party inference. The
existing geometry/surface/export stages are the durable boundaries.

Verification evidence must distinguish host/Android-source compilation,
actual forced-process-exit Android tests, and visual/production quality.
