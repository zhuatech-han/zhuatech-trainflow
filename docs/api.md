# TrainFlow 接口

知华科技（上海如静知华信息科技有限公司） · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2。

所有业务路径前缀 `/api`，同源JSON请求。GET `/auth/csrf` 返回CSRF头名与令牌，写请求带该头；HttpOnly会话Cookie由浏览器管理。不在localStorage保存凭据。POST `/auth/login` 输入username/password，GET `/auth/me` 取得安全档案，POST `/auth/logout` 退出，POST `/auth/password` 输入oldPassword/newPassword并使旧会话失效。

| 方法与路径 | 行为 |
|---|---|
| GET /options | 范围内人员、部门、字典和参数；SELF只本人 |
| GET /courses、/enrollments | search、status、page默认0、size默认20最多100、sort为newest/oldest |
| GET /{kind}/{id} | kind仅courses/enrollments，安全详情、正文、题目及历史 |
| POST /courses | CourseInput：familyCode/title/category/departmentId/content/passScore/maxAttempts/validDays/practicalRequired/questions |
| PUT /courses/{id} | 同输入加version，只有草稿编制人可改，编号和部门冻结 |
| DELETE /courses/{id}?version= | 未发布草稿删除，引用保护 |
| POST /courses/{id}/commands/{action} | publish/archive/revise，version/note/requestKey |
| POST /courses/{id}/assign | version/learnerId/reviewerId/dueDate/requestKey |
| POST /enrollments/{id}/commands/{action} | read/exam/pass/fail/cancel/revoke，version/requestKey，加必要note或answers |
| GET /{kind}/{id}/report.json | 相同详情范围和标准答案屏蔽，需export权限 |
| GET /workbench、/dashboard、/audit | 本人待办、范围统计、最近1000条范围审计 |
| GET/POST /admin/{type}、PUT/DELETE /admin/{type}/{id} | type为users/roles/departments/permissions/menus/dictionaries/settings；需admin与ALL |

题目输入 `{prompt, options:[A,B,C,D], correctAnswer:0..3}`，2–30题，选项不可重复。答案按冻结题目顺序提交整数数组；不接收客户端得分。学员接口从不返回标准答案；非SELF课程管理者允许查看编制资料。角色范围ALL/DEPARTMENT/SELF；SELF有额外业务写权限也不能替其他岗位操作。

命令 `requestKey` 为小写UUID，绑定当前账号、路径动作与完整载荷（含version）。精确重试返回同一结果对象的当前状态，不重复写历史或扣次数；同键改载荷返回409。客户端应在失败重试时保留键和原载荷，成功读取新版本后才发新命令。

错误格式 `{code}`。401未认证或会话失效，403功能/范围/指定人限制，404不存在，409陈旧版本/状态/重复任务/幂等冲突/引用，400参数和日期校验。数据库细节、密码与哈希不在响应中。主端口 `/actuator/health` 公共健康接口仅给出UP状态。
