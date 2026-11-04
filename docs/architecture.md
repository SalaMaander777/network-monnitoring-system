# Архитектура: контракты для исполнителей

Версия 1 · основание: `docs/TZ.md` v2.2 · ведёт `architect`. Изменить контракт может только `architect`: исполнитель, которому нужно изменение, сообщает об этом в отчёте и не правит сам.

Пометки: **[ПОДТВ]** проверено на этой машине запуском пробного проекта; **[ЧЕЛОВЕК]** проверка человеком, здесь проверить нельзя.

## 1. Стек и версии

Версии фиксированы в корневом `pom.xml`. Новая зависимость или смена версии только через `architect` и с повторным `scripts/prepare-offline.sh`.

| Компонент | Версия | Причина и статус |
|---|---|---|
| Java | язык и API 21 (`maven.compiler.release=21`), в поставке JRE 21 | Раздел 10 ТЗ. Пробный проект: macOS, JDK 26, сборка с `--release 21` [ПОДТВ]. Текущая машина разработки: Linux, OpenJDK 21.0.12. Прогон на Windows с JRE поставки: T8.17 |
| Maven | 3.9.x, без wrapper | На текущей машине 3.9.11 (пробный проект 3.9.16); на офлайн-машину ставится из комплекта (T8.12). Wrapper скачивал бы дистрибутив, а это лишний сетевой шаг |
| Spring Boot | 4.1.2 (`spring-boot-starter-parent`) | Последний выпуск ветки 4.1 на дату решения; Java 21 и 26 [ПОДТВ] |
| Spring Security / Jackson | из BOM: 7.1.2 / 3.1.7 | Пакет Jackson 3: `tools.jackson.*`, аннотации остаются в `com.fasterxml.jackson.annotation` |
| PostgreSQL | 18.6 в тестах (`embedded-postgres-binaries-bom:18.6.0`), та же ветка 18 в поставке | Совпадение версий тестов и поставки. См. риск R-1 |
| Драйвер PostgreSQL, Flyway | из BOM: 42.7.14, 12.4.0 + `flyway-database-postgresql` | [ПОДТВ] |
| Доступ к БД | `JdbcClient` (spring-jdbc), без JPA/Hibernate | Разделы, частичные индексы, `ON CONFLICT` и пакетная запись видны прямо в SQL |
| SNMP | `org.snmp4j:snmp4j:3.9.7` | Есть в Central, разрешается [ПОДТВ] |
| Excel | `org.apache.poi:poi-ooxml:5.5.1` | Зрелая ветка 5.x, не первая версия 6 |
| Тесты БД | `io.zonky.test:embedded-postgres:2.2.2` | Без Docker. Транзитивно (область `runtime`) приходят двоичные файлы `windows-amd64`, `linux-amd64`, `linux-amd64-alpine`, `darwin-amd64`; все они x86_64 [ПОДТВ состав `offline/m2`]. Поддерживаемые платформы тестов в §3 |
| Границы модулей | `com.tngtech.archunit:archunit-junit5:1.5.1` | [ПОДТВ] |
| Браузерные тесты | Selenium из BOM 4.43.0, geckodriver 0.37.1 (минимум Firefox 115 ESR по таблице Mozilla), Firefox 115.44.0esr, Chrome for Testing 156.0.8078.80 | Playwright не управляет Firefox 115. Selenium Manager отключён, пути к драйверам заданы явно (§12.4) |
| Карта | Leaflet 1.9.4, Leaflet.markercluster 1.5.3, локальные файлы | Раздел 10 ТЗ |
| Тайлы | `org.xerial:sqlite-jdbc:3.54.1.0` для чтения MBTiles | Содержит нативные библиотеки Windows и macOS |
| Служба Windows | WinSW 2.12.0 (`WinSW-x64.exe`) | Один exe, работает без .NET-установки в x64-сборке [ЧЕЛОВЕК] |
| Фронтенд | Без сборщика: ES-модули и CSS как есть, без npm в сборке | C-1, C-5; нет лишней цепочки; Firefox 115 поддерживает ES-модули |

**Риск R-1.** На странице совместимости EDB для коммерческих редакций PostgreSQL 18 указаны Windows Server 2022/2025 и Windows 11, а допущение Q-1 включает Windows 10 и Server 2016. Для общедоступного установщика это здесь не проверено. Поэтому: T8.03 содержит проверку человеком на целевой ОС; SQL проекта не использует возможности новее PostgreSQL 16; откат на ветку 17 или 16 меняет одно свойство `pg.binaries.version` и архив в поставке.

## 2. Структура проекта

```
pom.xml                 родитель: версии, enforcer, профили
server/                 приложение Spring Boot (nm-server)
emulators/              эмуляторы, без Spring (nm-emulators), запускаемый jar с классификатором exec
scripts/                prepare-offline, offline-verify, prepare-browsers (.sh и .ps1)
dist/                   шаблоны и скрипты поставки Windows (задача 8)
docs/                   документы
offline/, tools/        создаются скриптами, не хранятся в git
```

Координаты: `groupId=ru.netmon`, `version=0.1.0` у всех модулей. Корень `nm-parent` (`packaging=pom`, родитель `spring-boot-starter-parent:4.1.2`), модули `emulators` (artifactId `nm-emulators`) и `server` (artifactId `nm-server`), порядок в реакторе: `emulators`, `server`. Артефакты: `server/target/nm-server-0.1.0.jar` (исполняемый, `repackage` заменяет основной jar), `emulators/target/nm-emulators-0.1.0.jar` (обычный, его получает `server` в тестах) и `emulators/target/nm-emulators-0.1.0-exec.jar` (исполняемый). `finalName` не переопределяется.

### 2.1 Пакеты сервера и владельцы

Базовый пакет `ru.netmon`. Модуль = подпакет.

| Пакет | Содержимое | Таблицы (только этот модуль пишет) | Владелец |
|---|---|---|---|
| `contract` | Общие enum, record, интерфейсы между модулями (§7). Без Spring и Jackson | нет | только T1.03, далее через `architect` |
| `common` | Ошибки API, страница `/error`, постраничность, `Clock`, заголовки безопасности, пользователь сессии `common.security.CurrentUser` (§5.1), удаление секретных полей из JSON `common.json.SecretFieldGuard` (§7.2, с T1.06), замена NUL `common.json.NulGuard` (§7.2, с T1.07), проверка периода `common.web.PeriodParams` (§5.1, с T1.07) | нет | backend-core |
| `auth` | Вход, сессии, блокировка, пользователи | `app_user` | backend-core |
| `audit` | Журнал действий: запись, чтение, хранение (T3.23) | `audit_log` | backend-core |
| `settings` | Настройки системы | `system_setting` | backend-core |
| `device` | Устройства, типы, группы | `device`, `device_type`, `device_group` | backend-core; `device.status*` пишет только `status` |
| `profile` | Профили, проверки, начальные данные | `profile`, `profile_check` | backend-core |
| `secret` | Секреты, шифрование | `access_secret` | backend-core |
| `stream` | Поток SSE | нет | backend-core |
| `journal` | Журнал событий: запись и поиск | `event` | запись T1.06 (backend-core), поиск и хранение T3.15, T3.16 (polling-engineer) |
| `poll` | Планировщик, очередь результатов, разделы | `check_result` | polling-engineer |
| `check.ping`, `check.tcp` | Исполнители проверок | нет | polling-engineer |
| `check.scpi` | Исполнитель SCPI | нет | polling-engineer |
| `status` | Счётчики, расчёт статуса, устаревание | `check_state`, столбцы статуса `device` | polling-engineer |
| `system` | Реестр здоровья, страница состояния | нет | polling-engineer, дополняет windows-packager |
| `snmp` | Клиент SNMP, исполнитель, приём трапов, правила трапов | `trap_rule` | snmp-engineer |
| `alarm` | Пороги, тревоги, сводка | `alarm` | alarm-engineer |
| `excel` | Выгрузка и загрузка | нет | excel-engineer |
| `tiles` | Тайл-сервер | нет | map-frontend |

Ресурсы:

| Путь в `server/src/main/resources` | Содержимое |
|---|---|
| `db/migration/` | Flyway; правило номеров в §4.1 |
| `seed/profiles/<ключ>.json` | Начальные профили, §9.4 |
| `static/` | Фронтенд, §11 |

### 2.2 Правила границ (проверяет `ArchitectureTest`, ArchUnit)

1. Модуль зависит только от `contract`, `common` и себя. Исключений нет: межмодульные вызовы идут через интерфейсы `contract`.
2. `contract` не зависит ни от чего, кроме `java.*`.
3. Классы контроллеров лежат в своём модуле, каждый метод с `@*Mapping` имеет `@PreAuthorize` или входит в короткий список публичных (§5.1). Единственное исключение (с T1.05): `ru.netmon.common.web.ApiErrorController` лежит в `common` и его метод `/error` не несёт `@PreAuthorize`. Причина: это страница ошибки контейнера, доступ к ней задаёт `dispatcherTypeMatchers(ERROR)` (§5.1), а не права ресурса.
4. В `main` нет литералов OID: шаблон `1.3.6.1` запрещён во всём коде, кроме ресурсов `seed/`.
5. (с T1.05, ревью T1.04 Н-а) Тело запроса `@RequestBody` верхнего уровня всегда объект JSON: параметр с `@RequestBody` не бывает `Collection`, `Map` или массивом. Списки передаются полем объекта (`{"items":[...]}`). Причина: тогда путь ошибки элемента (`items[1].port`) Spring строит сам, а `ParameterErrors` элемента контейнера с `getField()` без префикса (`port`, проверено на Spring 7.0.10) в API не возникает; объект верхнего уровня к тому же расширяем без смены формата.

Формулировка в `ArchitectureTest` (archunit-junit5 1.5.1, `@AnalyzeClasses(packages = "ru.netmon", importOptions = ImportOption.DoNotIncludeTests.class)`; проверено прототипом, нарушения ловятся):
- Модуль класса: первый сегмент пакета после `ru.netmon.`, для `ru.netmon.check.<x>..` это `check.<x>` (`check.ping` и `check.tcp` разные модули). `contract.events` входит в `contract`. Корневой пакет `ru.netmon` (класс приложения) модулем не является и правилу 1 не подчиняется, но и модули от него не зависят.
- П. 1: собственное `ArchCondition<JavaClass>` на `classes().that().resideInAPackage("ru.netmon.*..")`: каждая прямая зависимость (`getDirectDependenciesFromSelf()`, тип цели через `getBaseComponentType()`) на класс из `ru.netmon` или `ru.netmon..` ведёт в модуль `contract`, `common` или собственный. Внешние библиотеки правилом 1 не ограничены.
- П. 2: `classes().that().resideInAPackage("ru.netmon.contract..").should().onlyDependOnClassesThat().resideInAnyPackage("java..", "ru.netmon.contract..")`.
- П. 3, два правила с `allowEmptyShould(true)`, оба с отбором `doNotHaveFullyQualifiedName("ru.netmon.common.web.ApiErrorController")` (для правила методов через `areDeclaredInClassesThat()`; с T1.05, проверено прототипом: без отбора оба правила падают на этом классе): классы, мета-аннотированные `@Controller`, лежат в `ru.netmon.*..` вне `ru.netmon.contract..` и `ru.netmon.common..`; методы, объявленные в таких классах и мета-аннотированные `@RequestMapping`, аннотированы `@PreAuthorize` на самом методе. Публичный метод (§5.1) несёт `@PreAuthorize("permitAll()")`; что именно эти методы и только они публичны, проверяет `SecurityMatrixTest` (T1.26). Причина: правило ArchUnit не сопоставляет пути, а отдельный список имён методов в тесте пришлось бы править каждой задаче.
- П. 4 не выразим в ArchUnit (литералы строк в теле метода он не импортирует): обычный метод `@Test` в том же классе читает все файлы под `server/src/main` (рабочий каталог Surefire `server/`, путь `src/main`), кроме `src/main/resources/seed/`, и ищет подстроку `1.3.6.1` без регулярного выражения. Файл читается байтами и декодируется ISO-8859-1 (любой байт допустим), поэтому двоичные ресурсы (`alarm.wav`, изображения Leaflet) проверяются и не роняют тест; ошибка ввода-вывода роняет тест исключением и не засчитывается как нарушение: внутри лямбды фильтра `IOException` оборачивается в `UncheckedIOException` и пробрасывается, другого перехвата нет. Модуль `emulators` не проверяется.
- П. 5 (T1.05): собственное `ArchCondition<JavaMethod>` с `allowEmptyShould(true)` на методы тех же классов, что в п. 3, мета-аннотированные `@RequestMapping`: каждый `JavaParameter`, аннотированный `@RequestBody`, имеет `getRawType()`, который не массив (`isArray()`) и не `isAssignableTo(Collection.class)`, `isAssignableTo(Map.class)`.

Модуль пишет только в свои таблицы. Чтение чужих таблиц SQL-запросом разрешено только там, где это явно указано в карточке (сводка, карточка устройства, отчёты), запись запрещена.

## 3. Сборка и офлайн (C-5, OPS-10)

Решение: **Maven, многомодульный проект**. Причина: установлен на машине, артефакты разрешаются в обычный каталог, копируемый на другую машину.

| Действие | Команда | Статус |
|---|---|---|
| Подготовка, нужен интернет | `scripts/prepare-offline.sh` (`.ps1` на Windows): `mvn -B -Dmaven.repo.local=offline/m2 verify` | Полный `verify`, а не `dependency:go-offline`: только так докачиваются все плагины и провайдер Surefire. После `mvn test` офлайновый `verify` падает на `maven-jar-plugin`, проверено [ПОДТВ] |
| Сборка без сети | `scripts/offline-verify.sh` (`.ps1`): `mvn -B -o -Dmaven.repo.local=offline/m2 verify` | Работает после подготовки, 225 МБ на пробном проекте [ПОДТВ]. Перенос каталога на Windows [ЧЕЛОВЕК] |
| Поставка | `mvn -B -o -Pdist -Dmaven.repo.local=offline/m2 verify` | Появляется в T8.01; подготовка тогда запускается с `-Pdist,ui` |
| Обычная работа | `mvn -B -pl server -am test -Dtest=ИмяТеста` | `~/.m2`, родитель задаёт `surefire.failIfNoSpecifiedTests=false` |

Enforcer: `requireJavaVersion [21,)`, `requireMavenVersion [3.9,4)`.

**Платформы тестов с встроенным PostgreSQL:** Windows x86_64 (целевая) и Linux x86_64 (машина разработки). Двоичные файлы для них приходят транзитивно от `embedded-postgres:2.2.2` на любой машине, где запущен `prepare-offline`, поэтому `offline/m2`, подготовленный на Linux, содержит `windows-amd64` [ПОДТВ наличие файла; запуск на Windows ЧЕЛОВЕК]. Решение: arm64-варианты (`embedded-postgres-binaries-darwin-arm64v8`, `-linux-arm64v8`) не добавляются. Причина: ни целевая платформа, ни текущая машина разработки не arm64, а лишний артефакт только увеличивает комплект. Понадобится разработка на Apple Silicon или Linux arm64: запрос к `architect`, добавляется одна зависимость `test` с версией из BOM и повторяется `prepare-offline`. Работа `darwin-amd64` под Rosetta не подтверждена.

Артефакты Spring Boot 4 разнесены по стартерам. Использовать: `spring-boot-starter-webmvc`, `-security`, `-validation`, `-jdbc`, `-flyway`; для тестов `-webmvc-test`, `-security-test`. Класс: `org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc` [ПОДТВ].

Правила `pom.xml` (T1.01):
- Свойство `pg.binaries.version=18.6.0` в корне; импорт `io.zonky.test.postgres:embedded-postgres-binaries-bom:${pg.binaries.version}` в `dependencyManagement` стоит до остальных импортов.
- `server` получает `org.snmp4j:snmp4j` в области `compile` сразу в T1.01: модуль `snmp` (задача 4) не правит `pom.xml`. `poi-ooxml` вносит T7.01, `sqlite-jdbc` T2.02, Selenium T1.27.
- Внешних адресов в `pom.xml` нет. `xsi:schemaLocation` и `xmlns:xsi` не пишутся, остаётся только `xmlns="http://maven.apache.org/POM/4.0.0"`: это имя пространства имён, а не адрес, Maven по нему не обращается. Репозитории и `pluginRepositories` в `pom.xml` не объявляются (используется Central по умолчанию, только в `prepare-offline`). Проверка механическая: в `pom.xml` нет `http://` и `https://` вне атрибута `xmlns`.
- `emulators` в областях `compile`/`runtime` зависит только от `snmp4j` и `slf4j-simple`. В области `test` разрешены `org.junit.jupiter:junit-jupiter` и `org.assertj:assertj-core` без указания версии (берутся из BOM Spring Boot 4.1.2). Причина: `EmulatorMainTest` и тесты эмуляторов не собрать без JUnit, а `spring-boot-starter-test` тянул бы Spring в модуль без Spring. Другие тестовые зависимости в `emulators` только через `architect`.
- Зависимость `server` от `nm-emulators` (область `test`) исключает `org.slf4j:slf4j-simple`: в `server` единственный провайдер SLF4J это Logback, иначе `LogCapture` не видит вывод.
- Пароль, который Spring Security генерирует при отсутствии своего `UserDetailsService`, не должен попадать в лог (правило секретов). До T1.05 (свой `AppUserDetailsService`) автоконфигурация пользователя по умолчанию исключается; имя класса берётся из jar Spring Boot 4.1.2, не по памяти.


## 4. Схема БД

