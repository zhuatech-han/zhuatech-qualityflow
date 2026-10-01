// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2
package cn.zhuatech.qualityflow;

import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/** 质量问题与系统管理接口，所有业务写入通过权限与版本校验。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@RestController
@RequestMapping("/api")
public class ApiController {
  final QualityService service;
  final AdminService admin;

  public ApiController(QualityService service, AdminService admin) {
    this.service = service;
    this.admin = admin;
  }

  /** 获取授权人员与字典。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/options")
  public Object options() {
    return service.options();
  }

  /** 搜索与分页查询。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/cases")
  public Object list(
      @RequestParam(defaultValue = "") String search,
      @RequestParam(defaultValue = "") String status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "newest") String sort) {
    return service.list(search, status, page, size, sort);
  }

  /** 新建本人问题草稿。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/cases")
  public Object create(@RequestBody QualityService.Draft v) {
    return service.draft(null, v);
  }

  /** 更新尚未提交的报告。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/cases/{id}")
  public Object update(@PathVariable Long id, @RequestBody QualityService.Draft v) {
    return service.draft(id, v);
  }

  /** 获取详情及当前可执行操作。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/cases/{id}")
  public Object detail(@PathVariable Long id) {
    return service.detail(id);
  }

  /** 删除本人草稿。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @DeleteMapping("/cases/{id}")
  public Object delete(@PathVariable Long id, @RequestParam Long version) {
    service.delete(id, version);
    return Map.of("ok", true);
  }

  /** 执行有限业务命令。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/cases/{id}/{action}")
  public Object act(
      @PathVariable Long id, @PathVariable String action, @RequestBody QualityService.Command v) {
    return service.act(id, action, v);
  }

  /** 编制本轮措施。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/cases/{id}/actions")
  public Object addAction(@PathVariable Long id, @RequestBody QualityService.ActionInput v) {
    return service.saveAction(id, null, v);
  }

  /** 修改尚未送审的措施。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/cases/{id}/actions/{aid}")
  public Object editAction(
      @PathVariable Long id, @PathVariable Long aid, @RequestBody QualityService.ActionInput v) {
    return service.saveAction(id, aid, v);
  }

  /** 删除未送审措施。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @DeleteMapping("/cases/{id}/actions/{aid}")
  public Object deleteAction(
      @PathVariable Long id, @PathVariable Long aid, @RequestParam Long version) {
    return service.deleteAction(id, aid, version);
  }

  /** 提交本人措施执行证据。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/cases/{id}/actions/{aid}/complete")
  public Object complete(
      @PathVariable Long id, @PathVariable Long aid, @RequestBody QualityService.Command v) {
    return service.complete(id, aid, v);
  }

  /** 获取当前账号待办。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/workbench")
  public Object workbench() {
    return service.workbench();
  }

  /** 质量统计按数据权限聚合。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/dashboard")
  public Object dashboard() {
    return service.dashboard();
  }

  /** 读取部门操作审计。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/audit")
  public Object audit() {
    return service.audit();
  }

  /** 下载无广告的 JSON 业务报告。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/cases/{id}/report.json")
  public ResponseEntity<String> export(@PathVariable Long id) {
    return ResponseEntity.ok()
        .header(
            HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=quality-report-" + id + ".json")
        .contentType(MediaType.APPLICATION_JSON)
        .body(service.export(id));
  }

  /** 管理资源目录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/admin/{type}")
  public Object adminList(@PathVariable String type) {
    return admin.list(type);
  }

  /** 创建管理资源。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/admin/{type}")
  public Object adminCreate(@PathVariable String type, @RequestBody AdminService.Input v) {
    return admin.save(type, null, v);
  }

  /** 更新管理资源。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/admin/{type}/{id}")
  public Object adminSave(
      @PathVariable String type, @PathVariable Long id, @RequestBody AdminService.Input v) {
    return admin.save(type, id, v);
  }

  /** 删除未被业务引用资源。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @DeleteMapping("/admin/{type}/{id}")
  public Object adminDelete(@PathVariable String type, @PathVariable Long id) {
    admin.delete(type, id);
    return Map.of("ok", true);
  }
}
