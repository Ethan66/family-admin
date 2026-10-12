# Family Admin MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a deployable multi-tenant family administration MVP with a Vue 3 frontend, Spring Boot backend, MySQL persistence, and tenant-protected local image storage.

**Architecture:** A single repository contains the independently runnable `web/` and `server/` applications. Spring Security establishes user, role, and tenant context before every business service call; MyBatis-Plus queries always include tenant ownership, and object-level checks protect updates, deletes, and files. The Vue application uses typed API modules, Pinia session state, route metadata, and feature pages matching the supplied prototype.

**Tech Stack:** Java 21, Spring Boot 3, Spring Security, MyBatis-Plus, Flyway, MySQL, JUnit 5, Mockito, Vue 3, TypeScript, Vite, Vue Router, Pinia, Axios, Element Plus, UnoCSS, Vitest, Vue Test Utils.

---

## File map

```text
server/
├─ pom.xml                                      dependency and build definition
├─ src/main/java/com/ethan/familyadmin/
│  ├─ FamilyAdminApplication.java               backend entry point
│  ├─ common/                                   response, exception, paging, audit base
│  ├─ security/                                 JWT, SecurityConfig, TenantContext
│  ├─ auth/                                     login and profile use cases
│  ├─ family/                                   family and account administration
│  ├─ transaction/                              finance records and summaries
│  ├─ exam/                                     exam records and summaries
│  ├─ dashboard/                                overview aggregation
│  ├─ file/                                     protected local file abstraction
│  ├─ photo/                                    photo metadata and upload
│  ├─ movie/                                    movie metadata and cover
│  ├─ note/                                     note query, preview, delete
│  └─ audit/                                    operation log query and writer
├─ src/main/resources/
│  ├─ application.yml                           environment bindings
│  └─ db/migration/V1__init_schema.sql          initial schema
└─ src/test/java/com/ethan/familyadmin/          unit and MVC tests mirroring modules

web/
├─ package.json                                 scripts and dependencies
├─ vite.config.ts                               Vite and test configuration
├─ uno.config.ts                                design tokens and shortcuts
├─ src/
│  ├─ api/                                      Axios client and typed feature clients
│  ├─ router/                                   route definitions and guards
│  ├─ stores/session.ts                         authenticated user and selected family
│  ├─ layouts/                                  family and admin shells
│  ├─ components/common/                        page state, paging, confirm helpers
│  ├─ views/auth/                               login
│  ├─ views/dashboard/                          family overview
│  ├─ views/finance/                            transaction list and form
│  ├─ views/exam/                               exam list and form
│  ├─ views/photo/                              photo grid and upload
│  ├─ views/movie/                              movie list and form
│  ├─ views/note/                               note list and preview
│  ├─ views/admin/                              family, accounts, audit, global view
│  └─ views/profile/                            profile and password
└─ src/**/*.spec.ts                             component, store, guard, and page tests

deploy/compose.yml                              local MySQL service
storage/.gitkeep                                empty persistent storage root
```

## Task 1: Repository hygiene and runnable application skeletons

**Files:**
- Create: `.gitignore`
- Create: `deploy/compose.yml`
- Create: `storage/.gitkeep`
- Create: `server/pom.xml`
- Create: `server/mvnw`, `server/mvnw.cmd`, `server/.mvn/wrapper/maven-wrapper.properties`
- Create: `server/src/main/java/com/ethan/familyadmin/FamilyAdminApplication.java`
- Create: `server/src/main/resources/application.yml`
- Create: `server/src/test/java/com/ethan/familyadmin/FamilyAdminApplicationTest.java`
- Create: `web/package.json`, `web/vite.config.ts`, `web/tsconfig*.json`, `web/index.html`
- Create: `web/src/main.ts`, `web/src/App.vue`, `web/src/App.spec.ts`

- [ ] **Step 1: Add ignore rules and local infrastructure**

```gitignore
.idea/
.vscode/
.codex/progress/
graphify-out/
web/node_modules/
web/dist/
server/target/
storage/*
!storage/.gitkeep
*.log
.env
.env.*
!.env.example
```

`deploy/compose.yml` must expose MySQL only to localhost, use database `family_admin`, and read credentials from `deploy/.env` rather than committing a password.

- [ ] **Step 2: Write backend and frontend smoke tests before implementation**

```java
@Test
void applicationClassCanBeLoaded() {
    assertThat(FamilyAdminApplication.class).isNotNull();
}
```