Всё время в `timestamptz`, расчёт дат разделов в UTC. Весь SQL совместим с PostgreSQL 16 и новее.

### 4.1 Правило миграций

`V<ЗЗММ>__описание.sql`, где `ЗЗ` номер задачи, `ММ` номер микрозадачи: T3.05 создаёт `V0305__...`, несколько файлов `V0305.1__`, `V0305.2__`. Так параллельные исполнители не сталкиваются. Весь начальный DDL создаёт T1.02 (`V0102__core_schema.sql`). Применённую миграцию не правят. Изменение схемы позже только через `architect`.

### 4.2 DDL (T1.02 переносит дословно)

```sql
CREATE TABLE app_user (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  login text NOT NULL CHECK (length(login) BETWEEN 1 AND 64),
  password_hash text NOT NULL,
  role text NOT NULL CHECK (role IN ('OPERATOR','ADMIN')),
  active boolean NOT NULL DEFAULT true,
  failed_attempts int NOT NULL DEFAULT 0,
  locked_until timestamptz,
  password_changed_at timestamptz NOT NULL DEFAULT now(),
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_app_user_login ON app_user (lower(login));

CREATE TABLE system_setting (
  key text PRIMARY KEY,
  value text NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by bigint REFERENCES app_user(id)
);
INSERT INTO system_setting(key, value) VALUES
  ('retention.results_days','30'),
  ('retention.journal_days','365'),
  ('traps.udp_port','162'),
  ('session.timeout_minutes','720'),
  ('auth.lock_attempts','5'),
  ('auth.lock_minutes','15');

CREATE TABLE profile (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  name text NOT NULL UNIQUE CHECK (length(name) BETWEEN 1 AND 128),
  description text NOT NULL DEFAULT '',
  seed_key text UNIQUE,
  version bigint NOT NULL DEFAULT 0,
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE profile_check (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  profile_id bigint NOT NULL REFERENCES profile(id) ON DELETE CASCADE,
  code text NOT NULL CHECK (code ~ '^[a-z0-9_-]{1,64}$'),
  name text NOT NULL,
  kind text NOT NULL CHECK (kind IN ('PING','TCP','SNMP','SCPI')),
  params jsonb NOT NULL DEFAULT '{}',
  interval_sec int NOT NULL CHECK (interval_sec BETWEEN 5 AND 86400),
  timeout_ms int NOT NULL CHECK (timeout_ms BETWEEN 100 AND 120000),
  warn_threshold numeric,
  crit_threshold numeric,
  hysteresis numeric NOT NULL DEFAULT 0 CHECK (hysteresis >= 0),
  threshold_direction text NOT NULL DEFAULT 'ABOVE' CHECK (threshold_direction IN ('ABOVE','BELOW')),
  fail_count int NOT NULL DEFAULT 3 CHECK (fail_count >= 1),
  success_count int NOT NULL DEFAULT 2 CHECK (success_count >= 1),
  required boolean NOT NULL DEFAULT true,
  is_primary boolean NOT NULL DEFAULT false,
  enabled boolean NOT NULL DEFAULT true,
  unconfirmed boolean NOT NULL DEFAULT false,
  unconfirmed_note text,
  source text,
  UNIQUE (profile_id, code),
  CHECK (NOT is_primary OR (required AND enabled))
);
CREATE UNIQUE INDEX ux_profile_check_primary ON profile_check (profile_id) WHERE is_primary;

CREATE TABLE device_group (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  name text NOT NULL UNIQUE CHECK (length(name) BETWEEN 1 AND 128)
);

CREATE TABLE device_type (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  name text NOT NULL UNIQUE CHECK (length(name) BETWEEN 1 AND 128),
  default_profile_id bigint REFERENCES profile(id),
  icon text NOT NULL DEFAULT 'generic' CHECK (icon ~ '^[a-z0-9-]{1,32}$')
);

CREATE TABLE device (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  name text NOT NULL CHECK (length(name) BETWEEN 1 AND 255),
  type_id bigint NOT NULL REFERENCES device_type(id),
  group_id bigint REFERENCES device_group(id),
  ip inet NOT NULL CHECK (family(ip) = 4 AND masklen(ip) = 32),
  lat numeric(9,6) NOT NULL CHECK (lat BETWEEN -90 AND 90),
  lon numeric(9,6) NOT NULL CHECK (lon BETWEEN -180 AND 180),
  address text NOT NULL DEFAULT '',
  profile_id bigint REFERENCES profile(id),
  status text NOT NULL DEFAULT 'NO_DATA' CHECK (status IN ('CRITICAL','WARNING','NO_DATA','OK')),
  status_changed_at timestamptz NOT NULL DEFAULT now(),
  monitoring_enabled boolean NOT NULL DEFAULT true,
  note text NOT NULL DEFAULT '',
  version bigint NOT NULL DEFAULT 0,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  deleted_at timestamptz,
  CHECK (deleted_at IS NOT NULL OR profile_id IS NOT NULL)
);
CREATE UNIQUE INDEX ux_device_ip_active ON device (ip) WHERE deleted_at IS NULL;
CREATE INDEX ix_device_profile_active ON device (profile_id) WHERE deleted_at IS NULL;
CREATE INDEX ix_device_name_active ON device (lower(name)) WHERE deleted_at IS NULL;

CREATE TABLE access_secret (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  snmp_version text NOT NULL CHECK (snmp_version IN ('V2C','V3')),
  ciphertext bytea NOT NULL,
  key_version smallint NOT NULL DEFAULT 1,
  device_id bigint REFERENCES device(id),
  group_id bigint REFERENCES device_group(id),
  updated_at timestamptz NOT NULL DEFAULT now(),
  CHECK (num_nonnulls(device_id, group_id) = 1)
);
CREATE UNIQUE INDEX ux_secret_device ON access_secret (device_id) WHERE device_id IS NOT NULL;
CREATE UNIQUE INDEX ux_secret_group ON access_secret (group_id) WHERE group_id IS NOT NULL;

CREATE TABLE trap_rule (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  profile_id bigint NOT NULL REFERENCES profile(id) ON DELETE CASCADE,
  trap_oid text NOT NULL CHECK (trap_oid ~ '^[0-9]+(\.[0-9]+)+$'),
  action text NOT NULL CHECK (action IN ('EVENT','ALARM','CLOSE')),
  level text CHECK (level IN ('WARNING','CRITICAL')),
  pair_key text,
  correlation_oid text,
  description text NOT NULL DEFAULT '',
  unconfirmed boolean NOT NULL DEFAULT false,
  source text,
  UNIQUE (profile_id, trap_oid),
  CHECK (action <> 'ALARM' OR level IS NOT NULL),
  CHECK (action = 'EVENT' OR pair_key IS NOT NULL)
);

CREATE TABLE check_state (
  device_id bigint NOT NULL REFERENCES device(id),
  check_id bigint NOT NULL REFERENCES profile_check(id) ON DELETE CASCADE,
  last_ts timestamptz,
  last_success boolean,
  last_value_num numeric,
  last_value_text text,
  last_error text,
  consecutive_failures int NOT NULL DEFAULT 0,
  consecutive_successes int NOT NULL DEFAULT 0,
  health text NOT NULL DEFAULT 'UNKNOWN' CHECK (health IN ('UNKNOWN','OK','FAILED')),
  threshold_level text NOT NULL DEFAULT 'NONE' CHECK (threshold_level IN ('NONE','WARNING','CRITICAL')),
  threshold_ok_streak int NOT NULL DEFAULT 0,
  PRIMARY KEY (device_id, check_id)
);

-- Результаты: разделы по суткам UTC. Без внешних ключей ради скорости вставки.
CREATE TABLE check_result (
  ts timestamptz NOT NULL,
  device_id bigint NOT NULL,
  check_id bigint NOT NULL,
  success boolean NOT NULL,
  value_num numeric,
  value_text text,
  error_code text,
  duration_ms int NOT NULL
) PARTITION BY RANGE (ts);
CREATE INDEX ix_check_result_device_ts ON check_result (device_id, ts DESC);
CREATE TABLE check_result_default PARTITION OF check_result DEFAULT;

CREATE TABLE event (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  ts timestamptz NOT NULL,
  device_id bigint REFERENCES device(id),
  type text NOT NULL CHECK (type IN ('STATUS_CHANGE','TRAP','TRAP_UNKNOWN_SOURCE','USER_ACTION')),
  source_ip inet,
  description text NOT NULL,
  details jsonb
);
CREATE INDEX ix_event_ts ON event (ts DESC);
CREATE INDEX ix_event_device_ts ON event (device_id, ts DESC);
CREATE INDEX ix_event_type_ts ON event (type, ts DESC);

CREATE TABLE alarm (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  device_id bigint NOT NULL REFERENCES device(id),
  level text NOT NULL CHECK (level IN ('WARNING','CRITICAL')),
  reason_key text NOT NULL,
  reason_text text NOT NULL,
  source text NOT NULL CHECK (source IN ('CHECK','TRAP')),
  check_id bigint,
  trap_rule_id bigint,
  started_at timestamptz NOT NULL,
  ended_at timestamptz,
  close_reason text CHECK (close_reason IN ('RECOVERED','PAIR_TRAP','POLL_OK','DEVICE_DELETED','MONITORING_OFF','CHECK_REMOVED')),
  ack_by bigint REFERENCES app_user(id),
  ack_at timestamptz,
  ack_comment text,
  CHECK ((ended_at IS NULL) = (close_reason IS NULL)),
  CHECK ((ack_at IS NULL) = (ack_by IS NULL) AND (ack_at IS NULL) = (ack_comment IS NULL))
);
CREATE UNIQUE INDEX ux_alarm_active ON alarm (device_id, reason_key) WHERE ended_at IS NULL;
CREATE INDEX ix_alarm_active_started ON alarm (started_at DESC) WHERE ended_at IS NULL;
CREATE INDEX ix_alarm_archive ON alarm (ended_at DESC) WHERE ended_at IS NOT NULL;
CREATE INDEX ix_alarm_device_started ON alarm (device_id, started_at DESC);

CREATE TABLE audit_log (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  ts timestamptz NOT NULL,
  user_id bigint REFERENCES app_user(id),
  user_login text NOT NULL,
  action text NOT NULL,
  object_type text NOT NULL,
  object_id text,
  old_value jsonb,
  new_value jsonb
);
CREATE INDEX ix_audit_ts ON audit_log (ts DESC);
```

Начальные значения `system_setting` не равноправны по источнику: `retention.*` взяты из раздела 6 ТЗ, `traps.udp_port` из SNMP-08, а `session.timeout_minutes=720`, `auth.lock_attempts=5`, `auth.lock_minutes=15` в ТЗ не заданы (AUTH-07 и AUTH-08 требуют только настраиваемости) и являются допущениями A-2, A-3 (§15). Все шесть правятся администратором в настройках (`/api/settings`, T1.07, механизм OPS-08) без изменения миграции. Источники по ключам: §13.2.

### 4.3 Решения по схеме

- **Мягкое удаление.** Устройство получает `deleted_at`, а `profile_id` и `group_id` обнуляются: история к ним не привязана, поэтому удалённое устройство не мешает удалить профиль или группу (PRF-06 проверяет только действующие). Строка не удаляется, поэтому `event`, `alarm` и `check_result` сохраняют ссылку. Частичный индекс `ux_device_ip_active` даёт уникальность IP только среди действующих. Дубль определяется этим индексом, а не предварительным SELECT: так гонка двух одновременных созданий исключена.
- **IP.** Только IPv4 без маски (допущение A-1, §15).
- **Разделы `check_result`.** Сутки UTC, имя `check_result_yYYYYMMDD`, границы `[00:00Z, следующие 00:00Z)`. Обслуживание (T3.05): при старте и раз в час создавать разделы на сегодня и 7 суток вперёд; удалять целиком разделы, у которых верхняя граница не позже `начало_суток_UTC(now) - retention.results_days`.
- **Раздел по умолчанию** страхует от потери записи, если обслуживание не сработало. Строки в нём считаются сбоем (POLL-09). Перед созданием раздела строки попавшего диапазона переносятся из него в одной транзакции (T3.06). Поэтому `DETACH ... CONCURRENTLY` недоступен (PostgreSQL запрещает его при наличии раздела по умолчанию); применяется `DROP TABLE` с `lock_timeout = 2s` и повтором, очередь результатов (§7.3) переживает паузу.
- **Журнал и архив тревог, журнал действий** удаляются пакетами по 10 000 строк по `retention.journal_days`, разделов нет: объём на порядки меньше результатов. Каждый модуль удаляет только свою таблицу (§2.2): `event` модуль `journal` (T3.16), `audit_log` модуль `audit` (T3.23), архив `alarm` модуль `alarm` (T6.12). Причина: правило «модуль пишет только свои таблицы» действует и для удаления; одна настройка срока у всех трёх (A-7).
- **Группа** не удаляется, пока на неё ссылается устройство или секрет (409). **Тип** не удаляется, пока на него ссылается любое устройство, в том числе удалённое (409): история показывает тип. **Профиль** не удаляется, пока на него ссылается действующее устройство или тип (409 `PROFILE_IN_USE`).
- **Удаление проверки из профиля** удаляет её `check_state` каскадно, активные тревоги закрываются с `CHECK_REMOVED` (T6.17), история в `check_result` остаётся без ссылки. При правке профиля проверка сохраняет `id`, если совпал `code`.

## 5. REST API

### 5.1 Общие правила

- JSON в UTF-8, время `Instant` ISO-8601 в UTC. Показ по Москве только на клиенте.
- Роли: `OPERATOR` (дежурный), `ADMIN` (администратор).
- Публичны только: `GET /login.html`, `GET /login/**`, `GET /api/auth/csrf`, `POST /api/auth/login`. Всё остальное, включая статику, требует сессии. Без сессии: `/api/**` отвечает 401 JSON, страницы перенаправляют на `/login.html`.
- Сессия: cookie `JSESSIONID`, HttpOnly, SameSite=Lax, в памяти сервера. После перезапуска нужен новый вход.
- CSRF: `CookieCsrfTokenRepository.withHttpOnlyFalse()` и `csrf.spa()` [метод `spa()` есть в 7.1.2, ПОДТВ]. Изменяющие запросы несут заголовок `X-XSRF-TOKEN`. Тот же режим для входа: клиент сначала вызывает `/api/auth/csrf`.
- Права ставятся `@PreAuthorize` на методах контроллеров, `SecurityConfig` при этом не правится. Выражения: `О` = `hasAnyRole('OPERATOR','ADMIN')`, `А` = `hasRole('ADMIN')`, публичный = `permitAll()`.
- Правка с `version`: несовпадение даёт 409 `VERSION_CONFLICT`.

**Безопасность (T1.05; проверено прототипом на Spring Boot 4.1.2 / Security 7.1.2, MockMvc и Tomcat):**
- `ru.netmon.auth.SecurityConfig`: `@Configuration(proxyBeanMethods = false)`, `@EnableMethodSecurity`; бины `PasswordEncoder` = `new BCryptPasswordEncoder(10)` (хэш `$2a$10$`) и `SecurityFilterChain`. `DaoAuthenticationProvider` бином не объявляется: Spring Security строит его сам из единственных бинов `UserDetailsService` (`AppUserDetailsService`) и `PasswordEncoder`. Исключение `UserDetailsServiceAutoConfiguration` в `NetMonitoringApplication` остаётся.
- Цепочка: `authorizeHttpRequests`: `dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()`, затем публичный список выше через `PathPatternRequestMatcher.withDefaults().matcher(метод, путь)` с `permitAll()`, затем `anyRequest().authenticated()`. `csrf(c -> c.spa())`. `formLogin`: `loginPage("/login.html")` (без него Spring отдаёт свою страницу входа на `GET /login`), `loginProcessingUrl("/api/auth/login")`, параметры `username`, `password` по умолчанию, `successHandler` и `failureHandler` = `JsonAuthHandlers`. `logout`: `logoutUrl("/api/auth/logout")`, `HttpStatusReturningLogoutSuccessHandler(NO_CONTENT)`, `deleteCookies("JSESSIONID")`. `requestCache(c -> c.disable())`: анонимный запрос не создаёт сессию. `exceptionHandling`: `defaultAuthenticationEntryPointFor(JsonAuthHandlers, matcher("/api/**"))`, `defaultAuthenticationEntryPointFor(new LoginUrlAuthenticationEntryPoint("/login.html"), matcher("/**"))`, `accessDeniedHandler(JsonAuthHandlers)` (его же берёт `CsrfFilter`). Фиксация сессии: умолчание `changeSessionId`, отдельно не настраивается. Прямой запрос `/error` (не `ERROR`-диспетчеризация) не публичен: без сессии 302 на вход.
- `JsonAuthHandlers` (`@Component`, реализует `AuthenticationEntryPoint`, `AccessDeniedHandler`, `AuthenticationSuccessHandler`, `AuthenticationFailureHandler`) пишет тело байтами внедрённого `tools.jackson.databind.json.JsonMapper` в `getOutputStream()` с `setContentType("application/json")`; `getWriter()` не используется (кодировка ISO-8859-1 по умолчанию испортила бы русский текст). Ответы, `fields` всегда `[]`:

