// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.trainflow;

import jakarta.persistence.*;
import java.time.*;

/** 课程版本的固定四选一题目；标准答案仅服务端与课程管理者可读。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "question")
public class Question {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "course_id", nullable = true)
  public Long courseId;

  @Column(name = "position", nullable = true)
  public Integer position;

  @Column(name = "prompt", nullable = true, length = 1000)
  public String prompt;

  @Column(name = "option_a", nullable = true, length = 500)
  public String optionA;

  @Column(name = "option_b", nullable = true, length = 500)
  public String optionB;

  @Column(name = "option_c", nullable = true, length = 500)
  public String optionC;

  @Column(name = "option_d", nullable = true, length = 500)
  public String optionD;

  @Column(name = "correct_answer", nullable = true)
  public Integer correctAnswer;
}