```ts
it('renders the application root', () => {
  expect(mount(App).get('[data-testid="app-root"]').exists()).toBe(true)
})
```

- [ ] **Step 3: Run both tests and confirm the missing projects fail**

Run: `cd server; .\mvnw.cmd test` and `cd web; npm test -- --run`  
Expected: both commands fail because the build files or application entries are not complete.

- [ ] **Step 4: Add the minimal Spring Boot and Vue/Vite entries**

The backend POM must include web, validation, security, MyBatis-Plus, MySQL, Flyway, JWT, Lombok, and test dependencies. The Vue entry must install Router, Pinia, Element Plus, UnoCSS, Axios, Vitest, and Vue Test Utils.

- [ ] **Step 5: Install and verify the skeletons**

Run: `cd server; .\mvnw.cmd test`  
Expected: one backend smoke test passes.

Run: `cd web; npm install; npm test -- --run; npm run typecheck; npm run build`  
Expected: one frontend smoke test passes and both checks exit with code 0.

- [ ] **Step 6: Commit the skeleton**

```powershell
git add .gitignore deploy storage server web
git commit -m "chore: 初始化前后端工程骨架"
```

## Task 2: Database schema and shared backend contracts

**Files:**
- Create: `server/src/main/resources/db/migration/V1__init_schema.sql`
- Create: `server/src/main/java/com/ethan/familyadmin/common/api/ApiResponse.java`
- Create: `server/src/main/java/com/ethan/familyadmin/common/api/PageResponse.java`
- Create: `server/src/main/java/com/ethan/familyadmin/common/exception/BusinessException.java`
- Create: `server/src/main/java/com/ethan/familyadmin/common/exception/GlobalExceptionHandler.java`
- Create: `server/src/main/java/com/ethan/familyadmin/common/model/BaseEntity.java`
- Test: `server/src/test/java/com/ethan/familyadmin/common/exception/GlobalExceptionHandlerTest.java`

- [ ] **Step 1: Write MVC tests for stable response codes**

```java
@Test
void validationFailureUsesStableEnvelope() throws Exception {
    mvc.perform(post("/test/validation").contentType(APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.message").isNotEmpty());
}
```

- [ ] **Step 2: Run the focused test and verify RED**

Run: `cd server; .\mvnw.cmd -Dtest=GlobalExceptionHandlerTest test`  
Expected: compilation fails because the shared response and handler do not exist.

- [ ] **Step 3: Implement shared contracts and migration**

`ApiResponse<T>` exposes exactly `code`, `message`, and `data`. `PageResponse<T>` exposes `records`, `total`, `page`, and `pageSize`. The migration creates all ten approved tables with `BIGINT` primary keys, business-table `family_id`, audit columns, `deleted` flag, foreign keys, unique login name, and composite tenant indexes.

- [ ] **Step 4: Verify Flyway syntax and handler tests**

Run: `cd server; .\mvnw.cmd -Dtest=GlobalExceptionHandlerTest test`  
Expected: all focused tests pass.

- [ ] **Step 5: Commit**

```powershell
git add server/src/main
git add server/src/test/java/com/ethan/familyadmin/common
git commit -m "feat: 建立数据库结构和统一响应"
```

## Task 3: Authentication, JWT, and tenant context

**Files:**
- Create: `server/src/main/java/com/ethan/familyadmin/security/CurrentUser.java`
- Create: `server/src/main/java/com/ethan/familyadmin/security/TenantContext.java`
- Create: `server/src/main/java/com/ethan/familyadmin/security/JwtService.java`
- Create: `server/src/main/java/com/ethan/familyadmin/security/JwtAuthenticationFilter.java`
- Create: `server/src/main/java/com/ethan/familyadmin/security/SecurityConfig.java`
- Create: `server/src/main/java/com/ethan/familyadmin/auth/AuthController.java`
- Create: `server/src/main/java/com/ethan/familyadmin/auth/AuthService.java`
- Create: `server/src/main/java/com/ethan/familyadmin/auth/dto/LoginRequest.java`
- Create: `server/src/main/java/com/ethan/familyadmin/auth/dto/LoginResponse.java`
- Create: `server/src/main/java/com/ethan/familyadmin/auth/dto/CurrentUserResponse.java`
- Create: `server/src/main/java/com/ethan/familyadmin/auth/dto/ChangePasswordRequest.java`
- Create: `server/src/main/java/com/ethan/familyadmin/family/UserEntity.java`
- Create: `server/src/main/java/com/ethan/familyadmin/family/FamilyEntity.java`
- Create: `server/src/main/java/com/ethan/familyadmin/family/FamilyMemberEntity.java`
- Create: corresponding mapper interfaces
- Test: `server/src/test/java/com/ethan/familyadmin/auth/AuthServiceTest.java`
- Test: `server/src/test/java/com/ethan/familyadmin/security/TenantContextTest.java`