| Случай | HTTP | `code` | `message` |
|---|---|---|---|
| Нет сессии, путь `/api/**` (в том числе отказ `@PreAuthorize` анонимному) | 401 | `AUTH_REQUIRED` | «Требуется вход в систему» |
| Нет сессии, иной путь | 302 на `/login.html` | | |
| Неверный вход: неверный пароль, неизвестный логин, отключённый, пустой или длинный логин (любое `AuthenticationException`; `AUTH_LOCKED` добавляет T1.09) | 401 | `AUTH_FAILED` | «Неверный логин или пароль» |
| Нет прав у вошедшего | 403 | `FORBIDDEN` | «Недостаточно прав» |
| CSRF: нет или неверен `X-XSRF-TOKEN` (`CsrfException`), в том числе у анонимного: `CsrfFilter` стоит до проверки сессии | 403 | `CSRF_INVALID` | «Недействительный токен защиты, обновите страницу» |
| Вход успешен | 200 | тело `{"login":…,"role":…}`, `login` как записан в БД | |
| Выход, в том числе без сессии (фильтр выхода стоит до проверки прав, завершать нечего) | 204 | | |

- CSRF для клиента без внешних ресурсов: `GET /api/auth/csrf` (`AuthController`, `permitAll()`, параметр `CsrfToken`, вызов `getToken()`) даёт 204 и cookie `XSRF-TOKEN` (не HttpOnly, `Path=/`); `csrf.spa()` ставит эту cookie и на любом ответе запросу без неё. Заголовок `X-XSRF-TOKEN` несёт значение cookie как есть. Вход удаляет прежний токен; `JsonAuthHandlers` при успехе вызывает `getToken()` у `CsrfToken` из атрибута запроса `CsrfToken.class.getName()`, поэтому ответ 200 несёт два `Set-Cookie: XSRF-TOKEN`: удаление и новое значение (браузер применяет по порядку; тест берёт последнее). Выход удаляет cookie, перед новым входом клиент снова вызывает `/api/auth/csrf`.
- Cookie сессии: в `application.yml` `server.servlet.session.cookie.http-only: true`, `server.servlet.session.cookie.same-site: lax`, `server.servlet.session.tracking-modes: cookie` (без `;jsessionid=` в адресе). Срок сессии ставит вход (T1.08, ниже «Сессии»); `server.servlet.session.timeout` не задаётся (умолчание Boot 30 мин действует только внутри запроса входа, до `SessionTimeoutApplier`).
- Пользователь сессии `ru.netmon.common.security.CurrentUser` (`public final class`, `implements UserDetails, CredentialsContainer`): конструктор `(long id, String login, Role role, boolean active, String passwordHash)`, методы `id()`, `login()`, `role()`, `actor()` = `new Actor(id, login)`; `getAuthorities()` = `ROLE_` + роль, `isEnabled()` = `active`, `eraseCredentials()` обнуляет хэш (`ProviderManager` вызывает его после входа, в сессии хэша нет), `toString()` без хэша. `equals`/`hashCode` не переопределяются (сравнение по ссылке): код, которому нужен пользователь как ключ, берёт `id()` (решение ревью T1.05; так `UserSessionRegistry` T1.08 не зависит от того, что principal в разных сессиях разные объекты). Principal есть снимок строки `app_user` на момент входа: роль и `active` в действующей сессии не перечитываются, поэтому смена роли, отключение и смена или сброс пароля завершают все сессии пользователя через `UserSessionRegistry.terminateAll(userId)` (T1.11, T1.12). Лежит в `common`, потому что его читают контроллеры всех модулей (`@AuthenticationPrincipal CurrentUser user`), а зависимость от `auth` запрещена §2.2. `AppUserRepository.findByLogin`: `where lower(login) = lower(?)` (индекс `ux_app_user_login`).

**Сессии (T1.08; проверено прототипом на Spring Boot 4.1.2 / Security 7.1.2 / Tomcat 11.0.27, `MockMvc` и Tomcat; полный `mvn -pl server -am test` зелёный в порядках `alphabetical` и `reversealphabetical`):**
- Вход. `JsonAuthHandlers.onAuthenticationSuccess` первым делом берёт `request.getSession(false)` (сессию уже создало сохранение контекста безопасности, идентификатор уже сменён; `null`: `IllegalStateException` «Нет сессии после входа»), затем `SessionTimeoutApplier.apply(session)` и `UserSessionRegistry.register(user.id(), session)`. Исключение любого из двух вызовов: `session.invalidate()` и проброс того же исключения (вход без срока и без записи в реестре не остаётся; на Tomcat ответ 500 через `/error`, в `MockMvc` `perform` бросает это исключение). Дальше без изменений: токен CSRF, тело 200.
- `ru.netmon.auth.SessionTimeoutApplier` (`@Component`, пакетный, конструктор `(Settings settings, Environment env)`), `void apply(HttpSession s)`: `s.setMaxInactiveInterval(settings.getInt("session.timeout_minutes") * 60)` при каждом вызове, без кэша (§7.2 «Настройки»). Срок записывается в саму сессию, поэтому открытые сессии сохраняют свой (§13.2). Неверное хранимое значение: `IllegalStateException` из `getInt`, вход не состоится. Тестовое переопределение `nm.session.timeout-seconds-override` (§13.1) читает конструктор: `env.getProperty(OVERRIDE, Integer.class)`; задано при `!env.matchesProfiles("test")`: `IllegalStateException` «nm.session.timeout-seconds-override допустимо только в профиле test» (запуск прерывается, а не игнорирует молча); задано вне 1..3600: `IllegalStateException` «nm.session.timeout-seconds-override: целое число от 1 до 3600»; задано верно: `apply` ставит это число секунд, настройка не читается. Константа `static final String OVERRIDE`. Причина: нижняя граница настройки 1 минута, а тест истечения на Tomcat ждёт секунды. Свойство передаётся только аргументом `RestartableApp`, в `application*.yml` его нет.
- Истечение делает Tomcat по системному времени: при обращении с просроченной сессией (ленивое истечение) и фоновой проверкой раз в `server.tomcat.background-processor-delay` × `processExpiresFrequency` (умолчание 10 с × 6 = 60 с). Запрос с истекшей или завершённой сессией идёт как анонимный: `/api/**` 401 `AUTH_REQUIRED` (тело по таблице выше), страницы 302 на вход. Своей стратегии недействительной сессии (`invalidSessionUrl`, `InvalidSessionStrategy`) нет. `MutableClock` на истечение не влияет, а `MockHttpSession` по времени не истекает: `MockMvc` проверяет `getMaxInactiveInterval()`, реестр и `invalidate`, истечение проверяется только на Tomcat.
- `ru.netmon.auth.UserSessionRegistry` (`@Component`, `public final class`; публичен для тестов других пакетов, `ArchitectureTest` всё равно запрещает зависимость модулей от `auth`): поле `ConcurrentHashMap<Long, Set<Binding>>` (ключ `CurrentUser.id()`, множество `ConcurrentHashMap.newKeySet()`). `Binding` (вложенный `private final class`, `implements HttpSessionBindingListener`, поля `long userId`, `HttpSession session`, `equals`/`hashCode` не переопределены) лежит в сессии атрибутом `static final String ATTRIBUTE` = `UserSessionRegistry.class.getName() + ".BINDING"`. `valueBound` добавляет `this` в множество пользователя (`compute`), `valueUnbound` удаляет `this`, пустое множество удаляется (`computeIfPresent` возвращает `null`). Методы:
  - `void register(long userId, HttpSession session)` (пакетный): `session.setAttribute(ATTRIBUTE, new Binding(userId, session))`.
  - `public void terminateAll(long userId)` и `public void terminateOthers(long userId, HttpSession current)`; `current` сохраняется сравнением его `Binding` (`current.getAttribute(ATTRIBUTE)`) по ссылке. При активной синхронизации транзакции (`TransactionSynchronizationManager.isSynchronizationActive()`) действие регистрируется в `afterCommit` (откат сессии не трогает), иначе выполняется сразу. Действие: снимок `List.copyOf(множество)`, для каждого `session.invalidate()`; `IllegalStateException` (сессия уже истекла) снимает этот `Binding` и не прерывает обход.
  - `public int sessionCount(long userId)`; пакетный `int userCount()` (число ключей), оба для тестов.
  - Причина `HttpSessionBindingListener` вместо `HttpSessionListener`: Tomcat вызывает `valueUnbound` при истечении (ленивом и фоновом), `invalidate`, выходе и остановке контекста, `MockHttpSession.invalidate()` тоже, а контейнерные слушатели в `MockMvc` не вызываются; повторный вход другим пользователем в той же сессии заменяет атрибут, и прежняя запись снимается сама. Tomcat при замене атрибута вызывает `valueBound` нового до `valueUnbound` старого, `MockHttpSession` наоборот; запись по объекту `Binding` верна при любом порядке и не зависит от того, тот ли объект фасада `HttpSession` отдаёт Tomcat. `SecurityConfig` не меняется. `SessionRegistryImpl`, `HttpSessionEventPublisher`, `maximumSessions` не применяются: их ключ principal по `equals` (у `CurrentUser` сравнение по ссылке), а `expireNow()` ленив и отвечает своей стратегией, не 401 §5.1.
  - Поведение (проверено прототипом на Tomcat): `invalidate()` из чужого потока (поток теста, поток запроса администратора) безопасен. Запрос этой сессии, уже прошедший фильтры, завершается штатно: 200, `getSession(false)` внутри даёт `null`, новой сессии и `Set-Cookie: JSESSIONID` нет; следующий запрос 401. Держатель ссылки на завершённую сессию получает `IllegalStateException` («getLastAccessedTime: Session already invalidated») из `getLastAccessedTime()` (так поток SSE T1.24 узнаёт о завершении, §6). Остановка `RestartableApp` истекает все сессии, реестр пуст.
- Вызывающие: T1.11 (смена роли, отключение, сброс пароля) `terminateAll(id)`; T1.12 (смена своего пароля) `terminateOthers(id, request.getSession(false))`. Вызов внутри транзакции изменения допустим: сессии завершаются после фиксации.

**Ошибка:**

```json
{"code":"DEVICE_IP_DUPLICATE","message":"Устройство с таким IP уже существует","fields":[{"field":"ip","message":"..."}]}
```

Статусы: 400 проверка данных, 401 нет сессии или неверный вход, 403 нет прав или CSRF, 404 нет объекта (и удалённое устройство), 409 конфликт, 413 слишком большой файл, 500 без подробностей (стек только в логе). Сообщения на русском, `code` стабильны.

**Страница:** запрос `?page=0&size=50&sort=name,asc`; `size` от 1 до 500 (иначе 400); `sort` только из перечня ресурса (иначе 400). Ответ `{"items":[],"page":0,"size":50,"total":123}`.

**Реализация в `ru.netmon.common.web` (T1.04; проверено прототипом на текущем `server/pom.xml`):**
- `record FieldError(String field, String message)`. `field`: путь поля (`name`, `checks[1].params.port`) или имя параметра запроса; `null` для ошибки уровня объекта.
- `class ApiException extends RuntimeException`: конструкторы `(HttpStatus status, String code, String message)`, `(…, List<FieldError> fields)`, `(…, List<FieldError> fields, Map<String,?> details)`; методы `status()`, `code()`, `fields()`, `details()`, `getMessage()` = `message`. Ключи `details` выводятся в тело на верхний уровень после `fields` (так `PROFILE_IN_USE` несёт `devices`, `types`); ключ `code`, `message` или `fields` в `details` даёт `IllegalArgumentException`. Фабрики: `notFound()` = 404 `NOT_FOUND` «Объект не найден»; `versionConflict()` = 409 `VERSION_CONFLICT` «Данные изменены другим пользователем, обновите страницу»; `validation(List<FieldError>)` = 400 `VALIDATION_FAILED` «Проверьте введённые данные».
- Тело ошибки всегда `{"code","message","fields"[,ключи details]}`, `fields` всегда массив (пустой `[]`, если полей нет), `Content-Type: application/json` (UTF-8, параметр `charset` Spring не пишет). В теле никогда нет текста исключения, имён классов, SQL, имён ограничений и отвергнутого значения.
- `ApiErrorHandler` (`@RestControllerAdvice`), сопоставление (точный тип выигрывает у общего, правило Spring):

| Исключение | HTTP | `code` | `message` | `fields` | Лог |
|---|---|---|---|---|---|
| `ApiException` | его | его | его | его, плюс `details` | нет |
| `MethodArgumentNotValidException` | 400 | `VALIDATION_FAILED` | «Проверьте введённые данные» | ошибки полей: `getField()`, `getDefaultMessage()`; ошибки объекта: `field=null` | нет |
| `HandlerMethodValidationException` (в том числе тело `@Valid @RequestBody`, если у любого параметра метода есть ограничение) | 400 | `VALIDATION_FAILED` | то же | по каждому `ParameterValidationResult`: если это `ParameterErrors` (тело, объект), то как у `MethodArgumentNotValidException`: `getFieldErrors()` с `field=getField()`, `getGlobalErrors()` с `field=null`; иначе для каждого `getResolvableErrors()` `field` = имя параметра (`getMethodParameter().getParameterName()`), для элемента контейнера плюс `[getContainerIndex()]` или `[getContainerKey()]`; сообщение `getDefaultMessage()`. `ParameterErrors` с `getContainerIndex()`/`getContainerKey()` (тело-коллекция) не дополняется префиксом: такое тело запрещено §2.2 п. 5. Кросс-параметрические ограничения (`getCrossParameterValidationResults()`, с T1.05): каждое даёт `field=null`, сообщение `getDefaultMessage()`, после ошибок параметров. Ограничение на методе контроллера Spring 7.0.10 само по себе не применяет (проверено прототипом T1.05: без ограничения на параметре проверка не запускается, а `HandlerMethodValidator` смотрит только `getParameterValidationResults()`), поэтому в прикладном коде оно запрещено; совместная проверка параметров пишется кодом через `ApiException.validation(...)` | нет |
| `HttpMessageNotReadableException` (неверный JSON, нет тела, неверный тип или enum в теле) | 400 | `BAD_JSON` | «Тело запроса отсутствует или не является корректным JSON» | `[]` | нет |
| `MethodArgumentTypeMismatchException` | 400 | `BAD_PARAMETER` | «Неверное значение параметра» | `[{field: имя параметра, message: "Неверное значение"}]` | нет |
| `DataIntegrityViolationException` и подклассы (непредусмотренное ресурсом нарушение ограничения) | 409 | `DATA_CONFLICT` | «Данные противоречат существующим записям» | `[]` | `WARN` без стека и без текста исключения: «Нарушение ограничения БД», метод, `getRequestURI()` |
| `AccessDeniedException`, `AuthenticationException` (Spring Security) само или в цепочке `getCause()` (не глубже 20 звеньев, без зацикливания) | не обрабатываются: обработчик `Exception` пробрасывает исходное исключение (`throw e`), ответ даёт фильтр безопасности (его `ThrowableAnalyzer` сам находит причину в цепочке) | | | | |
| `org.springframework.web.context.request.async.AsyncRequestNotUsableException` (клиент ушёл, в том числе из SSE) само или, с T1.24, в цепочке `getCause()` (тот же обход, что для исключений безопасности; ревью T1.04 Н-г) | ответа нет (`return null`) | | | | `DEBUG` |
| любое `Exception` при `response.isCommitted()` (ответ уже начат, например `text/event-stream` или выгрузка `.xlsx`) | JSON не пишется. С T1.24 (ревью T1.04 Н-б, Н-в): обрыв клиента, то есть `AsyncRequestTimeoutException` само или в цепочке (тот же обход) `org.apache.catalina.connector.ClientAbortException` или `AsyncRequestNotUsableException`: `return null`. Иное (ошибка сервера, в том числе любое другое `IOException`): `throw e` (проброс исходного исключения), чтобы Tomcat оборвал соединение без завершающего блока и клиент не принял обрезанный файл за успешную загрузку. До T1.24 (код T1.04): `return null` всегда | | | | обрыв клиента: `DEBUG`; иное: `ERROR` со стеком как в последней строке, затем проброс. До T1.24: `DEBUG`, если `AsyncRequestTimeoutException` или в цепочке есть любое `IOException`, иначе `ERROR` |
| `AsyncRequestTimeoutException` при неначатом ответе | 503 | `SERVICE_UNAVAILABLE` | «Сервис временно недоступен» | `[]` | `DEBUG` |
| прочее `org.springframework.web.ErrorResponse` со статусом 4xx (нет пути 404, метод 405, тип тела 415, нет параметра 400 и т. п.) | его | по постоянной таблице кода (не `HttpStatus.name()`, имена меняются между версиями Spring): 400 `BAD_REQUEST`, 401 `UNAUTHORIZED`, 403 `FORBIDDEN`, 404 `NOT_FOUND`, 405 `METHOD_NOT_ALLOWED`, 406 `NOT_ACCEPTABLE`, 413 `PAYLOAD_TOO_LARGE`, 415 `UNSUPPORTED_MEDIA_TYPE`, иное `HTTP_` + число (`HTTP_422`) | «Запрос не может быть выполнен» | `[]` | нет. Заголовки (с T1.05, ревью T1.04 Н-д): все из `ErrorResponse.getHeaders()` копируются в ответ, кроме `Content-Type` и `Content-Length`; так 405 несёт `Allow` (RFC 9110 §15.5.6), 415 несёт `Accept` |
| любое другое `Exception`, в том числе `ErrorResponse` 5xx и `ServletException`, которым `DispatcherServlet` оборачивает `Error` из контроллера | 500 | `INTERNAL_ERROR` | «Внутренняя ошибка сервера» | `[]` | `ERROR` со стеком: метод и `getRequestURI()` без строки запроса |

  Порядок проверок в методе `@ExceptionHandler(Exception.class)` (`HttpServletRequest`, `HttpServletResponse` параметрами): исключение безопасности, `AsyncRequestNotUsableException`, `isCommitted()`, `AsyncRequestTimeoutException`, `ErrorResponse` 4xx, 500. Причина проброса исключений безопасности: иначе общий обработчик превратил бы отказ `@PreAuthorize` (с T1.05) в 500. Причина правил для начатого ответа: поток SSE (T1.24) и выгрузки (задача 7) рвутся клиентом штатно, а JSON в начатый `text/event-stream` не пишется; ошибка сервера при начатом ответе пробрасывается, потому что `return null` даёт штатное завершение ответа и обрезанный `.xlsx` выглядит скачанным. Поведение Tomcat при пробросе (закрытие соединения без завершающего блока chunked, `include` страницы `/error` в начатый ответ) [НЕ ПОДТВЕРЖДЕНО, проверяет тест T1.24 `CommittedResponseTest`]; поэтому `ApiErrorController` при `response.isCommitted()` ничего не пишет в тело и не пишет в лог (ошибку уже записал `ApiErrorHandler`). Повторная запись `ERROR` самим Tomcat (`StandardWrapperValve`) допустима. Ошибки вне `DispatcherServlet` (исключение фильтра, `sendError` фильтра безопасности) обрабатывает не этот класс, а `/error` (T1.05, `ApiErrorController` в `ru.netmon.common.web`: `@RestController`, `implements org.springframework.boot.webmvc.error.ErrorController` [ПОДТВ по `spring-boot-webmvc-4.1.2.jar`; при этом бине `BasicErrorController` отключается], один метод `@RequestMapping("/error")` без указания метода HTTP и без `@PreAuthorize` (§2.2 п. 3), код 4xx через существующую пакетную `ApiErrorHandler.codeFor(int)`; исключение фильтра, в том числе анонимного запроса `POST`, доходит до него через `ERROR`-диспетчеризацию, проверено прототипом на Tomcat): тело по §5.1, статус из атрибута `jakarta.servlet.error.status_code` (нет атрибута: 500); 4xx с кодом по той же постоянной таблице (одна статическая функция кода на оба класса) и «Запрос не может быть выполнен»; 5xx как 500 `INTERNAL_ERROR`, при атрибуте `jakarta.servlet.error.exception` `ERROR` со стеком, метод и `jakarta.servlet.error.request_uri`. Текст исключения и `jakarta.servlet.error.message` в тело не попадают. Сообщения полей берутся из атрибута `message` ограничения: каждое ограничение Bean Validation в запросах API задаёт `message` на русском явно (сообщения Hibernate Validator по умолчанию зависят от локали JVM).
