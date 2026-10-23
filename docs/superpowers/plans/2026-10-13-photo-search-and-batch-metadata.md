# Photo Search and Batch Metadata Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move photo category/rating filters into the search area and reuse their toolbar slots for batch category/rating edits.

**Architecture:** Keep all UI state and batch orchestration in `ResourceLibraryView.vue`. Reuse the existing single-photo Tauri save API for each selected photo, refreshing the in-memory index after every success; do not change Rust, JSON schema, Web, or server code.

**Tech Stack:** Vue 3, TypeScript, Element Plus, Vitest, Tauri resource API.

---

## File map

- `desktop/src/components/ResourceLibraryView.vue`: search area, filter menus, batch action menus and save orchestration.
- `desktop/src/test/resource-library-photo.spec.ts`: component-level interaction and failure-path tests.
- `docs/superpowers/specs/2026-10-13-photo-search-and-batch-metadata-design.md`: approved behavior and acceptance criteria.

### Task 1: Place existing filters in the search area

- [x] Add a failing component test: before clicking search, the filter triggers are not visible; after clicking search, `.photo-search-panel` contains category trigger, rating trigger, and the search input in that DOM order. Verify the existing filter selections still change `photo-card` results and survive closing/reopening the panel.
- [x] Run `npm test -- --run src/test/resource-library-photo.spec.ts` from `desktop`; the new test failed because the two filters were still in the top toolbar.
- [x] Move the existing filter markup inside a conditional search panel and style it as a second row below the search icon, with category/rating on the left of the input. Keep `keyword`, `activePhotoCategory`, `activePhotoRating`, and existing handlers unchanged.
- [x] Run the same test; photo component tests passed.

### Task 2: Batch category append

- [x] Add failing tests for disabled state with zero selection, selecting an existing category, entering a new category, preserving each photo's original categories and other fields, and removing successfully saved IDs from selection. The shared batch loop's partial-failure path is tested with rating below.
- [x] Run the focused test command; tests failed because no batch category trigger or save handler existed.
- [x] Add a category popover in the former category-filter toolbar position. On apply, trim the chosen category and skip blank values; for each selected photo, derive existing details from the latest index or use the same default fields as `showPhotoInInspector`, then call `saveResourceMetadata` with the original fields and appended category. Update the index and selection on each success; leave failures selected and report both counts.
- [x] Run the focused test command; tests passed.

### Task 3: Batch rating overwrite and conflict handling

- [x] Add failing tests for disabled state with zero selection, applying stars, preserving categories/title/other metadata, partial failures, and disabled conflicting controls while a batch save is active.
- [x] Run the focused test command; tests failed because batch rating did not exist and folder history was not disabled during save.
- [x] Add a rating popover in the former rating-filter toolbar position. Reuse the batch save loop from Task 2, replacing only `rating` with the selected integer. Guard both operations against family/kind change and concurrent scan, move, delete, single save, or another batch save. Close menus on apply and keep failed photos selected.
- [x] Run the focused test command; tests passed.

### Task 4: Final focused verification

- [x] Run `npm test -- --run src/test/resource-library-photo.spec.ts` in `desktop`; 35 tests passed, including family-switch cancellation.
- [x] Run `npm run typecheck` in `desktop`; exit code 0.
- [x] Run `git diff --check -- desktop/src/components/ResourceLibraryView.vue desktop/src/test/resource-library-photo.spec.ts` at the workspace root; exit code 0.
- [x] Review the final diff against the approved spec, leaving unrelated pre-existing edits untouched. No commit, desktop package, or whole-project tests were run.