- [ ] **Step 1: Write authentication and context tests**

```java
@Test
void familyUserContextComesFromMembershipNotRequestInput() {
    CurrentUser user = new CurrentUser(7L, 31L, Role.FAMILY_MEMBER, true);
    TenantContext.runAs(user, () -> assertThat(TenantContext.requiredFamilyId()).isEqualTo(31L));
}

@Test
void disabledUserCannotLogin() {
    assertThatThrownBy(() -> authService.login(new LoginRequest("disabled", "secret123")))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("账号或密码错误");
}
```

- [ ] **Step 2: Run focused tests and verify RED**

Run: `cd server; .\mvnw.cmd -Dtest=AuthServiceTest,TenantContextTest test`  
Expected: tests fail on missing authentication classes.

- [ ] **Step 3: Implement authentication and request context**

Use BCrypt for passwords. JWT claims contain user ID and token version, not a client-editable authority source; the filter reloads current status, membership, family, role, and token version from MySQL. `TenantContext` is request-scoped/thread-local and cleared in `finally`.

- [ ] **Step 4: Add controller security tests**

Verify anonymous requests return 401, role failures return 403, successful login returns a token, and password change increments token version so old tokens fail.

- [ ] **Step 5: Run security tests and commit**

Run: `cd server; .\mvnw.cmd -Dtest=AuthServiceTest,TenantContextTest,*SecurityTest test`  
Expected: all authentication and context tests pass.

```powershell
git add server/src/main/java/com/ethan/familyadmin/security server/src/main/java/com/ethan/familyadmin/auth server/src/main/java/com/ethan/familyadmin/family
git add server/src/test/java/com/ethan/familyadmin/security server/src/test/java/com/ethan/familyadmin/auth
git commit -m "feat: 实现登录认证和租户上下文"
```

## Task 4: Super-admin family and account management

**Files:**
- Create: `server/src/main/java/com/ethan/familyadmin/family/admin/AdminFamilyController.java`
- Create: `server/src/main/java/com/ethan/familyadmin/family/admin/AdminFamilyService.java`
- Create: `server/src/main/java/com/ethan/familyadmin/family/dto/*.java`
- Create: `server/src/main/java/com/ethan/familyadmin/audit/OperationLogEntity.java`
- Create: `server/src/main/java/com/ethan/familyadmin/audit/OperationLogMapper.java`
- Create: `server/src/main/java/com/ethan/familyadmin/audit/AuditService.java`
- Create: `server/src/main/java/com/ethan/familyadmin/audit/AdminAuditController.java`
- Test: `server/src/test/java/com/ethan/familyadmin/family/admin/AdminFamilyServiceTest.java`
- Test: `server/src/test/java/com/ethan/familyadmin/family/admin/AdminFamilySecurityTest.java`

- [ ] **Step 1: Write tests for family lifecycle and role boundaries**

```java
@Test
void familyAdminCannotCallSuperAdminApi() throws Exception {
    mvc.perform(get("/api/admin/families").with(jwtFor(Role.FAMILY_ADMIN, 10L)))
        .andExpect(status().isForbidden());
}

@Test
void disablingFamilyBlocksItsMembers() {
    adminFamilyService.changeStatus(familyId, false);
    assertThat(authService.canAuthenticate(memberId)).isFalse();
}
```

- [ ] **Step 2: Verify RED, implement CRUD and audit, verify GREEN**

Run before and after implementation: `cd server; .\mvnw.cmd -Dtest=AdminFamilyServiceTest,AdminFamilySecurityTest test`  
Expected before: missing classes. Expected after: tests pass.

Account creation must enforce one family per non-super user and globally unique login names. Role/status changes and cross-family reads call `AuditService`.

- [ ] **Step 3: Commit**

```powershell
git add server/src/main/java/com/ethan/familyadmin/family server/src/main/java/com/ethan/familyadmin/audit
git add server/src/test/java/com/ethan/familyadmin/family
git commit -m "feat: 实现家庭和成员账号管理"
```

