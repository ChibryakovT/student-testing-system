-- Номер зачётной книжки студента (колонка «ID» в ведомости)
ALTER TABLE users ADD COLUMN record_book VARCHAR(20);

-- Существующим студентам присваивается номер вида 2023050012
UPDATE users SET record_book = '202305' || LPAD(CAST(id AS VARCHAR(10)), 4, '0') WHERE role = 'STUDENT';

CREATE INDEX idx_users_group ON users (group_name);
