// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
export const states = {
  DRAFT: "草稿",
  PUBLISHED: "已发布",
  ARCHIVED: "已归档",
  ASSIGNED: "待学习",
  LEARNING: "待考试",
  PRACTICAL: "待实操复核",
  QUALIFIED: "有效能力",
  EXPIRED: "已到期",
  FAILED: "未通过",
  CANCELLED: "已取消",
  REVOKED: "已撤销",
};
export const commands = {
  publish: "批准发布",
  archive: "归档课程",
  revise: "创建新版",
  read: "确认阅读并开始考试",
  pass: "实操通过",
  fail: "实操未通过",
  cancel: "取消培训",
  revoke: "撤销能力",
};
export const labels = {
  familyCode: "课程编号",
  title: "课程名称",
  category: "培训分类",
  departmentId: "归属部门",
  content: "学习正文",
  passScore: "通过分数",
  maxAttempts: "最多考试次数",
  validDays: "能力有效天数",
  practicalRequired: "需要实操复核",
  learnerId: "学员",
  reviewerId: "实操复核人",
  dueDate: "完成截止日",
  username: "账号",
  displayName: "姓名",
  password: "密码",
  roleId: "角色",
  enabled: "启用",
  name: "名称",
  nameEn: "英文名称",
  scope: "数据范围",
  permissions: "角色权限",
  code: "代码",
  permissionCode: "菜单权限",
  position: "顺序",
  type: "类型",
  value: "参数值",
  oldPassword: "原密码",
  newPassword: "新密码",
  note: "审核证据或原因",
};
export const errors = {
  UNAUTHENTICATED: "登录已失效，请重新登录",
  FORBIDDEN: "没有操作权限",
  OUT_OF_SCOPE: "记录不在当前数据范围",
  NOT_ASSIGNED: "仅指定人员可以操作",
  INDEPENDENT_REVIEW: "必须由独立账号复核",
  INVALID_STATE: "状态已经变化，请刷新",
  STALE_VERSION: "记录已经更新，请刷新后操作",
  FROZEN: "已冻结的资料不能修改",
  USE_REVISION: "编号已有版本，请在原课程创建新版",
  OPEN_REVISION: "该课程已有未发布新版",
  DUPLICATE_TRAINING: "该员工已有未完成或有效的本版培训",
  INVALID_QUESTIONS: "至少2题，每题4个不同选项及一个标准答案",
  INVALID_ANSWERS: "请完整回答所有题目",
  INELIGIBLE_ACCOUNT: "人员已停用、缺少权限或部门不匹配",
  TRAINING_OVERDUE: "培训已逾期，不能继续考试或复核，请联系培训管理员",
  WEAK_PASSWORD: "密码至少12位，包含大小写字母和数字",
  LAST_ADMIN: "必须保留一个启用的全范围管理员",
  IDEMPOTENCY_CONFLICT: "请求内容已改变，请刷新后重新提交",
  INVALID_DATE: "截止日期必须在今日至两年内",
  INVALID_INPUT: "输入不完整或超出允许范围",
  CONFLICT: "编号重复或记录仍被引用",
  NOT_FOUND: "记录不存在",
  REPORT_LIMIT: "数据超过统计上限，请联系管理员",
  REGISTERED_MENUS_ONLY: "只能维护已有导航",
  BUILTIN_RESOURCE: "内建资源或历史引用不能删除",
};
/** 操作按钮遵循角色、状态与指定人员；服务端再次校验。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function actions(kind, r, me) {
  const can = (p) => me.permissions.includes(p);
  if (kind === "courses")
    return [
      ...(r.status === "DRAFT" && r.authorId !== me.id && can("course.publish")
        ? ["publish"]
        : []),
      ...(r.status === "PUBLISHED" && can("course.manage") ? ["archive"] : []),
      ...(r.status !== "DRAFT" && can("course.manage") ? ["revise"] : []),
    ];
  return [
    ...(r.status === "ASSIGNED" && r.learnerId === me.id && can("learn")
      ? ["read"]
      : []),
    ...(r.status === "PRACTICAL" &&
    r.reviewerId === me.id &&
    r.learnerId !== me.id &&
    r.assignedBy !== me.id &&
    can("practical.review")
      ? ["pass", "fail"]
      : []),
    ...(can("training.assign") &&
    ["ASSIGNED", "LEARNING", "PRACTICAL"].includes(r.status)
      ? ["cancel"]
      : []),
    ...(r.status === "QUALIFIED" && can("training.assign") ? ["revoke"] : []),
  ];
}
/** 显示服务器日期，不改变资格判定。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export const date = (v) =>
  v ? new Date(v).toLocaleString("zh-CN", { timeZone: "Asia/Shanghai" }) : "—";
