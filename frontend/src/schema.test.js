// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
import test from "node:test";
import assert from "node:assert/strict";
import { actions } from "./schema.js";
const me = {
  id: 4,
  permissions: [
    "learn",
    "course.manage",
    "course.publish",
    "practical.review",
    "training.assign",
  ],
};
test("author cannot publish own draft", () =>
  assert.deepEqual(
    actions("courses", { status: "DRAFT", authorId: 4 }, me),
    [],
  ));
test("independent publisher can publish", () =>
  assert.deepEqual(actions("courses", { status: "DRAFT", authorId: 5 }, me), [
    "publish",
  ]));
test("only learner acknowledges assigned reading", () => {
  assert.ok(
    actions("enrollments", { status: "ASSIGNED", learnerId: 4 }, me).includes(
      "read",
    ),
  );
  assert.ok(
    !actions("enrollments", { status: "ASSIGNED", learnerId: 5 }, me).includes(
      "read",
    ),
  );
});
test("only independent designated reviewer can evaluate practice", () => {
  const r = { status: "PRACTICAL", reviewerId: 4, learnerId: 5, assignedBy: 6 };
  assert.ok(actions("enrollments", r, me).includes("pass"));
  assert.ok(
    !actions("enrollments", { ...r, assignedBy: 4 }, me).includes("pass"),
  );
  assert.ok(
    !actions("enrollments", { ...r, learnerId: 4 }, me).includes("pass"),
  );
});
test("expired qualification does not show revoke", () =>
  assert.deepEqual(actions("enrollments", { status: "EXPIRED" }, me), []));
