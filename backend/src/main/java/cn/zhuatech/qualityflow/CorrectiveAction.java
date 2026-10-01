// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2
package cn.zhuatech.qualityflow;

import jakarta.persistence.*;

/** 整改和预防措施，执行证据仅能由指定责任人提交 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "corrective_action")
public class CorrectiveAction {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "case_id", nullable = false)
  public Long caseId;

  @Column(name = "cycle", nullable = false)
  public int cycle;

  @Column(name = "kind", nullable = false, length = 20)
  public String kind;

  @Column(name = "description", nullable = false, length = 2000)
  public String description;

  @Column(name = "owner_id", nullable = false)
  public Long ownerId;

  @Column(name = "due_date", nullable = false)
  public java.time.LocalDate dueDate;

  @Column(name = "status", nullable = false, length = 20)
  public String status;

  @Column(name = "evidence", nullable = false, length = 3000)
  public String evidence;

  @Column(name = "completed_at", nullable = true)
  public java.time.Instant completedAt;

  @Column(name = "completed_by", nullable = true)
  public Long completedBy;
}
