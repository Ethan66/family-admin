# 开发进度

## 当前需求

- 名称：家庭管理后台
- 分支：`v_1.0`
- 模式：当前仓库直接开发
- 服务：`web`、`server`
- 技术栈：Vue 3 + TypeScript + Element Plus + UnoCSS；Java 21 + Spring Boot 3 + MyBatis-Plus + MySQL
- 设计：`docs/superpowers/specs/2026-09-23-family-admin-design.md`
- 需求文档：`docs/20260923-家庭管理后台/`
- 实施计划：`docs/superpowers/plans/2026-09-23-family-admin-mvp.md`
- 当前阶段：v1.0 功能开发完成，后端测试/打包与前端测试/类型检查/构建均通过

## 下一步

- 已完成多家庭租户隔离、超级管理员跨家庭只读查看、家庭与账号管理、审计日志。
- 已完成概览、收支、试卷、照片、影片、学习笔记及修改密码页面。
- 已补齐本地与 Docker 部署说明；当前机器没有 Docker，且本机 MySQL 默认项目账号不可用，因此未进行真实数据库联调。
- 实现代码尚未提交，需用户明确确认后再提交。