- `public final class PageQuery` (не `record`: канонический конструктор `record` публичен и позволил бы обойти белый список; конструктор `private`, экземпляр создаёт только `parse`): методы `int page()`, `int size()`, `String sortColumn()`, `boolean descending()`; `static PageQuery parse(String page, String size, String sort, Map<String,String> allowed, String defaultSort)`, `long offset()` = `(long) page * size`, `String orderBy(String tieBreaker)` = `sortColumn + " asc"|" desc"` и, если `tieBreaker` не равен `sortColumn`, `", " + tieBreaker` с тем же направлением (`name asc, id asc`; `created_at desc, id desc`). `tieBreaker`: выражение SQL уникального столбца (`id`, `d.id`), константа кода; `orderBy()` без аргумента нет, поэтому ресурс не может забыть устойчивость порядка. Контроллер принимает `page`, `size`, `sort` как `@RequestParam(required = false) String` и передаёт как есть. `allowed`: имя в API → выражение SQL, константа кода (белый список §14 п. 3). Правила: `null` даёт умолчание (`page` 0, `size` 50, `sort` = `defaultSort`); `page` и `size` только десятичные цифры (`[0-9]{1,10}`, без знака и пробелов, пустая строка неверна), `page` 0..`Integer.MAX_VALUE`, `size` 1..500; `sort` = `поле` или `поле,asc|desc` (направление строчными, без направления `asc`), поле точно совпадает с ключом `allowed`; иное, в том числе больше двух частей (повтор `sort=a&sort=b` Spring склеивает в `a,b`), неверно. Все нарушения собираются вместе и дают `ApiException` 400 `VALIDATION_FAILED` «Проверьте параметры запроса» с полями `page` «Номер страницы: целое число от 0», `size` «Размер страницы: целое число от 1 до 500», `sort` «Недопустимая сортировка». `defaultSort` разбирается по тем же правилам при каждом вызове `parse`, в том числе когда `sort` задан; неверный `defaultSort` даёт `IllegalArgumentException` (ошибка кода). `allowed` и `defaultSort` не `null` (константы кода): `null` есть ошибка программиста, тип исключения не оговаривается и не тестируется. Страница за пределами данных не ошибка: 200, `items` пустой, `page` как запрошено, `total` настоящий. Устойчивость порядка даёт `orderBy(tieBreaker)`; ресурс подставляет его результат в `ORDER BY` как есть.
- `record PageResponse<T>(List<T> items, int page, int size, long total)` со `static <T> PageResponse<T> of(List<T> items, PageQuery q, long total)`; поля JSON в этом порядке.
- `count` и выборка страницы идут отдельными запросами без общей транзакции (решение ревью T1.06). Причина: расхождение `total` и `items` при одновременной записи безвредно и исправляется следующим запросом, а снимок `REPEATABLE READ` на каждую страницу не нужен ни одному экрану.
- `public final class PeriodParams` (T1.07; ревью T1.06: `from=+100000-01-01T00:00:00Z` или длинное число эпохи давали 500, потому что `timestamptz` и `OffsetDateTime` не вмещают такие даты): константы `Instant MIN` = `0001-01-01T00:00:00Z`, `Instant MAX_EXCLUSIVE` = `10000-01-01T00:00:00Z`; `static void check(Instant from, Instant to)`: каждый не `null` параметр вне `[MIN, MAX_EXCLUSIVE)` даёт ошибку поля `from` или `to` (в этом порядке) с сообщением «Дата вне диапазона 0001-01-01 .. 9999-12-31», все вместе одним `ApiException.validation(...)`; если ошибок диапазона нет и оба заданы, `from.isAfter(to)` даёт `ApiException.validation(List.of(new FieldError(null, "Начало периода позже конца")))`. Каждый эндпоинт с периодом `from`/`to` (`/api/audit`, `/api/events` T3.15, `/api/alarms/archive` T6) вызывает `check` сразу после `PageQuery.parse`. Проверено прототипом: `+10000-…`, `+100000-…`, `-000001-…`, `0000-12-31T23:59:59.999999Z`, `99999999999999999` (миллисекунды) дают 400, `0001-01-01T00:00:00Z` и `9999-12-31T23:59:59.999999999Z` дают 200. В `MockMvc` дата со знаком `+` передаётся переменной шаблона (`get("/api/audit?from={f}", "+10000-01-01T00:00:00Z")`): `%2B` в строке адреса шаблон кодирует повторно, и до контроллера доходит `%2B`.

### 5.2 Эндпоинты

`О` = OPERATOR и ADMIN; `А` = только ADMIN; `—` без входа. Колонка «Задача» указывает, кто реализует.

| Метод и путь | Роль | Назначение | Задача |
|---|---|---|---|
| `GET /api/auth/csrf` | — | Выставляет cookie `XSRF-TOKEN`, ответ 204 | 1 |
| `POST /api/auth/login` | — | form-urlencoded `username`, `password`; 200 `{login,role}`; 401 `AUTH_FAILED` или `AUTH_LOCKED` | 1 |
| `POST /api/auth/logout` | О | 204 | 1 |
| `GET /api/me` | О | `{id,login,role}` | 1 |
| `POST /api/me/password` | О | `{oldPassword,newPassword}`; 204; прочие сессии пользователя завершаются | 1 |
| `GET /api/users` | А | Страница пользователей, без хэша | 1 |
| `POST /api/users` | А | `{login,password,role}` | 1 |
| `PATCH /api/users/{id}` | А | `{role?,active?}`; нельзя лишить системы последнего действующего администратора: 409 `LAST_ADMIN`; смена роли или отключение завершают все сессии пользователя (§5.1, `CurrentUser` есть снимок) | 1 |
| `POST /api/users/{id}/password` | А | Сброс `{newPassword}`, сессии пользователя завершаются | 1 |
| `GET /api/devices` | О | Страница; фильтр §5.3; `sort`: `name,ip,status,type,group,createdAt` | 1 |
| `GET /api/devices/map` | О | `{seq,items:[{id,name,ip,lat,lon,status,typeId,typeName,icon,groupId,monitoringEnabled}]}` без постраничности | 1 |
| `GET /api/devices/{id}` | О | Устройство (§5.4) | 1 |
| `POST /api/devices` | А | Создание; профиль по умолчанию берётся из типа, если не указан | 1 |
| `PUT /api/devices/{id}` | А | Правка с `version` | 1 |
| `DELETE /api/devices/{id}` | А | Мягкое удаление | 1 |
| `PATCH /api/devices/{id}/monitoring` | А | `{enabled}` | 3 |
| `PATCH /api/devices/{id}/location` | А | `{lat,lon,version}` | 1 |
| `GET /api/devices/{id}/checks` | О | Текущие результаты: `[{checkId,code,name,kind,required,primary,unconfirmed,lastTs,lastSuccess,lastValueNum,lastValueText,lastError,health,thresholdLevel}]` | 3 |
| `GET /api/device-types`, `GET /api/groups` | О | Списки | 1 |
| `POST`, `PUT /{id}`, `DELETE /{id}` на `/api/device-types`, `/api/groups` | А | Правка | 1 |
| `GET /api/profiles`, `GET /api/profiles/{id}` | О | §9.1 | 1 |
| `POST /api/profiles`, `PUT /api/profiles/{id}` | А | Правка с `version` | 1 |
| `DELETE /api/profiles/{id}` | А | 409 `PROFILE_IN_USE` с `devices:[{id,name}]` и `types:[{id,name}]` | 1 |
| `GET /api/profiles/{id}/trap-rules` | О | §9.3 | 4 |
| `POST /api/profiles/{id}/trap-rules`, `PUT`, `DELETE /api/trap-rules/{id}` | А | §9.3 | 4 |
| `GET /api/secrets` | А | Только метаданные: `{id,snmpVersion,deviceId,groupId,updatedAt}` | 1 |
| `POST /api/secrets`, `PUT /api/secrets/{id}`, `DELETE /api/secrets/{id}` | А | Запись §10.3; ответы без значения | 1 |
| `GET /api/audit` | А | Страница, фильтры `userId,objectType,from,to`; `sort` только `ts` (умолчание `ts,desc`); формат ниже «Журнал действий и журнал событий» | 1 |
| `GET /api/settings` | А | `[{key,value,min,max}]`, формат ниже «Настройки» | 1 |
| `PUT /api/settings/{key}` | А | `{value}`; 200 `{key,value,min,max}`; неизвестный ключ 404, неверное значение 400 | 1 |
| `GET /api/events` | О | Страница; фильтры `deviceId,type,from,to,q`; `sort` только `ts` | 3 |
| `GET /api/alarms/active` | О | Активные | 6 |
| `GET /api/alarms/active/summary` | О | `{total,critical,warning,unacknowledged,maxId}` | 6 |
| `GET /api/alarms/archive` | О | Страница; `deviceId,from,to,level` | 6 |
| `POST /api/alarms/{id}/ack` | О | `{comment}`, непустой; повтор 409 `ALARM_ALREADY_ACKED`; закрытая 409 `ALARM_CLOSED` | 6 |
| `GET /api/overview` | О | `{devices:{CRITICAL,WARNING,NO_DATA,OK},alarms:{...как summary},asOf}` | 6 |
| `GET /api/system/health` | О | `{version,components:[{name,state:UP|DEGRADED|DOWN,message,details}]}` | 3, 8 |
| `GET /api/export/devices.xlsx` | О | Фильтр §5.3; потоковый ответ | 7 |
| `GET /api/import/template.xlsx` | А | Шаблон | 7 |
| `POST /api/import/devices` | А | multipart `file`; `{imported,errors:[{row,field,message}]}` | 7 |
| `GET /api/stream` | О | SSE, §6 | 1 |
| `GET /tiles/{z}/{x}/{y}.png` | О | Тайл, 404 если нет | 2 |

Журнал действий (`GET /api/audit`) и все правки: дежурный получает 403.

**Пароли (с T1.10, ревью T1.05).** `BCryptPasswordEncoder.encode` бросает `IllegalArgumentException` на пароле длиннее 72 байт, `matches` с таким паролем даёт `false` (проверено прототипом T1.06 на Security 7.1.2; вход с длинным паролем 401 `AUTH_FAILED`, не 500). Поэтому `ru.netmon.auth.PasswordPolicy` (T1.10): `static Optional<String> violation(String password)`; `null` или меньше 8 символов (`codePointCount`): «Не менее 8 символов»; больше 72 байт `getBytes(UTF_8)`: «Не более 72 байт в UTF-8 (72 латинских или 36 русских букв)»; иначе пусто. Длина считается в байтах UTF-8, потому что предел у BCrypt байтовый. API (`POST /api/users` поле `password`, `POST /api/users/{id}/password` и `POST /api/me/password` поле `newPassword`) отвечает 400 `VALIDATION_FAILED` с этим полем и сообщением; первый администратор (T1.10) прерывает запуск.

**Журнал действий и журнал событий (T1.06; проверено прототипом на текущем `server/pom.xml`).** Элемент страницы `GET /api/audit`: `{id,ts,userId,userLogin,action,objectType,objectId,oldValue,newValue}` в этом порядке; `ts` строка ISO-8601 UTC (`"2026-03-01T00:00:10Z"`, Jackson 3 по умолчанию), `oldValue` и `newValue` вложенный JSON как записан (не строка), отсутствие значения `null`. Параметры: `page,size,sort` по `PageQuery` (`allowed` = `{"ts":"ts"}`, `defaultSort` `"ts,desc"`, `ORDER BY` из `orderBy("id")`); `userId` (`Long`), `objectType` (точное совпадение), `from`, `to` (`Instant`, ISO-8601 с `Z`). Период полуоткрытый: `ts >= from`, `ts < to`. Неверный тип `userId`, `from`, `to` (`abc`, `2026-03-01`): 400 `BAD_PARAMETER` с полем параметра (`MethodArgumentTypeMismatchException`, §5.1). Пустое значение параметра (`from=`) Spring передаёт как `null`, то есть без фильтра; `objectType=` фильтрует по пустой строке. Число из одних цифр Spring принимает в `Instant` как миллисекунды эпохи: это поведение Spring, не контракт, клиент шлёт ISO-8601. `from` позже `to`: 400 `VALIDATION_FAILED` с `fields` ровно `[{"field":null,"message":"Начало периода позже конца"}]`; дата вне диапазона: 400 `VALIDATION_FAILED` с полем `from` или `to` (с T1.07; обе проверки делает `PeriodParams.check` после `PageQuery.parse`, §5.1).

**Настройки (T1.07; проверено прототипом на текущем `server/pom.xml`).** Элемент `{key,value,min,max}` в этом порядке: `value` строка JSON как хранится (`"30"`), `min` и `max` числа из `SettingDefinitions` (§13.2), чтобы страница T1.32 показала границы. `GET` отдаёт ровно ключи `SettingDefinitions` в порядке `String.compareTo` ключа (сортировка в Java, а не `order by key`: порядок `text` в PostgreSQL зависит от правила сравнения базы, а в тестах и в поставке оно разное); строки `system_setting` без определения не показываются. Тело `PUT` `{"value":"45"}`; число JSON `{"value":45}` Jackson приводит к строке `"45"` и оно принимается, `true` становится `"true"` и даёт 400 `VALIDATION_FAILED`, объект или массив в `value`, тело-массив, нет тела: 400 `BAD_JSON`; лишние поля тела игнорируются. Ключ сравнивается точно: `{key}` захватывает весь сегмент с точками (`auth.lock_attempts.json` и `AUTH.LOCK_ATTEMPTS` дают 404, а не усечённый ключ); `GET /api/settings/{key}` нет (405). Порядок проверок: ключ (нет определения: 404 `NOT_FOUND` «Объект не найден», даже при неверном значении), затем значение. Значение верно, если совпадает с `[1-9][0-9]{0,9}` и число в `[min, max]`; иначе (в том числе `null`, нет поля, `""`, `" 5"`, `"05"`, `"+5"`, `"-1"`, `"1.0"`) 400 `VALIDATION_FAILED` «Проверьте введённые данные» с `fields` ровно `[{"field":"value","message":"Целое число от <min> до <max>"}]`. Хранится строка как пришла: шаблон уже даёт каноническую запись. Равное текущему значение: 200 без записи, без журнала и без события. Оптимистической блокировки (`version`) у настроек нет: значение одно, «записать X» не теряет чужих полей, а журнал хранит действительное старое значение, прочитанное под замком строки. Причина: столбца `version` в `system_setting` нет, а миграция ради него ничего не даёт пользователю.

### 5.3 Фильтр устройств `DeviceFilter`

Один и тот же для таблицы, карты (на клиенте) и выгрузки (XLS-02): `q` (подстрока без учёта регистра в названии, или IP, адресе), `typeId` (повторяемый), `groupId` (повторяемый), `status` (повторяемый), `monitoring` (`true|false`). Значения передаются только параметрами SQL. Удалённые устройства не возвращаются никогда.

### 5.4 Устройство

