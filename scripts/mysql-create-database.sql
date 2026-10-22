-- Run once as a MySQL administrator. Replace the example password before use.
CREATE DATABASE IF NOT EXISTS contributions
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

CREATE USER IF NOT EXISTS 'contrib_app'@'localhost'
  IDENTIFIED BY 'REPLACE_WITH_A_STRONG_PASSWORD';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES
  ON contributions.* TO 'contrib_app'@'localhost';
