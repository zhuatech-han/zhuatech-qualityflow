// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2
package cn.zhuatech.qualityflow;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 真实 HTTP、迁移、指派隔离、独立复核、并发重试与多轮整改验收。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@SpringBootTest
@AutoConfigureMockMvc
@org.springframework.context.annotation.Import(QualityIntegrationTest.TimeConfig.class)
class QualityIntegrationTest {
  static final String password = "Aa9" + UUID.randomUUID();

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry r) {
    r.add("qualityflow.admin-password", () -> password);
  }

  /** 验收时钟不会修改生产时间。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @org.springframework.boot.test.context.TestConfiguration
  static class TimeConfig {
    @org.springframework.context.annotation.Bean
    @org.springframework.context.annotation.Primary
    TestClock testClock() {
      return new TestClock();
    }
  }

  /** 可推进日期的业务时钟。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  static class TestClock extends Clock {
    volatile Instant now = Instant.now();

    /** 当前验收时间。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
    @Override
    public Instant instant() {
      return now;
    }

    /** 固定 UTC。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    /** 返回具有指定时区的时钟视图。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
    @Override
    public Clock withZone(ZoneId zone) {
      return Clock.fixed(now, zone);
    }
  }

  @Autowired MockMvc mvc;
  @Autowired TestClock clock;
  final JsonMapper json = JsonMapper.builder().findAndAddModules().build();
  MockHttpSession admin, reporter, owner, worker, reviewer, stranger;
  long dept, ownerId, workerId, reviewerId, id, coordRole, execRole, reviewRole;
  JsonNode d;
  String ownerName;

  LocalDate today() {
    return LocalDate.ofInstant(clock.now, ZoneId.of("Asia/Shanghai"));
  }

  @BeforeEach
  void setup() throws Exception {
    clock.now = Instant.now();
    admin = login("admin", password);
    String s = UUID.randomUUID().toString().substring(0, 8);
    dept = ok(admin, "POST", "/admin/departments", Map.of("name", "质量验收-" + s)).path("id").asLong();
    for (var r : ok(admin, "GET", "/admin/roles", null))
      switch (r.path("name").asString()) {
        case "质量协调员" -> coordRole = r.path("id").asLong();
        case "整改执行人" -> execRole = r.path("id").asLong();
        case "质量复核人" -> reviewRole = r.path("id").asLong();
        default -> {}
      }
    long rid = user("report-" + s, coordRole);
    reporter = login("report-" + s, password);
    ownerName = "owner-" + s;
    ownerId = user(ownerName, execRole);
    owner = login(ownerName, password);
    workerId = user("worker-" + s, execRole);
    worker = login("worker-" + s, password);
    reviewerId = user("review-" + s, reviewRole);
    reviewer = login("review-" + s, password);
    user("stranger-" + s, execRole);
    stranger = login("stranger-" + s, password);
    d = ok(reporter, "POST", "/cases", draft());
    id = d.path("case").path("id").asLong();
    assertNotEquals(rid, reviewerId);
  }

  private long user(String name, long role) throws Exception {
    return ok(
            admin,
            "POST",
            "/admin/users",
            Map.of(
                "username",
                name,
                "displayName",
                name,
                "password",
                password,
                "roleId",
                role,
                "departmentId",
                dept,
                "enabled",
                true))
        .path("id")
        .asLong();
  }

  private Map<String, Object> draft() {
    return new LinkedHashMap<>(
        Map.of(
            "title",
            "验收：过程偏差",
            "description",
            "虚构测试资料，不代表实际质量结论",
            "source",
            "INTERNAL",
            "category",
            "PROCESS",
            "severity",
            "MAJOR",
            "departmentId",
            dept,
            "ownerId",
            ownerId,
            "reviewerId",
            reviewerId,
            "dueDate",
            today().plusDays(3).toString()));
  }

  private Map<String, Object> command() {
    return new LinkedHashMap<>(
        Map.of(
            "version",
            d.path("case").path("version").asLong(),
            "requestKey",
            UUID.randomUUID().toString(),
            "note",
            "验收复核意见"));
  }

  private void act(MockHttpSession s, String action, Map<String, Object> c) throws Exception {
    d = ok(s, "POST", "/cases/" + id + "/" + action, c);
  }

  private void act(MockHttpSession s, String action) throws Exception {
    act(s, action, command());
  }

  private void begin() throws Exception {
    act(reporter, "submit");
    var c = command();
    c.put("containment", "验收：隔离异常流程并登记影响范围");
    act(owner, "begin", c);
  }

  private void analysis() throws Exception {
    var c = command();
    c.put("rootCause", "验收：控制点缺少复核");
    c.put("verificationPlan", "验收：连续抽查十次，确认复核完成");
    c.put("verifyAfter", today().plusDays(1).toString());
    act(owner, "analysis", c);
  }

  private long addAction() throws Exception {
    d =
        ok(
            owner,
            "POST",
            "/cases/" + id + "/actions",
            Map.of(
                "version",
                d.path("case").path("version").asLong(),
                "kind",
                "CORRECTIVE",
                "description",
                "验收：更新复核规程",
                "ownerId",
                workerId,
                "dueDate",
                today().toString()));
    return d.path("actions").get(0).path("id").asLong();
  }

  private long execution() throws Exception {
    begin();
    analysis();
    long aid = addAction();
    act(owner, "submit-plan");
    act(reviewer, "approve-plan");
    return aid;
  }

  private void ready() throws Exception {
    long aid = execution();
    var c = command();
    c.put("evidence", "验收：规程更新和执行记录已核对");
    d = ok(worker, "POST", "/cases/" + id + "/actions/" + aid + "/complete", c);
    c = command();
    c.put("evidence", "验收：全部措施完成，进入效果观察");
    act(owner, "submit-evidence", c);
  }

  private void close() throws Exception {
    ready();
    clock.now = clock.now.plusSeconds(86401);
    var c = command();
    c.put("evidence", "验收：十次抽查均按规程复核");
    act(reviewer, "verify", c);
  }

  private MockHttpSession login(String name, String value) throws Exception {
    var r = request(null, "POST", "/auth/login", Map.of("username", name, "password", value), true);
    assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
    return (MockHttpSession) r.getRequest().getSession();
  }

  private MvcResult request(
      MockHttpSession s, String method, String path, Object body, boolean token) throws Exception {
    var b =
        switch (method) {
          case "POST" -> post("/api" + path);
          case "PUT" -> put("/api" + path);
          case "DELETE" -> delete("/api" + path);
          default -> get("/api" + path);
        };
    if (s != null) b.session(s);
    if (token) b.with(csrf());
    if (body != null) b.contentType("application/json").content(json.writeValueAsString(body));
    return mvc.perform(b).andReturn();
  }

  private JsonNode ok(MockHttpSession s, String method, String path, Object body) throws Exception {
    var r = request(s, method, path, body, true);
    assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
    return json.readTree(r.getResponse().getContentAsString());
  }

  private void denied(MockHttpSession s, String method, String path, Object body, int status)
      throws Exception {
    var r = request(s, method, path, body, true);
    assertEquals(status, r.getResponse().getStatus(), r.getResponse().getContentAsString());
  }

  @Test
  void fullFlowAndReport() throws Exception {
    close();
    assertEquals("CLOSED", d.path("case").path("status").asString());
    var report = ok(owner, "GET", "/cases/" + id + "/report.json", null);
    assertEquals("QUALITY_CORRECTIVE_ACTION_REPORT", report.path("kind").asString());
    assertFalse(json.writeValueAsString(report).contains("zhuatech2"));
    assertTrue(report.path("report").path("events").size() >= 8);
  }

  @Test
  void observationGate() throws Exception {
    ready();
    var c = command();
    c.put("evidence", "过早验证");
    denied(reviewer, "POST", "/cases/" + id + "/verify", c, 409);
    assertEquals(
        "VERIFY_READY",
        ok(owner, "GET", "/cases/" + id, null).path("case").path("status").asString());
  }

  @Test
  void incompleteActionsCannotSubmit() throws Exception {
    execution();
    var c = command();
    c.put("evidence", "虚构");
    denied(owner, "POST", "/cases/" + id + "/submit-evidence", c, 409);
  }

  @Test
  void assignedIsolationIncludesListExportAndDashboard() throws Exception {
    denied(stranger, "GET", "/cases/" + id, null, 403);
    denied(stranger, "GET", "/cases/" + id + "/report.json", null, 403);
    assertEquals(0, ok(stranger, "GET", "/cases", null).path("total").asLong());
    assertEquals(0, ok(stranger, "GET", "/dashboard", null).path("total").asLong());
  }

  @Test
  void departmentIsolation() throws Exception {
    long old = dept;
    dept =
        ok(admin, "POST", "/admin/departments", Map.of("name", "另一部门-" + UUID.randomUUID()))
            .path("id")
            .asLong();
    String n = "other-" + UUID.randomUUID().toString().substring(0, 8);
    user(n, coordRole);
    var other = login(n, password);
    dept = old;
    denied(other, "GET", "/cases/" + id, null, 403);
    assertEquals(0, ok(other, "GET", "/cases", null).path("total").asLong());
  }

  @Test
  void cannotAssignReviewerToOwnerOrReporter() throws Exception {
    var c = draft();
    c.put("reviewerId", ownerId);
    denied(reporter, "POST", "/cases", c, 400);
  }

  @Test
  void reviewerCannotOwnAction() throws Exception {
    begin();
    analysis();
    var a =
        new LinkedHashMap<String, Object>(
            Map.of(
                "version",
                d.path("case").path("version").asLong(),
                "kind",
                "CORRECTIVE",
                "description",
                "验收",
                "ownerId",
                reviewerId,
                "dueDate",
                today().toString()));
    denied(owner, "POST", "/cases/" + id + "/actions", a, 400);
  }

  @Test
  void onlyActionOwnerCanCompleteEvenAdmin() throws Exception {
    long aid = execution();
    var c = command();
    c.put("evidence", "伪代填");
    denied(owner, "POST", "/cases/" + id + "/actions/" + aid + "/complete", c, 403);
    denied(admin, "POST", "/cases/" + id + "/actions/" + aid + "/complete", c, 403);
  }

  @Test
  void onlyDesignatedReviewerMayApprove() throws Exception {
    begin();
    analysis();
    addAction();
    act(owner, "submit-plan");
    denied(admin, "POST", "/cases/" + id + "/approve-plan", command(), 403);
    denied(owner, "POST", "/cases/" + id + "/approve-plan", command(), 403);
  }

  @Test
  void planRequiresCauseAndActions() throws Exception {
    begin();
    denied(owner, "POST", "/cases/" + id + "/submit-plan", command(), 400);
    analysis();
    denied(owner, "POST", "/cases/" + id + "/submit-plan", command(), 400);
  }

  @Test
  void planRejectedAndResubmitted() throws Exception {
    begin();
    analysis();
    addAction();
    act(owner, "submit-plan");
    act(reviewer, "reject-plan");
    assertEquals("INVESTIGATION", d.path("case").path("status").asString());
    act(owner, "submit-plan");
    act(reviewer, "approve-plan");
  }

  @Test
  void verificationFailurePreservesEarlierActionsAndStartsNewCycle() throws Exception {
    ready();
    var c = command();
    c.put("evidence", "验收：复核仍有一次不符合");
    act(reviewer, "fail-verification", c);
    assertEquals(2, d.path("case").path("cycle").asInt());
    assertEquals("COMPLETED", d.path("actions").get(0).path("status").asString());
    analysis();
    denied(owner, "POST", "/cases/" + id + "/submit-plan", command(), 400);
  }

  @Test
  void reopenRetainsClosedEvidence() throws Exception {
    close();
    act(reviewer, "reopen");
    assertEquals(2, d.path("case").path("cycle").asInt());
    assertTrue(json.writeValueAsString(d.path("events")).contains("十次抽查"));
    assertTrue(d.path("case").path("closedAt").isNull());
  }

  @Test
  void staleVersionAndPublishedFreeze() throws Exception {
    var c = draft();
    c.put("version", -1);
    denied(reporter, "PUT", "/cases/" + id, c, 409);
    act(reporter, "submit");
    c.put("version", d.path("case").path("version").asLong());
    denied(reporter, "PUT", "/cases/" + id, c, 409);
    denied(reporter, "DELETE", "/cases/" + id + "?version=" + c.get("version"), null, 409);
  }

  @Test
  void submittedPlanFreezesMeasures() throws Exception {
    long aid = execution();
    denied(
        owner,
        "DELETE",
        "/cases/" + id + "/actions/" + aid + "?version=" + d.path("case").path("version").asLong(),
        null,
        409);
  }

  @Test
  void retriesAreIdempotentAndPayloadMismatchRejected() throws Exception {
    var c = command();
    act(reporter, "submit", c);
    int n = d.path("events").size();
    act(reporter, "submit", c);
    assertEquals(n, d.path("events").size());
    c.put("note", "different");
    denied(reporter, "POST", "/cases/" + id + "/submit", c, 409);
  }

  @Test
  void concurrentRetriesOnlySubmitOnce() throws Exception {
    var c = command();
    try (var pool = Executors.newFixedThreadPool(2)) {
      var tasks =
          List.<Callable<Integer>>of(
              () ->
                  request(reporter, "POST", "/cases/" + id + "/submit", c, true)
                      .getResponse()
                      .getStatus(),
              () ->
                  request(reporter, "POST", "/cases/" + id + "/submit", c, true)
                      .getResponse()
                      .getStatus());
      for (var r : pool.invokeAll(tasks)) assertEquals(200, r.get());
    }
    assertEquals(2, ok(reporter, "GET", "/cases/" + id, null).path("events").size());
  }

  @Test
  void csrfAnonymousAndAdminBoundaries() throws Exception {
    denied(null, "GET", "/cases", null, 401);
    denied(owner, "GET", "/admin/users", null, 403);
    assertEquals(
        403,
        request(admin, "POST", "/admin/departments", Map.of("name", "CSRF验收"), false)
            .getResponse()
            .getStatus());
  }

  @Test
  void lastAdminAndCredentialResponse() throws Exception {
    var users = ok(admin, "GET", "/admin/users", null);
    for (var a : users) {
      assertFalse(a.has("passwordHash"));
      if (a.path("username").asString().equals("admin"))
        denied(admin, "DELETE", "/admin/users/" + a.path("id").asLong(), null, 409);
    }
  }

  @Test
  void paginationSortingAndNoInjection() throws Exception {
    assertEquals(
        1, ok(reporter, "GET", "/cases?search=验收&size=1&sort=severity", null).path("items").size());
    denied(reporter, "GET", "/cases?sort=title%20desc", null, 400);
    denied(reporter, "GET", "/cases?size=1000", null, 400);
  }

  @Test
  void passwordRotationInvalidatesPriorLogin() throws Exception {
    var second = login(ownerName, password);
    String next = "Bb8" + UUID.randomUUID();
    ok(owner, "POST", "/auth/password", Map.of("oldPassword", password, "newPassword", next));
    assertTrue(owner.isInvalid());
    denied(second, "GET", "/auth/me", null, 401);
    login(ownerName, next);
  }

  @Test
  void draftCrudAndCancellation() throws Exception {
    var input = draft();
    input.put("version", d.path("case").path("version").asLong());
    input.put("title", "已修改草稿");
    d = ok(reporter, "PUT", "/cases/" + id, input);
    assertEquals("已修改草稿", d.path("case").path("title").asString());
    ok(
        reporter,
        "DELETE",
        "/cases/" + id + "?version=" + d.path("case").path("version").asLong(),
        null);
    denied(reporter, "GET", "/cases/" + id, null, 404);
    d = ok(reporter, "POST", "/cases", draft());
    id = d.path("case").path("id").asLong();
    act(reporter, "cancel");
    assertEquals("CANCELLED", d.path("case").path("status").asString());
  }

  @Test
  void measuresCrudAndEvidenceRequired() throws Exception {
    begin();
    analysis();
    long aid = addAction();
    var v =
        Map.of(
            "version",
            d.path("case").path("version").asLong(),
            "kind",
            "PREVENTIVE",
            "description",
            "验收预防措施",
            "ownerId",
            workerId,
            "dueDate",
            today().toString());
    d = ok(owner, "PUT", "/cases/" + id + "/actions/" + aid, v);
    assertEquals("PREVENTIVE", d.path("actions").get(0).path("kind").asString());
    d =
        ok(
            owner,
            "DELETE",
            "/cases/"
                + id
                + "/actions/"
                + aid
                + "?version="
                + d.path("case").path("version").asLong(),
            null);
    assertEquals(0, d.path("actions").size());
    aid = addAction();
    act(owner, "submit-plan");
    act(reviewer, "approve-plan");
    denied(worker, "POST", "/cases/" + id + "/actions/" + aid + "/complete", command(), 400);
  }

  @Test
  void maximumAnalysisRetainedWithoutTruncation() throws Exception {
    begin();
    var c = command();
    String root = "因".repeat(3000), plan = "验".repeat(3000);
    c.put("rootCause", root);
    c.put("verificationPlan", plan);
    c.put("verifyAfter", today().plusDays(1).toString());
    act(owner, "analysis", c);
    assertEquals(root, d.path("case").path("rootCause").asString());
    String history = d.path("events").get(0).path("note").asString();
    assertTrue(history.contains(root));
    assertTrue(history.contains(plan));
  }
}
