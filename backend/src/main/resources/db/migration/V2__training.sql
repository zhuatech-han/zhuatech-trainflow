-- Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2

CREATE TABLE course (
 id bigint AUTO_INCREMENT PRIMARY KEY,
 version bigint NOT NULL,
 family_code varchar(60) NOT NULL,
 edition int NOT NULL,
 title varchar(160) NOT NULL,
 category varchar(60) NOT NULL,
 department_id bigint NOT NULL,
 author_id bigint NOT NULL,
 publisher_id bigint ,
 content text NOT NULL,
 pass_score int NOT NULL,
 max_attempts int NOT NULL,
 valid_days int NOT NULL,
 practical_required boolean NOT NULL,
 status varchar(20) NOT NULL,
 created_at timestamp(6) NOT NULL
);

CREATE TABLE question (
 id bigint AUTO_INCREMENT PRIMARY KEY,
 course_id bigint NOT NULL,
 position int NOT NULL,
 prompt varchar(1000) NOT NULL,
 option_a varchar(500) NOT NULL,
 option_b varchar(500) NOT NULL,
 option_c varchar(500) NOT NULL,
 option_d varchar(500) NOT NULL,
 correct_answer int NOT NULL
);

CREATE TABLE enrollment (
 id bigint AUTO_INCREMENT PRIMARY KEY,
 version bigint NOT NULL,
 course_id bigint NOT NULL,
 department_id bigint NOT NULL,
 learner_id bigint NOT NULL,
 reviewer_id bigint ,
 assigned_by bigint NOT NULL,
 due_date date NOT NULL,
 status varchar(20) NOT NULL,
 attempts_used int NOT NULL,
 best_score int NOT NULL,
 read_at timestamp(6) ,
 qualified_at timestamp(6) ,
 valid_until date ,
 created_at timestamp(6) NOT NULL
);

CREATE TABLE exam_attempt (
 id bigint AUTO_INCREMENT PRIMARY KEY,
 enrollment_id bigint NOT NULL,
 number int NOT NULL,
 score int NOT NULL,
 answers varchar(500) NOT NULL,
 submitted_at timestamp(6) NOT NULL
);

ALTER TABLE course ADD CONSTRAINT uq_course_edition UNIQUE(family_code,edition);
ALTER TABLE course ADD FOREIGN KEY(department_id) REFERENCES department(id);
ALTER TABLE course ADD FOREIGN KEY(author_id) REFERENCES account(id);
ALTER TABLE course ADD FOREIGN KEY(publisher_id) REFERENCES account(id);
ALTER TABLE question ADD FOREIGN KEY(course_id) REFERENCES course(id);
ALTER TABLE question ADD CONSTRAINT uq_question_position UNIQUE(course_id,position);
ALTER TABLE enrollment ADD FOREIGN KEY(course_id) REFERENCES course(id);
ALTER TABLE enrollment ADD FOREIGN KEY(department_id) REFERENCES department(id);
ALTER TABLE enrollment ADD FOREIGN KEY(learner_id) REFERENCES account(id);
ALTER TABLE enrollment ADD FOREIGN KEY(reviewer_id) REFERENCES account(id);
ALTER TABLE enrollment ADD FOREIGN KEY(assigned_by) REFERENCES account(id);
ALTER TABLE exam_attempt ADD FOREIGN KEY(enrollment_id) REFERENCES enrollment(id);
ALTER TABLE exam_attempt ADD CONSTRAINT uq_attempt_number UNIQUE(enrollment_id,number);
CREATE INDEX ix_course_scope ON course(department_id,status);
CREATE INDEX ix_enrollment_learner ON enrollment(learner_id,status);
CREATE INDEX ix_enrollment_scope ON enrollment(department_id,status,due_date);
CREATE TABLE command_record (id bigint AUTO_INCREMENT PRIMARY KEY, request_key varchar(36) NOT NULL UNIQUE, fingerprint varchar(64) NOT NULL, result_id bigint NOT NULL);
CREATE TABLE flow_event (id bigint AUTO_INCREMENT PRIMARY KEY, kind varchar(20) NOT NULL, object_id bigint NOT NULL, department_id bigint NOT NULL, actor_id bigint NOT NULL, action varchar(40) NOT NULL, note varchar(2000) NOT NULL, created_at timestamp(6) NOT NULL, FOREIGN KEY(department_id) REFERENCES department(id), FOREIGN KEY(actor_id) REFERENCES account(id));
CREATE INDEX ix_event_object ON flow_event(kind,object_id,id);
