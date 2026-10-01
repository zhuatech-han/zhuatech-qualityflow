-- Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2
-- 本脚本仅供个人学习、技术研究和非商业交流使用。
-- 原因与验证计划组合历史需要保留两项字段的完整长度。
ALTER TABLE case_event MODIFY COLUMN note text NOT NULL;