Запрос: `{name,typeId,groupId?,ip,lat,lon,address?,profileId?,note?,monitoringEnabled?,version?}`. Ответ добавляет `id,typeName,groupName,profileName,status,statusChangedAt,createdAt,updatedAt,version`. Секретов в ответе нет. Поля: `name` 1..255, `ip` только точечная запись IPv4 из четырёх десятичных чисел 0..255 без ведущих нулей, `lat` -90..90, `lon` -180..180, числа с точностью до 6 знаков.

## 6. Поток SSE `GET /api/stream`

Именованные события, `data` в JSON:

| Событие | data | Кто публикует |
|---|---|---|
| `hello` | `{seq}` | `stream` при подключении |
| `heartbeat` | `{seq}` каждые 15 с | `stream` |
| `device-status` | `{seq,deviceId,status,changedAt}` | `status` |
| `catalog-changed` | `{seq,deviceId,change:"CREATED|UPDATED|DELETED"}` | `device` |
| `alarm-changed` | `{seq,alarmId,deviceId,change:"RAISED|UPDATED|ACKED|CLOSED",level}` | `alarm` |

Правила:

- Повтора пропущенного нет. При каждом подключении клиент сначала подписывается, затем заново читает `/api/devices/map` и сводку; событие с `seq` не больше `seq` ответа отбрасывается. `seq` единый счётчик процесса, возрастает строго; в ответе `/api/devices/map` содержится текущее значение. После перезапуска сервера счётчик начинается заново: новый `hello` сбрасывает базу сравнения клиента.
- Клиент считает связь потерянной, если нет никаких данных 45 с или сработал `error` у `EventSource` (MAP-04), показывает баннер и маркеры как устаревшие.
- На каждую сессию не более 4 потоков, старейший закрывается: браузер держит не более 6 соединений HTTP/1.1 на сервер. Интерфейс устроен как одна страница с хэш-маршрутами, поэтому переходы поток не рвут.
- Поток не продлевает сессию, а истечение её закрывает: при каждом `heartbeat` сессия проверяется. Реконнект получит 401, клиент уходит на вход. Проверка (T1.24; `stream` не зависит от `auth`, §2.2) по объекту `HttpSession`, сохранённому при подключении: `getLastAccessedTime()` и `getMaxInactiveInterval()` в `try`; поток закрывается при `IllegalStateException` (сессия завершена выходом, `terminateAll` или истечением, §5.1 «Сессии») или при `clock.millis() - getLastAccessedTime() >= getMaxInactiveInterval() * 1000L` (внедрённый `Clock`). Второе условие обязательно: Tomcat снимает простаивающую сессию лениво или фоновой проверкой раз в 60 с. Совпадение этого условия со сроком Tomcat при открытом потоке (Tomcat считает простой от начала запроса потока, `getLastAccessedTime()` отдаёт конец предыдущего запроса) [НЕ ПОДТВЕРЖДЕНО, проверяет T1.24]: расхождение в сторону более раннего закрытия допустимо.
- Медленный клиент: очередь на подключение не более 1000 событий, переполнение закрывает поток, клиент переподключится и перечитает данные. Издатель не блокируется никогда.
- Публикация только после фиксации транзакции (`TransactionSynchronization.afterCommit`).

NFR-02: от фиксации транзакции до отправки не более 1 с, запись результатов не более 1 с (§7.3), итого запас до 5 с.

## 7. Внутренние контракты (`ru.netmon.contract`)

Сигнатуры обязательны. Все создаёт T1.03. Единственная заглушка: `stream/SeqStreamPublisher` (`@Component`, реализует `StreamPublisher`, считает `seq` через `AtomicLong`, не отправляет), T1.24 заменяет её настоящей реализацией.

### 7.1 Типы

```java
enum DeviceStatus { CRITICAL, WARNING, NO_DATA, OK }           // порядок = худший первым
enum Role { OPERATOR, ADMIN }
enum CheckKind { PING, TCP, SNMP, SCPI }
enum CheckHealth { UNKNOWN, OK, FAILED }
enum AlarmLevel { WARNING, CRITICAL }
enum ThresholdLevel { NONE, WARNING, CRITICAL }
enum ThresholdDirection { ABOVE, BELOW }
enum EventType { STATUS_CHANGE, TRAP, TRAP_UNKNOWN_SOURCE, USER_ACTION }
enum CheckError { TIMEOUT, REFUSED, UNREACHABLE, AUTH, NO_SUCH_OBJECT, EMPTY, PARSE, MISMATCH, INTERNAL }

record CheckDefinition(long id, long profileId, String code, String name, CheckKind kind,
    Map<String,Object> params, int intervalSec, int timeoutMs,
    BigDecimal warn, BigDecimal crit, BigDecimal hysteresis, ThresholdDirection direction,
    int failCount, int successCount, boolean required, boolean primary, boolean enabled, boolean unconfirmed) {}

record CheckTask(long deviceId, InetAddress ip, Long groupId, CheckDefinition check) {}

record CheckOutcome(boolean success, BigDecimal valueNum, String valueText, CheckError error, int durationMs) {}

record CheckResult(long deviceId, long checkId, Instant ts, CheckOutcome outcome) {}

record CheckTransition(long deviceId, CheckDefinition check, CheckHealth oldHealth, CheckHealth newHealth,
    ThresholdLevel oldLevel, ThresholdLevel newLevel, CheckOutcome outcome, Instant ts) {}

record DeviceStatusChange(long deviceId, DeviceStatus oldStatus, DeviceStatus newStatus, Instant ts, String cause) {}

record TrapRecord(InetAddress source, String trapOid, Map<String,String> varbinds, Instant ts, String snmpVersion) {}

record TrapMatch(long deviceId, long ruleId, String trapOid, TrapAction action, AlarmLevel level,
    String pairKey, String correlationValue, String description, Instant ts) {}
enum TrapAction { EVENT, ALARM, CLOSE }

sealed interface SnmpCredentials { 
  record V2c(String community) implements SnmpCredentials { /* toString: V2c[community=***] */ }
  record V3(String user, String authProtocol, String authPassword, String privProtocol, String privPassword)
      implements SnmpCredentials { /* toString: все пять полей ***, включая user */ }
}

// Поля по §5.4 (устройство), §5.3 (фильтр), §9.1 (профиль), столбцам check_state (CheckView), §8.2.
record DeviceView(long id, String name, long typeId, String typeName, Long groupId, String groupName,
    InetAddress ip, BigDecimal lat, BigDecimal lon, String address, Long profileId, String profileName, String note,
    boolean monitoringEnabled, DeviceStatus status, Instant statusChangedAt, Instant createdAt, Instant updatedAt,
    long version) {}

record DeviceInput(String name, Long typeId, Long groupId, String ip, BigDecimal lat, BigDecimal lon,
    String address, Long profileId, String note, Boolean monitoringEnabled, Long version) {}

record DeviceFilter(String q, Set<Long> typeIds, Set<Long> groupIds, Set<DeviceStatus> statuses, Boolean monitoring) {}

record ProfileView(long id, String name, String description, long version, List<CheckDefinition> checks) {}

record CheckView(long deviceId, long checkId, Instant lastTs, Boolean lastSuccess, BigDecimal lastValueNum,
    String lastValueText, CheckError lastError, int consecutiveFailures, int consecutiveSuccesses, CheckHealth health,
    ThresholdLevel thresholdLevel, int thresholdOkStreak, boolean required, boolean primary, boolean enabled,
    int intervalSec) {}

record StatusInput(boolean monitoringEnabled, List<CheckView> checks, boolean activeTrapAlarm, Instant now,
    Instant serverStart) {}

record Actor(long userId, String login) {}

record ComponentHealth(String name, State state, String message, Map<String,String> details) {
  public enum State { UP, DEGRADED, DOWN } }
```

Правила переноса (проверено компиляцией на Java 21 и прогоном `ArchitectureTest` в прототипе):
- Каждый тип верхнего уровня из §7.1 и §7.2 в своём файле `ru.netmon.contract.<Имя>.java` с модификатором `public`; импорты только `java.*`. Вложенными остаются только `SnmpCredentials.V2c`, `SnmpCredentials.V3`, `ComponentHealth.State` и `DeviceChanged.ChangeType`; другие группировки по классам-держателям не делаются, имена типов как в §7. Итого 46 файлов в `contract` и 5 в `contract.events`.
- Вложенные типы в записях объявляются `public` (`ComponentHealth.State`, `DeviceChanged.ChangeType`): член записи без модификатора доступен только пакету `contract`. В интерфейсах (`SnmpCredentials.V2c`, `V3`) модификатор не нужен.
- `toString` секретных типов маскирует каждое поле, а не только пароли: `V2c[community=***]`, `V3[user=***, authProtocol=***, authPassword=***, privProtocol=***, privPassword=***]`. Причина: имя USM-пользователя вместе с протоколами облегчает подбор, а диагностике `toString` не нужен; одно правило проще проверить.
- `DeviceInput.ip` строка: формат §5.4 проверяет `device`, разбор строки в `InetAddress` по имени хоста дал бы обращение к DNS. Обёртки (`Long`, `Boolean`) в `DeviceInput` означают «поле не передано», ошибку обязательного поля даёт проверка `device`.
- `DeviceFilter`: пустое множество или `null` значит «без ограничения». `ProfileView` без `unconfirmedNote` и `source`: они нужны только API профилей внутри `profile`.
- `CheckView.lastError` равен `null` при успехе. `Actor.userId` примитив: системного актора нет (§7.2).

### 7.2 Интерфейсы

| Интерфейс | Метод | Реализует | Вызывает |
|---|---|---|---|
| `CheckExecutor` | `CheckKind kind(); CheckOutcome execute(CheckTask task);` Обязан уложиться в `timeoutMs`, ошибки транспорта возвращает как `CheckOutcome`, не бросает исключения | `check.ping`, `check.tcp` (T3), `snmp` (T4), `check.scpi` (T5) | `poll`, через список бинов |
| `ResultSink` | `void submit(CheckResult r);` Не блокирует | `poll` | `poll` |
| `CheckStateMachine` | `CheckView apply(CheckView current, CheckDefinition check, CheckResult result, ThresholdLevel target);` Чистая функция, §8.1 и снижение уровня §8.3; `current` до первого результата: `health=UNKNOWN`, счётчики 0, `thresholdLevel=NONE`; `target` от `ThresholdEvaluator` | `status` | `status` |
| `ThresholdEvaluator` | `ThresholdLevel evaluate(CheckDefinition c, BigDecimal value, ThresholdLevel previous);` Чистая | `alarm` (T6) | `status`, через `ObjectProvider`; до T6 всегда `NONE` |
| `StatusCalculator` | `DeviceStatus calculate(StatusInput in);` Чистая, §8.2 | `status` | `status` |
| `CheckTransitionListener` | `void onTransition(CheckTransition t);` Внутри транзакции записи результатов | `alarm` | `status`, список бинов, до T6 пуст |
| `DeviceStatusListener` | `void onStatusChange(DeviceStatusChange c);` Внутри транзакции | `journal`, `alarm` при необходимости | `status` |
| `ActiveTrapConditions` | `boolean hasActiveTrapAlarm(long deviceId);` | `alarm` | `status`; нет бина значит `false` |
| `TrapActionSink` | `void onTrapMatch(TrapMatch m);` Внутри транзакции обработки трапа | `alarm` | `snmp`, список бинов |
| `RecheckRequester` | `void requestNow(long deviceId);` Без блокировки, повтор в пределах 30 с схлопывается | `poll` | `snmp` |
| `DeviceCatalog` | `Optional<DeviceView> find(long id); Optional<DeviceView> findActiveByIp(InetAddress ip); Stream<DeviceView> streamActive(DeviceFilter f); DeviceView create(DeviceInput i, Actor a);` Единые правила проверки для API и импорта | `device` | `excel`, `snmp`, `poll` |
| `ProfileCatalog` | `Optional<ProfileView> find(long id); List<CheckDefinition> checks(long profileId);` | `profile` | `poll`, `status` |
| `SecretResolver` | `Optional<SnmpCredentials> forDevice(long deviceId, Long groupId);` Сначала устройство, затем группа. Использует только `snmp` | `secret` | `snmp` |
| `EventJournal` | `void record(EventType t, Long deviceId, InetAddress sourceIp, String description, Map<String,Object> details, Instant ts);` Участвует в текущей транзакции (`MANDATORY`, правила ниже) | `journal` | все |
| `AuditLog` | `void record(Actor a, String action, String objectType, String objectId, Object oldValue, Object newValue);` Также пишет `USER_ACTION` в `EventJournal`. Участвует в текущей транзакции (`MANDATORY`, правила ниже) | `audit` | все, кто меняет данные |
| `Settings` | `int getInt(String key); String get(String key);` Без кэша: каждый вызов читает строку `system_setting` по первичному ключу (с T1.07, правила ниже «Настройки») | `settings` | все |
| `StreamPublisher` | `void publish(String event, Map<String,Object> data); long currentSeq();` Откладывает отправку до фиксации транзакции | `stream` | `status`, `device`, `alarm` |
| `StatusRecalculator` | `void recalculate(long deviceId);` Внутри текущей транзакции, берёт замок строки устройства `SELECT ... FOR NO KEY UPDATE` (не `FOR UPDATE`, правило ниже «Замок строки устройства») | `status` | `snmp`, `alarm` |
| `HealthContributor` | `ComponentHealth health();` Не дольше 1 с | все компоненты | `system` |

Все интерфейсы таблицы лежат в `ru.netmon.contract`, имена и методы ровно как в таблице.

Межмодульные события Spring (`ru.netmon.contract.events`, рассылаются `ApplicationEventPublisher`), сигнатуры:

```java
public record DeviceChanged(long deviceId, ChangeType change) { public enum ChangeType { CREATED, UPDATED, DELETED } }
public record DeviceDeleted(long deviceId) {}
public record MonitoringChanged(long deviceId, boolean enabled) {}
public record ProfileChanged(long profileId) {}
public record SettingChanged(String key, String value) {}
```

| Событие | Когда | Подписчики |
|---|---|---|
| `DeviceChanged(deviceId, ChangeType)` | После фиксации, `@TransactionalEventListener(AFTER_COMMIT)` | `poll` |
| `DeviceDeleted(deviceId)` | Внутри транзакции удаления, `@EventListener` | `secret` (удаляет секрет устройства), `status`, `alarm` |
| `MonitoringChanged(deviceId, enabled)` | Внутри транзакции | `status`, `alarm` |
| `ProfileChanged(profileId)` | После фиксации | `poll`, `snmp` |
| `SettingChanged(key, value)` | Публикуется внутри транзакции изменения, подписчик `@TransactionalEventListener(phase = AFTER_COMMIT)` | кто использует (T4.11) |

`Actor(long userId, String login)`. Системный актор для начальных данных: `userId=null` запрещён в `audit`, поэтому начальные данные журнала действий не пишут. Контроллер берёт актора из `@AuthenticationPrincipal CurrentUser user` как `user.actor()`.

