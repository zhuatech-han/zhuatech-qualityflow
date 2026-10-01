// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2
package cn.zhuatech.qualityflow;

import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 初始化质量业务权限和管理员；重启不覆盖数据。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Component
public class Bootstrap implements ApplicationRunner {
  final Store db;
  final BCryptPasswordEncoder encoder;
  final String password;

  public Bootstrap(
      Store db,
      BCryptPasswordEncoder encoder,
      @Value("${qualityflow.admin-password}") String password) {
    this.db = db;
    this.encoder = encoder;
    this.password = password;
  }

  /** 空库生成组织、角色、菜单、字典；业务记录由操作者建立。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
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
            Map.entry("dashboard", "质量概况"),
            Map.entry("case.read", "查看质量问题"),
            Map.entry("case.write", "报告与维护草稿"),
            Map.entry("case.work", "处置与编制整改方案"),
            Map.entry("action.write", "执行本人措施"),
            Map.entry("case.review", "独立复核与效果验证"),
            Map.entry("export", "导出整改报告"),
            Map.entry("audit", "操作审计"),
            Map.entry("admin", "系统管理"));
    for (var e : new TreeMap<>(names).entrySet()) {
      var x = new Permission();
      x.code = e.getKey();
      x.name = e.getValue();
      db.save(x);
    }
    role("管理员", "ALL", names.keySet());
    role(
        "质量协调员",
        "DEPARTMENT",
        Set.of("dashboard", "case.read", "case.write", "case.work", "action.write", "export"));
    role(
        "整改执行人",
        "ASSIGNED",
        Set.of("dashboard", "case.read", "case.work", "action.write", "export"));
    role("质量复核人", "DEPARTMENT", Set.of("dashboard", "case.read", "case.review", "audit", "export"));
    role("问题报告人", "ASSIGNED", Set.of("dashboard", "case.read", "case.write"));
    var a = new Account();
    a.username = "admin";
    a.displayName = "管理员";
    a.passwordHash = encoder.encode(password);
    a.departmentId = d.id;
    a.roleId = db.all(AccessRole.class).getFirst().id;
    a.enabled = true;
    db.save(a);
    String[][] menus = {
      {"workbench", "我的待办", "My work", "case.read"},
      {"cases", "质量问题", "Nonconformances", "case.read"},
      {"dashboard", "质量统计", "Quality overview", "dashboard"},
      {"audit", "操作审计", "Audit", "audit"},
      {"users", "账号管理", "Accounts", "admin"},
      {"roles", "角色与权限", "Roles", "admin"},
      {"departments", "部门", "Departments", "admin"},
      {"menus", "导航管理", "Menus", "admin"},
      {"permissions", "权限目录", "Permissions", "admin"},
      {"dictionaries", "业务字典", "Dictionaries", "admin"},
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
    for (var e : Map.of("timezone", "Asia/Shanghai", "companyName", "知华质量整改协同").entrySet()) {
      var x = new SystemSetting();
      x.code = e.getKey();
      x.value = e.getValue();
      db.save(x);
    }
    String[][] dict = {
      {"source", "INTERNAL", "内部检查", "Internal inspection"},
      {"source", "CUSTOMER", "客户反馈", "Customer feedback"},
      {"source", "AUDIT", "审核发现", "Audit finding"},
      {"category", "PROCESS", "过程异常", "Process"},
      {"category", "PRODUCT", "产品缺陷", "Product"},
      {"category", "DOCUMENT", "文件偏差", "Documentation"}
    };
    for (var v : dict) {
      var x = new DictionaryEntry();
      x.type = v[0];
      x.code = v[1];
      x.name = v[2];
      x.nameEn = v[3];
      db.save(x);
    }
  }

  private void role(String n, String scope, Set<String> permissions) {
    var x = new AccessRole();
    x.name = n;
    x.scope = scope;
    x.permissions = new HashSet<>(permissions);
    db.save(x);
  }
}
