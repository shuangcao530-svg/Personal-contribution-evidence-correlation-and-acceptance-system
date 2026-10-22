CREATE TABLE IF NOT EXISTS users (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  username VARCHAR(80) NOT NULL UNIQUE,
  name VARCHAR(80) NOT NULL,
  role VARCHAR(20) NOT NULL,
  password_hash VARCHAR(255) NOT NULL
);
CREATE TABLE IF NOT EXISTS projects (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(160) NOT NULL,
  description TEXT NOT NULL,
  owner_id BIGINT NOT NULL,
  created_at VARCHAR(40) NOT NULL,
  FOREIGN KEY (owner_id) REFERENCES users(id)
);
CREATE TABLE IF NOT EXISTS project_members (
  project_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  PRIMARY KEY (project_id, user_id),
  FOREIGN KEY (project_id) REFERENCES projects(id),
  FOREIGN KEY (user_id) REFERENCES users(id)
);
CREATE TABLE IF NOT EXISTS tasks (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  project_id BIGINT NOT NULL,
  title VARCHAR(160) NOT NULL,
  description TEXT NOT NULL,
  assignee_id BIGINT NOT NULL,
  due_date VARCHAR(10) NOT NULL,
  created_at VARCHAR(40) NOT NULL,
  FOREIGN KEY (project_id) REFERENCES projects(id),
  FOREIGN KEY (assignee_id) REFERENCES users(id)
);
CREATE TABLE IF NOT EXISTS criteria (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  task_id BIGINT NOT NULL,
  text VARCHAR(1000) NOT NULL,
  FOREIGN KEY (task_id) REFERENCES tasks(id)
);
CREATE TABLE IF NOT EXISTS evidence (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  project_id BIGINT NOT NULL,
  uploader_id BIGINT NOT NULL,
  title VARCHAR(160) NOT NULL,
  type VARCHAR(10) NOT NULL,
  url VARCHAR(2000),
  file_name VARCHAR(255),
  storage_name VARCHAR(100),
  sha256 VARCHAR(64),
  created_at VARCHAR(40) NOT NULL,
  FOREIGN KEY (project_id) REFERENCES projects(id),
  FOREIGN KEY (uploader_id) REFERENCES users(id)
);
CREATE TABLE IF NOT EXISTS contributions (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  task_id BIGINT NOT NULL,
  student_id BIGINT NOT NULL,
  status VARCHAR(20) NOT NULL,
  current_version INT NOT NULL,
  created_at VARCHAR(40) NOT NULL,
  updated_at VARCHAR(40) NOT NULL,
  FOREIGN KEY (task_id) REFERENCES tasks(id),
  FOREIGN KEY (student_id) REFERENCES users(id)
);
CREATE TABLE IF NOT EXISTS revisions (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  contribution_id BIGINT NOT NULL,
  version INT NOT NULL,
  title VARCHAR(160) NOT NULL,
  description TEXT NOT NULL,
  created_at VARCHAR(40) NOT NULL,
  UNIQUE (contribution_id, version),
  FOREIGN KEY (contribution_id) REFERENCES contributions(id)
);
CREATE TABLE IF NOT EXISTS evidence_references (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  revision_id BIGINT NOT NULL,
  evidence_id BIGINT NOT NULL,
  criterion_id BIGINT NOT NULL,
  location VARCHAR(500) NOT NULL,
  note VARCHAR(2000) NOT NULL,
  FOREIGN KEY (revision_id) REFERENCES revisions(id),
  FOREIGN KEY (evidence_id) REFERENCES evidence(id),
  FOREIGN KEY (criterion_id) REFERENCES criteria(id)
);
CREATE TABLE IF NOT EXISTS reviews (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  revision_id BIGINT NOT NULL UNIQUE,
  reviewer_id BIGINT NOT NULL,
  decision VARCHAR(20) NOT NULL,
  comment VARCHAR(4000) NOT NULL,
  created_at VARCHAR(40) NOT NULL,
  FOREIGN KEY (revision_id) REFERENCES revisions(id),
  FOREIGN KEY (reviewer_id) REFERENCES users(id)
);
CREATE TABLE IF NOT EXISTS review_items (
  review_id BIGINT NOT NULL,
  criterion_id BIGINT NOT NULL,
  passed BOOLEAN NOT NULL,
  comment VARCHAR(2000) NOT NULL,
  PRIMARY KEY (review_id, criterion_id),
  FOREIGN KEY (review_id) REFERENCES reviews(id),
  FOREIGN KEY (criterion_id) REFERENCES criteria(id)
);

CREATE TABLE IF NOT EXISTS activity_logs (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 project_id BIGINT NOT NULL,
 actor_id BIGINT NOT NULL,
 action VARCHAR(40) NOT NULL,
 entity_type VARCHAR(40) NOT NULL,
 entity_id BIGINT NOT NULL,
 summary VARCHAR(500) NOT NULL,
 created_at VARCHAR(40) NOT NULL,
 INDEX idx_activity_project_created (project_id, created_at),
 FOREIGN KEY (project_id) REFERENCES projects(id),
 FOREIGN KEY (actor_id) REFERENCES users(id)
);
