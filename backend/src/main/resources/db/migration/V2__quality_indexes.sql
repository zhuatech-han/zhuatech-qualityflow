-- Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2
-- 本脚本仅供个人学习、技术研究和非商业交流使用。
CREATE INDEX ix_case_created ON quality_case(created_at,id);
ALTER TABLE nav_menu ADD CONSTRAINT fk_menu_permission FOREIGN KEY(permission_code) REFERENCES permission(code);
