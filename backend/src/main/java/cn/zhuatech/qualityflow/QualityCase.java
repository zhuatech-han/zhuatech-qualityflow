// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2
package cn.zhuatech.qualityflow;

import jakarta.persistence.*;

/** 不符合项及当前整改轮次，保存报告、处置、原因与效果验证 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "quality_case")
public class QualityCase {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "change_count", nullable = false)
  public long changeCount;

  @Version
  @Column(name = "version", nullable = false)
  public long version;

  @Column(name = "number", nullable = false, length = 60)
  public String number;

  @Column(name = "title", nullable = false, length = 200)
  public String title;

  @Column(name = "description", nullable = false, columnDefinition = "text")
  public String description;

  @Column(name = "source", nullable = false, length = 60)
  public String source;

  @Column(name = "category", nullable = false, length = 60)
  public String category;

  @Column(name = "severity", nullable = false, length = 20)
  public String severity;

  @Column(name = "reference", nullable = false, length = 200)
  public String reference;

  @Column(name = "department_id", nullable = false)
  public Long departmentId;

  @Column(name = "reporter_id", nullable = false)
  public Long reporterId;

  @Column(name = "owner_id", nullable = false)
  public Long ownerId;

  @Column(name = "reviewer_id", nullable = false)
  public Long reviewerId;

  @Column(name = "due_date", nullable = false)
  public java.time.LocalDate dueDate;

  @Column(name = "verify_after", nullable = true)
  public java.time.LocalDate verifyAfter;

  @Column(name = "status", nullable = false, length = 30)
  public String status;

  @Column(name = "cycle", nullable = false)
  public int cycle;

  @Column(name = "containment", nullable = false, columnDefinition = "text")
  public String containment;

  @Column(name = "root_cause", nullable = false, columnDefinition = "text")
  public String rootCause;

  @Column(name = "verification_plan", nullable = false, columnDefinition = "text")
  public String verificationPlan;

  @Column(name = "review_note", nullable = false, length = 2000)
  public String reviewNote;

  @Column(name = "verification_evidence", nullable = false, columnDefinition = "text")
  public String verificationEvidence;

  @Column(name = "created_at", nullable = false)
  public java.time.Instant createdAt;

  @Column(name = "closed_at", nullable = true)
  public java.time.Instant closedAt;

  @Column(name = "closed_by", nullable = true)
  public Long closedBy;
}
