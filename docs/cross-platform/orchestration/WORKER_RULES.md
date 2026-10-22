# Rules for opencode workers (read every time)

1. **Scope:** change only what your task brief lists. If something outside the scope must change, stop and write it in the Worker log as a question instead of doing it.
2. **No git:** do not run git commands (no commit, stash, checkout, reset). The orchestrator reviews and commits.
3. **Codegraph first:** before editing a symbol, use the `codegraph_explore` MCP tool (or `codegraph impact <symbol>` / `codegraph callers <symbol>` in the shell) to see its callers. Every caller must keep compiling.
4. **Move, don't rewrite:** when moving code to `shared/` or `ui/`, keep behaviour, names and structure. Only change what the target platform requires (imports, `R.string.x` → `Res.string.x`, Android-only APIs behind an interface or `expect`/`actual`).
5. **Android must not change for users:** same screens, texts, colours, behaviour.
6. **Verify locally** with the commands in your brief. Typical ones:
   - `./gradlew :shared:compileKotlinJvm :ui:compileKotlinJvm -q` (common code, compiled for JVM; works on this machine)
   - `cd tools/sync-harness && ../../gradlew -p . test -q` (sync tests)
   - Android (`:app`) and iOS can NOT be built on this machine (no Android build-tools, no Xcode). Don't try to install them. The orchestrator runs CI.
7. **No new dependencies** unless your brief names them (with version).
8. **Disk is tight (~1.5 GB free):** don't download SDKs or large tools; don't create big files.
9. **Style:** Kotlin, 4-space indent, trailing commas, match the surrounding code (the project uses ktlint via kotlinter). Comments only for a non-obvious "why".
10. **Finish:** append to the brief's `## Worker log`: date, model, files changed, the verification commands + results, open questions. Be honest: if something fails or is untested, say so.
