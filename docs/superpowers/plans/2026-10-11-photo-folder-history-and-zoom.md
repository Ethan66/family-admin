# Photo Folder History and Gallery Zoom Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:test-driven-development. Execute inline in the current checkout; the desktop directory is untracked, so do not create a new worktree or commit without user authorization.

**Goal:** Add back/forward folder navigation, the current folder name, and thumbnail zoom to the photo gallery's slim top controls.

**Architecture:** Keep folder history and zoom as local UI state in `ResourceLibraryView.vue`. Reuse the existing directory-selection function for the tree and navigation buttons. Bind a CSS custom property to the gallery grid so the slider changes card size without changing source files, previews, or persisted metadata.

**Tech Stack:** Vue 3, TypeScript, Element Plus icons, Vitest.

---

### Task 1: Folder browsing history

**Files:** `desktop/src/components/ResourceLibraryView.vue`, `desktop/src/test/resource-library-photo.spec.ts`

- [x] Add a test: select `D:/photos/trip`, select `D:/photos/trip2`, go back to `trip`, go forward to `trip2`; verify the displayed folder name and disabled edge buttons.
- [x] Run `vitest` for the photo test file and confirm the new test fails because the controls do not exist.
- [x] Track history as paths plus a current index. Tree selections truncate forward history; back/forward selections reuse the existing selection reset but do not append history. Reset history on family or resource-kind switch; discard removed directories after rescan.
- [x] Run the photo test file and confirm the test passes.

### Task 2: Thumbnail size controls

**Files:** `desktop/src/components/ResourceLibraryView.vue`, `desktop/src/test/resource-library-photo.spec.ts`

- [x] Add a test: minus, plus, and range input update the gallery-only thumbnail size within fixed minimum and maximum bounds.
- [x] Run the focused test and confirm it fails because zoom controls do not exist.
- [x] Add the compact controls and bind a CSS variable to the photo grid's minimum card width. Leave the inspector thumbnail and large preview unchanged.
- [x] Run the focused test and `npm run typecheck`; inspect the modified source and report that desktop packaging was not run.
