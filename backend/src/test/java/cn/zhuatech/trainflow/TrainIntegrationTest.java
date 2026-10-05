// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.trainflow;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 培训事务、评分、隔离、复核、版本、幂等与并发考试测试。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TrainIntegrationTest.TimeConfig.class)
class TrainIntegrationTest {
  static final String password = "Aa9" + UUID.randomUUID();

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry r) {
    r.add("trainflow.admin-password", () -> password);
  }

  /** 测试专用时间，不向运行应用暴露修改时钟入口。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  static class MutableClock extends Clock {
    volatile Instant value = Instant.parse("2026-10-05T02:00:00Z");

    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    public Clock withZone(ZoneId z) {
      return this;
    }

    public Instant instant() {
      return value;
    }
  }

  /** 固定业务日期以验证过期边界。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @TestConfiguration
  static class TimeConfig {
    @Bean
    @Primary
    MutableClock testClock() {
      return new MutableClock();
    }
  }

  @Autowired MockMvc mvc;
  @Autowired MutableClock clock;
  final JsonMapper json = JsonMapper.builder().findAndAddModules().build();
  MockHttpSession admin, writer, reviewer, learner, other;
  long dept, course, learnerId, reviewerId, writerId;
  String suffix;

  @BeforeEach
  void setup() throws Exception {
    clock.value = Instant.parse("2026-10-05T02:00:00Z");
    admin = login("admin", password);
    suffix = UUID.randomUUID().toString().substring(0, 8);
    dept =
        ok(admin, "POST", "/admin/departments", Map.of("name", "TEST 培训-" + suffix))
            .path("id")
            .asLong();
    long w = 0, r = 0, l = 0;
    for (var x : ok(admin, "GET", "/admin/roles", null)) {
      switch (x.path("name").asString()) {
        case "培训管理员" -> w = x.path("id").asLong();
        case "培训复核员" -> r = x.path("id").asLong();
        case "学员" -> l = x.path("id").asLong();
      }
    }
    writerId = user("writer-" + suffix, w, dept);
    reviewerId = user("review-" + suffix, r, dept);
    learnerId = user("learn-" + suffix, l, dept);
    user("other-" + suffix, l, dept);
    writer = login("writer-" + suffix, password);
    reviewer = login("review-" + suffix, password);
    learner = login("learn-" + suffix, password);
    other = login("other-" + suffix, password);
    course = ok(writer, "POST", "/courses", input()).path("id").asLong();
  }

  long user(String n, long role, long d) throws Exception {
    return ok(
            admin,
            "POST",
            "/admin/users",
            Map.of(
                "username",
                n,
                "displayName",
                n,
                "password",
                password,
                "roleId",
                role,
                "departmentId",
                d,
                "enabled",
                true))
        .path("id")
        .asLong();
  }

  Map<String, Object> input() {
    var m = new HashMap<String, Object>();
    m.put("familyCode", "T-" + suffix);
    m.put("title", "TEST 操作培训");
    m.put("category", "OPERATIONS");
    m.put("departmentId", dept);
    m.put("content", "TEST 正文：完成核对后记录结果。");
    m.put("passScore", 80);
    m.put("maxAttempts", 2);
    m.put("validDays", 2);
    m.put("practicalRequired", true);
    m.put(
        "questions",
        List.of(
            Map.of(
                "prompt", "先做什么", "options", List.of("核对", "忽略", "猜测", "跳过"), "correctAnswer", 0),
            Map.of(
                "prompt",
                "最后做什么",
                "options",
                List.of("丢弃", "记录", "跳过", "猜测"),
                "correctAnswer",
                1)));
    return m;
  }

  Map<String, Object> command(JsonNode r) {
    var m = new HashMap<String, Object>();
    m.put("version", r.path("version").asLong());
    m.put("requestKey", UUID.randomUUID().toString());
    m.put("note", "TEST 独立复核依据");
    return m;
  }

  JsonNode c() throws Exception {
    return ok(writer, "GET", "/courses/" + course, null).path("record");
  }

  JsonNode e(long id) throws Exception {
    return ok(writer, "GET", "/enrollments/" + id, null).path("record");
  }

  JsonNode act(MockHttpSession s, String kind, JsonNode r, String a) throws Exception {
    return ok(s, "POST", "/" + kind + "/" + r.path("id").asLong() + "/commands/" + a, command(r));
  }

  void publish() throws Exception {
    act(reviewer, "courses", c(), "publish");
  }

  Map<String, Object> assignment() throws Exception {
    var m = command(c());
    m.put("learnerId", learnerId);
    m.put("reviewerId", reviewerId);
    m.put("dueDate", "2026-10-06");
    return m;
  }

  JsonNode assign() throws Exception {
    publish();
    return ok(writer, "POST", "/courses/" + course + "/assign", assignment());
  }

  JsonNode exam(JsonNode r, List<Integer> a) throws Exception {
    var m = command(r);
    m.put("answers", a);
    return ok(learner, "POST", "/enrollments/" + r.path("id").asLong() + "/commands/exam", m);
  }

  JsonNode practical() throws Exception {
    var e = assign();
    e = act(learner, "enrollments", e, "read");
    return exam(e, List.of(0, 1));
  }

  MockHttpSession login(String n, String p) throws Exception {
    var res =
        mvc.perform(
                post("/api/auth/login")
                    .with(csrf())
                    .contentType("application/json")
                    .content(json.writeValueAsString(Map.of("username", n, "password", p))))
            .andReturn();
    assertEquals(200, res.getResponse().getStatus());
    return (MockHttpSession) res.getRequest().getSession(false);
  }

  JsonNode request(MockHttpSession s, String method, String path, Object body, int expected)
      throws Exception {
    var b =
        switch (method) {
          case "GET" -> get("/api" + path);
          case "POST" -> post("/api" + path);
          case "PUT" -> put("/api" + path);
          default -> delete("/api" + path);
        };
    if (s != null) b.session(s);
    if (!method.equals("GET")) b.with(csrf());
    if (body != null) b.contentType("application/json").content(json.writeValueAsString(body));
    var res = mvc.perform(b).andReturn().getResponse();
    assertEquals(expected, res.getStatus(), path + " " + res.getContentAsString());
    return json.readTree(res.getContentAsString());
  }

  JsonNode ok(MockHttpSession s, String m, String p, Object b) throws Exception {
    return request(s, m, p, b, 200);
  }

  @Test
  void completeTrainingAndQualification() throws Exception {
    var r = practical();
    assertEquals("PRACTICAL", r.path("status").asString());
    r = act(reviewer, "enrollments", r, "pass");
    assertEquals("QUALIFIED", r.path("status").asString());
    assertEquals("2026-10-06", r.path("validUntil").asString());
    assertEquals(100, r.path("bestScore").asInt());
  }

  @Test
  void authorCannotSelfPublish() throws Exception {
    request(writer, "POST", "/courses/" + course + "/commands/publish", command(c()), 403);
  }

  @Test
  void unpublishedCourseCannotAssign() throws Exception {
    request(writer, "POST", "/courses/" + course + "/assign", assignment(), 409);
  }

  @Test
  void learnerCannotReadOthersOrAnswers() throws Exception {
    var r = assign();
    String details = ok(learner, "GET", "/enrollments/" + r.path("id").asLong(), null).toString();
    assertFalse(details.contains("correctAnswer"));
    assertFalse(
        ok(learner, "GET", "/courses/" + course + "/report.json", null)
            .toString()
            .contains("correctAnswer"));
    request(other, "GET", "/enrollments/" + r.path("id").asLong(), null, 403);
    assertEquals(0, ok(other, "GET", "/enrollments", null).path("total").asInt());
    assertEquals(0, ok(other, "GET", "/courses", null).path("total").asInt());
  }

  @Test
  void learnerOptionsHideColleaguesAndPasswords() throws Exception {
    var r = ok(learner, "GET", "/options", null);
    assertEquals(1, r.path("accounts").size());
    assertFalse(r.toString().contains("password"));
  }

  @Test
  void examRequiresAcknowledgedReading() throws Exception {
    var r = assign();
    var m = command(r);
    m.put("answers", List.of(0, 1));
    request(learner, "POST", "/enrollments/" + r.path("id").asLong() + "/commands/exam", m, 409);
  }

  @Test
  void twoFailedAttemptsEndTask() throws Exception {
    var r = act(learner, "enrollments", assign(), "read");
    r = exam(r, List.of(2, 2));
    assertEquals("LEARNING", r.path("status").asString());
    r = exam(r, List.of(2, 2));
    assertEquals("FAILED", r.path("status").asString());
    var m = command(r);
    m.put("answers", List.of(0, 1));
    request(learner, "POST", "/enrollments/" + r.path("id").asLong() + "/commands/exam", m, 409);
  }

  @Test
  void anotherAccountCannotTakeExam() throws Exception {
    var r = act(learner, "enrollments", assign(), "read");
    var m = command(r);
    m.put("answers", List.of(0, 1));
    request(admin, "POST", "/enrollments/" + r.path("id").asLong() + "/commands/exam", m, 403);
  }

  @Test
  void wrongReviewerCannotPass() throws Exception {
    var r = practical();
    request(
        admin, "POST", "/enrollments/" + r.path("id").asLong() + "/commands/pass", command(r), 403);
  }

  @Test
  void exactExamRetryDoesNotConsumeAttempt() throws Exception {
    var r = act(learner, "enrollments", assign(), "read");
    var m = command(r);
    m.put("answers", List.of(2, 2));
    String p = "/enrollments/" + r.path("id").asLong() + "/commands/exam";
    ok(learner, "POST", p, m);
    ok(learner, "POST", p, m);
    assertEquals(
        1,
        ok(learner, "GET", "/enrollments/" + r.path("id").asLong(), null).path("attempts").size());
    m.put("answers", List.of(0, 1));
    request(learner, "POST", p, m, 409);
  }

  @Test
  void assignmentRetriesAndDuplicatesProtected() throws Exception {
    publish();
    var m = assignment();
    var r = ok(writer, "POST", "/courses/" + course + "/assign", m);
    assertEquals(
        r.path("id").asLong(),
        ok(writer, "POST", "/courses/" + course + "/assign", m).path("id").asLong());
    request(writer, "POST", "/courses/" + course + "/assign", assignment(), 409);
  }

  @Test
  void incompleteExamRejectedWithoutAttempt() throws Exception {
    var r = act(learner, "enrollments", assign(), "read");
    var m = command(r);
    m.put("answers", List.of(0));
    request(learner, "POST", "/enrollments/" + r.path("id").asLong() + "/commands/exam", m, 400);
    assertEquals(0, e(r.path("id").asLong()).path("attemptsUsed").asInt());
  }

  @Test
  void publishedContentFrozen() throws Exception {
    publish();
    var v = input();
    v.put("version", c().path("version").asLong());
    request(writer, "PUT", "/courses/" + course, v, 409);
  }

  @Test
  void revisedCourseKeepsOldTaskAndImmutableQuestions() throws Exception {
    var r = assign();
    var newer = act(writer, "courses", c(), "revise");
    assertEquals(2, newer.path("edition").asInt());
    var v = input();
    v.put("version", newer.path("version").asLong());
    v.put("title", "TEST 新版");
    ok(writer, "PUT", "/courses/" + newer.path("id").asLong(), v);
    act(
        reviewer,
        "courses",
        ok(writer, "GET", "/courses/" + newer.path("id").asLong(), null).path("record"),
        "publish");
    assertEquals("ARCHIVED", c().path("status").asString());
    r = act(learner, "enrollments", r, "read");
    r = exam(r, List.of(0, 1));
    assertEquals("PRACTICAL", r.path("status").asString());
  }

  @Test
  void noSecondOpenRevision() throws Exception {
    publish();
    act(writer, "courses", c(), "revise");
    request(writer, "POST", "/courses/" + course + "/commands/revise", command(c()), 409);
  }

  @Test
  void expiryAndRetraining() throws Exception {
    var r = act(reviewer, "enrollments", practical(), "pass");
    clock.value = Instant.parse("2026-10-07T02:00:00Z");
    assertEquals("EXPIRED", e(r.path("id").asLong()).path("status").asString());
    var m = assignment();
    m.put("dueDate", "2026-10-08");
    ok(writer, "POST", "/courses/" + course + "/assign", m);
  }

  @Test
  void dueDateBlocksLateExamAndReview() throws Exception {
    var r = act(learner, "enrollments", assign(), "read");
    clock.value = Instant.parse("2026-10-07T02:00:00Z");
    var m = command(r);
    m.put("answers", List.of(0, 1));
    request(learner, "POST", "/enrollments/" + r.path("id").asLong() + "/commands/exam", m, 409);
  }

  @Test
  void noPracticalCourseQualifiesAutomatically() throws Exception {
    var v = input();
    v.put("practicalRequired", false);
    v.put("version", c().path("version").asLong());
    ok(writer, "PUT", "/courses/" + course, v);
    var r = act(learner, "enrollments", assign(), "read");
    assertEquals("QUALIFIED", exam(r, List.of(0, 1)).path("status").asString());
  }

  @Test
  void revokeKeepsAttemptsAndHistory() throws Exception {
    var r = act(reviewer, "enrollments", practical(), "pass");
    r = act(writer, "enrollments", r, "revoke");
    assertEquals("REVOKED", r.path("status").asString());
    assertEquals(
        1,
        ok(learner, "GET", "/enrollments/" + r.path("id").asLong(), null).path("attempts").size());
  }

  @Test
  void selfAndAssignerCannotBePracticalReviewer() throws Exception {
    publish();
    var m = assignment();
    m.put("reviewerId", writerId);
    request(writer, "POST", "/courses/" + course + "/assign", m, 400);
    m.put("reviewerId", learnerId);
    request(writer, "POST", "/courses/" + course + "/assign", m, 400);
  }

  @Test
  void anotherDepartmentDenied() throws Exception {
    request(
        admin,
        "POST",
        "/admin/roles",
        Map.of(
            "name",
            "TEST outsider-" + suffix,
            "scope",
            "DEPARTMENT",
            "permissions",
            Set.of("training.read")),
        200);
    long role = 0;
    for (var x : ok(admin, "GET", "/admin/roles", null))
      if (x.path("name").asString().endsWith(suffix)) role = x.path("id").asLong();
    user("outside-" + suffix, role, 1);
    var s = login("outside-" + suffix, password);
    request(s, "GET", "/courses/" + course, null, 403);
    assertEquals(0, ok(s, "GET", "/courses?search=T-" + suffix, null).path("total").asInt());
  }

  @Test
  void staleCourseUpdateRejected() throws Exception {
    var v = input();
    v.put("version", 100L);
    request(writer, "PUT", "/courses/" + course, v, 409);
  }

  @Test
  void anonymousAndCsrfProtected() throws Exception {
    request(null, "GET", "/courses", null, 401);
    assertEquals(
        403,
        mvc.perform(
                post("/api/courses").session(writer).contentType("application/json").content("{}"))
            .andReturn()
            .getResponse()
            .getStatus());
  }

  @Test
  void learnerCannotUseAdmin() throws Exception {
    request(learner, "GET", "/admin/users", null, 403);
  }

  @Test
  void disabledLearnerSessionInvalidated() throws Exception {
    JsonNode u = null;
    for (var r : ok(admin, "GET", "/admin/users", null))
      if (r.path("id").asLong() == learnerId) u = r;
    var m = json.convertValue(u, HashMap.class);
    m.put("enabled", false);
    ok(admin, "PUT", "/admin/users/" + learnerId, m);
    request(learner, "GET", "/options", null, 401);
  }

  @Test
  void concurrentExamConsumesOneVersion() throws Exception {
    var r = act(learner, "enrollments", assign(), "read");
    var start = new CountDownLatch(1);
    var pool = Executors.newFixedThreadPool(2);
    try {
      var futures = new ArrayList<Future<Integer>>();
      for (int n = 0; n < 2; n++) {
        var m = command(r);
        m.put("answers", List.of(2, 2));
        futures.add(
            pool.submit(
                () -> {
                  start.await();
                  return mvc.perform(
                          post("/api/enrollments/" + r.path("id").asLong() + "/commands/exam")
                              .session(learner)
                              .with(csrf())
                              .contentType("application/json")
                              .content(json.writeValueAsString(m)))
                      .andReturn()
                      .getResponse()
                      .getStatus();
                }));
      }
      start.countDown();
      var statuses = List.of(futures.get(0).get(), futures.get(1).get());
      assertTrue(statuses.contains(200) && statuses.contains(409));
      assertEquals(1, e(r.path("id").asLong()).path("attemptsUsed").asInt());
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void assignerWithReviewPermissionStillCannotReviewOwnTask() throws Exception {
    publish();
    var m = assignment();
    m.put("reviewerId", 1);
    request(admin, "POST", "/courses/" + course + "/assign", m, 403);
  }

  @Test
  void selfScopeCannotCompileEvenWithAddedPermission() throws Exception {
    var role =
        ok(
                admin,
                "POST",
                "/admin/roles",
                Map.of(
                    "name",
                    "TEST SELF-" + suffix,
                    "scope",
                    "SELF",
                    "permissions",
                    Set.of("training.read", "course.manage")))
            .path("id")
            .asLong();
    user("selfwriter-" + suffix, role, dept);
    var s = login("selfwriter-" + suffix, password);
    request(s, "POST", "/courses", input(), 403);
  }

  @Test
  void lastGlobalAdminProtected() throws Exception {
    JsonNode u = null;
    for (var x : ok(admin, "GET", "/admin/users", null))
      if (x.path("username").asString().equals("admin")) u = x;
    var m = json.convertValue(u, HashMap.class);
    m.put("enabled", false);
    request(admin, "PUT", "/admin/users/1", m, 409);
    assertEquals("admin", ok(admin, "GET", "/auth/me", null).path("username").asString());
  }

  @Test
  void questionOnlyEditAdvancesVersionAndRejectsOldPage() throws Exception {
    var old = c();
    var v = input();
    v.put("version", old.path("version").asLong());
    v.put(
        "questions",
        List.of(
            Map.of(
                "prompt", "改为新题干", "options", List.of("核对", "忽略", "猜测", "跳过"), "correctAnswer", 0),
            Map.of(
                "prompt",
                "最后做什么",
                "options",
                List.of("丢弃", "记录", "跳过", "猜测"),
                "correctAnswer",
                1)));
    var updated = ok(writer, "PUT", "/courses/" + course, v);
    assertTrue(updated.path("version").asLong() > old.path("version").asLong());
    request(writer, "PUT", "/courses/" + course, v, 409);
  }
}
