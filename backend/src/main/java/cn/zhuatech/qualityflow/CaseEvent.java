// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2
package cn.zhuatech.qualityflow;

import jakarta.persistence.*;

/** 只追加的业务历史，保留各轮复核、原因与证据 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "case_event")
public class CaseEvent {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "case_id", nullable = false)
  public Long caseId;

  @Column(name = "cycle", nullable = false)
  public int cycle;

  @Column(name = "actor", nullable = false, length = 60)
  public String actor;

  @Column(name = "action", nullable = false, length = 60)
  public String action;

  @Column(name = "from_status", nullable = false, length = 30)
  public String fromStatus;

  @Column(name = "to_status", nullable = false, length = 30)
  public String toStatus;

  @Column(name = "note", nullable = false, columnDefinition = "text")
  public String note;

  @Column(name = "created_at", nullable = false)
  public java.time.Instant createdAt;
}
