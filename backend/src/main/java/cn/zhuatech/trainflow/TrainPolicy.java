// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.trainflow;

import java.time.*;
import java.util.*;

/** 培训评分与能力有效日期规则。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
public final class TrainPolicy {
  private TrainPolicy() {}

  /** 全部题目有效作答后按正确数量取整数百分比，不信任客户端分数。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static int score(List<Integer> correct, List<Integer> answers) {
    if (correct.isEmpty()
        || answers == null
        || correct.size() != answers.size()
        || answers.stream().anyMatch(a -> a == null || a < 0 || a > 3))
      throw new Problem(400, "INVALID_ANSWERS");
    int matched = 0;
    for (int i = 0; i < correct.size(); i++) if (correct.get(i).equals(answers.get(i))) matched++;
    return matched * 100 / correct.size();
  }

  /** 合格期限包含截止当天，撤销优先于过期。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static String status(Enrollment e, Instant now) {
    return e.status.equals("QUALIFIED") && e.validUntil.isBefore(today(now)) ? "EXPIRED" : e.status;
  }

  /** 上海业务日期。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static LocalDate today(Instant now) {
    return now.atZone(ZoneId.of("Asia/Shanghai")).toLocalDate();
  }

  /** 防止旧页面覆盖新的培训状态。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static void version(Long actual, Long input) {
    if (input == null || !Objects.equals(actual, input)) throw new Problem(409, "STALE_VERSION");
  }
}
