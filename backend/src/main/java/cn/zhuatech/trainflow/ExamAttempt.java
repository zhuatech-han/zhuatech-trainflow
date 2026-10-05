// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.trainflow;

import jakarta.persistence.*;
import java.time.*;

/** 不可变的单次提交、评分与本人答案；不保存客户端提供的分数。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "exam_attempt")
public class ExamAttempt {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "enrollment_id", nullable = true)
  public Long enrollmentId;

  @Column(name = "number", nullable = true)
  public Integer number;

  @Column(name = "score", nullable = true)
  public Integer score;

  @Column(name = "answers", nullable = true, length = 500)
  public String answers;

  @Column(name = "submitted_at", nullable = true)
  public Instant submittedAt;
}