**Запись журналов (T1.06; проверено прототипом: прокси CGLIB, `MANDATORY`, откат, `jsonb`, `timestamptz`).**
- Транзакция. `AuditLogService` (`ru.netmon.audit`) и `EventJournalService` (`ru.netmon.journal`): `@Service`, метод `record` с `@Transactional(propagation = Propagation.MANDATORY)`. Запись идёт в транзакции вызывающего и видна только после её фиксации; откат вызывающего не оставляет ни строки `audit_log`, ни строки `event`. Вызов вне транзакции: `IllegalTransactionStateException` («No existing transaction found for transaction marked with propagation 'mandatory'»), строк нет. Причина: изменение без записи в журнале и запись без изменения одинаково недопустимы (LOG-04), а забытая транзакция видна сразу. Исключение внутри `record` проходит через прокси и помечает транзакцию вызывающего только на откат: даже если вызывающий его перехватил, фиксация даёт `UnexpectedRollbackException` и изменение откатывается (проверено). `REQUIRES_NEW` и запись после фиксации не применяются. Журналы ничего не публикуют: ни событий Spring §7.2, ни SSE §6.
- Сериализация. Значения переводятся в дерево внедрённым бином `tools.jackson.databind.json.JsonMapper` (`valueToTree`; не `new JsonMapper()`, чтобы модуль Jackson T1.22 для `SnmpCredentials` действовал и здесь), затем `SecretFieldGuard.strip`, затем `NulGuard.tree` (с T1.07), затем `writeValueAsString`, в SQL параметром с приведением `?::jsonb`. `null` значения пишется SQL `NULL`, не JSON `null`. Время параметром `OffsetDateTime.ofInstant(ts, ZoneOffset.UTC)`, чтение `rs.getObject("ts", OffsetDateTime.class).toInstant()`. `sourceIp` параметром `ip.getHostAddress()` без зоны IPv6 (с T1.07, ниже) с приведением `?::inet` (без обращения к DNS).
- `ru.netmon.common.json.SecretFieldGuard` (`public final class`, без экземпляров): `static boolean isSecretName(String name)` (с T1.07, ревью T1.06: точный набор имён T1.06 пропускал `auth_password`, `snmpCommunity`, `apiKey`, `privateKey` и подобные) нормализует имя: `toLowerCase(Locale.ROOT)`, затем удаляет все `_` и `-`; имя секретное, если нормализованное содержит подстроку `password`, `passphrase`, `secret`, `token`, `community`, `ciphertext`, `privatekey`, `apikey` или точно равно `authkey`, `privkey`, `masterkey`. Ложное срабатывание допустимо и безопаснее утечки: `passwordPolicy`, `passwordChangedAt`, `csrfToken`, `secretId` удаляются. Не секретные (закрепляет тест): `key`, `pairKey`, `keyVersion`, `authKeyVersion`, `user`, `login`, `note`, `description`, `value`, `authProtocol`, `privProtocol`. Подстрока `key` не вводится: она закрыла бы ключи настроек и `pairKey` трапов; `static JsonNode strip(JsonNode node)` возвращает глубокую копию (`deepCopy()`), из которой удалены поля с такими именами на любой глубине, в объектах и в элементах массивов; вход не меняется; `strip(null)` = `null`. Значения не проверяются: текст `password` в поле `note` остаётся, `description` события не чистится. Значения (в том числе `description`) не содержат секретов по построению: это обязанность вызывающего (§10.4, §10.5), а не страховки. Применяется к `oldValue`, `newValue` и `details` событий.
- `ru.netmon.common.json.NulGuard` (T1.07, ревью T1.06: `\u0000` в `jsonb` и байт 0x00 в `text` PostgreSQL отвергает, и запись журнала откатывала бы всю транзакцию, например трапа с двоичным varbind): `public final class`, без экземпляров; `static String text(String s)` заменяет каждый `U+0000` на `U+FFFD`, `null` остаётся `null`; `static JsonNode tree(JsonNode n)` возвращает новое дерево, где то же сделано в именах полей и строковых значениях на любой глубине (объекты, массивы), прочие узлы копируются, `null` даёт `null`, вход не меняется. Порядок сериализации в обоих журналах: `valueToTree`, `SecretFieldGuard.strip`, `NulGuard.tree`, `writeValueAsString`. `EventJournal.record` пропускает через `NulGuard.text` `description`, `AuditLog.record` пропускает `objectId`. Остальные текстовые столбцы журналов (`user_login`, `action`, `object_type`) берутся из БД или из проверенных шаблонов. Совпадение имён после замены (`a\u0000` и `a�` в одном объекте): остаётся последнее, не тестируется. Одиночные суррогаты UTF-16 (`\uD800` без пары) в `description`, `details` и `objectId` не нормализуются: pgjdbc при кодировании в UTF-8 молча заменяет каждый на `?`, запись не падает (известное поведение, ревью T1.07; не тестируется). Причина: такой текст приходит только из повреждённого ввода, а `?` его не скрывает хуже, чем `U+FFFD`.
- `sourceIp` с зоной IPv6 (`fe80::1%eth0`, `getHostAddress()` = `fe80:0:0:0:0:0:0:1%7`): `?::inet` такую запись не принимает, поэтому `EventJournal.record` отрезает от `getHostAddress()` всё начиная с `%` (с T1.07, ревью T1.06). Устройства остаются только IPv4 (A-1); правило защищает запись события `TRAP_UNKNOWN_SOURCE`, если приёмник получил трап по IPv6.
- Размер `oldValue`/`newValue` и `details` не ограничивается (решение ревью T1.06). Причина: вызывающий передаёт один изменённый объект API, его поля уже ограничены проверкой ресурса (§5.4, §9.1); загрузка Excel (T7) пишет запись на устройство, а не файл целиком. Лежит в `common`, потому что его вызывают `audit` и `journal`, а их взаимная зависимость запрещена §2.2. Поле `user` записи `SnmpCredentials.V3` и протоколы набором не закрываются: их закрывает модуль Jackson T1.22 (§10.4), а `secret` передаёт в журнал только метаданные.
- `AuditLog.record`: `a` не `null` (`NullPointerException`); `action` по шаблону `[A-Z][A-Z_]{0,63}` (`CREATE`, `UPDATE`, `DELETE`, `ACK`, `RESET_PASSWORD`), `objectType` по шаблону `[a-z][a-z_]{0,63}` (`user`, `setting`, `device`, `device_type`, `device_group`, `profile`, `secret`, `trap_rule`, `alarm`), иначе `IllegalArgumentException`; `objectId` может быть `null`. Время `clock.instant()` внедрённого `Clock`, одно значение для `audit_log.ts` и `event.ts`. После вставки `audit_log` (`returning id`) вызывается `EventJournal.record(USER_ACTION, deviceId, null, description, details, ts)`: `deviceId` = `Long.valueOf(objectId)` только при `objectType` `device` и `objectId` из цифр `[0-9]{1,18}`, иначе `null` (строка устройства к этому моменту существует: создание пишет журнал после вставки, удаление мягкое; иначе нарушение внешнего ключа, это ошибка вызывающего); `description` = `"Действие пользователя " + login + ": " + action + " " + objectType` и, если `objectId` не `null`, `" " + objectId`; `details` = `LinkedHashMap` `auditId`, `userId`, `login`, `action`, `objectType`, `objectId` (значения `oldValue`/`newValue` в событие не копируются).
- `EventJournal.record`: `t`, `description`, `ts` не `null` (`NullPointerException`); `deviceId`, `sourceIp`, `details` могут быть `null` (неизвестный источник трапа), `null` внутри `details` пишется JSON `null`.
- Вставка `event` с `device_id` (и так же `alarm`, `check_state`, `access_secret` с внешним ключом на `device`) берёт на строку устройства замок `FOR KEY SHARE` до фиксации транзакции. Это важно для правила ниже.

**Замок строки устройства (с T1.07, ревью T1.06; проверено прототипом на PostgreSQL 18.6).** Транзакция, которой нужна исключительность по устройству (`StatusRecalculator`, писатель результатов `status`, правка и удаление `device`), берёт `SELECT ... FOR NO KEY UPDATE` или обновляет неключевые столбцы `UPDATE device SET ...` (это тот же замок). `FOR UPDATE` и `FOR SHARE` на `device` запрещены. Причина: транзакция трапа по §7.3 сначала пишет `EventJournal` (`FOR KEY SHARE` от внешнего ключа), затем пересчитывает статус; с `FOR UPDATE` две параллельные транзакции трапа одного устройства повышают каждая свой `FOR KEY SHARE` и получают `deadlock detected` (40P01), а `FOR NO KEY UPDATE` с `FOR KEY SHARE` совместим, и вторая транзакция просто ждёт первую (прототип: `FOR UPDATE` дал 40P01, `FOR NO KEY UPDATE` обе зафиксированы; кроме того, `FOR UPDATE` блокирует саму вставку события другой транзакцией). Порядок замков не меняется: сначала устройство, затем тревога. Изменение ключевого столбца `device.id` не делается никогда.

**Настройки (T1.07; проверено прототипом).**
- `SettingsService` (`ru.netmon.settings`, `@Service`, пакетный, `implements Settings`) кэша не держит: `get` и `getInt` читают `select value from system_setting where key = ?` (транзакция вызывающего, если есть). Причина: `Settings` нигде не читается на горячем пути (вход, неудачный вход, задание раз в час, запуск приёмника трапов), а без кэша нет устаревших значений после отката, после записи `TestDb` прямым SQL и в другом контексте Spring на той же БД (`RestartableApp` видит изменение сразу, проверено). Понадобится кэш: решение `architect`.
- `get(key)`/`getInt(key)` с ключом без определения: `IllegalArgumentException` («Неизвестная настройка: <key>», ошибка кода). Нет строки: `IllegalStateException` «Нет строки system_setting для ключа <key>». `getInt` проверяет хранимое значение тем же правилом, что `PUT`; нарушение (строку правили SQL): `IllegalStateException` «Недопустимое значение настройки <key> в БД». Вызывающий не подставляет своё умолчание.
- Изменение (`@Transactional`, `REQUIRED`): определение, значение (§5.2 «Настройки»), `select value ... for update` (строки нет: `IllegalStateException`), при отличии `update system_setting set value = ?, updated_at = ?, updated_by = ? where key = ?` (`updated_at` из `Clock`, `updated_by` = `Actor.userId`), `AuditLog.record(actor, "UPDATE", "setting", key, Map.of("value", old), Map.of("value", value))`, `ApplicationEventPublisher.publishEvent(new SettingChanged(key, value))`. Две одновременные правки одного ключа выполняются по очереди, вторая пишет в журнал старым значением результат первой.
- Подписчик `SettingChanged` объявляет `@TransactionalEventListener(phase = AFTER_COMMIT)`: при откате он не вызывается. Обычный `@EventListener` запрещён (сработал бы до фиксации). Подписчик выполняется в потоке запроса после фиксации, быстро (перепривязка порта, а не долгая работа) и не бросает исключений: ошибку применения (порт занят, SNMP-14) он показывает своим `HealthContributor` и логом. Исключение подписчика Spring 7.0 пишет в лог `ERROR` («TransactionSynchronization.afterCompletion threw exception») и не пробрасывает: ответ `PUT` 200, значение сохранено (проверено прототипом). Изменение, сделанное не через `SettingsService` (SQL, `TestDb.restoreSettings()`), события не даёт: подписчик, которому это важно, читает `Settings` сам при запуске.

### 7.3 Поток результата

```
Планировщик -> CheckExecutor (с таймаутом) -> ResultSink: ограниченная очередь
   Пакет (до 500 результатов или 1 с), в одной транзакции:
     вставка check_result -> check_state (§8.1 + ThresholdEvaluator) -> CheckTransitionListener[]
     -> StatusCalculator -> device.status -> DeviceStatusListener[] -> журнал
   После фиксации: SSE
```

Один поток записи пакетов, поэтому по одному устройству гонок нет. Очередь 50 000; при переполнении результат отбрасывается, счётчик виден в `HealthContributor` как DEGRADED. Ошибка БД: пакет повторяется с задержкой до 3 раз; опрос не останавливается, не блокируется.

Трап: `TrapListener -> TrapProcessor`, отдельная транзакция на трап: `EventJournal`, `TrapActionSink[]`, пересчёт статуса через `StatusRecalculator.recalculate(deviceId)` (в `status`; блокировка строки устройства `SELECT ... FOR NO KEY UPDATE`, тот же замок берёт писатель результатов; `FOR UPDATE` давал взаимную блокировку с `FOR KEY SHARE` от вставки события, §7.2 «Замок строки устройства»). Порядок замка: сначала устройство, затем тревога. Трапы можно обрабатывать в нескольких потоках: две транзакции трапа одного устройства ждут друг друга, но не дают 40P01.

## 8. Правила статуса (раздел 6 ТЗ)

### 8.1 Состояние проверки

- Неудача: `failures+1`, `successes=0`. `health` становится `FAILED` при `failures >= failCount`.
- Успех: `successes+1`, `failures=0`. `health` из `FAILED` в `OK` только при `successes >= successCount`; из `UNKNOWN` в `OK` сразу.
- Пока `failures < failCount`, `health` остаётся прежним (включая `UNKNOWN`).
- Значение без числа не меняет `threshold_level`.

### 8.2 Статус устройства `StatusInput(monitoringEnabled, List<CheckView>, boolean activeTrapAlarm, Instant now, Instant serverStart)`

Учитываются только обязательные включённые проверки (`required && enabled`). Необязательная не влияет.

1. `monitoringEnabled=false` или нет обязательных проверок: `NO_DATA`.
2. Иначе худший из вкладов, порядок `CRITICAL > WARNING > NO_DATA > OK`:

| Вклад | Условие |
|---|---|
| CRITICAL | основная проверка `FAILED`; любая обязательная `threshold=CRITICAL` |
| WARNING | не основная обязательная `FAILED`; любая обязательная `threshold=WARNING`; `activeTrapAlarm` |
| NO_DATA | проверка `UNKNOWN`; устарела: `now - max(lastTs, serverStart) > 3 × intervalSec` |
| OK | иначе |

Устаревшая проверка с прежним `FAILED` даёт `NO_DATA`, а не аварию: данных нет. `serverStart` в формуле исключает ложные `NO_DATA` при перезапуске (POLL-08). `NO_DATA` тревогу не создаёт.

Правило трапа всегда даёт вклад WARNING, а уровень правила задаёт уровень тревоги: таблица раздела 6 ТЗ относит трап к предупреждению.

### 8.3 Пороги и гистерезис (T6.01)

Для `ABOVE`: вход в уровень при `value >= порог`. Выход из `CRITICAL` при `value <= crit - hysteresis` (падает до уровня, соответствующего значению). Выход из `WARNING` при `value <= warn - hysteresis`. Для `BELOW` зеркально. Значения `warn`/`crit` могут отсутствовать. Пример при warn=80, crit=95, h=5: 80 даёт WARNING, 79,9 остаётся WARNING, 75 даёт NONE; 95 даёт CRITICAL, 90,1 остаётся CRITICAL, 90 даёт WARNING. `ThresholdEvaluator` возвращает только целевой уровень. Повышение уровня применяется сразу. Снижение применяет `CheckStateMachine`: при цели ниже текущего уровня `threshold_ok_streak+1`, при достижении `successCount` уровень снижается и счётчик обнуляется; любая другая цель обнуляет счётчик. До T6 `ThresholdEvaluator` нет и уровень всегда `NONE`.

### 8.4 Ключи тревог (T6)

`reason_key`: `check:<checkId>:fail`, `check:<checkId>:threshold`, `trap:<pair_key>` или `trap:<pair_key>:<correlationValue>`. Одна активная тревога на устройство и ключ обеспечивается `ux_alarm_active` и `INSERT ... ON CONFLICT DO NOTHING`. Смена уровня обновляет существующую тревогу, принятие сохраняется. Опрос закрывает трап-тревогу (`POLL_OK`), когда основная проверка устройства успешна и трап-тревога старше момента этого успеха.

Трап-тревога закрывается успешным опросом всегда, как указано в разделе 6 ТЗ. Для проблем одного порта это может закрыть её раньше парного linkUp: поведение принято по ТЗ, отчёт T6.05 сообщает об этом заказчику.

## 9. Форматы профиля и правил трапов

### 9.1 Профиль (API и `seed/profiles/*.json`)

```json
{
  "name": "Пример", "description": "", "version": 0,
  "checks": [{
    "code": "ping", "name": "Ping", "kind": "PING", "params": {},
    "intervalSec": 60, "timeoutMs": 2000,
    "warn": null, "crit": null, "hysteresis": 0, "direction": "ABOVE",
    "failCount": 3, "successCount": 2,
    "required": true, "primary": true, "enabled": true,
    "unconfirmed": false, "unconfirmedNote": null, "source": null
  }]
}
```

Правила проверки: ровно одна `primary`, она `required` и `enabled`; `code` уникален в профиле; `warn`/`crit` согласованы с `direction` (при ABOVE `warn <= crit`); `hysteresis >= 0`. **PRF-05:** интервал задаётся в проверке профиля, профиль назначен типу устройства (`device_type.default_profile_id`), поэтому смена интервала в профиле типа меняет частоту для его устройств.

### 9.2 Параметры `params` по виду

| Вид | Поля | Проверка |
|---|---|---|
| `PING` | нет | пусто |
| `TCP` | `port` | целое 1..65535 |
| `SCPI` | `port`, `command`, `responsePattern?`, `readLimitBytes?` (умолчание 4096) | `command` непуст, без значений в коде; шаблон компилируется |
| `SNMP` | `version` (`V2C|V3`), `port?` (умолч. 161, стандартный порт SNMP), `op`, далее по `op` | см. ниже |

`SNMP.op`:

| `op` | Поля | Значение результата |
|---|---|---|
| `GET` | `oid` | Скаляр; число в `valueNum`, иначе `valueText` |
| `WALK_AGG` | `oid` (столбец), `aggregate` (`MAX,MIN,AVG,SUM,COUNT`), `filterOid?`, `filterRegex?`, `countEquals?` | Строки столбца, отобранные по значению столбца `filterOid` с тем же индексом; `valueNum` по `aggregate`; построчные значения в `valueText` как JSON `{индекс:значение}` до 16 КБ |
| `PERCENT_USED` | `usedOid`, `freeOid` | `100*used/(used+free)`; `freeOid=null` не допускается |

`oid` может быть пустым только при `enabled=false, unconfirmed=true`: поле заполняется после snmpwalk (SNMP-07), планировщик такую проверку не запускает, а API показывает её с пометкой.

**Источник (проверяет `ProfileValidator`).** Для проверки с OID, портом или командой: либо `unconfirmed=true` с `unconfirmedNote`, либо непустой `source` (раздел 2 ТЗ, RFC, файл MIB). В начальных профилях заносятся только значения из раздела 2 ТЗ и стандартных или производительских MIB. Значения эмулятора подтверждением не являются.

### 9.3 Правило трапа

```json
{"trapOid":"<из MIB>","action":"ALARM","level":"WARNING","pairKey":"link","correlationOid":"<varbind>",
 "description":"...","unconfirmed":false,"source":"<RFC или MIB>"}
```

`action`: `EVENT` только журнал; `ALARM` открывает тревогу с ключом `trap:<pairKey>[:<значение correlationOid>]`; `CLOSE` закрывает такую тревогу (парный трап). `EVENT` не требует `pairKey`. Правила берутся из профиля устройства, определённого по IP источника. Неизвестный источник: событие `TRAP_UNKNOWN_SOURCE`, без тревоги. Известное устройство без подходящего правила: событие `TRAP` без тревоги (трап не теряется). Смена частоты Infinet в начальном профиле `EVENT`.

### 9.4 Начальные профили

`seed/profiles/<ключ>.json`, ключи: `cisco-2960`, `cisco-2911`, `infinet-r5000`, `rs-esmd`, `rs-eb500`, `rs-ddf255`, `pc-windows`, `legacy`. `ProfileSeeder` работает только при первом запуске, когда таблица `profile` пуста: правки администратора не перезаписываются, а удалённый им профиль не возвращается. Вместе с ним создаётся тип устройства с тем же именем и `default_profile_id`. Значения умолчаний раздела 6 ТЗ заносятся в JSON, а не в код. Значение поля без подтверждения хранится с `unconfirmed=true`, `required=false` и `enabled` по §9.2.

