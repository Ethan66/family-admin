# Photo Move Implementation Plan

> **For agentic workers:** Use test-driven-development; execute inline in the current checkout because the desktop source is untracked. Do not commit without user authorization.

**Goal:** Move one or multiple indexed photos into an existing or newly created child directory without overwriting files or losing JSON metadata.

**Architecture:** A Rust index-store operation validates and moves a batch, then atomically persists the updated index; it rolls file moves back on any failure. One Tauri command exposes the operation. The photo page shares a move dialog between the gallery selection and the right inspector.

**Tech Stack:** Rust/Tauri, Vue 3/TypeScript, Element Plus, Vitest.

---

### Task 1: Safe native move

**Files:** `desktop/src-tauri/src/index_store.rs`, `desktop/src-tauri/src/commands.rs`, `desktop/src-tauri/src/lib.rs`, `desktop/src/test/index_store_tests.rs`

- [ ] Add failing Rust tests for existing/new destination, metadata migration, name collision, out-of-root target, and JSON-save rollback.
- [ ] Run `cargo test --lib move_photos` and confirm the new tests fail before implementation.
- [ ] Implement validated, no-overwrite batch move and register the Tauri command.
- [ ] Run `cargo test --lib` and confirm all Rust tests pass.

### Task 2: Photo page integration

**Files:** `desktop/src/api/resources.ts`, `desktop/src/components/ResourceLibraryView.vue`, `desktop/src/test/resource-library-photo.spec.ts`, `desktop/src/test/resource-move.spec.ts`

- [ ] Add failing frontend tests for batch/single entry points, existing/new destination, and command arguments.
- [ ] Run focused Vitest and confirm expected failures.
- [ ] Add the shared move dialog, destination options, frontend API binding, success/error state, and updated selection handling.
- [ ] Run focused Vitest and `npm run typecheck`; do not package the desktop application.