## Task 5: Reusable tenant-owned CRUD policy

**Files:**
- Create: `server/src/main/java/com/ethan/familyadmin/security/TenantAccessGuard.java`
- Create: `server/src/main/java/com/ethan/familyadmin/common/model/TenantOwnedEntity.java`
- Test: `server/src/test/java/com/ethan/familyadmin/security/TenantAccessGuardTest.java`

- [ ] **Step 1: Write object-level authorization tests**

```java
@Test
void memberCanEditOnlyOwnRecord() {
    assertThat(guard.canWrite(memberUser, entityOwnedBy(memberUser.id()))).isTrue();
    assertThat(guard.canWrite(memberUser, entityOwnedBy(otherUser.id()))).isFalse();
    assertThat(guard.canWrite(familyAdmin, entityOwnedBy(otherUser.id()))).isTrue();
}
```

- [ ] **Step 2: Run RED, implement the guard, run GREEN**

Run: `cd server; .\mvnw.cmd -Dtest=TenantAccessGuardTest test`  
Expected: fail before implementation and pass after implementation.

- [ ] **Step 3: Commit**

```powershell
git add server/src/main/java/com/ethan/familyadmin/security server/src/main/java/com/ethan/familyadmin/common/model
git add server/src/test/java/com/ethan/familyadmin/security/TenantAccessGuardTest.java
git commit -m "feat: 建立租户对象级权限规则"
```

## Task 6: Finance module

**Files:**
- Create: `server/src/main/java/com/ethan/familyadmin/transaction/TransactionEntity.java`
- Create: `server/src/main/java/com/ethan/familyadmin/transaction/TransactionMapper.java`
- Create: `server/src/main/java/com/ethan/familyadmin/transaction/TransactionService.java`
- Create: `server/src/main/java/com/ethan/familyadmin/transaction/TransactionController.java`
- Create: `server/src/main/java/com/ethan/familyadmin/transaction/dto/*.java`
- Test: `server/src/test/java/com/ethan/familyadmin/transaction/TransactionServiceTest.java`
- Test: `server/src/test/java/com/ethan/familyadmin/transaction/TransactionSecurityTest.java`

- [ ] **Step 1: Write finance validation, filter, summary, and access tests**

```java
@Test
void summaryUsesBigDecimalAndSameFiltersAsList() {
    TransactionSummary summary = service.summary(new TransactionQuery("2026-09", EXPENSE, "餐饮"));
    assertThat(summary.expense()).isEqualByComparingTo("1210.00");
    assertThat(summary.income()).isEqualByComparingTo("0.00");
}
```

- [ ] **Step 2: Verify RED, implement entity-to-controller flow, verify GREEN**

Run: `cd server; .\mvnw.cmd -Dtest=TransactionServiceTest,TransactionSecurityTest test`  
Expected before: missing module. Expected after: all finance tests pass.

All mapper conditions include `family_id` and `deleted = 0`. Create ignores client-supplied owner fields. Update/delete loads the record and calls `TenantAccessGuard`.

- [ ] **Step 3: Commit**

```powershell
git add server/src/main/java/com/ethan/familyadmin/transaction
git add server/src/test/java/com/ethan/familyadmin/transaction
git commit -m "feat: 实现家庭收支管理"
```

## Task 7: Exam and dashboard modules

**Files:**
- Create: `server/src/main/java/com/ethan/familyadmin/exam/*.java`
- Create: `server/src/main/java/com/ethan/familyadmin/exam/dto/*.java`
- Create: `server/src/main/java/com/ethan/familyadmin/dashboard/DashboardController.java`
- Create: `server/src/main/java/com/ethan/familyadmin/dashboard/DashboardService.java`
- Create: `server/src/main/java/com/ethan/familyadmin/dashboard/DashboardResponse.java`
- Test: `server/src/test/java/com/ethan/familyadmin/exam/ExamServiceTest.java`
- Test: `server/src/test/java/com/ethan/familyadmin/dashboard/DashboardServiceTest.java`

- [ ] **Step 1: Write score and aggregation tests**

```java
@Test
void scoreCannotExceedFullScore() {
    assertThatThrownBy(() -> service.create(requestWithScores(101, 100)))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("得分不能超过满分");
}
```

- [ ] **Step 2: Run RED, implement exam CRUD and dashboard aggregation, run GREEN**