## 10. Секреты

1. **Алгоритм:** AES-256-GCM, случайный nonce 12 байт на каждое шифрование, тег 128 бит, AAD = байты `nm-secret-v1`. В `access_secret.ciphertext`: `nonce || шифртекст+тег`. Открытый текст: UTF-8 JSON из §10.3.
2. **Ключ:** файл `nm.secret-key-file` (умолчание `${nm.data-dir}/secret.key`), Base64 от 32 случайных байт, вне БД.
   - Файла нет и в таблице нет секретов: файл создаётся.
   - Файла нет, а секреты есть: старт прерывается с понятным сообщением. Новый ключ не создаётся никогда, иначе секреты потеряются.
   - Файл неверного формата или длины: старт прерывается.
   - Ключ не подходит (`AEADBadTagException`): секрет считается недоступным, опрос получает `CheckError.AUTH`, компонент `secrets` DEGRADED, значение в лог не идёт.
   - Права на файл на Windows задаёт установщик [ЧЕЛОВЕК]. В резервной копии ключ хранится отдельно от дампа (T8.07).
3. **Запись через API:** `{"snmpVersion":"V2C","deviceId":1,"community":"..."}` или `{"snmpVersion":"V3","groupId":2,"user":"...","authProtocol":"...","authPassword":"...","privProtocol":"...","privPassword":"..."}`. Ровно один из `deviceId`, `groupId`.
4. **Чтение:** значений в API нет (§5.2). Журнал действий пишет только метаданные; страховка на записи `SecretFieldGuard` (§7.2, T1.06). Логирование тела запроса к `/api/secrets` и `/api/users*`, `/api/me/password`, `/api/auth/login` запрещено. Вход принимает `username` и `password` и из строки запроса (`getParameter`, ревью T1.05), поэтому строка запроса в лог не пишется нигде: `ApiErrorHandler` и `ApiErrorController` пишут только `getRequestURI()`, а журнал доступа, если он появится (T8.08), пишет путь без строки запроса (`%U`, не `%r` и не `%q`). `toString()` у типов с секретами возвращает маску. `SnmpCredentials` (записи, Jackson сериализует их через аксессоры со значениями) живёт только внутри `secret` и `snmp`: не входит в поля событий, `CheckOutcome`, `details`, ответов API, SSE и журналов, не передаётся в `ObjectMapper` и в параметры логирования, кроме `toString()`. Страховка: `secret` (T1.22) регистрирует бин Jackson `Module`, который сериализует любой `SnmpCredentials` строкой `"***"`. Логирование параметров SNMP в SNMP4J не выше INFO. Проверка: тест с уникальной строкой ищет её во всём выводе (§12.3).
5. **Трапы:** community входящего трапа не проверяется и не сохраняется; подлинность источника определяется по IP. Значение не попадает в `details`.

## 11. Фронтенд

- `static/index.html`: оболочка, одна страница с хэш-маршрутами. `static/login.html` и `static/login/*`: вход (публичны).
- `static/js/core/`: `api.js` (fetch, CSRF, 401 ведёт на вход, разбор ошибки §5.1), `router.js`, `dom.js`, `sse.js` (единственная точка `EventSource`, подписка `on(event, fn)`), `format.js` (время по Москве через `Intl` с `Europe/Moscow`).
- `static/js/pages/<страница>.js`: экспорт `render(container, params)` и `dispose()`. Файлы всех страниц раздела 5 ТЗ создаются заглушками в T1.28: `map, overview, alarms, journal, devices, profiles, system, secrets, users, audit, settings, account`. Каждая дальнейшая задача правит только файл своей страницы. Навигация скрывает пункты по роли, но права обеспечивает API.
- `static/js/components/`: `alarm-badge.js` (заглушка в T1.28, реализует T6.13), `status.js` (цвет, значок, русская подпись четырёх статусов).
- `static/vendor/`: сторонние библиотеки с файлами лицензий. `static/fonts/`: не нужны, используется системный шрифт (`system-ui, sans-serif`). `static/sounds/alarm.wav`.
- **XSS:** пользовательские данные только через `textContent` и `dom.js`. `innerHTML`, `insertAdjacentHTML`, `document.write`, `eval` в своём коде запрещены (проверяет `FrontendRulesTest`, grep по `static/js`). В Leaflet: подсказки и попапы только с DOM-элементом, а не строкой.
- **Совместимость:** только возможности Firefox 115 ESR, сомнительные сверяются по таблице совместимости MDN. Браузерные тесты запускаются в Firefox 115.
- **Заголовки (T1.25):** `Content-Security-Policy: default-src 'self'; img-src 'self' data:; style-src 'self'; script-src 'self'; connect-src 'self'; font-src 'self'; object-src 'none'; base-uri 'self'; frame-ancestors 'none'; form-action 'self'`, `X-Content-Type-Options: nosniff`. Leaflet ставит стили через CSSOM, CSP им не мешает; проверка в T2.03. Встроенных `<script>` и `style=""` нет.
- **Звук (ALM-09):** настройка хранится в `localStorage` браузера рабочего места, включение через нажатие (политика автозапуска браузеров).

## 12. Тесты и эмуляторы

### 12.1 Команды

| Цель | Команда |
|---|---|
| Все тесты без браузеров | `mvn -B verify` |
| Один класс или шаблон | `mvn -B -pl server -am test -Dtest=ИмяТеста` |
| Эмуляторы | `mvn -B -pl emulators test` |
| Браузерные, один тест | `mvn -B -pl server -am verify -Pui -Dit.test=ИмяUiIT -Dnm.ui.browser=firefox` |
| Браузерные, все | `mvn -B -pl server -am verify -Pui -Dnm.ui.browser=firefox` и то же с `chrome` |
| Сборка без сети | `scripts/offline-verify.sh` |

Имена: `*Test` быстрые и интеграционные (Surefire), `*UiIT` браузерные (Failsafe, только профиль `ui`), `*LoadIT` нагрузочные (Failsafe, только профиль `load`).

### 12.2 PostgreSQL в тестах

`io.zonky.test:embedded-postgres` на один процесс JVM: класс `testsupport.TestPostgres` запускает сервер один раз (порт случайный, каталог временный) и сам реализует `ApplicationContextInitializer<ConfigurableApplicationContext>`, передавая `spring.datasource.url/username/password`. Подключение: `@ContextConfiguration(initializers = TestPostgres.class)` (в T1.01 прямо на `SmokeTest`, с T1.03 внутри `@IntegrationTest`). `SmokeTest` несёт `@DirtiesContext(classMode = BEFORE_CLASS)` (с T1.02): он проверяет журнал запуска, а контекст из кеша Spring запуск не выводит. Первый запуск берёт двоичные файлы из артефакта Maven, поэтому интернет нужен только для `prepare-offline` [ПОДТВ на macOS x86_64 в пробном проекте; платформы §3; Windows ЧЕЛОВЕК]. Docker не нужен. Не запускать тесты от администратора Windows: `initdb` откажет [ЧЕЛОВЕК].

Тестовые помощники (`server/src/test/java/ru/netmon/testsupport`; `TestPostgres` создаёт T1.01, остальные T1.03, далее только добавления через `architect`):

| Класс | Назначение |
|---|---|
| `@IntegrationTest` | Мета-аннотация: `@SpringBootTest`, `@AutoConfigureMockMvc`, `@ActiveProfiles("test")`, `@ContextConfiguration(initializers = TestPostgres.class)`, `@Import({MutableClock.Config.class, TestDb.class, TestData.class, Api.class})`, `@ExtendWith(TestDb.CleanBeforeEach.class)` |
| `TestDb` | Бин с `JdbcClient` и `PlatformTransactionManager`. Снимок настроек: статическое поле, один раз на JVM, при первом вызове `clean()` или `restoreSettings()` до любых изменений читает `select key, value from system_setting order by key` (то есть значения всех применённых миграций, а не копию в коде); `defaults()` отдаёт его неизменяемым `Map<String,String>` в порядке ключей. `clean()`: в одной транзакции (`TransactionTemplate`) `set local lock_timeout = '5s'`, одной командой `TRUNCATE` всех таблиц схемы `public` из `pg_class` (`relkind in ('r','p') and not relispartition`; имена с явной схемой `format('public.%I', relname)`, чтобы результат не зависел от `search_path`; список отдаёт пакетный метод `List<String> truncateTargets()`), кроме `flyway_schema_history`, включая `system_setting`, без `CASCADE` и без `RESTART IDENTITY`, затем строки снимка в `system_setting` параметризованным `insert into system_setting(key, value) values (?, ?)`. `restoreSettings()`: в одной транзакции тот же `lock_timeout`, `delete from system_setting` и та же вставка снимка. Вложенный `CleanBeforeEach implements BeforeEachCallback` берёт контекст через `SpringExtension.getApplicationContext(context)`, вызывает `TestDb.clean()` и `MutableClock.reset()` |
| `TestData` | Бин с `JdbcClient`, построители строк прямым SQL с уникальными значениями: пользователь, тип, группа, профиль, проверка, устройство; возвращают `id`. Статический `ip()`: уникальный на процесс IPv4 из `10.0.0.0/8` (счётчик `AtomicInteger`) для построителя устройства и тестов |
| `MutableClock` | `final class MutableClock extends Clock`, зона UTC, начальное значение `Instant.now()` с точностью до микросекунд, `set(Instant)`, `advance(Duration)`, `reset()` (снова `Instant.now()` до микросекунд; вызывает `TestDb.CleanBeforeEach`). Запись (`set`, `advance`, `reset`) `synchronized`, поле `volatile`. `withZone(z)`: `this` только при `z.equals(ZoneOffset.UTC)` (без `normalized()`: `ZoneId.of("UTC")` и `Etc/UTC` дают представление, иначе `getZone()` вернул бы не ту зону, что запрошена), иначе представление с той же шкалой времени (`instant()` читает этот `MutableClock`) и зоной `z`; у представления `equals`/`hashCode` по паре (исходный `MutableClock` по ссылке, зона), как требует контракт `Clock`. `Instant.now()` здесь разрешён (§14, тестовый помощник). Вложенный `@TestConfiguration(proxyBeanMethods = false) Config` с `@Bean @Primary MutableClock mutableClock()` |
| `Api` | Бин с `MockMvc`: `api.as(Role.ADMIN).get/post/put/patch/delete(...)` возвращает `ResultActions`; `as(Role)` отдаёт вложенный `public final class As`, `post/put/patch` принимают тело строкой JSON (`application/json`); `SecurityMockMvcRequestPostProcessors.user(...)` с ролью и `csrf()` на каждом запросе (до T1.06; `csrf()` заменён, см. ниже). С T1.06 CSRF: `public RequestPostProcessor csrfToken() throws Exception` (`org.springframework.test.web.servlet.request.RequestPostProcessor`) при вызове выполняет анонимный `GET /api/auth/csrf`, берёт из ответа cookie `XSRF-TOKEN` (нет cookie: `IllegalStateException` «GET /api/auth/csrf не выдал cookie XSRF-TOKEN») и возвращает обработчик, который добавляет эту cookie к уже имеющимся cookie запроса и ставит её значение в заголовок `X-XSRF-TOKEN`; `As` прикладывает `csrfToken()` к каждому запросу с методом вне `GET`, `HEAD`, `OPTIONS`, `TRACE`, безопасные методы идут без токена. Тест, шлющий изменяющий запрос через `MockMvc` напрямую, берёт токен так же: `.with(api.csrfToken())`. С T1.05 вместо `user(...)` `authentication(UsernamePasswordAuthenticationToken.authenticated(p, null, p.getAuthorities()))` с principal `CurrentUser` (§5.1); конструктор `Api(MockMvc, JdbcClient)`; на каждом запросе строка `app_user` с логином `test-operator` или `test-admin` (`password_hash` `'h'`) создаётся `insert ... on conflict ((lower(login))) do nothing`, её `id` читается запросом (после `TestDb.clean()` строки нет). Тест, считающий строки `app_user`, учитывает эти строки или не вызывает `Api` |
| `LogCapture` | JUnit-расширение (`@RegisterExtension`, `BeforeEachCallback`, `AfterEachCallback`): `beforeEach` очищает буфер (`setLength(0)`), `text()` отдаёт всё, что записал Logback с начала метода. `level(String logger, Level level)` (`ch.qos.logback.classic.Level`) ставит уровень логгеру из кода и запоминает прежний (в том числе `null`, наследование); `afterEach` возвращает прежние уровни. Appender `LogCapture.Appender` подключён в `server/src/test/resources/logback-test.xml` вместе с `CONSOLE` из `defaults.xml` и `console-appender.xml` Spring Boot и пишет в статический буфер JVM |
| `RestartableApp` | `AutoCloseable`: `new RestartableApp(Map<String,String> extraProperties, Class<?>... extraSources)`, `start()`, `stop()`, `restart()`, `port()`, `context()`. `SpringApplicationBuilder(NetMonitoringApplication.class, extraSources...)`, профиль `test`; свойства `TestPostgres.properties()`, затем `extraProperties` и `server.port=0` передаются аргументами `run("--ключ=значение", ...)`. Ошибка запуска выходит исключением из `start()`. `start()` при уже запущенном контексте бросает `IllegalStateException` (прежний контекст не теряется); перезапуск только через `restart()`. `MutableClock` только если тест передал `MutableClock.Config.class` |

Решения по помощникам (проверены прототипом на текущем `server/pom.xml`):
- Снимок настроек вместо копии строк: миграция с новым ключом `system_setting` (после T1.02) восстанавливается `TestDb` без правки помощников. Снимок верен, пока до первого `clean()` в JVM никто не пишет в `system_setting`: по правилам ниже писать могут только классы `@IntegrationTest`, а их первый метод начинается с `clean()`. Это соглашение, а не проверка кода: `RestartableApp` и тест вне `@IntegrationTest`, меняющие настройки (через API T1.07 или SQL), запрещены; такой тест помечается `@IntegrationTest` и возвращает настройки в `@AfterEach` через `restoreSettings()`. `TestDbSelfTest` дополнительно сверяет, что снимок содержит шесть пар §4.2. Внутри такого класса `RestartableApp` может читать и менять настройки (T1.07, T1.08): `Settings` без кэша (§7.2), поэтому восстановление прямым SQL сразу видно всем контекстам на этой БД; события `SettingChanged` оно не даёт, это допустимо, потому что приёмник трапов в тестах включается только с `@DirtiesContext` или в `try` с ресурсами (§12.2 «Фоновые задачи в тестах»).
- `lock_timeout` у очистки: `TRUNCATE` берёт `ACCESS EXCLUSIVE`; соединение, оставшееся у фоновой задачи другого контекста, роняет очистку ошибкой (SQLState `55P03`) через 5 с, а не вешает сборку.
- `system_setting` очищается вместе со всеми: на неё ссылается `app_user` через `updated_by`, а PostgreSQL не выполняет `TRUNCATE` таблицы, на которую ссылается таблица вне той же команды. `check_result` очищается через родителя, разделы список не пополняют. Без `RESTART IDENTITY`: совпадение `id` разных таблиц не скрывает ошибок.
- `MutableClock` подменяет `Clock` из `common/ClockConfig` (`@Configuration(proxyBeanMethods = false)`, `@Bean Clock clock()` = `Clock.systemUTC()`) через `@Primary`; имя бина `mutableClock` отличается от `clock`, переопределение бинов в Spring Boot выключено.
- `RestartableApp` передаёт свойства аргументами командной строки: свойства `SpringApplicationBuilder.properties(...)` имеют низший приоритет, и `application.yml` перебил бы их (в прототипе Tomcat поднялся на 8080 вместо случайного порта; то же было бы с `nm.data-dir` из T1.21).
- `LogCapture` через `logback-test.xml`: каждый запуск Spring сбрасывает контекст Logback и снимает appender, подключённый из кода (проверено: после `restart()` вывод терялся). Уровни в `logback-test.xml` не меняются. Тест, которому нужен `DEBUG`, ставит его через `LogCapture.level(...)` в начале метода и после каждого `start()`/`restart()` `RestartableApp`; `logging.level.*` свойством контекста не задаётся. Причина: запуск любого другого контекста (кешированный контекст другого класса, `RestartableApp`) переинициализирует Logback и снимает уровни, заданные свойствами кешированного контекста.
- Ограничения `LogCapture` (приняты, тесты учитывают):
  1. Appender пишет в буфер всегда, а очищает его только `beforeEach` класса с `LogCapture`; в прогоне без `LogCapture` буфер растёт до следующего такого класса. Допустимо: объём вывода тестов при уровнях `logback-test.xml` мал. Тест не сверяет размер буфера.
  2. В буфер попадают строки всех потоков JVM, в том числе фоновых задач другого кешированного контекста. Тест проверяет наличие или отсутствие своей уникальной строки (маркер, значение секрета), а не точный состав или число строк `text()`.
  3. `RestartableApp.start()`/`restart()` переинициализирует Logback и сбрасывает уровни, выставленные `level(...)` до запуска: `level(...)` вызывается после каждого `start()`/`restart()`. `afterEach` возвращает запомненные прежние уровни и после перезапуска.
