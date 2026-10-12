# Two-Role Permission Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove `FAMILY_ADMIN` and allow a super administrator with an explicitly selected family to read and write all data in that family.

**Architecture:** Keep the existing `sys_user` plus `family_member` model and store every non-super user as `FAMILY_MEMBER`. Centralize write authorization in `TenantAccessGuard`: super administrators and members must both have a matching family context, while member writes remain creator-scoped. Keep the database schema unchanged and update server, desktop, deprecated Web code, and focused tests together.

**Tech Stack:** Java 21, Spring Boot 3.5, MyBatis Plus, JUnit 5, Mockito, Vue 3, TypeScript, Pinia, Vitest.

---

## File map

- `server/src/main/java/com/ethan/familyadmin/security/Role.java`: retain the two supported roles.
- `server/src/main/java/com/ethan/familyadmin/security/TenantAccessGuard.java`: enforce selected-family writes for super administrators and creator-scoped writes for members.
- `server/src/main/java/com/ethan/familyadmin/family/dto/CreateMemberRequest.java`: remove the role input from member creation.
- `server/src/main/java/com/ethan/familyadmin/family/dto/UpdateMemberRequest.java`: remove the role input from member updates.
- `server/src/main/java/com/ethan/familyadmin/family/admin/AdminFamilyService.java`: persist all ordinary users as `FAMILY_MEMBER`.
- `server/src/test/java/com/ethan/familyadmin/**`: prove the new permission model and remove old-role fixtures.
- `desktop/src/types/api.ts`: expose only two roles to the desktop client.
- `desktop/src/utils/business.ts`: allow super administrators to manage all records in their selected family.
- `desktop/src/layouts/AppShell.vue`: remove the obsolete role label.
- `desktop/src/views/TransactionsView.vue`: allow creation when a usable family context exists.
- `desktop/src/test/**`: cover super-administrator write controls and the remaining member role.
- `web/src/types/index.ts`, `web/src/layout/AppShell.vue`, `web/src/views/FamiliesView.vue`: remove stale `FAMILY_ADMIN` types and role selectors from the unused Web client so the repository has one role contract.

### Task 1: Change the central write-permission contract

**Files:**
- Modify: `server/src/test/java/com/ethan/familyadmin/security/TenantAccessGuardTest.java`
- Modify: `server/src/main/java/com/ethan/familyadmin/security/TenantAccessGuard.java`

- [x] **Step 1: Replace the old family-admin and read-only super-admin tests with the desired rules**

```java
@Test
void superAdminCanWriteAllRecordsInSelectedFamily() {
    CurrentUser superAdmin = new CurrentUser(1L, 31L, Role.SUPER_ADMIN, 0);

    assertThat(guard.canRead(superAdmin, entity(31L, 8L))).isTrue();
    assertThat(guard.canWrite(superAdmin, entity(31L, 8L))).isTrue();
    assertThat(guard.canWrite(superAdmin, entity(32L, 8L))).isFalse();
    guard.requireBusinessCreate(superAdmin);
}

@Test
void superAdminWithoutSelectedFamilyCannotCreateBusinessData() {
    CurrentUser superAdmin = new CurrentUser(1L, null, Role.SUPER_ADMIN, 0);

    assertThatThrownBy(() -> guard.requireBusinessCreate(superAdmin))
        .isInstanceOf(com.ethan.familyadmin.common.exception.BusinessException.class);
}
```

- [x] **Step 2: Run the focused test and confirm RED**

Run: `mvn -f server/pom.xml -Dtest=TenantAccessGuardTest test`

Expected: FAIL because super administrators are currently denied writes and business creation.

- [x] **Step 3: Implement the minimal permission rule**

```java
public boolean canWrite(CurrentUser user, TenantOwnedEntity entity) {
    if (!Objects.equals(user.familyId(), entity.getFamilyId())) {
        return false;
    }
    return user.role() == Role.SUPER_ADMIN
        || user.role() == Role.FAMILY_MEMBER && Objects.equals(user.userId(), entity.getCreatedBy());
}

public void requireBusinessCreate(CurrentUser user) {
    if (user.familyId() == null) {
        throw new BusinessException(ErrorCode.FORBIDDEN);
    }
}
```

