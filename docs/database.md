# TrainFlow 数据库

知华科技（上海如静知华信息科技有限公司） · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2。

MySQL8.4，库名zhuatech_trainflow。V1__identity.sql建立department/account/access_role/permission/role_permission/nav_menu/dictionary_entry/system_setting/audit_event。V2__training.sql建立course/question/enrollment/exam_attempt/flow_event/command_record；V3__course_edit_revision.sql为单独改题追加修订计数，兼容已存在的数据。结构由Flyway初始化，应用JPA只validate。

course按family_code与edition唯一，题目按course_id与position唯一，考试按enrollment_id与number唯一，命令UUID唯一。外键保护部门、版本、人员和成绩。所有课题内容属于固定课程版本，不跨版本共享答案。Course与Enrollment都有乐观version，题目变动伴随课程草稿更新；考试及状态变动更新任务版本。写入共用基础部门行锁，事务隔离READ_COMMITTED。

时间timestamp(6)，连接与会话UTC；资格日期和任务截止为DATE，按Asia/Shanghai判定。版本和考试保留真实输入，状态QUALIFIED在读取超过valid_until后显示EXPIRED，不修改历史原状态。初始化只建管理数据，无员工培训与成绩假数据。私有管理员密码通过环境注入，BCrypt12存储且不在接口返回。

禁止编辑已应用迁移。新增版本脚本，备份与恢复后验证新旧课程和考试。不要删除正式数据卷或导入真实隐私到公开测试样例。
