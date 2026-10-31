# zc 存折访问限制实施计划

**Goal:** zc 所属存折仅允许账号 zc、admin 访问；其他成员存折保留现有规则。

**Architecture:** 在 PassbookService 实施权限过滤。列表、分页计数及金额合计使用同一可见性条件；新增、修改成员归属和按 ID 修改/删除均校验受保护成员。以 sys_user.username 确认账号，不使用显示名或创建者识别存折归属，不放宽现有家庭及写权限。

**Tech Stack:** Java 21、Spring Boot、MyBatis Plus、JUnit 5、Mockito。

## 执行步骤

1. 在 server/src/test/java/com/ethan/familyadmin/passbook/PassbookPrivacyTest.java 覆盖普通成员、zc、admin、其他超级管理员的列表/合计条件，以及受保护记录新增、修改、删除与成员转移，先运行确认失败。
2. 修改 server/src/main/java/com/ethan/familyadmin/passbook/PassbookService.java：非白名单账号查询增加 `member_id NOT IN (SELECT id FROM sys_user WHERE username = 'zc')`；写操作检查旧归属与新归属。静态 SQL 不拼接用户输入。
3. 运行后端测试和打包，审查实际差异；不改数据库、不打包桌面端。
4. 精确提交本次代码、测试和文档，推送当前 v_1.0 分支。
5. 核验服务器当前容器和挂载，上传 JAR 并校验 SHA-256；保留旧容器后切换新版本。
6. 使用实际账号身份的短期认证请求，只读核对生产列表与汇总权限、原有存折数量及运行状态；不修改生产业务数据。

## 验收边界

- 其他账号不能看到 zc 存折记录、记录数及金额贡献，也不能通过写接口访问或创建 zc 记录。
- zc、admin 读取权限不受该限制；编辑权限仍遵守原有创建者和管理员规则。
- 其他成员记录行为、家庭隔离、MCP 只读接口保持原规则。
- 服务发布失败时恢复旧容器；验证不输出口令、JWT 或存折金额。

## 本地验证记录

- 新增权限测试最初运行 9 项，其中 4 项失败，复现原有查询及写操作缺口；后续扩大为 14 项权限测试。
- `mvn -q test package`：总计 130 项，126 项通过，4 项需独立数据库的持久化测试因未配置环境跳过，失败和错误均为 0。
- Java 注释审计、提交门禁、`git diff --check` 通过；本次只有权限过滤，不改变金额计算或数据库结构。
- 未运行前端测试、界面自动化和桌面打包；后端查询限制对桌面端及 MCP 同时生效。
- 发布证据保存在服务器 `/opt/family-admin/releases/20261031-zc-passbook-privacy/`，发布前备份现有容器与 JAR，发布后执行账号身份只读接口核对。