- [x] **Step 4: Run the focused test and confirm GREEN**

Run: `mvn -f server/pom.xml -Dtest=TenantAccessGuardTest test`

Expected: PASS.

### Task 2: Remove the family-administrator role from backend contracts

**Files:**
- Modify: `server/src/test/java/com/ethan/familyadmin/family/admin/AdminFamilyServiceTest.java`
- Modify: `server/src/test/java/com/ethan/familyadmin/auth/AuthServiceTest.java`
- Modify: `server/src/test/java/com/ethan/familyadmin/movie/MovieServiceTest.java`
- Modify: `server/src/test/java/com/ethan/familyadmin/dashboard/DashboardServiceTest.java`
- Modify: `server/src/main/java/com/ethan/familyadmin/security/Role.java`
- Modify: `server/src/main/java/com/ethan/familyadmin/family/dto/CreateMemberRequest.java`
- Modify: `server/src/main/java/com/ethan/familyadmin/family/dto/UpdateMemberRequest.java`
- Modify: `server/src/main/java/com/ethan/familyadmin/family/admin/AdminFamilyService.java`

- [x] **Step 1: Change member-service tests to require an implicit family-member role**

Use the three-field constructor:

```java
service.createMember(31L, new CreateMemberRequest("father", "父亲", "secret123"));
assertThat(captor.getValue().getRole()).isEqualTo(Role.FAMILY_MEMBER.name());
```

Update the duplicate-name test to the same constructor. Change authentication fixtures to `Role.FAMILY_MEMBER`; use `Role.SUPER_ADMIN` with a selected family in tests that need permission to modify someone else's record, and use `Role.FAMILY_MEMBER` for ordinary same-family reads.

- [x] **Step 2: Run affected backend tests and confirm RED**

Run:

```powershell
mvn -f server/pom.xml "-Dtest=AdminFamilyServiceTest,AuthServiceTest,MovieServiceTest,DashboardServiceTest" test
```

Expected: compilation/test failure because request records still require `role` and the old production role still exists.

- [x] **Step 3: Remove the obsolete enum value and request fields**

```java
public enum Role {
    SUPER_ADMIN,
    FAMILY_MEMBER
}
```

```java
public record CreateMemberRequest(
    @NotBlank(message = "登录名不能为空")
    @Pattern(regexp = "[A-Za-z0-9_]{3,64}", message = "登录名只能包含字母、数字、下划线且长度为 3 到 64 位")
    String username,
    @NotBlank(message = "显示名称不能为空") @Size(max = 100, message = "显示名称不能超过 100 字") String displayName,
    @NotBlank(message = "初始密码不能为空") @Size(min = 8, max = 100, message = "初始密码长度必须为 8 到 100 位") String password
) {}
```

```java
public record UpdateMemberRequest(
    @NotBlank(message = "显示名称不能为空") @Size(max = 100, message = "显示名称不能超过 100 字") String displayName,
    @NotNull(message = "启用状态不能为空") Boolean active
) {}
```

- [x] **Step 4: Make member persistence use the only ordinary role**

Delete the two `request.role() == Role.SUPER_ADMIN` validation branches and replace both role assignments with:

```java
membership.setRole(Role.FAMILY_MEMBER.name());
```

- [x] **Step 5: Run affected tests and confirm GREEN**

Run:

```powershell
mvn -f server/pom.xml "-Dtest=TenantAccessGuardTest,AdminFamilyServiceTest,AuthServiceTest,MovieServiceTest,DashboardServiceTest" test
```

Expected: PASS.

### Task 3: Update desktop permissions and role types

**Files:**
- Modify: `desktop/src/test/business.test.ts`
- Modify: `desktop/src/test/resource.test.ts`
- Modify: `desktop/src/test/transactions.spec.ts`
- Modify: `desktop/src/types/api.ts`
- Modify: `desktop/src/utils/business.ts`
- Modify: `desktop/src/layouts/AppShell.vue`
- Modify: `desktop/src/views/TransactionsView.vue`

