// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.trainflow;

import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 空库创建管理目录与管理员，不伪造课程或学员成绩。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Component
public class Bootstrap implements ApplicationRunner {
  final Store db;
  final BCryptPasswordEncoder encoder;
  final String password;

  public Bootstrap(
      Store db,
      BCryptPasswordEncoder encoder,
      @Value("${trainflow.admin-password}") String password) {
    this.db = db;
    this.encoder = encoder;
    this.password = password;
  }

  /** 重启不覆盖已有账号和培训记录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (!db.all(Account.class).isEmpty()) return;
    AdminService.validatePassword(password);
    var d = new Department();
    d.name = "总部";
    db.save(d);
    var names =
        Map.ofEntries(
            Map.entry("training.read", "查看培训资料"),
            Map.entry("course.manage", "编制课程版本"),
            Map.entry("course.publish", "独立发布课程"),
            Map.entry("training.assign", "分配培训与撤销"),
            Map.entry("learn", "本人学习与考试"),
            Map.entry("practical.review", "实操复核"),
            Map.entry("dashboard", "培训统计"),
            Map.entry("export", "导出授权记录"),
            Map.entry("audit", "操作审计"),
            Map.entry("admin", "系统管理"));
    new TreeMap<>(names)
        .forEach(
            (k, v) -> {
              var p = new Permission();
              p.code = k;
              p.name = v;
              db.save(p);
            });
    role("管理员", "ALL", names.keySet());
    role(
        "培训管理员",
        "DEPARTMENT",
        Set.of(
            "training.read", "course.manage", "training.assign", "dashboard", "export", "audit"));
    role(
        "培训复核员",
        "DEPARTMENT",
        Set.of(
            "training.read", "course.publish", "practical.review", "dashboard", "export", "audit"));
    role("学员", "SELF", Set.of("training.read", "learn", "export", "dashboard"));
    var a = new Account();
    a.username = "admin";
    a.displayName = "管理员";
    a.passwordHash = encoder.encode(password);
    a.roleId = db.all(AccessRole.class).getFirst().id;
    a.departmentId = d.id;
    a.enabled = true;
    db.save(a);
    String[][] menus = {
      {"workbench", "培训工作台", "Workbench", "training.read"},
      {"courses", "课程版本", "Courses", "training.read"},
      {"enrollments", "学习与能力", "Learning", "training.read"},
      {"dashboard", "培训统计", "Statistics", "dashboard"},
      {"audit", "操作审计", "Audit", "audit"},
      {"users", "账号管理", "Accounts", "admin"},
      {"roles", "角色与权限", "Roles", "admin"},
      {"departments", "部门管理", "Departments", "admin"},
      {"menus", "导航管理", "Navigation", "admin"},
      {"permissions", "权限目录", "Permissions", "admin"},
      {"dictionaries", "培训分类", "Categories", "admin"},
      {"settings", "系统参数", "Settings", "admin"}
    };
    for (int i = 0; i < menus.length; i++) {
      var m = new NavMenu();
      m.code = menus[i][0];
      m.name = menus[i][1];
      m.nameEn = menus[i][2];
      m.permissionCode = menus[i][3];
      m.position = i;
      m.enabled = true;
      db.save(m);
    }
    Map.of("timezone", "Asia/Shanghai", "companyName", "知华培训协作", "dueSoonDays", "30")
        .forEach(
            (k, v) -> {
              var s = new SystemSetting();
              s.code = k;
              s.value = v;
              db.save(s);
            });
    for (var k :
        new String[][] {
          {"OPERATIONS", "操作技能", "Operations"},
          {"ONBOARDING", "入职培训", "Onboarding"},
          {"OTHER", "其他培训", "Other"}
        }) {
      var e = new DictionaryEntry();
      e.type = "course";
      e.code = k[0];
      e.name = k[1];
      e.nameEn = k[2];
      db.save(e);
    }
  }

  private void role(String n, String scope, Set<String> p) {
    var r = new AccessRole();
    r.name = n;
    r.scope = scope;
    r.permissions = new HashSet<>(p);
    db.save(r);
  }
}