- Помощники и тесты не меняют состояние общего контекста (с T1.06): ни бинов, ни полей фильтров, ни статического состояния библиотек, видимого другим классам того же кешированного контекста. Причина: `SecurityMockMvcRequestPostProcessors.csrf()` (spring-security-test 7.1.2) через `WebTestUtils.setCsrfTokenRepository` навсегда ставит в `CsrfFilter` общего контекста `TestCsrfTokenRepository(new HttpSessionCsrfTokenRepository())` вместо `CookieCsrfTokenRepository` (`csrf.spa()`, §5.1), после чего `GET /api/auth/csrf` в этом контексте больше не выдаёт cookie `XSRF-TOKEN` (проверено прототипом T1.06: зонд отражением видит класс хранилища до и после `AuditApiTest`; в порядке Surefire `alphabetical` падали 8 тестов `CsrfTest` и `LoginTest`). Поэтому `csrf()` из spring-security-test в тестах запрещён; токен берётся настоящий через `Api.csrfToken()`. `@DirtiesContext` для обхода таких побочных эффектов не ставится. Проверка: `ApiSelfTest` и полный прогон в порядках `-Dsurefire.runOrder=alphabetical` и `reversealphabetical`.
- Кеш контекстов Spring: `SmokeTest` (`@DirtiesContext(BEFORE_CLASS)`), `@IntegrationTest` и `RestartableApp` дают разные контексты на одной БД; конфликта нет, `@DirtiesContext` сбрасывает только контекст своей конфигурации. Предел: `max_connections=300` у встроенного PostgreSQL, Hikari по умолчанию до 10 соединений на контекст, в кеше до 32 контекстов. Поэтому классы `@IntegrationTest` не вводят свои свойства и `@MockitoBean` без нужды.

Профиль `test`: `nm.poll.enabled=false`, `nm.jobs.enabled=false`, `nm.traps.enabled=false`, `nm.seed.enabled=false`. Каждый тест включает нужное явно.

**Сессии на Tomcat (T1.08).** Истечение по времени проверяется только на `RestartableApp` с аргументами `nm.session.timeout-seconds-override=2` и `server.tomcat.background-processor-delay=1s` (фоновая проверка Tomcat раз в 6 с, истечение наступает через 2..8 с; на прототипе 5,7 с). Ожидание опросом `sessionCount` бина `UserSessionRegistry` из `app.context()` (не бина общего контекста) с предельным сроком 20 с; проверяемая сессия в ожидании не запрашивается, потому что обращение продлевает её. `MutableClock` здесь бесполезен (Tomcat берёт системное время). Класс, меняющий `session.timeout_minutes`, следует правилу «Общая БД JVM» ниже.

**Фоновые задачи в тестах.** Класс `@IntegrationTest`, включающий свойством фоновую работу (`nm.poll.enabled`, `nm.jobs.enabled`, `nm.traps.enabled` или свой планировщик), несёт `@DirtiesContext(classMode = AFTER_CLASS)`; `RestartableApp` с такими свойствами закрывается в `try`-с-ресурсами того же метода. Причина: кешированный контекст продолжал бы опрос и запись в БД во время чужих тестов и держал бы замки, мешая `TestDb.clean()`.

**Тестовые контроллеры и бины.** Классы, нужные только тесту и замеченные бы сканированием компонентов (`@RestController`, `@Component`, `@Configuration`), лежат в тестовых исходниках вне пакета `ru.netmon` (пакет `nmtest.<модуль>`, каталог `server/src/test/java/nmtest/<модуль>/`) и подключаются только к своему тесту: `@Import(...)` на классе теста или `extraSources` у `RestartableApp`. Причина: сканирование `ru.netmon` из `NetMonitoringApplication` подхватило бы их во все `@SpringBootTest`, `RestartableApp` и `SecurityMatrixTest`; `TestTypeExcludeFilter` действует только в контекстах `@SpringBootTest`, не в `RestartableApp`.

**Общая БД JVM.** Одна БД на процесс, очистка `@IntegrationTest` только перед тестом, поэтому данные последнего теста класса остаются до следующего `@IntegrationTest`. Правила: тест, читающий или пишущий таблицы приложения, помечается `@IntegrationTest` (исключение: `SmokeTest`, он данных не пишет); тест не полагается на пустоту таблиц и значения настроек без очистки `TestDb`; тест, меняющий `system_setting`, возвращает настройки в `@AfterEach` через `TestDb.restoreSettings()` (не в теле метода и не только в `finally`); строка в `check_result_default` (по POLL-09 признак сбоя) удаляется тем же тестом, который её создал, в `finally`. Причина: порядок классов в Surefire не задан, а `RestartableApp` и классы вне `@IntegrationTest` видят оставшиеся данные.

**Каталог данных в тестах.** Свойство `nm.data-dir` (а не `nm.secret-key-file`) указывает на временный каталог процесса JVM: `TestPostgres` создаёт его один раз через `Files.createTempDirectory("nm-test-data-")` рядом с запуском PostgreSQL, удаляет хуком завершения JVM и передаёт вместе с `spring.datasource.*`. Набор свойств отдаёт `public static Map<String,String> TestPostgres.properties()`; `initialize` и `RestartableApp` берут свойства только оттуда, поэтому перезапуск видит тот же ключ. `nm.secret-key-file` и `nm.tiles-file` выводятся из `nm.data-dir` по §13 и в тестах не задаются; тест, которому нужен свой файл ключа, задаёт `nm.secret-key-file` на `@TempDir` явно. В `application-test.yml` свойство не пишется. Причина: каталог должен быть один на JVM и вне дерева исходников, а значение в YAML либо относительное (`./data` от рабочего каталога даёт `server/data/`), либо через `${random.uuid}`, который вычисляется заново при каждом разрешении. Распределение: T1.03 вводит `properties()` с `spring.datasource.*`, T1.21 добавляет в него `nm.data-dir`. До T1.21 файлы в `nm.data-dir` никто не пишет.

### 12.3 Матрица прав

Для каждого модуля файл `server/src/test/resources/security-matrix/<модуль>.csv`: `метод,путь,OPERATOR,ADMIN,ANON` с ожидаемым итогом `allow|deny|public` (с T1.05: первая строка файла заголовок; в столбце `ANON` значение `deny` означает 401, `public` означает «не 401»). В матрице только методы контроллеров: вход и выход (фильтры `SecurityConfig`) проверяют `LoginTest` и `CsrfTest`, `/error` (`ApiErrorController`) в инвентарь и матрицу не входит. `SecurityMatrixTest` (T1.26) проходит все файлы и все методы контроллеров: эндпоинт, отсутствующий в матрице, это провал. Новые эндпоинты добавляются своим файлом матрицы; общий тест не правится.

### 12.4 Браузерные тесты

`scripts/prepare-browsers.sh` (нужен интернет один раз) кладёт в `tools/browsers/` Firefox 115.44.0esr, geckodriver 0.37.1, Chrome for Testing и chromedriver 156.0.8078.80. Настоящий Edge и Windows: [ЧЕЛОВЕК]. Тест получает пути из `nm.ui.firefox-bin`, `nm.ui.geckodriver`, `nm.ui.chrome-bin`, `nm.ui.chromedriver` (умолчания под `tools/browsers/`). Браузер без пути: тест падает с понятным сообщением, а не пропускается. Внешние запросы фиксируются и считаются провалом (T2.13).

### 12.5 Эмуляторы (OPS-11)

Модуль `emulators`: в `compile`/`runtime` зависит только от `snmp4j` и `slf4j-simple`, в `test` только от `junit-jupiter` и `assertj-core` из BOM (§3). `java -jar emulators/target/nm-emulators-0.1.0-exec.jar <вид> [--ключ=значение]`; вид `tcp`, `hang`, `scpi`, `snmp`, `trap`. `EmulatorMain` (T1.01) ищет класс `ru.netmon.emulators.<вид>.Main`, поэтому новые виды не правят его файл. Контракт: `public static int run(String[] args)` возвращает код, `main` вызывает `System.exit(run(args))`; тесты вызывают только `run`. Класс вида `ru.netmon.emulators.<вид>.Main` тоже объявляет `public static int run(String[] args)`; `EmulatorMain.run` загружает класс вида без инициализации (`Class.forName(name, false, loader)`), вызывает его `run` с аргументами без первого и возвращает его код. Код 2 и сообщение в stderr: класс не найден; `LinkageError` при загрузке или связывании; класс не `public`; нет `public static int run(String[])` (в том числе метод возвращает не `int`); `IllegalAccessException`. Код 1 и сообщение в stderr: исключение внутри вида, включая `ExceptionInInitializerError` при отложенной инициализации класса вида (первый вызов `run`). Причина загрузки без инициализации: ошибка статического инициализатора относится к поведению вида, а не к его отсутствию. Вид допускается только по шаблону `[a-z]+`, иначе код 2 без загрузки класса. Неизвестный вид или пустые аргументы: сообщение в stderr и код выхода 2. Код вида-эмулятора: 0 при штатном завершении.

Каждый эмулятор также имеет класс с `start()`, `port()` и `close()` для запуска прямо из теста, порт `0` даёт случайный свободный. `server` подключает модуль только в области `test`. Данные SNMP берутся из файла в формате `oid = ТИП: значение`, значения тестовые и не считаются подтверждением.

**Ping.** Эмулировать ICMP нельзя. `PingExecutor` вызывает команду ОС, шаблон которой находится в настройках `nm.poll.ping.command-windows`, `nm.poll.ping.command-unix`. В тестах и автоматической приёмке профиль `emulated` подставляет `SimulatedPingExecutor`, управляемый файлом со списком «недоступных» IP. Поведение на настоящем Windows: [ЧЕЛОВЕК], см. T3.02.

**Трапы и привилегии.** Порт UDP 162 на macOS без прав не откроется, тесты используют случайный порт.

## 13. Настройки запуска (`application.yml` + внешний `config/application.yml`)

### 13.1 Свойства запуска

| Ключ | Умолчание | Смысл |
|---|---|---|
| `server.port` | 8080 | Порт интерфейса |
| `spring.datasource.*` | нет | Подключение к БД |
| `nm.data-dir` | `./data` | Данные на диске; в тестах временный каталог JVM (§12.2) |
| `nm.secret-key-file` | `${nm.data-dir}/secret.key` | Ключ секретов |
| `nm.tiles-file` | `${nm.data-dir}/moscow.mbtiles` | Файл тайлов |
| `nm.bootstrap.admin-login`, `nm.bootstrap.admin-password-file` | нет | Первый администратор, T1.10 |
| `nm.poll.enabled` | true | Опрос включён |
| `nm.poll.max-concurrent` | 200 | Предел одновременных проверок |
| `nm.jobs.enabled` | true | Фоновые задания |
| `nm.traps.enabled` | true | Приём трапов |
| `nm.seed.enabled` | true | Начальные данные |
| `nm.session.timeout-seconds-override` | нет | Только профиль `test`: срок сессии в секундах вместо `session.timeout_minutes`, 1..3600; вне профиля `test` запуск прерывается (T1.08, §5.1 «Сессии»). Передаётся только аргументом `RestartableApp` |

### 13.2 Настройки в БД (`system_setting`, §4.2)

Правятся администратором через `/api/settings` (T1.07) и применяются без перезапуска. Начальное значение пишет миграция `V0102`, после правки действует значение из БД. Все ключи целые; границы задаёт `ru.netmon.settings.SettingDefinitions` (T1.07), правило значения в §5.2 «Настройки». Новый ключ: миграция, строка в `SettingDefinitions` и строка здесь, через `architect`.

| Ключ | Начальное значение | Границы | Когда применяется | Источник |
|---|---|---|---|---|
| `retention.results_days` | 30 | 1..3650 | Следующий запуск обслуживания разделов (раз в час, T3.05) | Раздел 6 ТЗ |
| `retention.journal_days` | 365 | 1..3650 | Следующий запуск удаления (T3.16, T3.23, T6.12) | Раздел 6 ТЗ |
| `traps.udp_port` | 162 | 1..65535 | Сразу после фиксации: приёмник перепривязывается по `SettingChanged` (T4.11); занятый порт виден на странице состояния (SNMP-14), значение остаётся сохранённым | SNMP-08 |
| `session.timeout_minutes` | 720 | 1..43200 (30 суток) | Следующий вход; открытые сессии сохраняют свой срок (T1.08) | Допущение A-2 (§15); ТЗ требует только настраиваемости (AUTH-07) |
| `auth.lock_attempts` | 5 | 1..100 | Следующая неудачная попытка входа (T1.09) | Допущение A-3 (§15); ТЗ требует только настраиваемости (AUTH-08) |
| `auth.lock_minutes` | 15 | 1..1440 (сутки) | Следующая блокировка; действующий `locked_until` не пересчитывается (T1.09) | Допущение A-3 (§15); ТЗ требует только настраиваемости (AUTH-08) |

Причины границ: нижняя 1, потому что 0 означал бы «хранить ноль дней», «не блокировать» или «сессия без простоя», а этого ТЗ не требует; верхние ограничивают явную ошибку ввода (10 лет хранения, 30 суток сессии, сутки блокировки) и дают `setMaxInactiveInterval(минуты * 60)` без переполнения `int`. Порт ниже 1024 на Linux требует прав (§12.5), на Windows нет; запрета нет, ошибка привязки идёт по SNMP-14. Шаблон значения `[1-9][0-9]{0,9}` (§5.2 «Настройки») исключает 0: новый ключ с `min=0` требует правки шаблона в `SettingDefinitions` и §5.2 (ревью T1.07).

## 14. Правила кода

- Время в прикладном коде (`server/src/main`) только из внедрённого `Clock`; `Instant.now()`, `LocalDateTime.now()`, `System.currentTimeMillis()` и подобные там запрещены. В тестовых исходниках (`server/src/test`) разрешены: тестовые помощники по §12.2 (`MutableClock` берёт начальное значение и `reset()` из `Instant.now()`) и измерение длительности в самом тесте. Тест, проверяющий логику со временем, задаёт время через `MutableClock`, а не через системные часы. Интервалы ожидания в тестах только опросом условия с предельным сроком, не `sleep`.
- **SQL и значения.** Правило проверяется по происхождению значения, а не по наличию литерала в тексте SQL:
  1. Значение, пришедшее извне (ввод пользователя, тело и параметры запроса API, содержимое файлов, включая профили и импорт, данные сети и оборудования, значения из БД, прочитанные ранее), попадает в SQL только параметром (`?` или именованный параметр `JdbcClient`). Склейка такого значения в текст SQL запрещена в `main` и в тестах. Нарушение.
  2. Константа, записанная в тексте SQL кодом и не зависящая от внешних данных, допустима и в `main`, и в тестах. Примеры в `main`: `where deleted_at is null`, `enabled = true`, литералы перечислений (`'ACTIVE'`, `'RECOVERED'`), `now()`, `order by id`, `limit 1`. Примеры в тестах: тестовые данные в тексте теста (`'10.0.0.1/24'`, `lat=90.000001`, `ts=now()`), ключи настроек в самотесте, каталожные запросы помощников (`relkind in ('r','p')`, `'public'::regnamespace`, `'public.check_result'::regclass`, имя `flyway_schema_history`), служебные команды (`set local lock_timeout = '5s'`, `lock table ... in access share mode`). Не нарушение.
  3. Имя сортировки только из белого списка. Идентификаторы (таблицы, столбцы, разделы) параметром не передаются: в `main` только из констант кода, белого списка или собранные кодом из константы и даты/числа (имя раздела `check_result`), в тестовых помощниках также из системного каталога PostgreSQL через `quote_ident`/`format('%I')` (так `TestDb.clean()` собирает `TRUNCATE`). Значения в таком SQL по пункту 1.
  4. Для аудитора: замечание выдаётся только по пункту 1 или 3 с указанием, откуда приходит значение. Литерал в тексте SQL сам по себе замечанием не является. Константа Java, подставленная склейкой (`"... where status = '" + Status.ACTIVE.name() + "'"`), относится к пункту 2, если её значение не зависит от входных данных; рекомендуемая форма в `main` всё равно параметр.
- Всё про оборудование: в профилях и `seed/`. OID и SCPI-команды в `main` запрещены, порты R&S тоже.
- Логи на русском или английском, значения секретов никогда.
- Новые зависимости и файлы вне зоны карточки только через `architect`.

## 15. Допущения архитектуры (передать `tech-writer` в реестр)

| ID | Допущение | Как снять |
|---|---|---|
| A-1 | IP устройства только IPv4 | Заказчик сообщит о потребности в IPv6 |
| A-2 | Срок сессии 12 часов простоя (`session.timeout_minutes=720`): смена дежурного не должна быть прервана входом, правится настройкой | Заказчик |
| A-3 | Блокировка после 5 неудач на 15 минут (`auth.lock_attempts=5`, `auth.lock_minutes=15`), правится настройкой; минимальная длина пароля 8 символов | Заказчик |
| A-4 | Трап-тревога вкладывает в статус всегда WARNING, уровень тревоги задаёт правило | Заказчик |
| A-5 | Подлинность трапа определяется по IP источника, community не проверяется | Заказчик или проверка на сети |
| A-6 | Установщик: архив и сценарии PowerShell 5.1, а не отдельный установщик Windows | Человек на Windows |
| A-7 | Журнал действий хранится столько же, сколько журнал событий | Заказчик |
| A-9 | Границы настроек §13.2 (хранение 1..3650 суток, сессия 1..43200 минут, 1..100 попыток, блокировка 1..1440 минут, порт 1..65535) выбраны архитектурой, в ТЗ их нет (T1.07) | Заказчик |
| A-8 | Исключение, проброшенное из `ApiErrorHandler` при начатом ответе, заставляет Tomcat (версия §1) оборвать соединение без завершающего блока chunked, и клиент не принимает обрезанную выгрузку за успешную (§5.1, ревью T1.04 Н-б) | Тест T1.24 `CommittedResponseTest` |
