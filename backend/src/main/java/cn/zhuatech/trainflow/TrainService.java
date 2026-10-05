// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.trainflow;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** 培训版本、分配、考试及能力审核的事务边界。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Service
@Transactional(isolation = Isolation.READ_COMMITTED)
public class TrainService {
  final Store db;
  final AccessService access;
  final Clock clock;

  public TrainService(Store db, AccessService access, Clock clock) {
    this.db = db;
    this.access = access;
    this.clock = clock;
  }

  /** 四选一题目输入，标准答案不出现在学员响应。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record QuestionInput(String prompt, List<String> options, Integer correctAnswer) {}

  /** 仅草稿可编辑的课程正文与考试规则。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record CourseInput(
      String familyCode,
      String title,
      String category,
      Long departmentId,
      String content,
      Integer passScore,
      Integer maxAttempts,
      Integer validDays,
      Boolean practicalRequired,
      List<QuestionInput> questions,
      Long version) {}

  /** 服务端验证指定学员、复核人、截止日期和幂等键。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record Assignment(
      Long version, Long learnerId, Long reviewerId, LocalDate dueDate, String requestKey) {}

  /** 流转不接收客户端分数、资格时间或他人身份。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record Command(Long version, String note, String requestKey, List<Integer> answers) {}

  /** 选项限定数据范围；SELF不列出同事。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object options() {
    access.require("training.read");
    var accounts =
        db.all(Account.class).stream()
            .filter(a -> self() ? a.id.equals(access.current().id) : access.visible(a.departmentId))
            .map(
                a ->
                    Map.of(
                        "id",
                        a.id,
                        "name",
                        a.displayName,
                        "departmentId",
                        a.departmentId,
                        "enabled",
                        a.enabled,
                        "scope",
                        db.get(AccessRole.class, a.roleId).scope,
                        "permissions",
                        db.get(AccessRole.class, a.roleId).permissions))
            .toList();
    return Map.of(
        "accounts",
        accounts,
        "departments",
        db.all(Department.class).stream().filter(d -> access.visible(d.id)).toList(),
        "dictionaries",
        db.all(DictionaryEntry.class),
        "settings",
        db.all(SystemSetting.class),
        "now",
        clock.instant());
  }

  /** 数据库条件同时限定部门、本人、搜索、状态、分页与固定排序。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object list(String kind, String search, String status, int page, int size, String sort) {
    access.require("training.read");
    if (page < 0
        || page > 100000
        || size < 1
        || size > 100
        || search.length() > 120
        || status.length() > 30
        || !Set.of("newest", "oldest").contains(sort)) throw new Problem(400, "INVALID_INPUT");
    String where = scope(kind);
    if (!search.isBlank()) {
      where +=
          " and "
              + (kind.equals("courses")
                  ? "(lower(e.title) like :search or lower(e.familyCode) like :search)"
                  : "exists(select c.id from Course c where c.id=e.courseId and (lower(c.title) like :search or lower(c.familyCode) like :search))");
    }
    if (!status.isBlank()) {
      if (kind.equals("enrollments") && status.equals("EXPIRED"))
        where += " and e.status='QUALIFIED' and e.validUntil < :today";
      else {
        where += " and e.status=:status";
        if (kind.equals("enrollments") && status.equals("QUALIFIED"))
          where += " and e.validUntil >= :today";
      }
    }
    String entity = kind.equals("courses") ? "Course" : "Enrollment";
    var count = db.jpql(Long.class, "select count(e.id) from " + entity + " e where " + where);
    var query =
        db.jpql(
            type(kind),
            "from "
                + entity
                + " e where "
                + where
                + " order by e.id "
                + (sort.equals("oldest") ? "asc" : "desc"));
    bind(query, kind);
    bind(count, kind);
    for (var q : List.of(query, count)) {
      if (!search.isBlank()) q.setParameter("search", "%" + search.toLowerCase(Locale.ROOT) + "%");
      if (!status.isBlank()) {
        if (!status.equals("EXPIRED") || !kind.equals("enrollments"))
          q.setParameter("status", status);
        if (kind.equals("enrollments") && Set.of("QUALIFIED", "EXPIRED").contains(status))
          q.setParameter("today", today());
      }
    }
    var rows = query.setFirstResult(page * size).setMaxResults(size).getResultList();
    return Map.of(
        "items",
        rows.stream()
            .map(o -> o instanceof Course c ? courseView(c) : enrollmentView((Enrollment) o))
            .toList(),
        "total",
        count.getSingleResult(),
        "page",
        page,
        "size",
        size);
  }

  /** 授权详情隐藏答案，返回课程版本、本人历次尝试和不可变事件。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object detail(String kind, Long id) {
    access.require("training.read");
    var out = new LinkedHashMap<String, Object>();
    Long dept;
    if (kind.equals("courses")) {
      var c = course(id);
      out.put("record", courseView(c));
      out.put("content", c.content);
      out.put("questions", questions(c.id).stream().map(this::questionView).toList());
      dept = c.departmentId;
    } else {
      var e = enrollment(id);
      var c = db.get(Course.class, e.courseId);
      out.put("record", enrollmentView(e));
      out.put("course", courseView(c));
      out.put("content", c.content);
      out.put("questions", questions(c.id).stream().map(this::questionView).toList());
      out.put(
          "attempts",
          db.query(
              ExamAttempt.class, "from ExamAttempt where enrollmentId=?1 order by number", id));
      dept = e.departmentId;
    }
    out.put(
        "events",
        db.query(
            FlowEvent.class, "from FlowEvent where kind=?1 and objectId=?2 order by id", kind, id));
    return out;
  }

  /** 草稿保存逐题校验；已有版本的编号和部门不可改变。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object saveCourse(Long id, CourseInput v) {
    gate("course.manage");
    if (v == null) throw new Problem(400, "INVALID_INPUT");
    access.department(v.departmentId);
    db.get(Department.class, v.departmentId);
    String code = AdminService.text(v.familyCode, 60);
    if (!code.matches("[A-Za-z0-9_.-]{2,60}")) throw new Problem(400, "INVALID_INPUT");
    if (v.passScore == null
        || v.passScore < 1
        || v.passScore > 100
        || v.maxAttempts == null
        || v.maxAttempts < 1
        || v.maxAttempts > 10
        || v.validDays == null
        || v.validDays < 1
        || v.validDays > 730
        || v.practicalRequired == null) throw new Problem(400, "INVALID_INPUT");
    if (db.query(
            DictionaryEntry.class,
            "from DictionaryEntry where type='course' and code=?1",
            v.category)
        .isEmpty()) throw new Problem(400, "INVALID_CATEGORY");
    validateQuestions(v.questions);
    var c = id == null ? new Course() : course(id);
    if (id == null) {
      if (!db.query(Course.class, "from Course where familyCode=?1", code).isEmpty())
        throw new Problem(409, "USE_REVISION");
      c.familyCode = code;
      c.edition = 1;
      c.departmentId = v.departmentId;
      c.authorId = access.current().id;
      c.status = "DRAFT";
      c.createdAt = clock.instant();
    } else {
      TrainPolicy.version(c.version, v.version);
      state(c.status, "DRAFT");
      if (!c.authorId.equals(access.current().id)) throw new Problem(403, "NOT_ASSIGNED");
      if (!c.familyCode.equals(code) || !c.departmentId.equals(v.departmentId))
        throw new Problem(409, "FROZEN");
    }
    c.revision++;
    c.title = AdminService.text(v.title, 160);
    c.category = v.category;
    c.content = AdminService.text(v.content, 30000);
    c.passScore = v.passScore;
    c.maxAttempts = v.maxAttempts;
    c.validDays = v.validDays;
    c.practicalRequired = v.practicalRequired;
    if (id == null) db.save(c);
    else for (var q : questions(c.id)) db.delete(q);
    setQuestions(c.id, v.questions);
    event("courses", c.id, c.departmentId, "SAVE", "保存课程草稿");
    db.flush();
    return courseView(c);
  }

  /** 独立发布、新版复制及归档；历史培训仍引用原始课程版本。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object courseCommand(Long id, String action, Command v) {
    gate(action.equals("publish") ? "course.publish" : "course.manage");
    var c = course(id);
    String payload = "course:" + id + ":" + action + ":" + v;
    Long prior = replay(v.requestKey, payload);
    if (prior != null) return courseView(course(prior));
    TrainPolicy.version(c.version, v.version);
    switch (action) {
      case "publish" -> {
        state(c.status, "DRAFT");
        if (c.authorId.equals(access.current().id)) throw new Problem(403, "INDEPENDENT_REVIEW");
        AdminService.text(v.note, 2000);
        if (questions(id).size() < 2) throw new Problem(400, "INVALID_QUESTIONS");
        for (var old :
            db.query(
                Course.class,
                "from Course where familyCode=?1 and status='PUBLISHED'",
                c.familyCode)) {
          old.status = "ARCHIVED";
          event("courses", old.id, old.departmentId, "REPLACED", "新版发布");
        }
        c.status = "PUBLISHED";
        c.publisherId = access.current().id;
      }
      case "archive" -> {
        state(c.status, "PUBLISHED");
        AdminService.text(v.note, 2000);
        c.status = "ARCHIVED";
      }
      case "revise" -> {
        if (c.status.equals("DRAFT")) throw new Problem(409, "INVALID_STATE");
        if (!db.query(
                Course.class, "from Course where familyCode=?1 and status='DRAFT'", c.familyCode)
            .isEmpty()) throw new Problem(409, "OPEN_REVISION");
        var n = new Course();
        n.familyCode = c.familyCode;
        n.edition =
            db.query(Course.class, "from Course where familyCode=?1", c.familyCode).stream()
                    .mapToInt(x -> x.edition)
                    .max()
                    .orElse(0)
                + 1;
        n.title = c.title;
        n.category = c.category;
        n.departmentId = c.departmentId;
        n.authorId = access.current().id;
        n.content = c.content;
        n.passScore = c.passScore;
        n.maxAttempts = c.maxAttempts;
        n.validDays = c.validDays;
        n.practicalRequired = c.practicalRequired;
        n.status = "DRAFT";
        n.createdAt = clock.instant();
        db.save(n);
        setQuestions(
            n.id,
            questions(id).stream()
                .map(
                    q ->
                        new QuestionInput(
                            q.prompt,
                            List.of(q.optionA, q.optionB, q.optionC, q.optionD),
                            q.correctAnswer))
                .toList());
        event("courses", n.id, n.departmentId, "REVISE", "基于版本 " + c.edition);
        stamp(v.requestKey, payload, n.id);
        db.flush();
        return courseView(n);
      }
      default -> throw new Problem(400, "INVALID_ACTION");
    }
    event("courses", id, c.departmentId, action, v.note);
    stamp(v.requestKey, payload, id);
    db.flush();
    return courseView(c);
  }

  /** 仅未引用草稿可删除，不删除已发布版本。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void deleteCourse(Long id, Long version) {
    gate("course.manage");
    var c = course(id);
    TrainPolicy.version(c.version, version);
    state(c.status, "DRAFT");
    if (!c.authorId.equals(access.current().id)) throw new Problem(403, "NOT_ASSIGNED");
    for (var q : questions(id)) db.delete(q);
    event("courses", id, c.departmentId, "DELETE", "删除草稿");
    db.delete(c);
  }

  /** 分配已发布版本；复核人与学员及分配人独立，重复有效培训被阻止。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object assign(Long id, Assignment v) {
    gate("training.assign");
    var c = course(id);
    String payload = "assign:" + id + ":" + v;
    Long prior = replay(v.requestKey, payload);
    if (prior != null) return enrollmentView(enrollment(prior));
    TrainPolicy.version(c.version, v.version);
    state(c.status, "PUBLISHED");
    var learner = eligible(v.learnerId, c.departmentId, "learn");
    if (v.dueDate == null
        || v.dueDate.isBefore(today())
        || v.dueDate.isAfter(today().plusDays(730))) throw new Problem(400, "INVALID_DATE");
    Long reviewer = null;
    if (c.practicalRequired) {
      var r = eligible(v.reviewerId, c.departmentId, "practical.review");
      if (r.id.equals(learner.id) || r.id.equals(access.current().id))
        throw new Problem(403, "INDEPENDENT_REVIEW");
      reviewer = r.id;
    }
    for (var e :
        db.query(
            Enrollment.class,
            "from Enrollment where courseId=?1 and learnerId=?2",
            id,
            learner.id)) {
      if (Set.of("ASSIGNED", "LEARNING", "PRACTICAL", "QUALIFIED")
          .contains(TrainPolicy.status(e, clock.instant())))
        throw new Problem(409, "DUPLICATE_TRAINING");
    }
    var e = new Enrollment();
    e.courseId = id;
    e.departmentId = c.departmentId;
    e.learnerId = learner.id;
    e.reviewerId = reviewer;
    e.assignedBy = access.current().id;
    e.dueDate = v.dueDate;
    e.status = "ASSIGNED";
    e.attemptsUsed = 0;
    e.bestScore = 0;
    e.createdAt = clock.instant();
    db.save(e);
    event("enrollments", e.id, e.departmentId, "ASSIGN", "分配培训");
    stamp(v.requestKey, payload, e.id);
    db.flush();
    return enrollmentView(e);
  }

  /** 本人学习、自动考试、指定实操审核及有理由撤销，使用同一事务锁和请求键。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object enrollmentCommand(Long id, String action, Command v) {
    String permission =
        switch (action) {
          case "read", "exam" -> "learn";
          case "pass", "fail" -> "practical.review";
          default -> "training.assign";
        };
    gate(permission);
    var e = enrollment(id);
    var c = db.get(Course.class, e.courseId);
    String payload = "enroll:" + id + ":" + action + ":" + v;
    Long prior = replay(v.requestKey, payload);
    if (prior != null) return enrollmentView(enrollment(prior));
    TrainPolicy.version(e.version, v.version);
    switch (action) {
      case "read" -> {
        owner(e);
        state(e.status, "ASSIGNED");
        deadline(e);
        e.readAt = clock.instant();
        e.status = "LEARNING";
      }
      case "exam" -> {
        owner(e);
        state(e.status, "LEARNING");
        deadline(e);
        if (e.attemptsUsed >= c.maxAttempts) throw new Problem(409, "ATTEMPTS_EXHAUSTED");
        var qs = questions(c.id);
        int score = TrainPolicy.score(qs.stream().map(q -> q.correctAnswer).toList(), v.answers);
        e.attemptsUsed++;
        e.bestScore = Math.max(e.bestScore, score);
        var a = new ExamAttempt();
        a.enrollmentId = id;
        a.number = e.attemptsUsed;
        a.score = score;
        a.answers = v.answers.toString();
        a.submittedAt = clock.instant();
        db.save(a);
        if (score >= c.passScore) {
          if (c.practicalRequired) e.status = "PRACTICAL";
          else qualify(e, c);
        } else if (e.attemptsUsed >= c.maxAttempts) e.status = "FAILED";
      }
      case "pass", "fail" -> {
        state(e.status, "PRACTICAL");
        if (!Objects.equals(e.reviewerId, access.current().id)
            || e.learnerId.equals(access.current().id)
            || e.assignedBy.equals(access.current().id)) throw new Problem(403, "NOT_ASSIGNED");
        deadline(e);
        AdminService.text(v.note, 2000);
        if (action.equals("pass")) qualify(e, c);
        else e.status = "FAILED";
      }
      case "cancel" -> {
        if (!Set.of("ASSIGNED", "LEARNING", "PRACTICAL").contains(e.status))
          throw new Problem(409, "INVALID_STATE");
        AdminService.text(v.note, 2000);
        e.status = "CANCELLED";
      }
      case "revoke" -> {
        state(e.status, "QUALIFIED");
        AdminService.text(v.note, 2000);
        e.status = "REVOKED";
      }
      default -> throw new Problem(400, "INVALID_ACTION");
    }
    event("enrollments", id, e.departmentId, action, v.note == null ? "" : v.note);
    stamp(v.requestKey, payload, id);
    db.flush();
    return enrollmentView(e);
  }

  /** 统计复用相同范围；有效能力和逾期任务分开计算。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object dashboard() {
    access.require("dashboard");
    var es = bounded("enrollments");
    Map<String, Long> counts = new TreeMap<>();
    long overdue = 0, soon = 0;
    int days =
        Integer.parseInt(
            db.query(SystemSetting.class, "from SystemSetting where code='dueSoonDays'")
                .getFirst()
                .value);
    for (var o : es) {
      var e = (Enrollment) o;
      String s = TrainPolicy.status(e, clock.instant());
      counts.merge(s, 1L, Long::sum);
      if (Set.of("ASSIGNED", "LEARNING", "PRACTICAL").contains(s) && e.dueDate.isBefore(today()))
        overdue++;
      if (s.equals("QUALIFIED") && !e.validUntil.isAfter(today().plusDays(days))) soon++;
    }
    return Map.of("counts", counts, "total", es.size(), "overdue", overdue, "dueSoon", soon);
  }

  /** 本人学习任务和被指定的实操复核待办。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object workbench() {
    access.require("training.read");
    var es = bounded("enrollments").stream().map(o -> (Enrollment) o).toList();
    return Map.of(
        "learning",
        es.stream()
            .filter(
                e ->
                    e.learnerId.equals(access.current().id)
                        && Set.of("ASSIGNED", "LEARNING").contains(e.status))
            .map(this::enrollmentView)
            .toList(),
        "reviews",
        es.stream()
            .filter(
                e ->
                    Objects.equals(e.reviewerId, access.current().id)
                        && e.status.equals("PRACTICAL"))
            .map(this::enrollmentView)
            .toList(),
        "courses",
        bounded("courses").stream()
            .map(o -> (Course) o)
            .filter(
                c ->
                    c.status.equals("DRAFT")
                        && !c.authorId.equals(access.current().id)
                        && access.role().permissions.contains("course.publish"))
            .map(this::courseView)
            .toList(),
        "expired",
        es.stream()
            .filter(e -> TrainPolicy.status(e, clock.instant()).equals("EXPIRED"))
            .map(this::enrollmentView)
            .toList());
  }

  /** 学员导出同样不包含标准答案，管理者仅导出授权内容。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object report(String kind, Long id) {
    access.require("export");
    return detail(kind, id);
  }

  /** 审计只开放有审计权限的部门范围，SELF无此目录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object audit() {
    access.require("audit");
    if (self()) throw new Problem(403, "FORBIDDEN");
    var q =
        db.jpql(
            AuditEvent.class,
            "from AuditEvent e where "
                + (all() ? "1=1" : "e.departmentId=:department")
                + " order by e.id desc");
    if (!all()) q.setParameter("department", access.current().departmentId);
    return q.setMaxResults(1000).getResultList();
  }

  private boolean self() {
    return access.role().scope.equals("SELF");
  }

  private boolean all() {
    return access.role().scope.equals("ALL");
  }

  private LocalDate today() {
    return TrainPolicy.today(clock.instant());
  }

  private Class<?> type(String k) {
    return switch (k) {
      case "courses" -> Course.class;
      case "enrollments" -> Enrollment.class;
      default -> throw new Problem(404, "NOT_FOUND");
    };
  }

  private String scope(String kind) {
    type(kind);
    if (all()) return "1=1";
    if (!self()) return "e.departmentId=:department";
    return kind.equals("courses")
        ? "exists(select t.id from Enrollment t where t.courseId=e.id and t.learnerId=:actor)"
        : "e.learnerId=:actor";
  }

  private void bind(jakarta.persistence.TypedQuery<?> q, String k) {
    if (self()) q.setParameter("actor", access.current().id);
    else if (!all()) q.setParameter("department", access.current().departmentId);
  }

  private List<?> bounded(String kind) {
    var q =
        db.jpql(
            type(kind),
            "from "
                + type(kind).getSimpleName()
                + " e where "
                + scope(kind)
                + " order by e.id desc");
    bind(q, kind);
    var l = q.setMaxResults(10001).getResultList();
    if (l.size() > 10000) throw new Problem(400, "REPORT_LIMIT");
    return l;
  }

  private Course course(Long id) {
    if (id == null) throw new Problem(400, "INVALID_INPUT");
    var c = db.get(Course.class, id);
    if (self()) {
      if (db.query(
              Enrollment.class,
              "from Enrollment where courseId=?1 and learnerId=?2",
              id,
              access.current().id)
          .isEmpty()) throw new Problem(403, "OUT_OF_SCOPE");
    } else access.department(c.departmentId);
    return c;
  }

  private Enrollment enrollment(Long id) {
    if (id == null) throw new Problem(400, "INVALID_INPUT");
    var e = db.get(Enrollment.class, id);
    if (self()) {
      if (!e.learnerId.equals(access.current().id)) throw new Problem(403, "OUT_OF_SCOPE");
    } else access.department(e.departmentId);
    return e;
  }

  private Account eligible(Long id, Long dept, String perm) {
    if (id == null) throw new Problem(400, "INVALID_INPUT");
    var a = db.get(Account.class, id);
    var r = db.get(AccessRole.class, a.roleId);
    if (!a.enabled
        || !r.permissions.contains(perm)
        || (!r.scope.equals("ALL") && !a.departmentId.equals(dept)))
      throw new Problem(400, "INELIGIBLE_ACCOUNT");
    return a;
  }

  private void owner(Enrollment e) {
    if (!e.learnerId.equals(access.current().id)) throw new Problem(403, "NOT_ASSIGNED");
  }

  private void deadline(Enrollment e) {
    if (e.dueDate.isBefore(today())) throw new Problem(409, "TRAINING_OVERDUE");
  }

  private void state(String actual, String expected) {
    if (!actual.equals(expected)) throw new Problem(409, "INVALID_STATE");
  }

  private void qualify(Enrollment e, Course c) {
    e.status = "QUALIFIED";
    e.qualifiedAt = clock.instant();
    e.validUntil = today().plusDays(c.validDays - 1);
  }

  private void gate(String perm) {
    db.lock(Department.class, 1L);
    var a = access.current();
    db.refresh(a);
    db.refresh(db.get(AccessRole.class, a.roleId));
    access.require(perm);
    if (self() && !perm.equals("learn")) throw new Problem(403, "FORBIDDEN");
  }

  private List<Question> questions(Long id) {
    return db.query(Question.class, "from Question where courseId=?1 order by position", id);
  }

  private void validateQuestions(List<QuestionInput> qs) {
    if (qs == null || qs.size() < 2 || qs.size() > 30) throw new Problem(400, "INVALID_QUESTIONS");
    for (var q : qs) {
      if (q == null
          || q.options == null
          || q.options.size() != 4
          || q.correctAnswer == null
          || q.correctAnswer < 0
          || q.correctAnswer > 3) throw new Problem(400, "INVALID_QUESTIONS");
      AdminService.text(q.prompt, 1000);
      for (var o : q.options) AdminService.text(o, 500);
      if (new HashSet<>(q.options.stream().map(String::trim).toList()).size() != 4)
        throw new Problem(400, "INVALID_QUESTIONS");
    }
  }

  private void setQuestions(Long id, List<QuestionInput> qs) {
    for (int i = 0; i < qs.size(); i++) {
      var v = qs.get(i);
      var q = new Question();
      q.courseId = id;
      q.position = i;
      q.prompt = v.prompt.trim();
      q.optionA = v.options.get(0).trim();
      q.optionB = v.options.get(1).trim();
      q.optionC = v.options.get(2).trim();
      q.optionD = v.options.get(3).trim();
      q.correctAnswer = v.correctAnswer;
      db.save(q);
    }
  }

  private Map<String, Object> questionView(Question q) {
    var m = new LinkedHashMap<String, Object>();
    m.put("id", q.id);
    m.put("position", q.position);
    m.put("prompt", q.prompt);
    m.put("options", List.of(q.optionA, q.optionB, q.optionC, q.optionD));
    if (!self() && access.role().permissions.contains("course.manage"))
      m.put("correctAnswer", q.correctAnswer);
    return m;
  }

  private Map<String, Object> courseView(Course c) {
    var m = new LinkedHashMap<String, Object>();
    m.put("id", c.id);
    m.put("version", c.version);
    m.put("familyCode", c.familyCode);
    m.put("edition", c.edition);
    m.put("title", c.title);
    m.put("category", c.category);
    m.put("departmentId", c.departmentId);
    m.put("authorId", c.authorId);
    m.put("authorName", db.get(Account.class, c.authorId).displayName);
    m.put("publisherId", c.publisherId);
    m.put("passScore", c.passScore);
    m.put("maxAttempts", c.maxAttempts);
    m.put("validDays", c.validDays);
    m.put("practicalRequired", c.practicalRequired);
    m.put("status", c.status);
    m.put("createdAt", c.createdAt);
    return m;
  }

  private Map<String, Object> enrollmentView(Enrollment e) {
    var m = new LinkedHashMap<String, Object>();
    m.put("id", e.id);
    m.put("version", e.version);
    m.put("courseId", e.courseId);
    var c = db.get(Course.class, e.courseId);
    m.put("title", c.title);
    m.put("familyCode", c.familyCode);
    m.put("edition", c.edition);
    m.put("departmentId", e.departmentId);
    m.put("learnerId", e.learnerId);
    m.put("learnerName", db.get(Account.class, e.learnerId).displayName);
    m.put(
        "reviewerName",
        e.reviewerId == null ? "" : db.get(Account.class, e.reviewerId).displayName);
    m.put("reviewerId", e.reviewerId);
    m.put("assignedBy", e.assignedBy);
    m.put("dueDate", e.dueDate);
    m.put("status", TrainPolicy.status(e, clock.instant()));
    m.put("attemptsUsed", e.attemptsUsed);
    m.put("bestScore", e.bestScore);
    m.put("readAt", e.readAt);
    m.put("qualifiedAt", e.qualifiedAt);
    m.put("validUntil", e.validUntil);
    m.put("createdAt", e.createdAt);
    return m;
  }

  private void event(String kind, Long id, Long dept, String action, String note) {
    var e = new FlowEvent();
    e.kind = kind;
    e.objectId = id;
    e.departmentId = dept;
    e.actorId = access.current().id;
    e.action = action;
    e.note = note == null || note.isBlank() ? "" : AdminService.text(note, 2000);
    e.createdAt = clock.instant();
    db.save(e);
    access.audit(kind + ":" + action, id, dept);
  }

  private String fingerprint(String s) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest((access.current().id + ":" + s).getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private Long replay(String key, String payload) {
    if (key == null || !key.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))
      throw new Problem(400, "INVALID_REQUEST_KEY");
    var prior = db.query(CommandRecord.class, "from CommandRecord where requestKey=?1", key);
    if (prior.isEmpty()) return null;
    var c = prior.getFirst();
    if (!c.fingerprint.equals(fingerprint(payload))) throw new Problem(409, "IDEMPOTENCY_CONFLICT");
    return c.resultId;
  }

  private void stamp(String key, String payload, Long id) {
    var c = new CommandRecord();
    c.requestKey = key;
    c.fingerprint = fingerprint(payload);
    c.resultId = id;
    db.save(c);
  }
}
