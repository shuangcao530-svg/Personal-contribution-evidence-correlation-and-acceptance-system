# 面向学生团队项目的个人贡献证据关联与验收系统

本项目名为“证据台”，用于管理课程团队项目中的个人贡献。学生把工作说明、材料和具体任务标准关联，教师逐项验收，个人报告保留可追溯的依据。

这是第一版可运行工程，包含源码、演示数据、接口约定和开发计划。学校任务书、开题报告格式和论文模板尚未提供，后续按学校要求调整。

## 快速运行

电脑安装 Java 21 和 MySQL 8 后，在本文件所在目录双击 **启动系统.cmd**。本机的数据库账号配置保存在被 Git 忽略的 `.runtime/local-database.properties` 中；设置了 `DB_USER`、`DB_PASSWORD` 环境变量时会优先使用环境变量。启动后自动打开：

`http://127.0.0.1:18080`

也可以在 PowerShell 中运行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\start.ps1
```

关闭浏览器不会停止后端。双击 **停止系统.cmd** 停止服务，已保存的数据和附件保留。

## 演示账号

| 角色 | 姓名 | 账号 | 密码 |
| --- | --- | --- | --- |
| 教师 | 陈老师 | teacher | demo1234 |
| 学生 | 张三 | student | demo1234 |
| 学生 | 李四 | student2 | demo1234 |
| 学生 | 王五 | student3 | demo1234 |

首次启动创建“校园二手交易平台”演示项目、三项任务和待验收、已通过、待补充三种申报。示例文件用于展示流程，不代表真实学生的成果。可使用教师账号创建自己的项目及任务，再用学生账号提交实际材料。

## 建议演示顺序

1. 登录教师账号，查看项目、任务与验收标准。
2. 退出后登录 `student`，查看“用户登录接口实现”申报与对应证据。
3. 使用教师账号进入验收工作台，逐项检查证据并通过，或填写缺失内容退回。
4. 使用被退回申报对应的学生账号补交，检查新版本和历史意见。
5. 教师确认最新版本后，在个人报告中查看已通过贡献，导出 CSV 或打印。
6. 创建一个新项目、新任务，重新完成真实上传和验收流程。

同一项目内的证据可被多人引用。每次申报必须明确本人工作及证据位置。验收通过针对本次个人贡献，不自动等于整个项目验收完成。

## 工程目录

```text
backend/       Java 后端、数据库结构和关键集成测试
frontend/      Vue 前端源码
docs/          第一版需求、接口约定和设计参考
scripts/       开发辅助脚本
build.ps1      安装前端依赖、构建前端、测试并打包后端
start.ps1      后台启动本地服务并打开浏览器
stop.ps1       停止由本项目启动的服务
```

前端采用 Vue 3、Vue Router 和 Vite；后端采用 Java 21、Spring Boot 和 JdbcTemplate。前端构建结果打包进入后端 JAR，因此体验已打包的版本只需要 Java。

## 开发与重新构建

准备 Java 21、Node.js 22.12 或更新版本、Maven 3.9 或更新版本，然后双击 **重新构建.cmd**，或者：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\build.ps1
```

首次构建需要联网下载依赖。脚本可使用 PATH 中的 Maven，也会寻找当前用户 `.m2/wrapper/dists` 中已缓存的 Maven。

前端开发：

```powershell
cd frontend
npm install
npm run dev
```

浏览器访问 `http://127.0.0.1:5173`，接口代理至端口 18080 的后端。

后端开发（如果 Maven 已加入 PATH）：

```powershell
cd backend
mvn spring-boot:run
```

在 IntelliJ IDEA 直接运行 `Main` 时，也会读取 `.runtime/local-database.properties`；环境变量优先于本机配置。不要把该本机配置文件加入版本控制。`Main` 的运行工作目录应为项目根目录或 `backend` 目录。

后端测试：

```powershell
cd backend
mvn test
```

改代码后先停止旧服务，再重新构建并启动。

## 数据和附件

运行时使用 MySQL 8 的 `contributions` 数据库；附件保存在 `backend/uploads`。MySQL 业务数据重启后保留，登录令牌需要重新获取。H2 仅作为自动化测试依赖，不用于运行期数据。`.runtime` 保存启动记录和运行日志。

备份时先停止服务，使用 `mysqldump contributions` 导出数据库，并一并备份 `backend/uploads`；数据库中的证据记录需要引用实际附件。

第一版支持最大 10MB 的常见文档、图片、文本与压缩包附件。上传后保存独立文件及 SHA-256；修改材料时创建新证据，旧记录保留。外部链接由用户打开查看，服务器不会自动抓取或存档网页内容。

## 数据库与后续范围

MySQL 8 是运行时默认数据库。首次部署时先用管理员账号执行 `scripts/mysql-create-database.sql`，再设置数据库连接环境变量。启动会按 `schema.sql` 建立缺失的数据表，不会删除已有数据。当前已迁移桌面项目原 H2 数据。

- Active profiles：`mysql`
- Environment variables：`DB_URL=jdbc:mysql://127.0.0.1:3306/contributions?useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC&useSSL=false&allowPublicKeyRetrieval=true;DB_USER=root;DB_PASSWORD=你的 MySQL 密码`
- Working directory：`$PROJECT_DIR$/backend`

也可在 PowerShell 运行前设置 `SPRING_PROFILES_ACTIVE=mysql`、`DB_USER` 和 `DB_PASSWORD`。不要把真实密码提交到 Git。仅在空库首次启动时会创建演示账号和演示项目；已有用户数据不会被覆盖。H2 依赖保留在 test scope，只供自动化测试使用。

第一版不包含账号注册、项目成员后期维护、已发布标准改版、Git 自动采集、自动评分和线上部署。系统仅绑定本机地址，演示账号用于本地学习。完整范围和后续迭代见 `docs/第一版需求与开发计划.md`。

官方开发资料：

- [Vue](https://vuejs.org/guide/introduction.html)
- [Spring Boot](https://docs.spring.io/spring-boot/3.3/reference/)
- [H2](https://h2database.com/html/features.html)




