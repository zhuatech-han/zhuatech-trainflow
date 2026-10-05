// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.trainflow;

import jakarta.persistence.*;
import java.time.*;

/** 培训内容与单选题的版本目录；发布后内容及评分规则冻结。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "course")
public class Course {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Version
  @Column(nullable = false)
  public Long version;

  @Column(name = "revision", nullable = false)
  public Long revision = 0L;

  @Column(name = "family_code", nullable = true, length = 60)
  public String familyCode;

  @Column(name = "edition", nullable = true)
  public Integer edition;

  @Column(name = "title", nullable = true, length = 160)
  public String title;

  @Column(name = "category", nullable = true, length = 60)
  public String category;

  @Column(name = "department_id", nullable = true)
  public Long departmentId;

  @Column(name = "author_id", nullable = true)
  public Long authorId;

  @Column(name = "publisher_id", nullable = false)
  public Long publisherId;

  @Column(name = "content", nullable = true, columnDefinition = "text")
  public String content;

  @Column(name = "pass_score", nullable = true)
  public Integer passScore;

  @Column(name = "max_attempts", nullable = true)
  public Integer maxAttempts;

  @Column(name = "valid_days", nullable = true)
  public Integer validDays;

  @Column(name = "practical_required", nullable = true)
  public boolean practicalRequired;

  @Column(name = "status", nullable = true, length = 20)
  public String status;

  @Column(name = "created_at", nullable = true)
  public Instant createdAt;
}
