// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.trainflow;

import java.util.Map;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/** 培训业务接口；服务层再次执行权限与状态检查。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@RestController
@RequestMapping("/api")
public class ApiController {
  final TrainService service;
  final AdminService admin;

  public ApiController(TrainService s, AdminService a) {
    service = s;
    admin = a;
  }

  /** 读取或更新授权培训记录，校验数据范围及状态。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/options")
  public Object options() {
    return service.options();
  }

  /** 读取或更新授权培训记录，校验数据范围及状态。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/workbench")
  public Object workbench() {
    return service.workbench();
  }

  /** 读取或更新授权培训记录，校验数据范围及状态。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/dashboard")
  public Object dashboard() {
    return service.dashboard();
  }

  /** 读取或更新授权培训记录，校验数据范围及状态。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/audit")
  public Object audit() {
    return service.audit();
  }

  /** 读取或更新授权培训记录，校验数据范围及状态。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping({"/courses", "/enrollments"})
  public Object list(
      jakarta.servlet.http.HttpServletRequest req,
      @RequestParam(defaultValue = "") String search,
      @RequestParam(defaultValue = "") String status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "newest") String sort) {
    return service.list(
        req.getRequestURI().substring(req.getContextPath().length() + 5),
        search,
        status,
        page,
        size,
        sort);
  }

  /** 读取或更新授权培训记录，校验数据范围及状态。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/{kind:courses|enrollments}/{id}")
  public Object detail(@PathVariable String kind, @PathVariable Long id) {
    return service.detail(kind, id);
  }

  /** 读取或更新授权培训记录，校验数据范围及状态。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/{kind:courses|enrollments}/{id}/report.json")
  public ResponseEntity<Object> report(@PathVariable String kind, @PathVariable Long id) {
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_JSON)
        .header(
            HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + kind + "-" + id + ".json")
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .body(service.report(kind, id));
  }

  /** 读取或更新授权培训记录，校验数据范围及状态。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/courses")
  public Object create(@RequestBody TrainService.CourseInput v) {
    return service.saveCourse(null, v);
  }

  /** 读取或更新授权培训记录，校验数据范围及状态。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/courses/{id}")
  public Object save(@PathVariable Long id, @RequestBody TrainService.CourseInput v) {
    return service.saveCourse(id, v);
  }

  /** 读取或更新授权培训记录，校验数据范围及状态。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @DeleteMapping("/courses/{id}")
  public Object delete(@PathVariable Long id, @RequestParam Long version) {
    service.deleteCourse(id, version);
    return Map.of("ok", true);
  }

  /** 读取或更新授权培训记录，校验数据范围及状态。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/courses/{id}/commands/{action}")
  public Object courseCommand(
      @PathVariable Long id, @PathVariable String action, @RequestBody TrainService.Command v) {
    return service.courseCommand(id, action, v);
  }

  /** 读取或更新授权培训记录，校验数据范围及状态。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/courses/{id}/assign")
  public Object assign(@PathVariable Long id, @RequestBody TrainService.Assignment v) {
    return service.assign(id, v);
  }

  /** 读取或更新授权培训记录，校验数据范围及状态。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/enrollments/{id}/commands/{action}")
  public Object enrollmentCommand(
      @PathVariable Long id, @PathVariable String action, @RequestBody TrainService.Command v) {
    return service.enrollmentCommand(id, action, v);
  }

  /** 处理授权业务请求，服务内检查数据范围与状态。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/admin/{type}")
  public Object listAdmin(@PathVariable String type) {
    return admin.list(type);
  }

  /** 处理授权业务请求，服务内检查数据范围与状态。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/admin/{type}")
  public Object createAdmin(@PathVariable String type, @RequestBody AdminService.Input v) {
    return admin.save(type, null, v);
  }

  /** 处理授权业务请求，服务内检查数据范围与状态。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/admin/{type}/{id}")
  public Object saveAdmin(
      @PathVariable String type, @PathVariable Long id, @RequestBody AdminService.Input v) {
    return admin.save(type, id, v);
  }

  /** 处理授权业务请求，服务内检查数据范围与状态。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @DeleteMapping("/admin/{type}/{id}")
  public Object deleteAdmin(@PathVariable String type, @PathVariable Long id) {
    admin.delete(type, id);
    return Map.of("ok", true);
  }
}
