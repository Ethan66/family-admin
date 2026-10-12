# Photo Directory Tree and Compact Navigation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Show every scanned photo subfolder in a collapsible tree and make the authenticated global navigation icon-only with hover labels.

**Architecture:** The Rust scan records directories, including empty ones, in the current family's JSON resource index. A photo-specific Vue tree consumes that snapshot, filters existing resource entries by path, and leaves the file system unchanged. The global shell collapses visually while retaining all existing routes and account actions.

**Tech Stack:** Tauri 2/Rust, Vue 3/TypeScript, Element Plus, Vitest, Cargo tests.

---

## File map

- `desktop/src-tauri/src/scanner.rs`: gather every visited photo directory.
- `desktop/src-tauri/src/index_store.rs`: persist photo directory paths with a backward-compatible default.
- `desktop/src/test/scanner_tests.rs`, `desktop/src/test/index_store_tests.rs`: Rust scan/index tests.
- `desktop/src/types/resource.ts`: TypeScript index contract.
- `desktop/src/utils/photoDirectory.ts`: path-safe tree construction and descendant filtering.
- `desktop/src/components/PhotoDirectoryTree.vue`: directory navigation UI.
- `desktop/src/components/ResourceLibraryView.vue`: photo-page composition and active directory state.
- `desktop/src/layouts/AppShell.vue`: compact global navigation and accessible hover labels.
- `desktop/src/test/photo-directory.spec.ts`, `desktop/src/test/resource-library-photo.spec.ts`, `desktop/src/test/app-shell-navigation.spec.ts`: frontend behavior tests, all under `desktop/src/test`.

### Task 1: Record directories during photo scans

- [ ] **Step 1: Write failing Rust scan tests** in `desktop/src/test/scanner_tests.rs` for a root with `empty/` and `nested/child/`: `scan_roots(ResourceKind::Photo, ...)` must include all three canonical paths in `result.directories`, even without supported files; a symlink directory must not be included.
- [ ] **Step 2: Run RED** with `cargo test --lib scanner_tests -- --nocapture` from `desktop/src-tauri`; expect the missing `directories` field assertion to fail.
- [ ] **Step 3: Implement scanner storage**: add `pub directories: Vec<PathBuf>` to `ScanResult`, initialize it empty, push the canonical root and each visited real directory for photo scans only, then sort/deduplicate before returning. Keep current symlink-skip behavior.
- [ ] **Step 4: Run GREEN** with the same Cargo command; expect the directory test and existing scanner tests to pass.

### Task 2: Persist directories in family index

- [ ] **Step 1: Write failing index tests** in `desktop/src/test/index_store_tests.rs`: a photo scan assigns `photo_directories`, a movie scan preserves them, and old JSON without `photoDirectories` loads as an empty vector. Update existing `ScanResult` literals to include `directories: vec![]`.
- [ ] **Step 2: Run RED** with `cargo test --lib index_store_tests -- --nocapture`; expect missing index field/assignment failures.
- [ ] **Step 3: Implement index field** in `desktop/src-tauri/src/index_store.rs`:

  ```rust
  #[serde(default)]
  pub photo_directories: Vec<PathBuf>,
  ```

  Initialize it in `ResourceIndex::empty`, and in `merge_scan_result` assign `result.directories` only for `ResourceKind::Photo`. Add `photoDirectories: string[]` to `desktop/src/types/resource.ts` and update typed test fixtures.
- [ ] **Step 4: Run GREEN** with `cargo test --lib index_store_tests -- --nocapture`.

### Task 3: Build path-safe tree and filtering

- [ ] **Step 1: Write failing Vitest** in `desktop/src/test/photo-directory.spec.ts`: given two roots and paths such as `C:\\photos\\trip` and `C:\\photos\\trip2`, verify separate nodes, empty-folder visibility, descendant counts, and that selecting `trip` never includes `trip2`. Verify an old index with no directory list can reconstruct non-empty ancestors from photo paths.
- [ ] **Step 2: Run RED** with `npx vitest run src/test/photo-directory.spec.ts --maxWorkers=1` from `desktop`; expect missing module/export failures.
- [ ] **Step 3: Implement** `desktop/src/utils/photoDirectory.ts` with exported `buildPhotoDirectoryTree(index: ResourceIndex): PhotoDirectoryNode[]` and `isPhotoInsideDirectory(photoPath: string, directoryPath: string): boolean`. Normalize Windows separators and case, compare the next path separator as a boundary, include every registered root, merge persisted folders with ancestors inferred from photo paths, and calculate inclusive descendant photo counts.
- [ ] **Step 4: Run GREEN** with the same Vitest command.

### Task 4: Wire photo-page directory tree

- [ ] **Step 1: Write failing component tests** in `desktop/src/test/resource-library-photo.spec.ts`: tree shows root/nested/empty directories, clicking a parent filters its descendants, “全部照片” restores all, clicking a folder clears selected photo IDs, changing family clears directory selection, and rescan removes a vanished selection.
- [ ] **Step 2: Run RED** with `npx vitest run src/test/resource-library-photo.spec.ts --maxWorkers=1`; expect missing tree/interaction assertions to fail.
- [ ] **Step 3: Implement** `desktop/src/components/PhotoDirectoryTree.vue` using Element Plus tree with path node keys, separate expand and select behavior, tooltip path, and count label. In `ResourceLibraryView.vue`, add `activePhotoDirectory` state, compose it with existing search filtering, remove photo album chips, reset selection on folder change, and place the tree beside the existing card. Preserve non-photo views and all file mutation methods unchanged.
- [ ] **Step 4: Run GREEN** with the same Vitest command. Check that a scan error leaves the old index/tree visible.

### Task 5: Collapse authenticated navigation

- [ ] **Step 1: Write failing component test** in `desktop/src/test/app-shell-navigation.spec.ts`: navigation routes retain icon-only links with `title` and `aria-label`, active route indication, a compact account control, and accessible password/logout actions.
- [ ] **Step 2: Run RED** with `npx vitest run src/test/app-shell-navigation.spec.ts --maxWorkers=1`.
- [ ] **Step 3: Implement** in `desktop/src/layouts/AppShell.vue`: narrow the sidebar, visually hide menu text, add native hover/focus labels to links, keep brand/account actions reachable through compact icons, and retain existing route and family-switching logic. Do not alter the login page.
- [ ] **Step 4: Run GREEN** with the same Vitest command.

### Task 6: Verify and review

- [ ] Run `cargo test --lib` from `desktop/src-tauri`; confirm all Rust tests pass.
- [ ] Run `npx vitest run src/test/photo-directory.spec.ts src/test/resource-library-photo.spec.ts src/test/app-shell-navigation.spec.ts --maxWorkers=1` from `desktop`; confirm all affected frontend tests pass.
- [ ] Run `npm run typecheck` and `npm run build` from `desktop`; check exit codes and output.
- [ ] Inspect the photo page and compact sidebar for keyboard focus, clipped labels, selected state, and narrow desktop widths if static/test evidence does not establish the layout.
- [ ] Review changed files and `git diff --check`; do not commit, push, package the desktop app, or move photos without a separate request.