- [x] **Step 1: Write the desktop permission expectations first**

```ts
it('allows the super administrator to manage every record', () => {
  expect(canManageTransaction('SUPER_ADMIN', 1, 2)).toBe(true)
})

it('allows a family member to manage only their own records', () => {
  expect(canManageTransaction('FAMILY_MEMBER', 2, 2)).toBe(true)
  expect(canManageTransaction('FAMILY_MEMBER', 2, 1)).toBe(false)
})
```

Replace the resource-family fixture using `FAMILY_ADMIN` with `FAMILY_MEMBER`. Extend the transaction component test so a super administrator with `selectedFamilyId` sees the create control.

- [x] **Step 2: Run focused desktop tests and confirm RED**

Run:

```powershell
npm --prefix desktop test -- src/test/business.test.ts src/test/resource.test.ts src/test/transactions.spec.ts
```

Expected: FAIL because the current helper returns `false` for `SUPER_ADMIN` and the component hides creation from that role.

- [x] **Step 3: Apply the two-role desktop contract**

```ts
export type Role = 'SUPER_ADMIN' | 'FAMILY_MEMBER'
```

```ts
export function canManageTransaction(role: Role, userId: number, createdBy: number): boolean {
  if (role === 'SUPER_ADMIN') return true
  return userId === createdBy
}
```

Use only the two labels in `AppShell.vue`:

```ts
const displayRole = computed(() => {
  if (!authStore.user) return ''
  return authStore.user.role === 'SUPER_ADMIN' ? '超级管理员' : '家庭成员'
})
```

Base transaction creation on family context rather than role:

```ts
const canCreate = computed(() => activeFamilyId.value !== null)
```

Declare `activeFamilyId` before `canCreate` so the computed dependency is initialized in source order.

- [x] **Step 4: Run focused tests and type checking**

Run:

```powershell
npm --prefix desktop test -- src/test/business.test.ts src/test/resource.test.ts src/test/transactions.spec.ts
npm --prefix desktop run typecheck
```

Expected: all focused tests PASS and type checking exits successfully.

### Task 4: Remove stale role selection from the Web client

**Files:**
- Modify: `web/src/types/index.ts`
- Modify: `web/src/layout/AppShell.vue`
- Modify: `web/src/views/FamiliesView.vue`

- [x] **Step 1: Reduce the Web role type to two values**

```ts
export type Role = 'SUPER_ADMIN' | 'FAMILY_MEMBER'
```

- [x] **Step 2: Remove `role` from the member form and API payloads**

Keep create payload fields `username`, `displayName`, and `password`. Keep update payload fields `displayName` and `active`. Replace the role table formatter with the fixed label `家庭成员` and remove the role `<el-select>`.

- [x] **Step 3: Replace the three-way account label**

```vue
<small>{{ auth.user?.role === 'SUPER_ADMIN' ? '超级管理员' : '家庭成员' }}</small>
```

- [x] **Step 4: Run Web type checking**

Run: `npm --prefix web run typecheck`

Expected: type checking exits successfully.

### Task 5: Verify the repository permission contract

**Files:**
- Verify only; no production files should be added in this task.

- [x] **Step 1: Confirm no production or test code references the removed role**

Run:

```powershell
rg -n "FAMILY_ADMIN" server/src desktop/src web/src
```

Expected: no matches.

- [x] **Step 2: Run the complete backend unit-test suite because authorization is cross-cutting**

Run: `mvn -f server/pom.xml test`

Expected: BUILD SUCCESS with all tests passing.

- [x] **Step 3: Run the complete desktop test suite**

Run: `npm --prefix desktop test`

Expected: all tests PASS.

- [x] **Step 4: Record scope limits**

Do not run production builds, Tauri packaging, browser automation, or database migrations. Report that source tests and type checks passed, and that no live user migration was needed because the database contained no `FAMILY_ADMIN` membership before implementation.

## Commit policy

Do not create a Git commit unless the user separately authorizes committing after reviewing the completed changes.
