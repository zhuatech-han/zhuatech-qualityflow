// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2
package cn.zhuatech.qualityflow;

import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/** 质量问题、措施、独立复核与观察期验证的事务闭环。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Service
@Transactional
public class QualityService {
  final Store db;
  final AccessService access;
  final Clock clock;
  final JsonMapper json = JsonMapper.builder().findAndAddModules().build();

  public QualityService(Store db, AccessService access, Clock clock) {
    this.db = db;
    this.access = access;
    this.clock = clock;
  }

  /** 报告输入，组织与责任归属经过实时校验。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record Draft(
      Long version,
      String title,
      String description,
      String source,
      String category,
      String severity,
      String reference,
      Long departmentId,
      Long ownerId,
      Long reviewerId,
      LocalDate dueDate) {}

  /** 带版本和幂等键的命令，不接受任意实体字段。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record Command(
      Long version,
      String requestKey,
      String note,
      String containment,
      String rootCause,
      String verificationPlan,
      LocalDate verifyAfter,
      LocalDate dueDate,
      String evidence) {}

  /** 整改或预防措施编制输入。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record ActionInput(
      Long version, String kind, String description, Long ownerId, LocalDate dueDate) {}

  /** 当前数据范围内的可选组织、账号和业务字典；不返回密码。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object options() {
    access.current();
    var people =
        db.all(Account.class).stream()
            .filter(a -> a.enabled && access.visible(a.departmentId))
            .map(
                a ->
                    Map.of(
                        "id",
                        a.id,
                        "displayName",
                        a.displayName,
                        "departmentId",
                        a.departmentId,
                        "permissions",
                        db.get(AccessRole.class, a.roleId).permissions))
            .toList();
    return Map.of(
        "people",
        people,
        "departments",
        db.all(Department.class).stream().filter(d -> access.visible(d.id)).toList(),
        "dictionaries",
        db.all(DictionaryEntry.class),
        "settings",
        db.all(SystemSetting.class));
  }

  /** 真实分页、搜索、状态和安全排序，ASSIGNED 仅看关联问题。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object list(String search, String status, int page, int size, String sort) {
    access.require("case.read");
    if (page < 0
        || page > 100000
        || size < 1
        || size > 100
        || search.length() > 200
        || !Set.of("newest", "due", "severity").contains(sort)
        || (!status.isBlank() && !states().contains(status)))
      throw new Problem(400, "INVALID_INPUT");
    var params = new ArrayList<Object>();
    String where = scope(params);
    if (!search.isBlank()) {
      params.add("%" + search.toLowerCase(Locale.ROOT) + "%");
      where +=
          " and (lower(c.title) like ?"
              + params.size()
              + " or lower(c.number) like ?"
              + params.size()
              + ")";
    }
    if (!status.isBlank()) {
      params.add(status);
      where += " and c.status=?" + params.size();
    }
    var count = db.jpql(Long.class, "select count(c) from QualityCase c where " + where);
    var q =
        db.jpql(
            QualityCase.class,
            "from QualityCase c where "
                + where
                + " order by "
                + switch (sort) {
                  case "due" -> "c.dueDate asc,c.id desc";
                  case "severity" ->
                      "case c.severity when 'CRITICAL' then 0 when 'MAJOR' then 1 else 2 end,c.id desc";
                  default -> "c.id desc";
                });
    for (int i = 0; i < params.size(); i++) {
      count.setParameter(i + 1, params.get(i));
      q.setParameter(i + 1, params.get(i));
    }
    return Map.of(
        "items",
        q.setFirstResult(page * size).setMaxResults(size).getResultList(),
        "total",
        count.getSingleResult(),
        "page",
        page,
        "size",
        size);
  }

  /** 建立或更新本人草稿；报告提交后问题描述与归属冻结。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object draft(Long id, Draft v) {
    access.require("case.write");
    var me = access.current();
    QualityCase c;
    if (id == null) {
      c = new QualityCase();
      c.reporterId = me.id;
      c.number = "NC-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase(Locale.ROOT);
      c.status = "DRAFT";
      c.cycle = 1;
      c.createdAt = clock.instant();
      c.containment = "";
      c.rootCause = "";
      c.verificationPlan = "";
      c.reviewNote = "";
      c.verificationEvidence = "";
    } else {
      c = locked(id);
      QualityPolicy.version(c, v.version);
      QualityPolicy.state(c, "DRAFT");
      if (!me.id.equals(c.reporterId)) throw new Problem(403, "NOT_REPORTER");
    }
    access.department(v.departmentId);
    db.get(Department.class, v.departmentId);
    if (!"ALL".equals(access.role().scope) && !me.departmentId.equals(v.departmentId))
      throw new Problem(403, "OUT_OF_SCOPE");
    c.departmentId = v.departmentId;
    assigned(v.ownerId, c, "case.work");
    assigned(v.reviewerId, c, "case.review");
    if (Objects.equals(v.ownerId, v.reviewerId) || Objects.equals(me.id, v.reviewerId))
      throw new Problem(400, "INDEPENDENT_REVIEW_REQUIRED");
    c.ownerId = v.ownerId;
    c.reviewerId = v.reviewerId;
    c.title = text(v.title, 200);
    c.description = text(v.description, 3000);
    dict("source", v.source);
    dict("category", v.category);
    c.source = v.source;
    c.category = v.category;
    if (v.severity == null || !Set.of("MINOR", "MAJOR", "CRITICAL").contains(v.severity))
      throw new Problem(400, "INVALID_INPUT");
    c.severity = v.severity;
    c.reference = optional(v.reference, 200);
    QualityPolicy.actionDate(v.dueDate, v.dueDate, today());
    c.dueDate = v.dueDate;
    if (id == null) db.save(c);
    event(c, "SAVE_DRAFT", c.status, "报告草稿");
    db.flush();
    return detail(c.id);
  }

  /** 删除本人草稿及其附属历史，已提交报告保留追踪。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void delete(Long id, Long version) {
    access.require("case.write");
    var c = locked(id);
    QualityPolicy.version(c, version);
    QualityPolicy.state(c, "DRAFT");
    if (!c.reporterId.equals(access.current().id)) throw new Problem(403, "NOT_REPORTER");
    access.audit("DELETE_DRAFT", id, c.departmentId);
    for (var e : events(c)) db.delete(e);
    db.delete(c);
  }

  /** 返回问题、当前及历史措施和服务端计算的可执行命令。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Map<String, Object> detail(Long id) {
    access.require("case.read");
    var c = db.get(QualityCase.class, id);
    visible(c);
    return Map.of("case", c, "actions", actions(c), "events", events(c), "commands", commands(c));
  }

  /** 编制措施，必须指定具备执行权限的同部门人员，复核人不能参与执行。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object saveAction(Long id, Long actionId, ActionInput v) {
    access.require("case.work");
    var c = locked(id);
    owner(c);
    QualityPolicy.state(c, "INVESTIGATION");
    QualityPolicy.version(c, v.version);
    if (v.kind == null || !Set.of("CORRECTIVE", "PREVENTIVE").contains(v.kind))
      throw new Problem(400, "INVALID_INPUT");
    assigned(v.ownerId, c, "action.write");
    if (v.ownerId.equals(c.reviewerId)) throw new Problem(400, "INDEPENDENT_REVIEW_REQUIRED");
    QualityPolicy.actionDate(v.dueDate, c.dueDate, today());
    var a = actionId == null ? new CorrectiveAction() : db.get(CorrectiveAction.class, actionId);
    if (actionId != null && (!a.caseId.equals(id) || a.cycle != c.cycle))
      throw new Problem(409, "INVALID_ACTION");
    a.caseId = id;
    a.cycle = c.cycle;
    a.kind = v.kind;
    a.description = text(v.description, 2000);
    a.ownerId = v.ownerId;
    a.dueDate = v.dueDate;
    a.status = "PENDING";
    a.evidence = "";
    if (actionId == null) db.save(a);
    event(c, "SAVE_ACTION", c.status, a.description);
    db.flush();
    return detail(id);
  }

  /** 删除尚未送审的本轮措施，历史轮次不能修改。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object deleteAction(Long id, Long actionId, Long version) {
    access.require("case.work");
    var c = locked(id);
    owner(c);
    QualityPolicy.state(c, "INVESTIGATION");
    QualityPolicy.version(c, version);
    var a = db.get(CorrectiveAction.class, actionId);
    if (!a.caseId.equals(id) || a.cycle != c.cycle) throw new Problem(409, "INVALID_ACTION");
    db.delete(a);
    event(c, "DELETE_ACTION", c.status, "移除未送审措施");
    db.flush();
    return detail(id);
  }

  /** 责任人完成指定措施，证据不可留空；行锁串行化并发请求。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object complete(Long id, Long actionId, Command v) {
    access.require("action.write");
    var c = locked(id);
    var a = db.get(CorrectiveAction.class, actionId);
    if (!a.caseId.equals(id) || a.cycle != c.cycle) throw new Problem(409, "INVALID_ACTION");
    if (!a.ownerId.equals(access.current().id)) throw new Problem(403, "NOT_ACTION_OWNER");
    if (replay(c, "complete:" + actionId, v)) return detail(id);
    QualityPolicy.version(c, v.version);
    QualityPolicy.state(c, "EXECUTION");
    if (a.status.equals("COMPLETED")) throw new Problem(409, "INVALID_STATE");
    a.evidence = text(v.evidence, 3000);
    a.status = "COMPLETED";
    a.completedAt = clock.instant();
    a.completedBy = access.current().id;
    event(c, "COMPLETE_ACTION", c.status, a.description + "\n" + a.evidence);
    db.flush();
    return detail(id);
  }

  /** 有限状态转换，复核者不得为报告、方案或措施执行者，失败验证进入新轮次。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object act(Long id, String action, Command v) {
    String permission =
        switch (action) {
          case "submit", "cancel" -> "case.write";
          case "approve-plan", "reject-plan", "verify", "fail-verification", "reopen" ->
              "case.review";
          case "begin", "analysis", "submit-plan", "submit-evidence", "reschedule" -> "case.work";
          default -> throw new Problem(404, "NOT_FOUND");
        };
    access.require(permission);
    var c = locked(id);
    if (replay(c, action, v)) return detail(id);
    QualityPolicy.version(c, v.version);
    String old = c.status;
    String note = optional(v.note, 2000);
    switch (action) {
      case "submit" -> {
        QualityPolicy.state(c, "DRAFT");
        if (!c.reporterId.equals(access.current().id)) throw new Problem(403, "NOT_REPORTER");
        assigned(c.ownerId, c, "case.work");
        assigned(c.reviewerId, c, "case.review");
        c.status = "TRIAGE";
      }
      case "cancel" -> {
        QualityPolicy.state(c, "DRAFT", "TRIAGE");
        if (!c.reporterId.equals(access.current().id)) throw new Problem(403, "NOT_REPORTER");
        note = text(v.note, 2000);
        c.status = "CANCELLED";
      }
      case "begin" -> {
        owner(c);
        QualityPolicy.state(c, "TRIAGE");
        c.containment = text(v.containment, 3000);
        note = c.containment;
        c.status = "INVESTIGATION";
      }
      case "analysis" -> {
        owner(c);
        QualityPolicy.state(c, "INVESTIGATION");
        c.rootCause = text(v.rootCause, 3000);
        c.verificationPlan = text(v.verificationPlan, 3000);
        if (v.verifyAfter == null || v.verifyAfter.isBefore(today()))
          throw new Problem(400, "INVALID_VERIFY_DATE");
        c.verifyAfter = v.verifyAfter;
        note = "原因：" + c.rootCause + "\n验证计划：" + c.verificationPlan;
      }
      case "reschedule" -> {
        owner(c);
        QualityPolicy.state(c, "INVESTIGATION");
        QualityPolicy.actionDate(v.dueDate, v.dueDate, today());
        note = text(v.note, 2000);
        if (currentActions(c).stream().anyMatch(a -> a.dueDate.isAfter(v.dueDate)))
          throw new Problem(400, "INVALID_DUE_DATE");
        c.dueDate = v.dueDate;
        note = "截止日 " + v.dueDate + "\n" + note;
      }
      case "submit-plan" -> {
        owner(c);
        QualityPolicy.state(c, "INVESTIGATION");
        text(c.rootCause, 3000);
        text(c.verificationPlan, 3000);
        if (c.verifyAfter == null) throw new Problem(400, "INVALID_VERIFY_DATE");
        var items = currentActions(c);
        if (items.isEmpty()) throw new Problem(400, "ACTIONS_REQUIRED");
        for (var a : items) {
          assigned(a.ownerId, c, "action.write");
          if (c.verifyAfter.isBefore(a.dueDate)) throw new Problem(400, "INVALID_VERIFY_DATE");
        }
        c.status = "PLAN_REVIEW";
      }
      case "approve-plan" -> {
        reviewer(c);
        QualityPolicy.state(c, "PLAN_REVIEW");
        note = text(v.note, 2000);
        c.reviewNote = note;
        c.status = "EXECUTION";
      }
      case "reject-plan" -> {
        reviewer(c);
        QualityPolicy.state(c, "PLAN_REVIEW");
        note = text(v.note, 2000);
        c.reviewNote = note;
        c.status = "INVESTIGATION";
      }
      case "submit-evidence" -> {
        owner(c);
        QualityPolicy.state(c, "EXECUTION");
        var items = currentActions(c);
        if (items.isEmpty() || items.stream().anyMatch(a -> !a.status.equals("COMPLETED")))
          throw new Problem(409, "ACTIONS_INCOMPLETE");
        note = text(v.evidence, 3000);
        c.verificationEvidence = note;
        c.status = "VERIFY_READY";
      }
      case "verify" -> {
        reviewer(c);
        QualityPolicy.state(c, "VERIFY_READY");
        if (today().isBefore(c.verifyAfter)) throw new Problem(409, "OBSERVATION_NOT_FINISHED");
        note = text(v.evidence, 3000);
        c.verificationEvidence = note;
        c.closedAt = clock.instant();
        c.closedBy = access.current().id;
        c.status = "CLOSED";
      }
      case "fail-verification" -> {
        reviewer(c);
        QualityPolicy.state(c, "VERIFY_READY");
        note = text(v.evidence, 3000);
        newCycle(c);
        c.status = "INVESTIGATION";
      }
      case "reopen" -> {
        reviewer(c);
        QualityPolicy.state(c, "CLOSED");
        note = text(v.note, 2000);
        newCycle(c);
        c.status = "INVESTIGATION";
      }
      default -> throw new Problem(404, "NOT_FOUND");
    }
    event(c, action.toUpperCase(Locale.ROOT), old, note);
    db.flush();
    return detail(id);
  }

  /** 工作台返回本人当前责任事项，关闭记录不会混入待办。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object workbench() {
    access.require("case.read");
    var me = access.current();
    var rows = scoped();
    var pending =
        rows.stream()
            .filter(
                c -> commands(c).stream().anyMatch(x -> !Set.of("reopen", "cancel").contains(x)))
            .toList();
    var actions =
        rows.stream()
            .filter(c -> c.status.equals("EXECUTION"))
            .flatMap(c -> currentActions(c).stream())
            .filter(a -> a.ownerId.equals(me.id) && !a.status.equals("COMPLETED"))
            .toList();
    return Map.of("cases", pending, "actions", actions);
  }

  /** 聚合当前授权范围的质量状态、严重度、来源和逾期。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object dashboard() {
    access.require("dashboard");
    var rows = scoped();
    var counts = new TreeMap<String, Long>();
    var severities = new TreeMap<String, Long>();
    var sources = new TreeMap<String, Long>();
    for (var c : rows) {
      counts.merge(c.status, 1L, Long::sum);
      severities.merge(c.severity, 1L, Long::sum);
      sources.merge(c.source, 1L, Long::sum);
    }
    return Map.of(
        "total",
        rows.size(),
        "overdue",
        rows.stream().filter(c -> QualityPolicy.overdue(c, today())).count(),
        "reopened",
        rows.stream().filter(c -> c.cycle > 1).count(),
        "status",
        counts,
        "severity",
        severities,
        "source",
        sources);
  }

  /** 导出完整报告及各轮记录，内容不插入品牌广告。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public String export(Long id) {
    access.require("export");
    return json.writeValueAsString(
        Map.of(
            "schemaVersion",
            "1.0",
            "kind",
            "QUALITY_CORRECTIVE_ACTION_REPORT",
            "report",
            detail(id)));
  }

  /** 审计按角色部门隔离，ASSIGNED 无全部门审计权。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object audit() {
    access.require("audit");
    if (access.role().scope.equals("ASSIGNED"))
      return db.query(
          AuditEvent.class,
          "from AuditEvent where actor=?1 order by id desc",
          access.current().username);
    if (access.role().scope.equals("ALL"))
      return db.query(AuditEvent.class, "from AuditEvent order by id desc");
    return db.query(
        AuditEvent.class,
        "from AuditEvent where departmentId=?1 order by id desc",
        access.current().departmentId);
  }

  private String scope(List<Object> p) {
    var a = access.current();
    String scope = access.role().scope;
    if (scope.equals("ALL")) return "1=1";
    p.add(a.departmentId);
    String w = "c.departmentId=?1";
    if (scope.equals("ASSIGNED")) {
      p.add(a.id);
      w +=
          " and (c.reporterId=?2 or c.ownerId=?2 or c.reviewerId=?2 or exists (select x.id from CorrectiveAction x where x.caseId=c.id and x.ownerId=?2))";
    }
    return w;
  }

  private List<QualityCase> scoped() {
    access.require("case.read");
    var p = new ArrayList<Object>();
    return db.query(QualityCase.class, "from QualityCase c where " + scope(p), p.toArray());
  }

  private void visible(QualityCase c) {
    access.department(c.departmentId);
    if (access.role().scope.equals("ASSIGNED")) {
      long me = access.current().id;
      if (c.reporterId != me
          && c.ownerId != me
          && c.reviewerId != me
          && actions(c).stream().noneMatch(a -> a.ownerId == me))
        throw new Problem(403, "OUT_OF_SCOPE");
    }
  }

  private QualityCase locked(Long id) {
    var c = db.lock(QualityCase.class, id);
    visible(c);
    return c;
  }

  private void owner(QualityCase c) {
    if (!c.ownerId.equals(access.current().id)) throw new Problem(403, "NOT_CASE_OWNER");
  }

  private void reviewer(QualityCase c) {
    long me = access.current().id;
    if (c.reviewerId != me) throw new Problem(403, "NOT_REVIEWER");
    if (c.reporterId == me
        || c.ownerId == me
        || actions(c).stream().anyMatch(a -> a.ownerId == me || Objects.equals(a.completedBy, me)))
      throw new Problem(403, "INDEPENDENT_REVIEW_REQUIRED");
  }

  private void assigned(Long id, QualityCase c, String permission) {
    var a = db.get(Account.class, id);
    if (!a.enabled
        || !a.departmentId.equals(c.departmentId)
        || !db.get(AccessRole.class, a.roleId).permissions.contains(permission))
      throw new Problem(400, "INVALID_ASSIGNEE");
  }

  private List<CorrectiveAction> actions(QualityCase c) {
    return db.query(
        CorrectiveAction.class,
        "from CorrectiveAction where caseId=?1 order by cycle desc,id asc",
        c.id);
  }

  private List<CorrectiveAction> currentActions(QualityCase c) {
    return actions(c).stream().filter(a -> a.cycle == c.cycle).toList();
  }

  private List<CaseEvent> events(QualityCase c) {
    return db.query(CaseEvent.class, "from CaseEvent where caseId=?1 order by id desc", c.id);
  }

  private List<String> commands(QualityCase c) {
    var me = access.current();
    var perms = access.role().permissions;
    var out = new ArrayList<String>();
    if (perms.contains("case.write") && me.id.equals(c.reporterId)) {
      if (c.status.equals("DRAFT")) out.add("submit");
      if (Set.of("DRAFT", "TRIAGE").contains(c.status)) out.add("cancel");
    }
    if (perms.contains("case.work") && me.id.equals(c.ownerId))
      switch (c.status) {
        case "TRIAGE" -> out.add("begin");
        case "INVESTIGATION" -> out.addAll(List.of("analysis", "submit-plan", "reschedule"));
        case "EXECUTION" -> out.add("submit-evidence");
        default -> {}
      }
    if (perms.contains("case.review")
        && me.id.equals(c.reviewerId)
        && !me.id.equals(c.reporterId)
        && !me.id.equals(c.ownerId)
        && actions(c).stream()
            .noneMatch(a -> a.ownerId.equals(me.id) || Objects.equals(a.completedBy, me.id)))
      switch (c.status) {
        case "PLAN_REVIEW" -> out.addAll(List.of("approve-plan", "reject-plan"));
        case "VERIFY_READY" -> out.addAll(List.of("verify", "fail-verification"));
        case "CLOSED" -> out.add("reopen");
        default -> {}
      }
    return out;
  }

  private void event(QualityCase c, String action, String from, String note) {
    c.changeCount++;
    var e = new CaseEvent();
    e.caseId = c.id;
    e.cycle = c.cycle;
    e.actor = access.current().username;
    e.action = action;
    e.fromStatus = from;
    e.toStatus = c.status;
    e.note = note;
    e.createdAt = clock.instant();
    db.save(e);
    access.audit(action, c.id, c.departmentId);
  }

  private boolean replay(QualityCase c, String action, Command v) {
    String key = text(v.requestKey, 80);
    if (!key.matches("[a-zA-Z0-9_-]{8,80}")) throw new Problem(400, "INVALID_REQUEST_KEY");
    String fingerprint;
    try {
      fingerprint =
          HexFormat.of()
              .formatHex(
                  MessageDigest.getInstance("SHA-256")
                      .digest(
                          (action + json.writeValueAsString(v))
                              .getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
    var rows =
        db.query(
            MutationStamp.class,
            "from MutationStamp where caseId=?1 and actor=?2 and requestKey=?3",
            c.id,
            access.current().username,
            key);
    if (!rows.isEmpty()) {
      if (!rows.getFirst().fingerprint.equals(fingerprint))
        throw new Problem(409, "IDEMPOTENCY_CONFLICT");
      return true;
    }
    var s = new MutationStamp();
    s.caseId = c.id;
    s.actor = access.current().username;
    s.requestKey = key;
    s.fingerprint = fingerprint;
    db.save(s);
    return false;
  }

  private void newCycle(QualityCase c) {
    c.cycle++;
    c.rootCause = "";
    c.verificationPlan = "";
    c.verifyAfter = null;
    c.reviewNote = "";
    c.verificationEvidence = "";
    c.closedAt = null;
    c.closedBy = null;
  }

  private void dict(String type, String code) {
    if (db.query(
            DictionaryEntry.class, "from DictionaryEntry where type=?1 and code=?2", type, code)
        .isEmpty()) throw new Problem(400, "INVALID_DICTIONARY");
  }

  private LocalDate today() {
    return LocalDate.now(
        clock.withZone(
            ZoneId.of(
                db.query(SystemSetting.class, "from SystemSetting where code='timezone'")
                    .getFirst()
                    .value)));
  }

  private static Set<String> states() {
    return Set.of(
        "DRAFT",
        "TRIAGE",
        "INVESTIGATION",
        "PLAN_REVIEW",
        "EXECUTION",
        "VERIFY_READY",
        "CLOSED",
        "CANCELLED");
  }

  private static String text(String v, int n) {
    return AdminService.text(v, n);
  }

  private static String optional(String v, int n) {
    if (v == null) return "";
    if (v.length() > n) throw new Problem(400, "INVALID_INPUT");
    return v.trim();
  }
}
