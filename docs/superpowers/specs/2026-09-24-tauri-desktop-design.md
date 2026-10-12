# Family Admin Tauri Desktop Design

**Date:** 2026-09-24  
**Status:** Implemented; Windows 10 clean-machine acceptance pending

## Decision

Add an independent `desktop/` application using Tauri 2, Rust, Vue 3 and TypeScript. Keep the existing `server/` and `web/` source logic unchanged. The desktop renderer is rewritten independently and uses Element Plus with on-demand imports plus UnoCSS.

The existing Java service remains the authority for identity, tenant membership, families, transactions and audit data. Photos, movies, study notes and exam resources are local-only desktop modules backed by a per-family JSON index.

## Runtime Boundaries

- Vue calls the Java service with the existing JWT contract. The base URL comes from the build-time `VITE_API_BASE_URL` environment variable instead of a login-page field; development defaults to `http://localhost:8080`, while production uses an HTTPS URL.

## 2026-09-24 Service Address Change

- Remove the service-address field from the login form.
- Configure `VITE_API_BASE_URL` in desktop project environment files.
- Append `/api` centrally in the Axios client.
- Treat the configured URL as build-time application configuration; users cannot edit it from the desktop UI.
- Vue can call only explicit Tauri commands; it receives no unrestricted filesystem write permission.
- Rust selects directories, scans files, validates paths, updates JSON, reads bounded previews and asks Windows to open files.
- Local paths, index contents and source files are never uploaded to the Java service.
- The application does not copy, move, rename or delete source files in v1.

## Resource Index Lifecycle

Each family has `%APPDATA%/FamilyAdmin/families/<familyId>/resource-index.json`. Roots and scan results are divided into photos, movies, notes and exams. A rescan rebuilds only the selected resource type from the current disk state, then atomically replaces the JSON file. Removed paths disappear; moved paths get a new path-derived ID.

Scanning is recursive, case-insensitive by extension, does not follow directory symlinks/junctions, and continues after access-denied errors while reporting warnings. A malformed index is renamed as a timestamped corrupt backup before an empty index is created.

## Preview and Open Behavior

- Photos: preview inside the application.
- Movies: open through the Windows default application; no embedded player.
- Notes: render Markdown or show TXT read-only, with a 2 MB preview limit and no create/edit actions.
- Exams: preview supported images and PDF inside the application.

All access is revalidated in Rust against the roots registered for the current family. A missing file produces a rescan prompt instead of a crash.

## Windows Distribution

Build only Windows x64 NSIS with a per-user installation. Minimum supported OS is Windows 10 version 1803. Use Tauri's default WebView2 download bootstrapper rather than bundling the offline runtime. The installer acceptance target is at most 30 MB before any WebView2 download.

## Verification

The implementation must pass Vue lint/typecheck/tests, Rust format/clippy/tests, Tauri production build, installer-size inspection and a Windows 10 clean-machine smoke test. The final diff must show no application-code modifications under `server/` or `web/`.

## References

- [Tauri prerequisites](https://v2.tauri.app/start/prerequisites/)
- [Tauri Windows installer](https://v2.tauri.app/distribute/windows-installer/)
- [Tauri dialog plugin](https://v2.tauri.app/plugin/dialog/)
- [Tauri filesystem plugin](https://v2.tauri.app/plugin/file-system/)
- [Tauri opener plugin](https://v2.tauri.app/zh-cn/reference/javascript/opener/)
