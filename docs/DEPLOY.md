# Запуск на своём сервере с доменом

Инструкция для Ubuntu 22.04/24.04. Вместо `est.example.com` подставьте свой домен: его A-запись должна указывать на IP сервера.
Без файла `data/university.json` загружается демо-набор с вымышленными преподавателями: для публичной версии так и нужно.

## 1. Что поставить

```bash
sudo apt update && sudo apt install -y openjdk-21-jre-headless postgresql nginx certbot python3-certbot-nginx ufw
sudo ufw allow OpenSSH && sudo ufw allow 'Nginx Full' && sudo ufw enable
```

Порты 8080 и 5432 снаружи остаются закрытыми.

## 2. База данных

```bash
sudo -u postgres psql -c "CREATE USER testing_app WITH PASSWORD 'длинный_случайный_пароль';"
sudo -u postgres psql -c "CREATE DATABASE testing_system OWNER testing_app;"
```

## 3. Приложение

На своём компьютере: `./mvnw -DskipTests package`. Затем скопируйте `target/student-testing-system-1.0.0.jar` на сервер:

```bash
sudo useradd -r -m -d /opt/est -s /usr/sbin/nologin est
sudo cp student-testing-system-1.0.0.jar /opt/est/app.jar
sudo nano /opt/est/.env
sudo chown -R est:est /opt/est && sudo chmod 600 /opt/est/.env
```

Файл `/opt/est/.env` (сгенерировать пароль можно командой `openssl rand -base64 18`):

```ini
DB_URL=jdbc:postgresql://localhost:5432/testing_system
DB_USERNAME=testing_app
DB_PASSWORD=пароль_из_шага_2
ADMIN_USERNAME=admin
ADMIN_PASSWORD=свой_длинный_пароль
DEMO_DATA=true
DEMO_PASSWORD=свой_пароль_студентов1
DEMO_TEACHER_PASSWORD=свой_пароль_преподавателей1
COOKIE_SECURE=true
SERVER_ADDRESS=127.0.0.1
THYMELEAF_CACHE=true
```

Файл `/etc/systemd/system/est.service`:

```ini
[Unit]
Description=Электронная система тестирования
After=network.target postgresql.service
Requires=postgresql.service

[Service]
User=est
WorkingDirectory=/opt/est
ExecStart=/usr/bin/java -Xmx512m -jar /opt/est/app.jar
Restart=on-failure
NoNewPrivileges=true
PrivateTmp=true

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl daemon-reload && sudo systemctl enable --now est
journalctl -u est -f    # дождитесь строки Started TestingSystemApplication
```

## 4. Nginx и HTTPS

В блок `http` файла `/etc/nginx/nginx.conf`:

```nginx
limit_req_zone $binary_remote_addr zone=auth:10m rate=10r/m;
```

Файл `/etc/nginx/sites-available/est`:

```nginx
server {
    listen 80;
    server_name est.example.com;
    client_max_body_size 3m;

    location ~ ^/(login|register|api/auth/register)$ {
        limit_req zone=auth burst=10 nodelay;
        include /etc/nginx/est-proxy.conf;
    }
    location / {
        include /etc/nginx/est-proxy.conf;
    }
}
```

Файл `/etc/nginx/est-proxy.conf`:

```nginx
proxy_pass http://127.0.0.1:8080;
proxy_set_header Host $host;
proxy_set_header X-Real-IP $remote_addr;
proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
proxy_set_header X-Forwarded-Proto $scheme;
```

```bash
sudo ln -s /etc/nginx/sites-available/est /etc/nginx/sites-enabled/
sudo rm -f /etc/nginx/sites-enabled/default
sudo nginx -t && sudo systemctl reload nginx
sudo certbot --nginx -d est.example.com --redirect
```

## 5. Резервные копии

Файл `/etc/cron.daily/est-backup` (права: `sudo chmod +x`). Срок 14 дней указан в политике конфиденциальности:

```bash
#!/bin/sh
mkdir -p /var/backups/est && chmod 700 /var/backups/est
sudo -u postgres pg_dump testing_system | gzip > /var/backups/est/$(date +%F).sql.gz
find /var/backups/est -name '*.sql.gz' -mtime +14 -delete
```

## 6. Проверка после запуска

- `https://est.example.com` открывается с замком, `http://` перенаправляет на `https://`;
- вход работает, в браузере у cookie `JSESSIONID` стоит Secure;
- `curl -m 5 http://IP_СЕРВЕРА:8080` не отвечает;
- без галочки согласия регистрация не проходит.

## Обновление

Соберите новый jar, замените `/opt/est/app.jar` и выполните `sudo systemctl restart est`. Миграции применятся сами.

## Перед использованием в вузе

Демо-режим рассчитан на вымышленные данные. Для настоящих студентов нужны разрешение университета, личные пароли
вместо общих `DEMO_*`, регистрация только через администратора, и политику конфиденциальности надо заново согласовать с вузом.
