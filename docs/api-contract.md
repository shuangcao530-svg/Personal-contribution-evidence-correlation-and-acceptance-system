# 第一版接口约定

本文件是开发约定，需求来自当前讨论。项目名称：面向学生团队项目的个人贡献证据关联与验收系统。

## 通用约定

- API 前缀 `/api`，JSON 字段为 camelCase。成功直接返回对象或数组，错误返回 `{ "message": "中文错误说明" }`。
- 登录后使用 `Authorization: Bearer <token>`。业务接口必须验证登录、项目成员关系和角色。
- 角色 `TEACHER`、`STUDENT`；贡献状态 `SUBMITTED`、`RETURNED`、`APPROVED`；证据类型 `LINK`、`FILE`。
- 时间返回 ISO 字符串。数据库使用 H2 文件模式用于本地运行，保留 MySQL profile。
- 第一版任务标准发布后不可编辑；每次重新提交创建新修订，验收绑定修订 ID，历史只读。
- 一个贡献对应一名学生、一项任务；第一版每项任务分配一名负责人，同项目的不同学生可在各自任务中引用同一材料。提交人由登录身份确定。

## 登录

- `POST /auth/login` `{username,password}` → `{token,user:{id,username,name,role}}`
- `GET /auth/me` → user
- `POST /auth/logout` → `{message}`
- `GET /users` → 用户列表（教师可用，用于选项目成员）。
- 演示账户：`teacher` 陈老师、`student` 张三、`student2` 李四、`student3` 王五，初始密码均为 `demo1234`。密码哈希保存。

## 项目与任务

- `GET /dashboard` → `{projects,tasks,pending,approved}`，统计仅包括当前身份可见数据。
- `GET /projects` → `Project[]`
- `POST /projects` `{name,description,memberIds:[2,3,4]}` → Project，限教师。
- Project `{id,name,description,ownerId,ownerName,members:[User],createdAt}`，members 含教师。
- `GET /tasks?projectId=1` → `Task[]`，projectId 可省略。
- `POST /tasks` `{projectId,title,description,assigneeId,dueDate:"2026-10-15",criteria:["支持正确登录","错误密码返回提示"]}` → Task，限项目教师。
- Task `{id,projectId,projectName,title,description,assigneeId,assigneeName,dueDate,createdAt,criteria:[{id,text}]}`。
- 只有任务指定学生可以在该任务下发起申报；允许同一学生同一任务存在多条内容不同的申报，但不自动计分。

## 证据

- `GET /evidence?projectId=1` → `Evidence[]`，限项目成员，支持引用已有材料。
- `POST /evidence/link` `{projectId,title,url}` → Evidence，仅允许 http/https 链接；服务器不抓取 URL。
- `POST /evidence/upload` multipart `projectId,title,file` → Evidence，最大 10MB，限定常见文档/图片/文本/压缩包格式。
- `GET /evidence/{id}/download` → 附件流，限项目成员，需要 Bearer；原始文件名经响应头安全处理，磁盘使用随机文件名。
- Evidence `{id,projectId,uploaderId,uploaderName,title,type,url,fileName,sha256,createdAt}`。
- 证据记录不可覆盖；新文件创建新证据。sha256 用于标识文件完整性，不代表作者身份。

## 贡献与验收

- `GET /contributions?projectId=1` → `Contribution[]`，学生仅自己的，教师仅自己负责项目的。
- `GET /contributions/{id}` → Contribution，包含全部修订。
- `POST /contributions` `{taskId,title,description,evidence:[{evidenceId,criterionId,location,note}]}` → Contribution。
- `POST /contributions/{id}/revisions` `{title,description,evidence:[{evidenceId,criterionId,location,note}]}` → Contribution，仅本人且当前为 RETURNED 时允许。
- 提交必须至少一个证据引用；证据、任务及标准必须同项目/同任务。一次提交可以关联多个标准。
- `POST /contributions/{id}/reviews` `{revisionId,decision:"APPROVED"|"RETURNED",comment,items:[{criterionId,passed:true,comment:"说明"}]}` → Contribution，限该项目教师。
- 验收必须覆盖该修订引用的全部标准，无重复/无额外标准；通过时全部为 true，退回时至少一项 false 且总意见非空。
- 限最新待验收修订；重复验收、旧版本验收、非本人重提返回冲突/权限错误。
- Contribution `{id,projectId,projectName,taskId,taskTitle,studentId,studentName,title,status,createdAt,updatedAt,currentVersion,revisions:[Revision]}`。
- Revision `{id,version,title,description,createdAt,evidence:[EvidenceReference],review:null|Review}`，revisions 版本升序。
- EvidenceReference 为 Evidence 字段加 `{criterionId,criterionText,location,note}`。
- Review `{id,revisionId,reviewerName,decision,comment,createdAt,items:[{criterionId,passed,comment}]}`。

## 报告

- `GET /reports?studentId=2` → `{student:User,summary:{total,approved,pending,returned},contributions:Contribution[]}`。
- studentId 可省略，学生仅本人，教师仅自己负责项目中的学生；contributions 只含调用者有权查看的项目。
- 前端基于该响应生成个人报告与 UTF-8 BOM CSV，导出时防止表格公式注入。
- 报告显示状态和版本，不将待验收修订描述为已确认贡献。

## 演示数据

项目“校园二手交易平台”，教师陈老师，学生张三、李四、王五。
三项任务分别分配给三位学生，包含明确验收标准。三项贡献状态分别为待验收、已通过、待补充。
所有演示内容在界面标明，项目和记录可通过真实接口新增。

## 当前范围

本次实现可运行第一版，包括持久化、角色权限、上传/下载、按标准关联、退回补交、个人报告。
账号注册、成员邀请、标准改版、Git 自动采集、复杂评分、通知、正式部署属于后续迭代。