Run: `cd server; .\mvnw.cmd -Dtest=ExamServiceTest,DashboardServiceTest test`  
Expected before: missing module. Expected after: scores, weak-subject calculation, pending count, monthly finance, recent records, and content counts pass.

- [ ] **Step 3: Commit**

```powershell
git add server/src/main/java/com/ethan/familyadmin/exam server/src/main/java/com/ethan/familyadmin/dashboard
git add server/src/test/java/com/ethan/familyadmin/exam server/src/test/java/com/ethan/familyadmin/dashboard
git commit -m "feat: 实现试卷管理和家庭概览"
```

## Task 8: Protected file storage and photo module

**Files:**
- Create: `server/src/main/java/com/ethan/familyadmin/file/FileStorage.java`
- Create: `server/src/main/java/com/ethan/familyadmin/file/LocalFileStorage.java`
- Create: `server/src/main/java/com/ethan/familyadmin/file/FileObjectEntity.java`
- Create: `server/src/main/java/com/ethan/familyadmin/file/FileObjectMapper.java`
- Create: `server/src/main/java/com/ethan/familyadmin/file/FileController.java`
- Create: `server/src/main/java/com/ethan/familyadmin/photo/PhotoEntity.java`
- Create: `server/src/main/java/com/ethan/familyadmin/photo/PhotoMapper.java`
- Create: `server/src/main/java/com/ethan/familyadmin/photo/PhotoService.java`
- Create: `server/src/main/java/com/ethan/familyadmin/photo/PhotoController.java`
- Test: `server/src/test/java/com/ethan/familyadmin/file/LocalFileStorageTest.java`
- Test: `server/src/test/java/com/ethan/familyadmin/photo/PhotoSecurityTest.java`

- [ ] **Step 1: Write file type, path, and tenant tests**

```java
@Test
void rejectsExtensionContentMismatch() {
    MockMultipartFile fake = new MockMultipartFile("file", "photo.jpg", "image/jpeg", "not-an-image".getBytes(UTF_8));
    assertThatThrownBy(() -> storage.save(10L, fake)).isInstanceOf(BusinessException.class);
}
```

- [ ] **Step 2: Run RED, implement storage and photo flow, run GREEN**

Run: `cd server; .\mvnw.cmd -Dtest=LocalFileStorageTest,PhotoSecurityTest test`  
Expected before: missing storage. Expected after: JPEG/PNG/WebP acceptance, mismatch rejection, generated storage keys, and cross-tenant rejection pass.

- [ ] **Step 3: Commit**

```powershell
git add server/src/main/java/com/ethan/familyadmin/file server/src/main/java/com/ethan/familyadmin/photo
git add server/src/test/java/com/ethan/familyadmin/file server/src/test/java/com/ethan/familyadmin/photo
git commit -m "feat: 实现安全文件存储和照片管理"
```

## Task 9: Movie and read-only note modules

**Files:**
- Create: `server/src/main/java/com/ethan/familyadmin/movie/*.java`
- Create: `server/src/main/java/com/ethan/familyadmin/note/StudyNoteEntity.java`
- Create: `server/src/main/java/com/ethan/familyadmin/note/StudyNoteMapper.java`
- Create: `server/src/main/java/com/ethan/familyadmin/note/StudyNoteService.java`
- Create: `server/src/main/java/com/ethan/familyadmin/note/StudyNoteController.java`
- Test: `server/src/test/java/com/ethan/familyadmin/movie/MovieServiceTest.java`
- Test: `server/src/test/java/com/ethan/familyadmin/note/StudyNoteControllerTest.java`

- [ ] **Step 1: Write progress normalization and route-scope tests**

```java
@ParameterizedTest
@CsvSource({"WANT_TO_WATCH,0", "WATCHED,100"})
void normalizesProgress(MovieStatus status, int expected) {
    assertThat(service.normalizeProgress(status, 43)).isEqualTo(expected);
}
```

The note controller test asserts GET list/detail and DELETE exist, while POST and PUT return 405.

- [ ] **Step 2: Run RED, implement both modules, run GREEN**

Run: `cd server; .\mvnw.cmd -Dtest=MovieServiceTest,StudyNoteControllerTest test`  
Expected before: missing modules. Expected after: all tests pass.

- [ ] **Step 3: Commit**

