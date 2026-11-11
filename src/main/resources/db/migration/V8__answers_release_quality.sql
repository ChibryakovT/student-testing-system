-- Пояснения к вопросам, показ правильных ответов, выдача оценок после проверки, пауза между попытками, автосохранение
ALTER TABLE questions ADD COLUMN explanation VARCHAR(1000);
ALTER TABLE tests ADD COLUMN show_answers VARCHAR(20) NOT NULL DEFAULT 'NEVER';
ALTER TABLE tests ADD COLUMN manual_release BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE tests ADD COLUMN retake_delay_minutes INTEGER;
ALTER TABLE test_attempts ADD COLUMN released BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE test_attempts ADD COLUMN draft VARCHAR(8000);
