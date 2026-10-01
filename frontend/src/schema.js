// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2
export const states = {
  DRAFT: ["草稿", "Draft"],
  TRIAGE: ["待处置", "Triage"],
  INVESTIGATION: ["原因与方案", "Investigation"],
  PLAN_REVIEW: ["方案待复核", "Plan review"],
  EXECUTION: ["措施执行", "Execution"],
  VERIFY_READY: ["效果待验证", "Verification"],
  CLOSED: ["已关闭", "Closed"],
  CANCELLED: ["已取消", "Cancelled"],
};
export const commands = {
  submit: ["提交报告", "Submit report"],
  cancel: ["取消问题", "Cancel case"],
  begin: ["登记临时处置", "Record containment"],
  analysis: ["保存原因与验证计划", "Save analysis"],
  "submit-plan": ["提交整改方案", "Submit plan"],
  reschedule: ["调整截止日", "Reschedule"],
  "approve-plan": ["通过方案", "Approve plan"],
  "reject-plan": ["退回方案", "Return plan"],
  "submit-evidence": ["提交效果验证", "Submit for verification"],
  verify: ["验证有效并关闭", "Verify and close"],
  "fail-verification": ["验证无效，继续整改", "Verification failed"],
  reopen: ["重新开启整改", "Reopen case"],
};
export const labels = {
  verificationEvidence: ["效果验证证据", "Verification evidence"],
  title: ["问题标题", "Title"],
  description: ["问题描述或措施内容", "Description"],
  source: ["问题来源", "Source"],
  category: ["质量类别", "Category"],
  severity: ["严重度", "Severity"],
  reference: ["关联凭证", "Reference"],
  departmentId: ["部门", "Department"],
  ownerId: ["整改责任人", "Owner"],
  reviewerId: ["独立复核人", "Reviewer"],
  dueDate: ["整改截止日", "Due date"],
  verifyAfter: ["最早效果验证日", "Verification date"],
  containment: ["临时处置与影响范围", "Containment and affected scope"],
  rootCause: ["原因分析", "Root cause"],
  verificationPlan: [
    "效果验证方法与判定依据",
    "Verification method and criteria",
  ],
  note: ["意见或调整原因", "Review note or reason"],
  evidence: ["执行或验证证据", "Execution or verification evidence"],
  kind: ["措施类型", "Action type"],
  name: ["名称", "Name"],
  nameEn: ["英文名称", "English name"],
  username: ["登录账号", "Username"],
  displayName: ["显示名称", "Display name"],
  password: ["初始或重置密码", "Initial or reset password"],
  roleId: ["角色", "Role"],
  enabled: ["启用", "Enabled"],
  scope: ["数据范围", "Data scope"],
  permissions: ["权限", "Permissions"],
  permissionCode: ["菜单所需权限", "Required permission"],
  position: ["菜单顺序", "Position"],
  type: ["字典类型", "Dictionary type"],
  code: ["代码", "Code"],
  value: ["参数值", "Value"],
  oldPassword: ["当前密码", "Current password"],
  newPassword: ["新密码", "New password"],
};
export const adminFields = {
  users: [
    "username",
    "displayName",
    "password",
    "roleId",
    "departmentId",
    "enabled",
  ],
  roles: ["name", "scope", "permissions"],
  departments: ["name"],
  menus: ["name", "nameEn", "permissionCode", "position", "enabled"],
  permissions: ["name"],
  dictionaries: ["type", "code", "name", "nameEn"],
  settings: ["value"],
};
export const commandFields = {
  submit: [],
  cancel: ["note"],
  begin: ["containment"],
  analysis: ["rootCause", "verificationPlan", "verifyAfter"],
  reschedule: ["dueDate", "note"],
  "submit-plan": [],
  "approve-plan": ["note"],
  "reject-plan": ["note"],
  "submit-evidence": ["evidence"],
  verify: ["evidence"],
  "fail-verification": ["evidence"],
  reopen: ["note"],
};
export const severityNames = {
    MINOR: ["一般", "Minor"],
    MAJOR: ["严重", "Major"],
    CRITICAL: ["重大", "Critical"],
  },
  scopeNames = {
    ALL: ["全部数据", "All data"],
    DEPARTMENT: ["本部门", "Department"],
    ASSIGNED: ["本人关联", "Assigned records"],
  },
  kindNames = {
    CORRECTIVE: ["整改措施", "Corrective"],
    PREVENTIVE: ["预防措施", "Preventive"],
  };