```powershell
git add server/src/main/java/com/ethan/familyadmin/movie server/src/main/java/com/ethan/familyadmin/note
git add server/src/test/java/com/ethan/familyadmin/movie server/src/test/java/com/ethan/familyadmin/note
git commit -m "feat: 实现影片和学习笔记模块"
```

## Task 10: Typed frontend foundation, login, and layouts

**Files:**
- Create: `web/src/api/client.ts`, `web/src/api/types.ts`, `web/src/api/auth.ts`
- Create: `web/src/stores/session.ts`
- Create: `web/src/router/index.ts`
- Create: `web/src/layouts/FamilyLayout.vue`, `web/src/layouts/AdminLayout.vue`
- Create: `web/src/views/auth/LoginView.vue`
- Create: `web/src/components/common/PageState.vue`, `web/src/components/common/AppConfirm.vue`
- Create: `web/src/styles/main.css`
- Modify: `web/src/main.ts`, `web/src/App.vue`, `web/uno.config.ts`
- Test: `web/src/stores/session.spec.ts`, `web/src/router/router.spec.ts`, `web/src/views/auth/LoginView.spec.ts`

- [ ] **Step 1: Write session and route tests**

```ts
it('redirects an unauthenticated user to login', async () => {
  session.user = null
  await router.push('/dashboard')
  expect(router.currentRoute.value.name).toBe('login')
})
```

- [ ] **Step 2: Run tests and verify RED**

Run: `cd web; npm test -- --run src/stores/session.spec.ts src/router/router.spec.ts src/views/auth/LoginView.spec.ts`  
Expected: tests fail because the session, router, and view do not exist.

- [ ] **Step 3: Implement API interception, session hydration, guards, and shells**

The Axios client adds `Authorization: Bearer <token>`, clears session on 401, and converts the response envelope into typed data. Route metadata accepts `requiresAuth` and `roles`. Layouts reproduce the prototype navigation and mobile drawer without embedding business state.

- [ ] **Step 4: Run focused tests, typecheck, and commit**

Run: `cd web; npm test -- --run src/stores/session.spec.ts src/router/router.spec.ts src/views/auth/LoginView.spec.ts; npm run typecheck`  
Expected: focused tests pass and typecheck exits 0.

```powershell
git add web/src web/uno.config.ts
git commit -m "feat: 实现前端登录和权限框架"
```

## Task 11: Dashboard, finance, and exam frontend

**Files:**
- Create: `web/src/api/dashboard.ts`, `web/src/api/transaction.ts`, `web/src/api/exam.ts`
- Create: `web/src/views/dashboard/DashboardView.vue`
- Create: `web/src/views/finance/FinanceView.vue`, `web/src/views/finance/TransactionFormDialog.vue`
- Create: `web/src/views/exam/ExamView.vue`, `web/src/views/exam/ExamFormDialog.vue`
- Test: matching `*.spec.ts` files

- [ ] **Step 1: Write page tests for loading, empty, error, filters, validation, and permissions**

```ts
it('prevents an exam score greater than full score', async () => {
  const wrapper = mount(ExamFormDialog, { props: { modelValue: true } })
  await wrapper.get('[data-testid="full-score"]').setValue('100')
  await wrapper.get('[data-testid="score"]').setValue('101')
  await wrapper.get('[data-testid="submit"]').trigger('click')
  expect(wrapper.text()).toContain('得分不能超过满分')
})
```

- [ ] **Step 2: Verify RED, implement the three pages, verify GREEN**

Run: `cd web; npm test -- --run src/views/dashboard src/views/finance src/views/exam`  
Expected before: missing components. Expected after: all page tests pass.

- [ ] **Step 3: Run typecheck and commit**

Run: `cd web; npm run typecheck`  
Expected: exit 0.

```powershell
git add web/src/api web/src/views/dashboard web/src/views/finance web/src/views/exam
git commit -m "feat: 实现概览收支和试卷页面"
```

## Task 12: Photo, movie, and note frontend

**Files:**
- Create: `web/src/api/photo.ts`, `web/src/api/movie.ts`, `web/src/api/note.ts`
- Create: `web/src/views/photo/PhotoView.vue`, `web/src/views/photo/PhotoUploadDialog.vue`
- Create: `web/src/views/movie/MovieView.vue`, `web/src/views/movie/MovieFormDialog.vue`
- Create: `web/src/views/note/NoteView.vue`, `web/src/views/note/NotePreviewDialog.vue`
- Test: matching `*.spec.ts` files

- [ ] **Step 1: Write media and note tests**

