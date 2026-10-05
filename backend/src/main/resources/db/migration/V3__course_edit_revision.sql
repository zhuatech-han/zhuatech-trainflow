-- Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
-- 题目单独变化也推进课程版本，兼容已有V2数据。
ALTER TABLE course ADD COLUMN revision bigint NOT NULL DEFAULT 0;
