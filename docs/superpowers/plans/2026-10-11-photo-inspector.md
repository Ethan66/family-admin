# Photo Inspector Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver the approved three-pane photo workspace with click selection, double-click preview, metadata categories/rating, and family switch in compact navigation.

**Architecture:** Preserve existing Tauri resource API and family-scoped JSON index. Add backward-compatible photo fields in Rust and TypeScript; adapt the existing Vue photo view and AppShell only. Photo folder tree and batch actions remain intact.

**Tech Stack:** Vue 3, TypeScript, Element Plus, Vitest, Tauri 2, Rust.

---

### Task 1: Extend photo metadata

**Files:** `desktop/src-tauri/src/index_store.rs`, `desktop/src/test/index_store_tests.rs`, `desktop/src/types/resource.ts`, `desktop/src/test/resource-library-photo.spec.ts`.

- [ ] Add Rust test: old `PhotoMetadata` JSON without `categories` and `rating` loads as empty and `None`; invalid ratings are rejected; valid fields serialize.
- [ ] Run `cargo test --lib` and confirm the new test fails before implementation.
- [ ] Add `#[serde(default)] categories: Vec<String>` and `#[serde(default)] rating: Option<u8>` to `PhotoMetadata`; validate score `<= 5`, nonblank and unique category labels; mirror exact fields in `PhotoDetails`.
- [ ] Run `cargo test --lib` and confirm all Rust tests pass.

### Task 2: Global navigation

**Files:** `desktop/src/layouts/AppShell.vue`, `desktop/src/test/app-shell-navigation.spec.ts`.

- [ ] Add test that topbar has no global search/family select, while the super-admin sidebar has a family button/popover with current selection; ordinary users retain six navigation links.
- [ ] Run `vitest run src/test/app-shell-navigation.spec.ts` and confirm test failure.
- [ ] Remove the global `provide` search UI, move the super-admin selector into a compact sidebar popover, preserve filterable family options and `handleFamilyChange`.
- [ ] Run focused navigation test and confirm pass.

### Task 3: Photo interaction and inspector

**Files:** `desktop/src/components/ResourceLibraryView.vue`, `desktop/src/test/resource-library-photo.spec.ts`.

- [ ] Replace checkbox-focused tests with tests for single-click toggle, delayed double-click preview, selected outline, last-selected inspector, directory/family/deletion cleanup, and metadata save payload including categories and rating.
- [ ] Run focused photo tests and confirm expected failures.
- [ ] Introduce selected-photo inspector state; separate click/double-click via a cancellable short timer; keep batch selection controls. Replace photo edit dialog with fixed right panel, using the existing thumbnail and save API. Keep movie/exam dialogs untouched.
- [ ] Apply the approved three-column layout, dense photo grid and subtle selected state without changing the global background color.
- [ ] Run focused photo tests and confirm pass.

### Task 4: Verification and handoff

- [ ] Run `cargo fmt --all -- --check` and `cargo test --lib` in `desktop/src-tauri`.
- [ ] Run desktop frontend focused tests, `vue-tsc -b`/`npm run build`, and report exact results.
- [ ] Review changed files for accidental file moves, family isolation regressions, and unrelated edits; do not commit, push, or package without request.
