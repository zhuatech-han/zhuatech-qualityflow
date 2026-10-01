-- Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2

CREATE TABLE department (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  name varchar(120) NOT NULL UNIQUE
);

CREATE TABLE access_role (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  name varchar(120) NOT NULL UNIQUE,
  scope varchar(20) NOT NULL
);

CREATE TABLE permission (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  code varchar(60) NOT NULL UNIQUE,
  name varchar(120) NOT NULL
);

CREATE TABLE role_permission (role_id bigint NOT NULL, permission_code varchar(60) NOT NULL, PRIMARY KEY(role_id, permission_code), FOREIGN KEY(role_id) REFERENCES access_role(id), FOREIGN KEY(permission_code) REFERENCES permission(code));

CREATE TABLE nav_menu (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  code varchar(60) NOT NULL UNIQUE,
  name varchar(120) NOT NULL,
  name_en varchar(120) NOT NULL,
  permission_code varchar(60) NOT NULL,
  position int NOT NULL,
  enabled boolean NOT NULL
);

CREATE TABLE account (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  username varchar(60) NOT NULL UNIQUE,
  display_name varchar(120) NOT NULL,
  password_hash varchar(100) NOT NULL,
  role_id bigint NOT NULL,
  department_id bigint NOT NULL,
  enabled boolean NOT NULL,
  FOREIGN KEY (role_id) REFERENCES access_role(id),
  FOREIGN KEY (department_id) REFERENCES department(id)
);

CREATE TABLE audit_event (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  actor varchar(60) NOT NULL,
  action varchar(120) NOT NULL,
  object_id varchar(80) NOT NULL,
  department_id bigint NOT NULL,
  created_at timestamp(6) NOT NULL
);

CREATE TABLE system_setting (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  code varchar(60) NOT NULL UNIQUE,
  parameter_value varchar(200) NOT NULL
);

CREATE TABLE dictionary_entry (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  type varchar(60) NOT NULL,
  code varchar(60) NOT NULL,
  name varchar(120) NOT NULL,
  name_en varchar(120) NOT NULL,
  UNIQUE (type, code)
);



CREATE TABLE quality_case (
 id bigint AUTO_INCREMENT PRIMARY KEY, change_count bigint NOT NULL, version bigint NOT NULL, number varchar(60) NOT NULL UNIQUE,
 title varchar(200) NOT NULL, description text NOT NULL, source varchar(60) NOT NULL,
 category varchar(60) NOT NULL, severity varchar(20) NOT NULL, reference varchar(200) NOT NULL,
 department_id bigint NOT NULL, reporter_id bigint NOT NULL, owner_id bigint NOT NULL, reviewer_id bigint NOT NULL,
 due_date date NOT NULL, verify_after date NULL, status varchar(30) NOT NULL, cycle int NOT NULL,
 containment text NOT NULL, root_cause text NOT NULL, verification_plan text NOT NULL,
 review_note varchar(2000) NOT NULL, verification_evidence text NOT NULL,
 created_at timestamp(6) NOT NULL, closed_at timestamp(6) NULL, closed_by bigint NULL,
 FOREIGN KEY (department_id) REFERENCES department(id), FOREIGN KEY (reporter_id) REFERENCES account(id),
 FOREIGN KEY (owner_id) REFERENCES account(id), FOREIGN KEY (reviewer_id) REFERENCES account(id),
 FOREIGN KEY (closed_by) REFERENCES account(id), CHECK(cycle>0), CHECK(owner_id<>reviewer_id AND reporter_id<>reviewer_id)
);
CREATE TABLE corrective_action (
 id bigint AUTO_INCREMENT PRIMARY KEY, case_id bigint NOT NULL, cycle int NOT NULL, kind varchar(20) NOT NULL,
 description varchar(2000) NOT NULL, owner_id bigint NOT NULL, due_date date NOT NULL, status varchar(20) NOT NULL,
 evidence varchar(3000) NOT NULL, completed_at timestamp(6) NULL, completed_by bigint NULL,
 FOREIGN KEY (case_id) REFERENCES quality_case(id), FOREIGN KEY (owner_id) REFERENCES account(id),
 FOREIGN KEY (completed_by) REFERENCES account(id), CHECK(cycle>0)
);
CREATE TABLE case_event (
 id bigint AUTO_INCREMENT PRIMARY KEY, case_id bigint NOT NULL, cycle int NOT NULL, actor varchar(60) NOT NULL,
 action varchar(60) NOT NULL, from_status varchar(30) NOT NULL, to_status varchar(30) NOT NULL,
 note varchar(6000) NOT NULL, created_at timestamp(6) NOT NULL, FOREIGN KEY(case_id) REFERENCES quality_case(id)
);
CREATE TABLE mutation_stamp (
 id bigint AUTO_INCREMENT PRIMARY KEY, case_id bigint NOT NULL, actor varchar(60) NOT NULL,
 request_key varchar(80) NOT NULL, fingerprint varchar(64) NOT NULL,
 FOREIGN KEY(case_id) REFERENCES quality_case(id), UNIQUE(case_id,actor,request_key)
);
CREATE INDEX ix_case_department_status ON quality_case(department_id,status,due_date);
CREATE INDEX ix_case_assignees ON quality_case(owner_id,reviewer_id,reporter_id);
CREATE INDEX ix_action_case_cycle ON corrective_action(case_id,cycle,status);
CREATE INDEX ix_action_owner ON corrective_action(owner_id,case_id);
CREATE INDEX ix_event_case_time ON case_event(case_id,created_at);
CREATE INDEX ix_audit_scope_time ON audit_event(department_id,created_at);
