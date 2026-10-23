-- Форма контроля по дисциплине: экзамен, зачёт или дифференцированный зачёт
ALTER TABLE subjects ADD COLUMN control_form VARCHAR(20) DEFAULT 'EXAM' NOT NULL;
ALTER TABLE subjects ADD CONSTRAINT chk_subjects_control_form CHECK (control_form IN ('EXAM', 'CREDIT', 'GRADED_CREDIT'));

-- Демонстрационные дисциплины
UPDATE subjects SET control_form = 'CREDIT' WHERE name = 'Компьютерные сети';
UPDATE subjects SET control_form = 'GRADED_CREDIT' WHERE name = 'Информационная безопасность';
