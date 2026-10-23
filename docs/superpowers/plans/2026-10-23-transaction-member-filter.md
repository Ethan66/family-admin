# 收入支出成员筛选实施计划

**目标：** 桌面端类型旁新增成员筛选，管理员可选当前家庭全部成员，其他账号仅展示并默认选择自己。

**已确认方案：** 使用现有 memberName 归属字段，与创建人无关；管理员由 SUPER_ADMIN 角色判断。姓名相同的记录合并筛选。不更改编辑删除权限，不新增数据库字段，不修改 Web 页面，不自动部署或打包。

## 实施顺序

- [x] 新增页面测试：普通账号只有本人、管理员可选全部、查询重置分页、列表与统计参数一致、家庭切换清空旧成员。
- [x] 扩展统计 SQL 测试，验证 member_name 条件与家庭、日期、类型、分类同时生效。
- [x] 桌面 TransactionsView.vue 增加 memberName 状态、独立筛选选项、家庭切换初始化和查询处理。
- [x] 后端 TransactionController、TransactionQuery、TransactionService、TransactionMapper 增加可选 memberName，并用于列表和统计。
- [x] 执行相关桌面测试、桌面类型检查及后端交易模块测试；复核本次 diff。不运行生产构建、桌面打包或界面自动化。

## 验证结果

- `npm run test -- src/test/transactions-member-filter.spec.ts src/test/transactions.spec.ts`：8 个测试通过。
- `npm run typecheck`：通过。
- `mvn -q '-Dtest=TransactionServiceTest,TransactionMapperSqlTest' test`：7 个测试通过，覆盖列表成员条件、家庭条件和统计 SQL。
- 本次修改文件的 `git diff --check`：通过。
- 未执行全量测试、浏览器验证、生产构建、安装包生成、部署或 Git 提交。

## 边界

普通账号选项仅本人；该规则是筛选交互，不新增接口访问权限限制。管理员选项采用当前家庭成员和本人姓名，以兼容现有管理员记账。成员姓名改名及历史停用成员的追溯不属于本次范围。
