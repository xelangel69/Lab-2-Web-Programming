# Лабораторная работа №2 — вариант 722514

Доработка https://github.com/xelangel69/Lab-1-Web-Programming: сохранены оформление, имя студента и группа из исходной работы. Проверка перенесена с JavaScript в Java FastCGI; Canvas заменён SVG, X — кнопками −3…5, Y — текстом (−3; 3), R — checkbox 1…5. Данные отправляются асинхронно через jQuery $.ajax с POST, без перезагрузки страницы. jQuery включена локально. Gradle и Maven не используются.

## Область

Объединение трёх замкнутых фигур:

- Прямоугольник: −R ≤ x ≤ 0, −R/2 ≤ y ≤ 0.
- Треугольник: x ≤ 0, y ≥ 0, y − x ≤ R/2.
- Четверть круга: x ≥ 0, y ≤ 0, x² + y² ≤ R²/4.

Границы включены. Сервер использует BigDecimal, чтобы десятичные числа у границ проверялись без погрешности double. Y = −3 и Y = 3 запрещены. Можно выбрать несколько R: одна отправка создаёт несколько строк. Все параметры проверяются до изменения истории. Время — Instant сервера (UTC), браузер показывает его в своём часовом поясе; длительность — System.nanoTime, в миллисекундах.

## Сборка

Нужен JDK 17 или новее. Приложенная библиотека находится в lib/fastcgi-lib.jar.

```sh
sh build.sh
```

## Запуск на Helios

Загрузка на Helios и запуск в учётной записи пользователя здесь не выполнялись: адрес учётной записи и доступ не предоставлены. Ниже готовая последовательность. Выберите два свободных разрешённых вам порта, числа 24000 и 24001 — примеры.

1. Скопируйте каталог проекта на Helios в ~/lab2 (замените sXXXXXX своим логином):

```sh
scp -r lab2 sXXXXXX@helios.cs.ifmo.ru:~/
ssh sXXXXXX@helios.cs.ifmo.ru
cd ~/lab2
sh build.sh
mkdir -p httpd-root/mutex-dir
# Готовый конфиг рассчитан на ~/lab2, HTTP 24000 и FastCGI 24001.
# Если нужны другие порты или каталог: sh configure.sh 24000 24001
```

configure.sh использует приложенный пользователем шаблон Apache, подставляет пути, порты, текущие имя пользователя и группу; создаёт каталоги журналов и файл для FastCgiExternalServer. Исходные пути модулей /usr/local/libexec/apache24 оставлены из шаблона.

2. Запустите Java в отдельном терминале, чтобы видеть ошибки запуска:

```sh
cd ~/lab2
java -DFCGI_PORT=24001 -jar httpd-root/fcgi-bin/app.jar
```

После успешной проверки для фонового запуска используйте:

```sh
nohup java -DFCGI_PORT=24001 -jar httpd-root/fcgi-bin/app.jar > java.log 2>&1 &
echo $! > java.pid
```

Не запускайте оба варианта одновременно. JAR включает FastCGI-библиотеку, отдельный classpath при запуске не нужен. Библиотека принимает TCP FastCGI, а не HTTP. В ней сокет слушает все интерфейсы; используйте правила доступа Helios. Веб-запросы направляются на Apache.

3. В другом терминале проверьте и запустите Apache:

```sh
/usr/local/sbin/httpd -t -f "$HOME/lab2/httpd-root/conf/httpd.conf"
/usr/local/sbin/httpd -f "$HOME/lab2/httpd-root/conf/httpd.conf" -k start
```

Если расположение httpd отличается, найдите его командой command -v httpd. Откройте http://helios.cs.ifmo.ru:24000/ (либо используйте SSH-туннель, если требуется условиями доступа). Apache обслуживает static/ и передаёт /fcgi-bin/app.jar в Java через mod_fastcgi. В шаблоне используется -nph, поэтому Java возвращает полную строку HTTP/1.1 вместе с заголовками. Для mod_proxy_fcgi этот формат нужно изменить.

Остановка:

```sh
/usr/local/sbin/httpd -f "$HOME/lab2/httpd-root/conf/httpd.conf" -k stop
kill "$(cat java.pid)"
```

## История и API

POST /fcgi-bin/app.jar, Content-Type: application/x-www-form-urlencoded:

```text
action=check&x=-1&y=-0.5&r=2&r=3
action=history
action=clear
```

Ответ JSON содержит results, serverTime и elapsedMs. Строки результатов содержат x, y, r, hit, timestamp и elapsedMs. Ошибки возвращают 400, неверный метод — 405, формат тела — 415. Тело ограничено 4096 байтами, текст Y — 64 символами. Поддерживается десятичная запятая; экспоненциальная запись запрещена.

История хранится в памяти Java по случайной cookie LAB2SID (HttpOnly, SameSite=Lax). Она сохраняется между запросами и обновлениями страницы, отделена между браузерами. Перезапуск Java стирает историю. Это учебное приложение: сессии и история не имеют автоматического срока очистки; длительная публичная эксплуатация потребует ограничения памяти и постоянного хранилища.

## Проверки

```sh
sh build.sh
javac --release 17 -cp build/classes -d build/test-classes tests/AreaTest.java
java -cp build/test-classes:build/classes org.example.AreaTest
python3 tests/fastcgi_test.py
```

Проверены границы фигур, близкие десятичные значения, валидация, отсутствие частичной записи при ошибке. Интеграционный тест запускает настоящий Java FastCGI и проверяет POST, cookie, изоляцию истории, очистку, 400 и 405 через TCP. Apache на Helios и внешний браузер в этой среде не проверялись.

## Структура как в примере, без Gradle

Ориентир: https://github.com/sub-myitmo/web-1/tree/9fd864b9298e65e8d8141a0600e5b381451c6014

```text
src/main/java/org/example/
  Main.java       — FastCGI, POST, JSON, история
  Checker.java    — валидация и геометрия варианта
static/           — HTML, CSS, JavaScript, локальная jQuery
lib/fastcgi-lib.jar
httpd-root/
  conf/httpd.conf.template
  fcgi-bin/app.jar — готовый самодостаточный JAR
build.sh          — javac + jar
configure.sh      — пути, логин и порты для Helios
tests/
```

Оформление сохранено из вашей ЛР №1. У примера заимствован подход к структуре проекта, разделению Main/Checker, локальной jQuery и запуску JAR. GET и геометрия чужого варианта не переносились: в вашем задании нужен POST.

Готовый `httpd-root/conf/httpd.conf` включён в архив. Он использует `${HOME}/lab2`, поэтому логин Helios подставлять не нужно. Apache запускайте из своей учётной записи. Порты 24000/24001 должны быть свободны; для других портов используйте configure.sh. Проверка конфигурации должна выполняться на Helios, где доступны модули из предоставленного шаблона.
