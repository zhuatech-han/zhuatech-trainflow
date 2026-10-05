// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.trainflow;

import static org.junit.jupiter.api.Assertions.*;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

/** 自动评分与到期边界的规则测试。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
class TrainPolicyTest {
  @Test
  void fullScore() {
    assertEquals(100, TrainPolicy.score(List.of(0, 1), List.of(0, 1)));
  }

  @Test
  void fractionalScoreFloors() {
    assertEquals(66, TrainPolicy.score(List.of(0, 1, 2), List.of(0, 1, 3)));
  }

  @Test
  void missingAnswersRejected() {
    assertThrows(Problem.class, () -> TrainPolicy.score(List.of(0, 1), List.of(0)));
  }

  @Test
  void answerOutsideOptionsRejected() {
    assertThrows(Problem.class, () -> TrainPolicy.score(List.of(0, 1), List.of(0, 4)));
  }

  @Test
  void nullAnswerRejected() {
    assertThrows(Problem.class, () -> TrainPolicy.score(List.of(0, 1), Arrays.asList(0, null)));
  }

  @Test
  void cutoffIncluded() {
    var e = new Enrollment();
    e.status = "QUALIFIED";
    e.validUntil = LocalDate.parse("2026-10-05");
    assertEquals("QUALIFIED", TrainPolicy.status(e, Instant.parse("2026-10-05T15:59:59Z")));
    assertEquals("EXPIRED", TrainPolicy.status(e, Instant.parse("2026-10-05T16:00:00Z")));
  }

  @Test
  void revokedNeverReactivated() {
    var e = new Enrollment();
    e.status = "REVOKED";
    e.validUntil = LocalDate.parse("2026-10-05");
    assertEquals("REVOKED", TrainPolicy.status(e, Instant.parse("2026-10-06T00:00:00Z")));
  }

  @Test
  void staleAndMissingVersionRejected() {
    assertThrows(Problem.class, () -> TrainPolicy.version(2L, 1L));
    assertThrows(Problem.class, () -> TrainPolicy.version(2L, null));
  }
}