```ts
it('does not expose note create or edit controls', () => {
  const wrapper = mount(NoteView, { global: testGlobals() })
  expect(wrapper.find('[data-testid="create-note"]').exists()).toBe(false)
  expect(wrapper.find('[data-testid="edit-note"]').exists()).toBe(false)
})
```

- [ ] **Step 2: Verify RED, implement responsive grids/dialogs, verify GREEN**

Run: `cd web; npm test -- --run src/views/photo src/views/movie src/views/note`  
Expected before: missing views. Expected after: upload validation, progress normalization, note preview, delete confirmation, and empty/error states pass.

- [ ] **Step 3: Commit**

```powershell
git add web/src/api web/src/views/photo web/src/views/movie web/src/views/note
git commit -m "feat: 实现照片影片和笔记页面"
```

## Task 13: Super-admin and profile frontend

**Files:**
- Create: `web/src/api/admin.ts`, `web/src/api/profile.ts`, `web/src/api/audit.ts`
- Create: `web/src/views/admin/FamilyListView.vue`
- Create: `web/src/views/admin/FamilyDetailView.vue`
- Create: `web/src/views/admin/AuditLogView.vue`
- Create: `web/src/views/admin/GlobalDataView.vue`
- Create: `web/src/views/profile/ProfileView.vue`
- Test: matching `*.spec.ts` files

- [ ] **Step 1: Write role and account-management tests**

```ts
it('keeps cross-family business views read only', () => {
  const wrapper = mount(GlobalDataView, { global: testGlobalsFor('SUPER_ADMIN') })
  expect(wrapper.findAll('[data-write-action]')).toHaveLength(0)
})
```

- [ ] **Step 2: Verify RED, implement pages, verify GREEN**

Run: `cd web; npm test -- --run src/views/admin src/views/profile`  
Expected before: missing views. Expected after: family/account CRUD, role guards, read-only global data, audit filters, and password validation pass.

- [ ] **Step 3: Commit**

```powershell
git add web/src/api web/src/views/admin web/src/views/profile
git commit -m "feat: 实现超级管理员和个人中心页面"
```

## Task 14: Integration, deployment documentation, and final verification

**Files:**
- Create: `README.md`
- Create: `deploy/.env.example`
- Create: `web/.env.example`
- Create: `server/src/test/java/com/ethan/familyadmin/integration/TenantIsolationIntegrationTest.java`
- Modify: `deploy/compose.yml`, `server/src/main/resources/application.yml`

- [ ] **Step 1: Add an integration test covering every tenant-owned resource**

```java
@ParameterizedTest
@MethodSource("tenantOwnedResources")
void familyACannotReadFamilyBResource(ResourceProbe probe) {
    assertThat(probe.readAs(familyAUser, familyBResourceId)).isRejected();
}
```

- [ ] **Step 2: Run complete backend verification**

Run: `cd server; .\mvnw.cmd clean verify`  
Expected: all tests pass and `server/target/*.jar` is produced.

- [ ] **Step 3: Run complete frontend verification**

Run: `cd web; npm test -- --run; npm run typecheck; npm run build`  
Expected: all tests pass and `web/dist/` is produced.

- [ ] **Step 4: Start the stack and perform browser acceptance**

Run MySQL with `docker compose --env-file deploy/.env -f deploy/compose.yml up -d`, start the backend and frontend, then verify login and every page at desktop width and below 768px. Compare navigation, hierarchy, filters, dialogs, empty states, and responsive behavior with `产品文档/`.

- [ ] **Step 5: Document exact startup and production configuration**

README must include prerequisites, environment variables, database startup, backend/frontend commands, initial super-admin provisioning, storage backup, test commands, and the explicit statement that notes cannot be created or edited in v1.0.

- [ ] **Step 6: Review diff and commit**

```powershell
git diff --check
git status --short
git add README.md deploy web/.env.example server/src/test server/src/main/resources/application.yml
git commit -m "docs: 完善部署说明和交付验证"
```

## Plan self-review

- Every approved design section maps to at least one task.
- Tenant isolation is tested at authentication, query, object-write, file, and integration levels.
- Super-admin cross-family business access is read-only.
- Family users have no member-management page or API permission.
- Notes expose list, detail, and delete only.
- Local file storage is abstracted and protected by authenticated streaming.
- Each implementation task follows a test-fail, implementation, test-pass sequence and ends with a scoped commit.
