# 照片移动下拉面板 Implementation Plan

> **For agentic workers:** Inline execution in the existing checkout; this user explicitly chose direct workspace development. Do not commit or package without a separate request.

**Goal:** Replace the photo-move dialog with button-adjacent searchable menus for batch and single-photo moves.

**Architecture:** Keep the existing Tauri `move_photos` command and parent view's success/error/index-update flow. Add a focused `PhotoMoveDropdown.vue` component that owns menu visibility, search, Enter behavior, and create-parent selection; instantiate it at the two existing entry points.

**Tech Stack:** Vue 3, TypeScript, Element Plus icons, Vitest.

---

### Task 1: Search and keyboard behavior

**Files:** Create `desktop/src/components/PhotoMoveDropdown.vue`; create `desktop/src/test/photo-move-dropdown.spec.ts`.

- [x] Add component tests for clicking an existing folder, filtering by typed name, Enter selecting the first match, Enter creating on zero matches, and outside/Escape close.
- [x] Run `npm run test -- src/test/photo-move-dropdown.spec.ts` and confirm the new tests fail because the component does not exist.
- [x] Implement a button-anchored menu with local `visible`, `query`, `filteredDirectories`, and `createParentPath`. Call the supplied async `moveAction(resourceIds, destinationParent, newFolderName)`; close only when it returns `true`.
- [x] Re-run the focused test to green.

### Task 2: Connect both photo entry points

**Files:** Modify `desktop/src/components/ResourceLibraryView.vue`; modify `desktop/src/test/resource-library-photo.spec.ts`.

- [x] Replace dialog-based tests with page tests for batch existing-folder move, single-photo Enter-create move, and failed move keeping selection/menu open.
- [x] Run `npm run test -- src/test/resource-library-photo.spec.ts` and confirm the updated tests fail on the current dialog.
- [x] Replace the old dialog/state with two `photo-move-dropdown` instances sharing the parent `handleMovePhotos` action. Preserve family checks, JSON-index refresh, busy guards, and error feedback.
- [x] Re-run focused tests and `npm run typecheck`.

### Task 3: Final verification

**Files:** No new files.

- [x] Check both entry points, no remaining photo-move dialog, and updated styles at narrow/desktop widths by static review.
- [x] Run the two focused test files and typecheck. Do not run Rust tests or desktop packaging; backend code is unchanged.
