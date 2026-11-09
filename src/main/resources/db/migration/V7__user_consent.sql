-- Момент согласия на обработку персональных данных (ставится при самостоятельной регистрации)
ALTER TABLE users ADD COLUMN consent_at TIMESTAMP;
