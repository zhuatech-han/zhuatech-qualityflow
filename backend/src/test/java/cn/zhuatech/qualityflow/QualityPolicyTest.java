// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2
package cn.zhuatech.qualityflow;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** 日期、状态和过期版本边界。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
class QualityPolicyTest {
  @Test
  void dueTodayAllowed() {
    QualityPolicy.actionDate(
        LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 1));
  }

  @Test
  void dueBeforeTodayRejected() {
    assertThrows(
        Problem.class,
        () ->
            QualityPolicy.actionDate(
                LocalDate.of(2026, 9, 30), LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 1)));
  }

  @Test
  void dueBeyondCaseRejected() {
    assertThrows(
        Problem.class,
        () ->
            QualityPolicy.actionDate(
                LocalDate.of(2026, 10, 3), LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 1)));
  }

  @Test
  void staleVersionRejected() {
    var c = new QualityCase();
    c.version = 2;
    assertThrows(Problem.class, () -> QualityPolicy.version(c, 1L));
    QualityPolicy.version(c, 2L);
  }

  @Test
  void noDirectClose() {
    var c = new QualityCase();
    c.status = "INVESTIGATION";
    assertThrows(Problem.class, () -> QualityPolicy.state(c, "VERIFY_READY"));
  }

  @Test
  void closedAndCancelledNotOverdue() {
    var c = new QualityCase();
    c.dueDate = LocalDate.of(2026, 9, 30);
    c.status = "EXECUTION";
    assertTrue(QualityPolicy.overdue(c, LocalDate.of(2026, 10, 1)));
    c.status = "CLOSED";
    assertFalse(QualityPolicy.overdue(c, LocalDate.of(2026, 10, 1)));
    c.status = "CANCELLED";
    assertFalse(QualityPolicy.overdue(c, LocalDate.of(2026, 10, 1)));
  }
}
