// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.trainflow;

import jakarta.persistence.*;
import java.time.*;

/** 指定学员与独立实操复核人；通过时间和有效日期由服务器确定。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "enrollment")
public class Enrollment {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Version
  @Column(nullable = false)
  public Long version;

  @Column(name = "course_id", nullable = true)
  public Long courseId;

  @Column(name = "department_id", nullable = true)
  public Long departmentId;

  @Column(name = "learner_id", nullable = true)
  public Long learnerId;

  @Column(name = "reviewer_id", nullable = false)
  public Long reviewerId;

  @Column(name = "assigned_by", nullable = true)
  public Long assignedBy;

  @Column(name = "due_date", nullable = true)
  public LocalDate dueDate;

  @Column(name = "status", nullable = true, length = 20)
  public String status;

  @Column(name = "attempts_used", nullable = true)
  public Integer attemptsUsed;

  @Column(name = "best_score", nullable = true)
  public Integer bestScore;

  @Column(name = "read_at", nullable = false)
  public Instant readAt;

  @Column(name = "qualified_at", nullable = false)
  public Instant qualifiedAt;

  @Column(name = "valid_until", nullable = false)
  public LocalDate validUntil;

  @Column(name = "created_at", nullable = true)
  public Instant createdAt;
}