export const errors = {
  NETWORK_ERROR: ["连接失败，请稍后重试", "Connection failed. Retry."],
  UNAUTHENTICATED: ["登录已失效，请重新登录", "Session expired. Sign in."],
  LOGIN_FAILED: [
    "账号、密码错误或账号停用",
    "Invalid credentials or disabled account.",
  ],
  LOGIN_THROTTLED: ["请五分钟后重试登录", "Retry login in five minutes."],
  FORBIDDEN: ["没有操作权限", "Permission denied."],
  OUT_OF_SCOPE: ["没有该记录的数据权限", "Outside your data scope."],
  INVALID_INPUT: [
    "请检查必填项、长度和日期",
    "Check required fields, lengths and dates.",
  ],
  INVALID_STATE: ["状态已改变，请刷新详情", "State changed. Refresh detail."],
  STALE_VERSION: [
    "记录已更新，请刷新后操作",
    "Record changed. Refresh detail.",
  ],
  INDEPENDENT_REVIEW_REQUIRED: [
    "复核人必须独立于报告与执行人员",
    "Reviewer must be independent of reporting and execution.",
  ],
  INVALID_ASSIGNEE: [
    "责任人须启用、属于本部门且具有相应权限",
    "Assignee must be active, in the same department and authorized.",
  ],
  NOT_REPORTER: ["仅报告人可操作", "Only the reporter may do this."],
  NOT_CASE_OWNER: ["仅整改责任人可操作", "Only the case owner may do this."],
  NOT_ACTION_OWNER: [
    "仅措施责任人可完成措施",
    "Only the action owner may complete it.",
  ],
  NOT_REVIEWER: [
    "仅指定复核人可操作",
    "Only the designated reviewer may do this.",
  ],
  ACTIONS_REQUIRED: ["至少编制一项措施", "Add at least one action."],
  ACTIONS_INCOMPLETE: ["仍有未完成措施", "Actions remain incomplete."],
  OBSERVATION_NOT_FINISHED: [
    "尚未到最早效果验证日",
    "Observation date has not been reached.",
  ],
  INVALID_VERIFY_DATE: [
    "验证日期不得早于本轮措施截止日",
    "Verification date cannot precede action deadlines.",
  ],
  INVALID_DUE_DATE: [
    "日期不能早于今天或晚于问题截止日",
    "Date must be between today and the case deadline.",
  ],
  INVALID_DICTIONARY: ["来源或类别不存在", "Unknown source or category."],
  IDEMPOTENCY_CONFLICT: [
    "请求内容已改变，请重新打开操作",
    "Request payload changed. Reopen operation.",
  ],
  CONFLICT: ["数据重复或仍被其他记录引用", "Duplicate or referenced data."],
  LAST_ADMIN: [
    "须保留一个启用的全范围管理员",
    "Keep one active all-data administrator.",
  ],
  WEAK_PASSWORD: [
    "密码须 12–72 位并含大写、小写和数字",
    "Use 12–72 characters with upper case, lower case and digits.",
  ],
  OLD_PASSWORD_INVALID: ["当前密码不正确", "Current password is incorrect."],
  BUILTIN_RESOURCE: [
    "内建资源不能删除",
    "Built-in resource cannot be deleted.",
  ],
  NOT_FOUND: ["记录不存在", "Record not found."],
  INVALID_USERNAME: [
    "账号须为 3–60 位字母、数字、点、横线或下划线",
    "Invalid username.",
  ],
  INVALID_SCOPE: ["数据范围不正确", "Invalid data scope."],
  INVALID_PERMISSION: ["权限代码不存在", "Unknown permission code."],
  REGISTERED_PERMISSIONS_ONLY: [
    "只能编辑已登记权限的名称",
    "Only registered permission names may be edited.",
  ],
  REGISTERED_MENUS_ONLY: [
    "只能编辑已登记菜单",
    "Only registered menus may be edited.",
  ],
  REGISTERED_SETTINGS_ONLY: [
    "只能编辑已登记参数",
    "Only registered settings may be edited.",
  ],
  INVALID_SETTING: ["参数不正确", "Invalid setting."],
  INVALID_ACTION: [
    "措施不属于本轮整改",
    "Action does not belong to the current cycle.",
  ],
  INVALID_REQUEST_KEY: ["请重新打开操作", "Reopen the operation."],
};
/** 时间显示遵循系统参数，不改变业务日期。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function date(value, zone = "Asia/Shanghai") {
  return value
    ? new Intl.DateTimeFormat("zh-CN", {
        dateStyle: "short",
        timeStyle: "short",
        timeZone: zone,
      }).format(new Date(value))
    : "—";
}
/** 完成按钮依据执行责任、轮次、状态和权限，服务端仍独立校验。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function canComplete(a, c, me) {
  return (
    c.status === "EXECUTION" &&
    a.cycle === c.cycle &&
    a.status === "PENDING" &&
    a.ownerId === me.id &&
    me.permissions.includes("action.write")
  );
}

/** 将事件代码转换为读者可理解的操作名称。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function eventName(code, lang = "zh") {
  const fixed = {
    SAVE_DRAFT: ["保存报告草稿", "Save draft"],
    SAVE_ACTION: ["编制整改措施", "Save action"],
    DELETE_ACTION: ["移除未送审措施", "Remove action"],
    COMPLETE_ACTION: ["完成措施", "Complete action"],
    DELETE_DRAFT: ["删除报告草稿", "Delete draft"],
    LOGIN: ["登录", "Sign in"],
    PASSWORD_CHANGE: ["修改密码", "Change password"],
  };
  const value =
    fixed[code] || commands[code.toLowerCase().replaceAll("_", "-")];
  if (value) return value[lang === "zh" ? 0 : 1];
  if (code.startsWith("ADMIN_"))
    return (
      (lang === "zh" ? "系统管理 · " : "Administration · ") +
      code.substring(6).toLowerCase()
    );
  return code;
}
