# 家序 · 家庭管理后台

一个支持多家庭租户隔离的家庭资料管理系统。Windows 桌面端位于 `desktop/`，后端位于 `server/`；原 Web 管理端保留在 `web/`，不再作为当前交付入口。

## 功能

- 家庭概览、收入支出、家庭管理和审计日志
- 桌面端扫描本机照片、影片、Markdown/TXT 笔记和图片/PDF 试卷
- 本地文件只保存绝对路径与元数据索引，不复制、不上传、不删除源文件
- 一个家庭对应一个租户，业务数据和图片文件均按家庭隔离
- 普通成员只能修改或删除本人创建的数据
- 超级管理员管理家庭和成员账号，并可选择家庭读写业务数据
- 学习笔记仅支持本地扫描和只读预览，不提供新增、编辑

## 角色与权限

目前只有超级管理员和普通家庭成员两种角色：

| 功能 | 超级管理员 | 普通家庭成员 |
|---|---|---|
| 家庭管理、审计日志 | 可以使用 | 无权访问 |
| 查看业务数据 | 选择家庭后，可查看该家庭全部数据 | 只能查看所属家庭的数据 |
| 新增业务数据 | 选择家庭后可以新增 | 可以在所属家庭新增 |
| 修改、删除业务数据 | 可管理所选家庭的全部记录 | 只能管理自己创建的记录 |

两种角色都可以修改自己的密码。

## 桌面端开发

环境要求：Node.js、Rust stable、Visual Studio 2022 Build Tools（Desktop development with C++）和 Windows 10 x64 1803 及以上。

```shell
cd desktop
npm install
npm run tauri:dev:local
```

登录页只填写账号和密码，Java 服务地址由启动模式决定：

- `local` 模式固定访问本机 `http://localhost:8080/api`。
- `server` 模式读取 `VITE_API_BASE_URL`，支持 HTTP 和 HTTPS；公网使用建议选择 HTTPS。

连接服务器进行开发时，先复制并修改服务器配置：

```powershell
Copy-Item .env.server.example .env.server
npm run tauri:dev:server
```

`.env.server` 示例：

```dotenv
VITE_API_BASE_URL=https://family.example.com/api
```

`.env.server` 只保存在本机，不提交到代码仓库。`VITE_API_BASE_URL` 是打包后用户可见的公开配置，不能在其中填写密钥或 Token。

桌面端资源索引保存在 `%APPDATA%/FamilyAdmin/families/<familyId>/resource-index.json`。照片、影片和试卷的资料保存在同一家庭索引的 `metadataById` 中。重新扫描会按照磁盘现状更新对应资源类型的文件清单，并保留已填写的资料；原始文件不会被复制、上传或删除。

### 构建 Windows 安装包

```shell
cd desktop
npm run lint
npm run typecheck
npm run test

# 打包连接本机 Java 服务的版本
npm run tauri:build:local

# 打包连接服务器的版本（需先配置 .env.server）
npm run tauri:build:server
```

NSIS 安装包生成到 `desktop/src-tauri/target/release/bundle/nsis/`。

## Hermes 微信对话接入

独立的家庭管理 MCP 服务位于 [`mcp-server/`](mcp-server/README.md)，提供对话记账、查账、支出删除、任务创建/查询/打卡/删除、课程表查询与上课/请假记录，以及只读存折查询（不可新增、编辑、删除存折）。通过现有后端 API 操作数据，账号固定家庭；仅限授权私聊。配置、安装、验证和防重边界见该目录说明。

## 本地启动

1. 在 `deploy/` 目录复制 `.env.example` 为 `.env`，修改三个数据库密码。
2. 启动数据库：`docker compose -f deploy/compose.yml up -d mysql`。
3. 启动后端：进入 `server/`，执行 `mvn spring-boot:run`。
4. 启动前端：进入 `web/`，执行 `npm install` 和 `npm run dev`。
5. 浏览器访问 `http://localhost:5173`。

首次启动会在不存在超级管理员时创建初始账号：

- 账号：`admin`
- 密码：`family-admin-123`

正式部署前必须通过 `BOOTSTRAP_ADMIN_USERNAME`、`BOOTSTRAP_ADMIN_PASSWORD` 修改初始凭据，并设置足够长的 `JWT_SECRET`。创建首个管理员后，可关闭 `BOOTSTRAP_ADMIN_ENABLED`。

## 一键容器启动

配置 `deploy/.env` 后执行：

```shell
docker compose -f deploy/compose.yml up -d --build
```

访问 `http://localhost:8088`。图片文件保存在仓库的 `storage/` 目录，数据库使用 Docker 卷持久化。

## 验证

```shell
cd server
mvn test
mvn -DskipTests package

cd ../web
npm run typecheck
npm test -- --run
npm run build

cd ../desktop
npm run lint
npm run typecheck
npm run test
npm run build

cd src-tauri
cargo fmt -- --check
cargo clippy --all-targets --all-features -- -D warnings
cargo test
```
