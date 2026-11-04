# Задача 1. Каркас и база: карточки микрозадач

Фичи: AUTH-01..08, DEV-01..07, PRF-01..06, SEC-01..05, LOG-04. Приёмка ТЗ: устройство создаётся, правится, удаляется, права по ролям работают. Контракты: `docs/architecture.md` (далее «§»). Порядок и параллельность: `docs/plans/backlog.md`.

## Правила для всех карточек

**Запрещено всегда:** править `docs/TZ.md`, `docs/architecture.md`, `CLAUDE.md`, `.claude/`; делать операции git; добавлять зависимости вне T1.01 и T1.27; менять `contract/` после T1.03 и применённые миграции после T1.02; вводить OID, порты и команды оборудования; обращаться к интернету из кода приложения. Нужно изменение вне зоны карточки: остановиться на этом месте, сообщить в отчёте.

**Готово значит:** команда проверки карточки завершается успешно, в отчёте перечислено непроверенное. С T1.03 команда включает `ArchitectureTest`.

**Постраничные ресурсы (с T1.04, круг 1 ревью):** `ORDER BY` строится только из `PageQuery.orderBy(tieBreaker)` с уникальным столбцом (`id`) и подставляется как есть, `LIMIT ?`/`OFFSET ?` параметрами из `size()` и `offset()`. Обязательный тест каждого постраничного эндпоинта: не меньше трёх записей с одинаковым значением поля сортировки, `size=1` (или меньше их числа), обход всех страниц даёт каждую запись ровно один раз; страница за последней 200 с пустым `items` и настоящим `total`.

**Тела запросов и ограничения (с T1.05, повторное ревью T1.04 Н-а, Н-е):** тело `@RequestBody` верхнего уровня всегда объект JSON, не коллекция, не `Map` и не массив (§2.2 п. 5, проверяет `ArchitectureTest`); списки передаются полем объекта. Кросс-параметрическое ограничение Bean Validation на методе контроллера запрещено: Spring 7.0.10 его без нарушения ограничения параметра не применяет (проверено прототипом T1.05, §5.1); совместная проверка параметров пишется кодом через `ApiException.validation(...)`, ошибка уровня запроса с `field: null`. Правки `ApiErrorHandler` после T1.04 делают только карточки, где это прямо записано (T1.05, T1.24).

**Обозначение пути:** `S/` = `server/src/main/java/ru/netmon/`, `T/` = `server/src/test/java/ru/netmon/`, `R/` = `server/src/main/resources/`.

---

## T1.01. Сборка и каркас

**Цель:** многомодульный Maven-проект собирается, запускает пустое приложение на встроенном PostgreSQL в тесте и пересобирается без сети.
**Исполнитель:** backend-core. **Зависит от:** нет.

**Файлы:** `pom.xml`, `server/pom.xml`, `emulators/pom.xml`, `.gitignore`, `scripts/prepare-offline.sh`, `.ps1`, `scripts/offline-verify.sh`, `.ps1`, `S/NetMonitoringApplication.java`, `R/application.yml`, `server/src/test/resources/application-test.yml`, `T/testsupport/TestPostgres.java`, `T/SmokeTest.java`, `emulators/src/main/java/ru/netmon/emulators/EmulatorMain.java`, `emulators/src/test/java/ru/netmon/emulators/EmulatorMainTest.java`, `emulators/src/test/java/ru/netmon/emulators/probe/Main.java`, `emulators/src/test/java/ru/netmon/emulators/voidprobe/Main.java`, `emulators/src/test/java/ru/netmon/emulators/hiddenprobe/Main.java`, `emulators/src/test/java/ru/netmon/emulators/initprobe/Main.java`.

**Контракты:** §1 версии, §2 структура, §3 сборка, §12.1, §12.2, §12.5, §13.

**Шаги:**
0. Во всех `pom.xml` корневой элемент `<project xmlns="http://maven.apache.org/POM/4.0.0">` без `xmlns:xsi` и `xsi:schemaLocation` (§3).
1. Родитель `ru.netmon:nm-parent:0.1.0` (координаты §2): `spring-boot-starter-parent:4.1.2`, `java.version=21` (даёт `maven.compiler.release=21`), свойство `pg.binaries.version=18.6.0` и импорт BOM по §3, `surefire.failIfNoSpecifiedTests=false`, enforcer по §3, модули `emulators`, `server`.
2. `server` (`nm-server`): стартеры и тестовые зависимости из §3, `flyway-database-postgresql`, `postgresql`, `snmp4j:3.9.7` (compile, §3), `embedded-postgres:2.2.2`, `archunit-junit5:1.5.1`, `nm-emulators` в области `test` с исключением `slf4j-simple`. Плагин `spring-boot-maven-plugin`. Сгенерированный пароль Spring Security не выводится (§3).
3. `emulators` (`nm-emulators`): `snmp4j:3.9.7`, `slf4j-simple`; в области `test` `junit-jupiter` и `assertj-core` без версии (BOM, §3); `spring-boot-maven-plugin` с `repackage`, `classifier=exec`, `mainClass=ru.netmon.emulators.EmulatorMain`.
4. `EmulatorMain` по контракту §12.5: `int run(String[])`, первый аргумент вид по шаблону `[a-z]+`, загрузка `ru.netmon.emulators.<вид>.Main` без инициализации (`Class.forName(name, false, loader)`), вызов его `public static int run(String[])` с аргументами без первого, возврат его кода. Код 2 и сообщение в stderr: нет аргумента, недопустимый вид, класс не найден, `LinkageError` при загрузке или связывании, класс не `public`, нет `public static int run(String[])`, `IllegalAccessException`. Код 1: исключение внутри вида, включая `ExceptionInInitializerError` при отложенной инициализации класса вида.
5. `TestPostgres` по §12.2; `SmokeTest`: `@SpringBootTest`, `@ActiveProfiles("test")`, `@ContextConfiguration(initializers = TestPostgres.class)`, `OutputCaptureExtension`.
6. `prepare-offline`: `mvn -B -Dmaven.repo.local=offline/m2 verify`. `offline-verify`: проверяет наличие хотя бы одного `*.jar` в `offline/m2/io/zonky/test/postgres/embedded-postgres-binaries-windows-amd64/<pg.binaries.version>/` (версия читается из корневого `pom.xml`), иначе сообщение в stderr и ненулевой код; затем `mvn -B -o -Dmaven.repo.local=offline/m2 verify`. Скрипты находят корень проекта от своего положения: `.sh` через `CDPATH= cd -- "$(dirname -- "$0")/.." >/dev/null && pwd`; `.ps1` сохраняются в UTF-8 с BOM (иначе Windows PowerShell 5.1 читает кириллицу в ANSI-кодировке), переход в корень через `Push-Location`, возврат `Pop-Location` в `finally` блока `try/finally`.
7. `.gitignore`: `target/` без привязки (на любой глубине); остальные записи привязаны к корню: `/offline/`, `/tools/`, `/data/`, `/server/data/` (рабочий каталог данных при тестах Surefire), `/*.log`, `/server/*.log`. Причина привязки: непривязанные `data/` и `*.log` скрывали пакеты `data` и фикстуры `*.log` в исходниках.

**Обязательные тесты:**
- `SmokeTest`: контекст поднимается, `select version()` начинается с `PostgreSQL 18`; в выводе (`OutputCaptureExtension`) есть `Started SmokeTest in` (положительный контроль: захват вывода работает) и нет `generated security password`.
- `EmulatorMainTest`: неизвестный вид и пустые аргументы дают код 2; разбор имени не выполняет произвольный класс с точками в имени (негативный случай `a.b`, код 2); тестовый вид `probe` (`int run(String[])`) получает аргументы без первого, и его код возвращается; тестовый вид `voidprobe` (`void run(String[])`) даёт код 2; тестовый вид `hiddenprobe` (непубличный класс `Main`) даёт код 2; тестовый вид `initprobe` (статический инициализатор бросает исключение, `public static int run(String[])` корректен) даёт код 1, что одновременно доказывает загрузку без инициализации: при инициализации во время `Class.forName` `ExceptionInInitializerError` (подкласс `LinkageError`) дал бы код 2; проверка через `run`, без `System.exit`.

**Команда проверки:** `scripts/prepare-offline.sh && scripts/offline-verify.sh`

**Критерии готовности:** оба скрипта проходят подряд; `mvn -B verify` с обычным репозиторием зелёный; собраны `server/target/nm-server-0.1.0.jar` и `emulators/target/nm-emulators-0.1.0-exec.jar`. Проверка `.ps1` и сборка на Windows: в отчёте как [проверка человеком].

**Запрещено:** прикладная логика, миграции, контракты; Maven Wrapper.

---

## T1.02. Схема БД

**Цель:** весь DDL раздела §4.2 применяется Flyway, и его ограничения подтверждены тестами.
**Исполнитель:** backend-core. **Зависит от:** T1.01.

**Файлы:** `R/db/migration/V0102__core_schema.sql`, `T/schema/SchemaConstraintsTest.java`, `T/SmokeTest.java` (только шаг 3).

**Контракты:** §4.1, §4.2, §4.3, §12.2.

**Шаги:**
1. Миграция: содержимое блока `sql` §4.2 от первой до последней строки дословно, без добавлений. Проверено architect: применяется Flyway 12.4 на встроенном PostgreSQL 18.6 в режиме `-o`.
2. `SchemaConstraintsTest`: `@SpringBootTest`, `@ActiveProfiles("test")`, `@ContextConfiguration(initializers = TestPostgres.class)`, внедрённый `JdbcClient`; только прямой SQL, без классов приложения, без `@Transactional` (после ошибки ограничения транзакция PostgreSQL прерывается). `TestDb` появится в T1.03, поэтому каждый метод создаёт свои строки с уникальными значениями (IP, логины, имена), не зависит от порядка методов и других классов и не меняет `system_setting`. В негативном случае все поля, кроме проверяемого, допустимы, и строка нарушает ровно одно ограничение.
3. `SmokeTest`: добавить `@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)`, остальное без изменений. Причина: `SchemaConstraintsTest` имеет ту же конфигурацию контекста, Spring берёт контекст из кеша, и `Started SmokeTest in` не выводится (воспроизведено architect: `SmokeTest` падает в `verify`).

**Проверка нарушения:** исключение `DataAccessException`; в цепочке причин `org.postgresql.util.PSQLException`; сверяются `getSQLState()` и `getServerErrorMessage().getConstraint()` с таблицей. Имена ограничений `*_check` присваивает PostgreSQL по дословному DDL §4.2 (получены прогоном architect).

| Случай | SQLSTATE | Ограничение |
|---|---|---|
| Дубль IP действующих | `23505` | `ux_device_ip_active` |
| `ip` `10.0.0.1/24`, IPv6 `::1` | `23514` | `device_ip_check` |
| `lat=90.000001` / `lon=-180.000001` | `23514` | `device_lat_check` / `device_lon_check` |
| Вторая основная проверка | `23505` | `ux_profile_check_primary` |
| Основная с `required=false` (`enabled=true`) | `23514` | `profile_check_check` |
| `kind='HTTP'` | `23514` | `profile_check_kind_check` |
| `interval_sec=4` | `23514` | `profile_check_interval_sec_check` |
| Секрет без привязки / с `device_id` и `group_id` | `23514` | `access_secret_check` |
| Второй секрет устройства | `23505` | `ux_secret_device` |
| Вторая активная тревога `(device_id, reason_key)` | `23505` | `ux_alarm_active` |
| `ended_at` без `close_reason` | `23514` | `alarm_check` |
| `ALARM` без `level` (с `pair_key`) | `23514` | `trap_rule_check` |
| `CLOSE` без `pair_key` | `23514` | `trap_rule_check1` |
| `trap_oid='1.3.6.a'` (`action='EVENT'`) | `23514` | `trap_rule_trap_oid_check` |
| `admin` при существующем `Admin` | `23505` | `ux_app_user_login` |
| Действующее устройство без профиля | `23514` | `device_check` |

**Обязательные тесты (каждая строка отдельным методом):**
- `tablesAndDefaultPartition`: множество `relname` из `pg_class` схемы `public` с `relkind in ('r','p')` без `flyway_schema_history` в точности равно: `access_secret`, `alarm`, `app_user`, `audit_log`, `check_result`, `check_result_default`, `check_state`, `device`, `device_group`, `device_type`, `event`, `profile`, `profile_check`, `system_setting`, `trap_rule`; `check_result` имеет `relkind='p'`; вставленная строка `check_result` с `ts=now()` имеет `tableoid::regclass::text = 'check_result_default'`.
- `deviceIpUniqueAmongActive`: дубль IP действующего отклоняется; после мягкого удаления первого (`UPDATE device SET deleted_at=now(), profile_id=NULL`) тот же IP проходит; мягкое удаление второго при уже удалённом первом с тем же IP проходит; третий действующий с тем же IP проходит.
- `deviceIpAndCoordinates`: `ip` `10.0.0.1/24` и `::1` отклоняются; `lat=90.000001`, `lon=-180.000001` отклоняются; `lat=90`, `lon=-180` проходят.
- `profileCheckConstraints`: вторая основная, основная с `required=false`, `kind='HTTP'`, `interval_sec=4` отклоняются.
- `accessSecretBinding`: без привязки и с двумя привязками отклоняются; первый секрет устройства проходит, второй отклоняется.
- `alarmActiveUniqueAndClose`: вторая активная с тем же ключом отклоняется; после `UPDATE alarm SET ended_at=now(), close_reason='RECOVERED'` первой новая проходит; `ended_at` без `close_reason` отклоняется.
- `trapRuleConstraints`: три случая `trap_rule` из таблицы отклоняются.
- `loginUniqueIgnoringCase`: `Admin` проходит, `admin` отклоняется.
- `deviceProfileRequiredUnlessDeleted`: действующее без профиля отклоняется; с `deleted_at=now()` без профиля проходит.
- `defaultSettings`: пары `key -> value` из `system_setting` в точности равны шести строкам `INSERT` §4.2 (`retention.results_days=30` и `retention.journal_days=365` по разделу 6 ТЗ, `traps.udp_port=162` по SNMP-08; `session.timeout_minutes=720`, `auth.lock_attempts=5`, `auth.lock_minutes=15` в ТЗ не заданы, это допущения A-2 и A-3 архитектуры, источники по ключам в §13.2). Тест сверяет значения миграции, а не требования ТЗ.

**Команда проверки:** `mvn -B -pl server -am test -Dtest=SchemaConstraintsTest && scripts/offline-verify.sh`

**Критерии готовности:** все методы зелёные; `scripts/offline-verify.sh` зелёный вместе с `SmokeTest` и `EmulatorMainTest`; миграция построчно равна блоку §4.2 (расхождение только с разрешения `architect`).

**Запрещено:** менять §4.2 самостоятельно; прикладные классы; индексы сверх §4.2; иные изменения `SmokeTest`, кроме шага 3.

---

## T1.03. Контракты, общие типы и тестовые помощники

**Цель:** все межмодульные типы и интерфейсы §7 существуют и защищены тестом границ, чтобы дальше исполнители работали параллельно.
**Исполнитель:** backend-core. **Зависит от:** T1.02.

**Файлы:** `S/contract/*.java` (46 файлов, по одному на тип верхнего уровня по правилам переноса §7.1, без логики; это исключение правила о числе файлов), `S/contract/events/*.java` (5 файлов, §7.2), `S/stream/SeqStreamPublisher.java`, `S/common/ClockConfig.java`, `T/testsupport/` (`IntegrationTest`, `TestDb`, `TestData`, `MutableClock`, `Api`, `LogCapture`, `RestartableApp`, `TestDbSelfTest`; правка `TestPostgres` по шагу 6), `server/src/test/resources/logback-test.xml` (§12.2, `LogCapture`), `T/ArchitectureTest.java`, `T/contract/ContractTypesTest.java`, `T/schema/SchemaConstraintsTest.java` (только шаг 7).

**Контракты:** §2.2, §7.1, §7.2, §12.2, §14.

**Шаги:**
1. Перенести сигнатуры §7.1 и §7.2 (интерфейсы и события) без изменений, по правилам переноса §7.1. `ClockConfig` по §12.2 («Решения по помощникам»).
2. Для `SnmpCredentials.V2c` и `V3` переопределить `toString`, значения всех полей (у `V3` все пять, включая `user` и протоколы) заменяются на `***`, формат по правилам переноса §7.1.
3. `SeqStreamPublisher`: возрастающий `seq`, `publish` ничего не отправляет. Реализует `StreamPublisher`.
4. `ArchitectureTest` с правилами §2.2 пунктов 1–4 в формулировке §2.2 («Формулировка в `ArchitectureTest`»): пункты 1–3 как `@ArchTest`, пункт 4 как `@Test` в том же классе. Оба правила пункта 3 с `allowEmptyShould(true)`: контроллеров пока нет.
5. Помощники по таблице и решениям §12.2, включая `logback-test.xml`. `Api` использует `SecurityMockMvcRequestPostProcessors.user(...)` и `csrf()`. `RestartableApp` запускает реальный Tomcat на случайном порту, свойства передаёт аргументами командной строки.
6. В `T/testsupport/TestPostgres.java` добавить `public static Map<String,String> properties()` (пока только `spring.datasource.url/username/password`); `initialize` и `RestartableApp` передают свойства только из него (§12.2, «Каталог данных в тестах»). Других изменений `TestPostgres` нет.
7. `SchemaConstraintsTest` (замечания ревью T1.02), других изменений нет:
   1. Заменить `@SpringBootTest`, `@ActiveProfiles`, `@ContextConfiguration` на `@IntegrationTest`: очистка `TestDb` перед каждым методом убирает зависимость от данных и настроек других классов. Запрет `@Transactional` сохраняется.
   2. `defaultSettings`: читать `key, value` из `system_setting where key in (...)` по шести ключам §4.2 и сверять эти шесть пар; не сравнивать всю таблицу. Причина: набор ключей расширяется только миграциями, а сверка всей таблицы ломается от лишней строки, не относящейся к тесту.
   3. `tablesAndDefaultPartition`: `relkind` читать по `'public.check_result'::regclass` (`select relkind::text from pg_class where oid = 'public.check_result'::regclass`), без поиска по `relname` без схемы.
   4. `tablesAndDefaultPartition`: вставленную строку `check_result` удалить в `finally` того же метода (`delete from check_result where device_id=? and check_id=?`). Число удалённых строк сохранить в `finally` и сверять с 1 после блока `try/finally`, а не в `finally`: иначе провал проверки перекроет исходную ошибку `INSERT`. Причина: по POLL-09 строка в `check_result_default` означает сбой, тесты разделов и здоровья задачи 3 не должны её видеть.
   5. Проверка нарушения: перед `getServerErrorMessage().getConstraint()` утверждать `getServerErrorMessage()` не `null` с сообщением, содержащим SQL; так вместо NPE тест падает с понятной причиной.
   6. Каждый `update` подготовки (мягкое удаление устройства, закрытие тревоги и другие `UPDATE`) сверять с числом строк 1: `assertThat(...update()).isEqualTo(1)`.

**Обязательные тесты:**
- `ContractTypesTest`: порядок `DeviceStatus` = худший первым; `toString` секретных типов не содержит значения; `equals` и логирование через `String.valueOf` также не раскрывают значение.
- `SchemaConstraintsTest`: зелёный после шага 7; в отчёте показать, что метод с `UPDATE` по несуществующему `id` падает на проверке числа строк, и удалить его.
- `ArchitectureTest`: зелёный; в отчёте показать, что временное нарушение (класс `contract`, импортирующий Spring) ловится, и удалить его.
- `T/testsupport/TestDbSelfTest.java` (`@IntegrationTest`): очистка удаляет данные, в том числе строку `check_result_default` и пользователя, на которого ссылается `system_setting.updated_by`, и возвращает настройки к умолчанию (шесть пар после восстановления равны строкам `INSERT` §4.2, лишние ключи удаляются; то же для `restoreSettings()`); внедрённый `Clock` есть `MutableClock`, `advance` работает; `RestartableApp` слушает случайный порт (не 8080), после `restart()` видит те же данные, а `LogCapture` видит строку, записанную после `restart()` и после остановки.

**Команда проверки:** `mvn -B -pl server -am test -Dtest='ContractTypesTest,ArchitectureTest,TestDbSelfTest,SchemaConstraintsTest,SmokeTest'`

**Критерии готовности:** тесты зелёные; все интерфейсы таблицы §7.2 присутствуют; логики в `contract` нет.

**Запрещено:** добавлять поля и методы сверх §7; реализовывать интерфейсы (кроме заглушки); менять в `SchemaConstraintsTest` что-либо сверх шага 7 (случаи, SQL вставок, таблицу ограничений T1.02).

**Круг 2 (аудит и ревью T1.03), только `T/testsupport/` и `TestDbSelfTest`; `contract`, `ArchitectureTest`, `SchemaConstraintsTest`, `TestPostgres`, `TestData`, `Api`, `IntegrationTest` не меняются:**
1. `TestDb` по новой строке таблицы §12.2: снимок настроек на JVM вместо шести строк в коде, `defaults()`, транзакция с `set local lock_timeout = '5s'` в `clean()` и `restoreSettings()`, параметризованная вставка; `CleanBeforeEach` дополнительно вызывает `MutableClock.reset()`.
2. `MutableClock`: `reset()`, `synchronized` у `set`, `withZone` возвращает представление (§12.2).
3. `LogCapture`: `setLength(0)` в `beforeEach` вместо отметки, `level(...)` с возвратом уровней в `afterEach` (§12.2).
4. `RestartableApp.start()`: `IllegalStateException`, если контекст уже запущен.
5. `TestDbSelfTest`: `@AfterEach` с `db.restoreSettings()`; в `restoreSettingsOnly` `dirty()` внутри `try`, число удалённых строк `check_result_default` сохраняется в `finally` и сверяется с 1 после `try/finally` (как шаг 7.4); проверки настроек после `clean()`/`restoreSettings()` идут против `db.defaults()` плюс «содержит шесть пар §4.2» и «нет `extra`»; новые проверки: `reset()`, `withZone`, повторный `start()`, `LogCapture.level`, `lock_timeout` (чужое соединение держит `lock table device_type in access share mode`, `clean()` падает за время меньше 30 с с корневой `SQLException` SQLState `55P03`, соединение откатывается до конца метода).

---

## T1.04. Ошибки API и постраничность

**Цель:** все ошибки и постраничные ответы API имеют единый формат без утечки внутренностей (основа DEV-03 «понятное сообщение» и DEV-05 «постраничный вывод, сортировка»).
**Исполнитель:** backend-core. **Зависит от:** T1.03.

**Файлы:** `S/common/web/FieldError.java`, `ApiException.java`, `ApiErrorHandler.java`, `PageQuery.java`, `PageResponse.java`; `T/common/ApiErrorsTest.java`, `T/common/PageQueryTest.java`, `T/common/ApiErrorsQaTest.java` (независимая проверка QA); `server/src/test/java/nmtest/common/ErrorsTestController.java` (тестовый контроллер вне `ru.netmon`, §12.2 «Тестовые контроллеры и бины»); по шагам «Тестовые помощники»: `T/testsupport/TestDb.java`, `T/testsupport/MutableClock.java`, `T/testsupport/TestDbSelfTest.java`.

**Контракты:** §5.1 (в том числе «Реализация в `ru.netmon.common.web`»), §2.2, §12.2, §14.

**Шаги:**
1. `FieldError`, `ApiException`, `PageQuery`, `PageResponse` по §5.1 буквально (сигнатуры, коды, тексты сообщений).
2. `ApiErrorHandler` по таблице §5.1 (с круга 1 ревью: разбор `ParameterErrors`, цепочка причин исключений безопасности, начатый ответ, `AsyncRequestNotUsableException`, `AsyncRequestTimeoutException`, постоянная таблица кодов 4xx, `WARN` у `DATA_CONFLICT`; порядок проверок по абзацу под таблицей): по методу `@ExceptionHandler` на каждую строку, кроме двух последних; последние две и проброс `AccessDeniedException`/`AuthenticationException` в одном методе `@ExceptionHandler(Exception.class)` (`throws Exception`). Тело собирается в `LinkedHashMap` (`code`, `message`, `fields`, затем `details`), ответ `ResponseEntity` с `MediaType.APPLICATION_JSON`.
3. `ErrorsTestController` (`@RestController`, `@RequestMapping("/api/test-errors")`, каждый метод с `@PreAuthorize("permitAll()")`), конечные точки:
   - `POST /body`: `@Valid @RequestBody Body` (вложенный `record Body(@NotBlank(message = "Название обязательно") String name, @Min(value = 1, message = "Не меньше 1") int n)`), возвращает тело;
   - `GET /typed?n=`: `@RequestParam long n`;
   - `GET /checked?n=`: `@RequestParam @Min(value = 1, message = "Не меньше 1") int n` (встроенная проверка метода Spring MVC, `HandlerMethodValidationException`);
   - `GET /not-found`: `throw ApiException.notFound()`;
   - `GET /conflict`: `ApiException` 409 `PROFILE_IN_USE` «Профиль используется» с `details` `{"devices":[{"id":1,"name":"d"}]}`;
   - `GET /db`: `throw new DataIntegrityViolationException(...)` с текстом, содержащим маркер `ux_marker_db` и слово `insert`;
   - `GET /boom?m=`: `throw new IllegalStateException(m)`;
   - `GET /denied`: `throw new AccessDeniedException("denied")`;
   - `GET /page?page=&size=&sort=`: три параметра `@RequestParam(required = false) String`, `PageQuery.parse(page, size, sort, Map.of("name", "name", "createdAt", "created_at"), "name,asc")`, ответ `PageResponse.of(List.of(q.orderBy("id")), q, 0)`;
   - (круг 1 ревью) `POST /mixed/{id}`: `@PathVariable @Min(value = 1, message = "Не меньше 1") long id`, `@Valid @RequestBody Mixed m`, где `record Mixed(@NotBlank(message = "Название обязательно") String name, @Valid List<Item> checks)`, `record Item(@Min(value = 1, message = "Не меньше 1") int port)`; возвращает `m`. Ограничение на параметре включает проверку метода, и тело приходит в `HandlerMethodValidationException`;
   - `GET /denied-wrapped`: `throw new RuntimeException(new AccessDeniedException("denied"))`;
   - `GET /status?s=`: `@RequestParam int s`, `throw new ResponseStatusException(HttpStatusCode.valueOf(s))`;
   - `GET /fatal`: `throw new StackOverflowError("fatal")` (настоящее переполнение не вызывать);
   - `GET /gone`: `throw new AsyncRequestNotUsableException("gone")`;
   - `GET /async-timeout`: `throw new AsyncRequestTimeoutException()`;
   - `GET /committed?m=`: параметр `HttpServletResponse r`; `r.setContentType("text/event-stream")`, `r.getWriter().write("data: x\n\n")`, `r.flushBuffer()`, затем `throw new IllegalStateException(m)`;
   - `GET /committed-abort`: то же начало ответа, затем `throw new UncheckedIOException(new IOException("abort"))`.
4. `ApiErrorsTest`: `@IntegrationTest` и `@Import(ErrorsTestController.class)` (сливается с `@Import` мета-аннотации, проверено прототипом); `LogCapture` через `@RegisterExtension`; все запросы через `Api` (`api.as(Role.OPERATOR)`), кроме запроса 415 в `springErrors`, без своих свойств и `@MockitoBean`. Тело читать `getContentAsString(StandardCharsets.UTF_8)` или `jsonPath`: без `charset` в ответе `getContentAsString()` декодирует ISO-8859-1 и портит русский текст.

**Безопасность до T1.05.** `SecurityConfig` нет, действует цепочка Spring Boot по умолчанию (`anyRequest().authenticated()`, CSRF включён; пользователь по умолчанию исключён в `NetMonitoringApplication`). Без пользователя ответ 401 с пустым телом, `POST` без CSRF 403 с пустым телом (проверено прототипом). `Api` ставит `user(...)` и `csrf()`, поэтому запросы теста доходят до контроллера. `@EnableMethodSecurity` не вводится (это `SecurityConfig` T1.05): до T1.05 `@PreAuthorize` не действует, `permitAll()` в тестовом контроллере нужен, чтобы тест не сломался после T1.05. `ArchitectureTest` п. 3 тестовый контроллер не проверяет (импорт только `ru.netmon` без тестов), `ApiErrorHandler` не мета-аннотирован `@Controller`; изменений `ArchitectureTest` нет. Тесты 401/403/CSRF в T1.04 не пишутся, кроме `accessDeniedGoesToSecurity`.

**Тестовые помощники (замечания повторного ревью T1.03), только перечисленные файлы `T/testsupport/`; `LogCapture`, `RestartableApp`, `TestPostgres`, `TestData`, `Api`, `IntegrationTest` не меняются:**
1. `TestDbSelfTest.cleanFailsFastOnLockedTable`: `db.clean()` выполняется в отдельном потоке (`Executors.newSingleThreadExecutor()`, `submit`), основной поток ждёт `future.get(30, SECONDS)` и утверждает `ExecutionException` (`assertThrows`); корень цепочки причин `SQLException` с SQLState `55P03`. При неработающем `lock_timeout` `get` бросает `TimeoutException`, тест падает, а `finally` основного потока откатывает и закрывает чужое соединение, блокировка снимается, `clean()` в потоке завершается. В `finally` после этого `shutdown()` и `awaitTermination(30, SECONDS)`, при `false` `IllegalStateException`. Причина отказа от `@Timeout`: поток, ждущий ответа PostgreSQL в сокете, прерыванием не освобождается; с `SAME_THREAD` сборка висит, с `SEPARATE_THREAD` тест падает, но блокировку никто не снимает и `@AfterEach` (`restoreSettings()`) висит на ней (проверено прототипом с `lock_timeout = 0`).
2. Там же: `rollback()` чужого соединения выполняется только если `setAutoCommit(false)` завершился успешно (флаг после вызова); `close()` всегда; исключение `rollback()`/`close()` при уже летящем исключении теста добавляется к нему через `addSuppressed`, а не заменяет его (исходное запоминается в `catch (Throwable t) { primary = t; throw t; }`), без летящего исключения бросается само.
3. `MutableClock.withZone(z)`: `this` только при `ZoneOffset.UTC.equals(z)`, без `normalized()`; у `Zoned` `equals` по паре (тот же `MutableClock` по `==`, `zone.equals`), `hashCode` из `System.identityHashCode(base)` и `zone.hashCode()` (§12.2). Новый метод `TestDbSelfTest.clockZoneEquality`: `withZone(ZoneId.of("UTC")).getZone()` равно `ZoneId.of("UTC")` и не равно `ZoneOffset.UTC`; два вызова `withZone` с одной зоной равны и имеют равный `hashCode`; представления с `UTC` и `Europe/Moscow` не равны; `withZone(ZoneOffset.UTC)` есть тот же объект.
4. `TestDb`: пакетный метод `List<String> truncateTargets()` с запросом `select format('public.%I', relname) from pg_class where …` (прежние условия, `order by relname`); `clean()` вызывает его внутри своей транзакции вместо `quote_ident(relname)`. Новый метод `TestDbSelfTest.truncateTargetsQualified`: каждое имя начинается с `public.`; есть `public.check_result` и `public.system_setting`; нет `public.check_result_default` и `public.flyway_schema_history`.
5. `LogCapture`: код не меняется; ограничения записаны в §12.2, `ApiErrorsTest` проверяет только свои уникальные строки.
6. Напоминание (снимок настроек держится на соглашении §12.2): ни `RestartableApp`, ни тест вне `@IntegrationTest` не меняют `system_setting` до первого `@IntegrationTest` в JVM. `ApiErrorsTest` настроек не меняет. Карточки, вводящие изменение настроек (T1.07 и далее), помечают такие тесты `@IntegrationTest` и вызывают `restoreSettings()` в `@AfterEach`.
7. (круг 1 ревью) `cleanFailsFastOnLockedTable`, блок `finally`: два независимых шага очистки, каждый в своём `try`/`catch (Throwable)`: (а) `rollback()` (при флаге) с `close()` в вложенном `finally`; (б) `pool.shutdown()` и `awaitTermination(30, SECONDS)`, при `false` `IllegalStateException`. Шаг (б) выполняется и при исключении шага (а). Первое исключение очистки запоминается, следующее добавляется к нему `addSuppressed`; затем при летящем исключении теста очистка добавляется к нему `addSuppressed`, иначе бросается. Сигнатура метода может стать `throws Throwable`.

**Обязательные тесты (имена методов обязательны):**
- `ApiErrorsTest.notFoundFormat`: `/not-found` 404, тело ровно `{"code":"NOT_FOUND","message":"Объект не найден","fields":[]}`.
- `ApiErrorsTest.conflictWithDetails`: `/conflict` 409, `code`, `message`, `fields` пустой, `devices[0].id` = 1.
- `ApiErrorsTest.validationFields`: `POST /body` с `{"name":"","n":0}` 400 `VALIDATION_FAILED`, `fields` содержит `name` «Название обязательно» и `n` «Не меньше 1»; `GET /checked?n=0` 400 `VALIDATION_FAILED` с полем `n`.
- `ApiErrorsTest.mixedMethodValidationFieldPaths` (круг 1): `POST /mixed/0` с `{"name":"","checks":[{"port":1},{"port":0}]}` 400 `VALIDATION_FAILED`, `fields` ровно три элемента в любом порядке: `id`, `name` «Название обязательно», `checks[1].port` «Не меньше 1»; поля `m` нет. `POST /mixed/5` с тем же телом: два поля `name` и `checks[1].port`.
- `ApiErrorsTest.badJson`: `POST /body` с `{"name":` и с `{"name":"x","n":"abc"}` 400 `BAD_JSON`; тело без `Exception`, `jackson`, `java.`, `ru.netmon`.
- `ApiErrorsTest.typeMismatch`: `/typed?n=abc` 400 `BAD_PARAMETER`, поле `n`; тело без `NumberFormat`, `java.` и `abc`.
- `ApiErrorsTest.springErrors`: `GET /api/test-errors/no-such-path` 404 `NOT_FOUND`; `POST /typed` 405 `METHOD_NOT_ALLOWED`; `POST /body` с `Content-Type: text/plain` 415 `UNSUPPORTED_MEDIA_TYPE`; все с `fields` `[]`. `Api` шлёт только JSON, поэтому запрос 415 идёт через бин `MockMvc` напрямую с `user(...)` и `csrf()`; `Api` не меняется.
- `ApiErrorsTest.stableStatusCodes` (круг 1): `/status?s=413` 413 `PAYLOAD_TOO_LARGE`, `s=422` 422 `HTTP_422`, `s=418` 418 `HTTP_418`, `s=406` 406 `NOT_ACCEPTABLE`, `s=503` 500 `INTERNAL_ERROR`.
- `ApiErrorsTest.dataIntegrity`: `/db` 409 `DATA_CONFLICT`; тело без `ux_marker_db` и `insert`; `logs.text()` содержит `Нарушение ограничения БД` и `/api/test-errors/db`, не содержит `ux_marker_db`.
- `ApiErrorsTest.unexpected500`: `/boom?m=<маркер 1>&q=<маркер 2>` (оба уникальны, `q` контроллер не читает) 500 `INTERNAL_ERROR` «Внутренняя ошибка сервера»; тело без обоих маркеров, `IllegalState`, `Exception`, `at `; `logs.text()` содержит маркер 1 и `/api/test-errors/boom`, не содержит маркер 2 (строка запроса в лог не попадает).
- `ApiErrorsTest.fatalErrorIs500` (круг 1): `/fatal` 500, тело ровно `{"code":"INTERNAL_ERROR","message":"Внутренняя ошибка сервера","fields":[]}`.
- `ApiErrorsTest.accessDeniedGoesToSecurity`: `/denied` и `/denied-wrapped` 403 (ответ фильтра безопасности), тело не содержит `INTERNAL_ERROR`.
- `ApiErrorsTest.clientGoneAndCommitted` (круг 1; `DEBUG` при уровнях `logback-test.xml` не пишется, поэтому «нет в логе» проверяется по пути): `/gone` тело ответа пустое, `logs.text()` не содержит `/api/test-errors/gone`; `/async-timeout` 503 `SERVICE_UNAVAILABLE` «Сервис временно недоступен», `fields` `[]`, лог не содержит `/api/test-errors/async-timeout`; `/committed?m=<маркер>` тело ответа ровно `data: x\n\n` (JSON не дописан), `logs.text()` содержит маркер; `/committed-abort` тело ровно `data: x\n\n`, лог не содержит `/api/test-errors/committed-abort`.
- `ApiErrorsTest.pageLimits`: `size` `0`, `501`, `-1`, `abc`, пустой и `page=-1`, `page=abc` дают 400 `VALIDATION_FAILED` с полем `size` или `page`; `size=500` 200 и `"size":500`; без параметров 200 `{"items":["name asc, id asc"],"page":0,"size":50,"total":0}`; `page=2147483647&size=500` 200.
- `ApiErrorsTest.sortWhitelist`: `sort=foo`, `sort=name;drop table device`, `sort=name,ASC`, `sort=name,asc,x`, `sort=name&sort=createdAt` дают 400 с полем `sort`; `sort=createdAt,desc` даёт `items[0]` = `created_at desc, id desc`, `sort=name` даёт `name asc, id asc`.
- `PageQueryTest` (без Spring): умолчания; `orderBy("id")` = `name asc, id asc` и `created_at desc, id desc`; `orderBy("name")` при сортировке по `name` = `name asc` (без повтора); `PageQuery.class.getConstructors()` пуст, класс `final` и не `record` (метод `noPublicConstructor`); `offset()` при `page=Integer.MAX_VALUE`, `size=500` равен `2147483647L * 500` без переполнения; `page=-1&size=0&sort=foo` дают одну `ApiException` с тремя полями; `+5` и ` 5` неверны; неверный `defaultSort` даёт `IllegalArgumentException` и при заданном верном `sort` (`defaultSort` проверяется всегда, §5.1); `null` в `defaultSort` не тестируется (§5.1); `ApiException` с ключом `code` в `details` даёт `IllegalArgumentException`.
- `ApiErrorsQaTest` (добавлен qa-verifier как независимая проверка; тот же `@IntegrationTest` и `@Import(ErrorsTestController.class)`, без изменений тестового контроллера). Закрывает то, чего нет в `ApiErrorsTest`:
  - `literalMessagesAndContentType`: буквальные тела по таблице §5.1: `VALIDATION_FAILED` «Проверьте введённые данные» для `POST /body` и `GET /checked` (начало тела, `fields` массив); целиком `BAD_JSON`, `BAD_PARAMETER` (с полем `n` «Неверное значение»), `DATA_CONFLICT`, `INTERNAL_ERROR`, Spring 404 `NOT_FOUND` и 405 `METHOD_NOT_ALLOWED` с «Запрос не может быть выполнен»; порядок `code`, `message`, `fields`, затем `devices` у `PROFILE_IN_USE`; `Content-Type` начинается с `application/json`.
  - `missingBodyAndMissingParam`: `POST /body` без тела 400 `BAD_JSON`; `GET /typed` без `n` 400 `BAD_REQUEST` (строка `ErrorResponse` 4xx) с пустым `fields`.
  - `leakFreeValidation`: `sort=<маркер>` 400 `VALIDATION_FAILED` «Проверьте параметры запроса», поле «Недопустимая сортировка»; в теле нет маркера, выражения SQL `created_at`, неверного направления `bad`, значения `foo`; `page=-1&size=0&sort=foo` даёт три поля.
  - `strictParsingOverHttp`: через HTTP 400 для `+5` и ` 5` в `page`/`size`, пустых `page`, `size`, `sort`, `name,`, `,asc`, `Name`, `name,desc,`, `name ` (с пробелом), `0500x`, `page=99999999999`, повторов `sort` и `page`; `sort=name,asc` 200.
  - `pageBeyondLastIsOk` (переименован в круге 1 из `pageBeyondLastIsOkAndEmptyItemsContract`: имя обещало проверку `items`, которой нет): `page=1000&size=10` 200, `page` 1000, `size` 10, `total` 0 (содержимое `items` не проверяет: тестовый контроллер возвращает `orderBy("id")`; пустой `items` за последней страницей проверяют ресурсы, «Правила для всех карточек»).
- `TestDbSelfTest`: зелёный с проверками шагов помощников 1–4 и 7 (`cleanFailsFastOnLockedTable`, `clockZoneEquality`, `truncateTargetsQualified`); в отчёте показать, что при временно выключенном `lock_timeout` (`set local lock_timeout = 0` в `clean()`) метод `cleanFailsFastOnLockedTable` падает с `TimeoutException` примерно за 30 с и прогон завершается, затем вернуть `'5s'`.

**Команда проверки:** `mvn -B -pl server -am test -Dtest='ApiErrorsTest,ApiErrorsQaTest,PageQueryTest,ArchitectureTest,TestDbSelfTest,SchemaConstraintsTest'`

**Критерии готовности:** всё зелёное, тело ошибки соответствует примеру и таблице §5.1.

**Запрещено:** логика конкретных ресурсов; `SecurityConfig` и `@EnableMethodSecurity` (T1.05); изменения `ArchitectureTest`; `ErrorController` для `/error` (T1.05).

---

## T1.05. Вход, выход и защита по умолчанию

**Цель:** без сессии недоступно ничего, кроме страницы входа; пользователь входит по логину и паролю из БД с BCrypt.
**Исполнитель:** backend-core. **Зависит от:** T1.04. **Фичи:** AUTH-01, AUTH-02, AUTH-07 (выход), SEC-05 (CSRF).

**Файлы:**
- Новые в `main`: `S/auth/SecurityConfig.java`, `S/auth/AppUserRepository.java`, `S/auth/AppUserDetailsService.java`, `S/auth/JsonAuthHandlers.java`, `S/auth/AuthController.java` (`GET /api/auth/csrf`), `S/auth/MeController.java` (`GET /api/me`), `S/common/security/CurrentUser.java`, `S/common/web/ApiErrorController.java`.
- Правки в `main`: `R/application.yml` (только три свойства `server.servlet.session.*` из §5.1), `S/common/web/ApiErrorHandler.java` (только шаги 6–7).
- Тесты: новые `T/auth/LoginTest.java`, `T/auth/CsrfTest.java`, `T/common/ErrorPageTest.java`, `server/src/test/java/nmtest/common/FailingFilter.java`, `nmtest/common/ErrorsExtraTestController.java`, `nmtest/common/OrderedRange.java` (аннотация с вложенным `Validator`), `nmtest/arch/ListBodyController.java`, `server/src/test/resources/security-matrix/auth.csv`; правки `T/testsupport/Api.java` (§12.2), `T/ArchitectureTest.java`, `T/common/ApiErrorsTest.java`.
- Не правятся: `NetMonitoringApplication.java`, `ErrorsTestController.java`, `ApiErrorsQaTest.java`, `SmokeTest.java`, прочие помощники `T/testsupport/`. `R/static/login.html` не создаётся, его делает T1.28.

**Контракты:** §5.1 (в том числе блок «Безопасность»), §5.2 (auth, me), §2.2 п. 3 и п. 5, §12.2 (`Api`), §12.3.

**Шаги:**
1. `SecurityConfig`, `JsonAuthHandlers` и cookie сессии строго по блоку «Безопасность» §5.1 (цепочка, коды и тексты ответов, запись JSON байтами, обновление `XSRF-TOKEN` после входа).
2. `CurrentUser` по §5.1. `AppUserRepository.findByLogin(String)` (`JdbcClient`, одна выборка `id, login, role, active, password_hash`); `AppUserDetailsService` отдаёт `CurrentUser` или бросает `UsernameNotFoundException`. Проверка `active` через `isEnabled()`.
3. `AuthController.csrf` (`@PreAuthorize("permitAll()")`, 204) и `MeController.me` (`hasAnyRole('OPERATOR','ADMIN')`, `@AuthenticationPrincipal CurrentUser`, тело `{"id","login","role"}` в этом порядке).
4. `Api` по §12.2: principal `CurrentUser` вместо `user(...)`, `csrf()` остаётся.
5. `auth.csv`: заголовок `метод,путь,OPERATOR,ADMIN,ANON` и ровно две строки `GET,/api/auth/csrf,allow,allow,public` и `GET,/api/me,allow,allow,deny` (§12.3: вход и выход в матрицу не входят).
6. (ревью T1.04, Н4) `ApiErrorController` по абзацу §5.1 под таблицей. При `response.isCommitted()` тело и лог не пишутся (`return null`). Свойства `server.error.*` не задаются. В `ArchitectureTest` оба правила п. 3 получают отбор этого класса по §2.2.
7. (повторное ревью T1.04, Н-а, Н-д, Н-е, Н-ж) `ApiErrorHandler`: (а) правило §2.2 п. 5 в `ArchitectureTest` полем `@ArchTest static final ArchRule objectBody`; (б) для `ErrorResponse` 4xx заголовки `getHeaders()` копируются в ответ, кроме `Content-Type` и `Content-Length` (в Spring 7 `HttpHeaders` не `Map`: обход `forEach((name, values) -> …)`, сравнение имён без учёта регистра); (в) `getCrossParameterValidationResults()` дают `field=null` и `getDefaultMessage()` после ошибок параметров. Остальное поведение `ApiErrorHandler` не меняется.

**Обязательные тесты** (тело читать `getContentAsString(StandardCharsets.UTF_8)`; пользователи с паролем вставляются в тесте через `JdbcClient` и бин `PasswordEncoder`, `TestData` не меняется):
- `LoginTest` (`@IntegrationTest`, `MockMvc`; вход формой с cookie и заголовком из `GET /api/auth/csrf`):
  - `anonymousIsRejected` (AUTH-01): `GET /api/me` и `GET /api/no-such` без сессии дают 401 и тело ровно `{"code":"AUTH_REQUIRED","message":"Требуется вход в систему","fields":[]}`; `GET /index.html` даёт 302, `Location` оканчивается на `/login.html`; `GET /login.html` и `GET /login/x.js` не 401 и не 302 (404 допустим, файлов ещё нет); после анонимного запроса `getRequest().getSession(false)` равен `null`.
  - `loginReturnsUser`: вход логином в другом регистре даёт 200 и тело ровно `{"login":<как в БД>,"role":"OPERATOR"}`; с полученной сессией `GET /api/me` даёт `{"id":…,"login":…,"role":…}`.
  - `loginFailuresAreUniform`: неверный пароль, неизвестный логин, отключённый пользователь (`active=false`, верный пароль), пустые логин и пароль, логин 1000 символов: каждый 401 с телом ровно `{"code":"AUTH_FAILED","message":"Неверный логин или пароль","fields":[]}`, без 500.
  - `passwordStoredAsBcrypt` (AUTH-02): `password_hash` своей строки начинается с `$2a$10$`; `select count(*) from app_user t where position(? in t::text) > 0` с открытым паролем параметром даёт 0.
  - `logoutEndsSession` (AUTH-07): `POST /api/auth/logout` с сессией и токеном даёт 204, затем та же сессия на `GET /api/me` даёт 401.
  - `sessionIdChangesOnLogin` (`RestartableApp` и `java.net.http.HttpClient`, cookie передаются заголовком `Cookie` вручную): `Set-Cookie` сессии после входа содержит `HttpOnly` и `SameSite=Lax`; повторный вход с этой сессией выдаёт другой `JSESSIONID`, прежний на `GET /api/me` даёт 401. `MockMvc` смену идентификатора не воспроизводит (`MockHttpSession` сохраняет id, проверено прототипом), поэтому только Tomcat.
- `CsrfTest` (`@IntegrationTest`, `MockMvc`, SEC-05):
  - `loginWithoutHeaderIs403`: вход с cookie `XSRF-TOKEN` без заголовка даёт 403 и тело ровно `{"code":"CSRF_INVALID","message":"Недействительный токен защиты, обновите страницу","fields":[]}`.
  - `foreignTokenIs403`: cookie из одного `GET /api/auth/csrf`, заголовок из другого: 403 `CSRF_INVALID`; с совпадающими: 200.
  - `loginRenewsToken`: в ответе входа последний `Set-Cookie` `XSRF-TOKEN` непустой и отличается от использованного; с ним `POST /api/auth/logout` даёт 204.
  - `logoutWithoutTokenIs403`: с сессией без заголовка 403 `CSRF_INVALID`, сессия жива (`GET /api/me` 200).
- `ErrorPageTest` (`@IntegrationTest`, Н4 ревью T1.04). `MockMvc` не переадресует на `/error`, а прямой запрос `/error` без сессии даёт 302, поэтому:
  - `errorAttributesToApiFormat`: `GET /error` с `user(...)` и атрибутами `jakarta.servlet.error.status_code` = 500, `jakarta.servlet.error.exception` = `IllegalStateException(<маркер>)`, `jakarta.servlet.error.request_uri`: 500 и тело ровно `{"code":"INTERNAL_ERROR","message":"Внутренняя ошибка сервера","fields":[]}`, маркер в `LogCapture` есть, в теле нет; `GET` со статусом 404 даёт `NOT_FOUND`, 413 `PAYLOAD_TOO_LARGE` (через `POST` без CSRF прямой запрос получил бы 403).
  - `filterExceptionGoesToErrorPage`: `RestartableApp` с `extraSources` `FailingFilter` (`OncePerRequestFilter` с `@Order(Ordered.HIGHEST_PRECEDENCE)`, то есть до Spring Security, иначе анонимный запрос получил бы 401; на пути `/api/test-filter-fail` бросает `IllegalStateException(<маркер>)`, иначе пропускает) и `HttpClient` на `http://127.0.0.1:<port>` без сессии: `GET` и `POST` дают 500, `Content-Type` `application/json`, тело ровно как выше (без маркера, `timestamp`, `trace`, `exception`), маркер в логе есть.
- `ApiErrorsTest` (шаг 7; `@Import` дополняется `ErrorsExtraTestController`, пути `/api/test-errors-extra/**`, все методы `permitAll()`):
  - `methodNotAllowedHasAllow`: `DELETE /get-only` (метод только `GET`) даёт 405 `METHOD_NOT_ALLOWED`, `Allow` содержит `GET` и не содержит `DELETE`.
  - `unsupportedMediaTypeHasAccept`: `POST /json-only` (`consumes = application/json`, тело record `Payload(String name)`) с `text/plain` через `MockMvc` с `user(...)` и `csrf()` даёт 415 `UNSUPPORTED_MEDIA_TYPE`, `Accept` содержит `application/json`. `Content-Type` ответа в обоих случаях `application/json`.
  - `crossParameterErrorsAfterParameterErrors`: `GET /range?from=-1&to=-2` (метод `@OrderedRange(message = "Начало диапазона больше конца")`, параметры `@RequestParam @Min(value = 0, message = "Не меньше 0") int from`, `@RequestParam int to`) даёт 400 `VALIDATION_FAILED`, `fields` ровно `[{"field":"from","message":"Не меньше 0"},{"field":null,"message":"Начало диапазона больше конца"}]`.
  - `crossParameterAloneIsIgnoredBySpring`: `GET /range?from=5&to=1` даёт 200. Фиксирует причину запрета (§5.1); падение после обновления Spring сообщается `architect`.
  - `pageLimits` дополняется `{"size=%205","size"}` (ревью T1.04 Н-ж).
- `ArchitectureTest`: `objectBodyRuleCatchesCollectionBody`: `objectBody.evaluate(new ClassFileImporter().importClasses(ListBodyController.class)).hasViolation()` истинно (`@RestController`, `@PreAuthorize("permitAll()") @PostMapping` с `@RequestBody List<String>`), для `ErrorsTestController` ложно; на `ru.netmon` правило проходит.
- Существующие тесты без правок остаются зелёными (проверено прототипом с `Api` на `CurrentUser`): `accessDeniedGoesToSecurity` теперь получает 403 `FORBIDDEN` JSON вместо пустого тела, `SmokeTest` не видит сгенерированного пароля.

**Команда проверки:** `mvn -B -pl server -am test -Dtest='LoginTest,CsrfTest,ErrorPageTest,ApiErrorsTest,ApiErrorsQaTest,ArchitectureTest,SmokeTest,TestDbSelfTest'`

**Критерии готовности:** тесты зелёные; `SecurityConfig` не содержит правил по отдельным ресурсам, кроме публичного списка §5.1; в логах нет сгенерированного пароля Spring; хэш пароля не попадает в ответы и `toString()` principal.

**Запрещено:** правила `requestMatchers` по ресурсам вместо `@PreAuthorize`; бин `DaoAuthenticationProvider`; блокировка (T1.09), срок сессии (T1.08).

---

## T1.06. Журнал действий и запись событий

**Цель:** каждое изменение данных записывается в журнал действий и журнал событий в транзакции изменения, без секретов, и читается администратором.
**Исполнитель:** backend-core. **Зависит от:** T1.05. **Фичи:** LOG-04 (запись и чтение журнала действий), LOG-01 (запись события `USER_ACTION` и общий механизм записи событий; события статуса и трапов пишут T3.11, T4.14).

**Файлы:**
- Новые в `main`: `S/common/json/SecretFieldGuard.java`, `S/audit/AuditLogService.java`, `S/audit/AuditRepository.java`, `S/audit/AuditEntry.java`, `S/audit/AuditController.java`, `S/journal/EventJournalService.java`.
- Тесты: новые `T/common/SecretFieldGuardTest.java`, `T/audit/AuditLogTest.java`, `T/audit/AuditApiTest.java`, `T/journal/EventJournalWriteTest.java`, `T/testsupport/ApiSelfTest.java`, `server/src/test/resources/security-matrix/audit.csv`.
- Правятся (дефект помощника T1.03, шаг 8): `T/testsupport/Api.java`, `T/common/ApiErrorsTest.java` (только две замены `.with(csrf())` и импорт).
- Не правятся: `contract/`, миграции, `SecurityConfig`, `ApiErrorHandler`, `CurrentUser`, остальные помощники `T/testsupport/`, `auth.csv`, остальные существующие тесты. Зависимости не добавляются (Jackson 3 и `spring-tx` уже есть).

**Контракты:** §7.2 (`AuditLog`, `EventJournal`, абзац «Запись журналов»), §5.2 (`GET /api/audit`, абзац «Журнал действий и журнал событий»), §5.1 (`PageQuery`, ошибки), §4.2 (`audit_log`, `event`), §2.1, §2.2, §10.4, §12.2, §12.3, §14.

**Решения (проверены прототипом в копии проекта, `-o`, полный `mvn -pl server -am test` зелёный вместе с прототипом):**
- Запись в той же транзакции: `@Transactional(propagation = MANDATORY)` на `record` обоих сервисов; прокси CGLIB строится для пакетных классов `@Service` с публичным методом интерфейса. Вне транзакции `IllegalTransactionStateException`; откат вызывающего убирает и `audit_log`, и `event`; исключение `record`, перехваченное вызывающим, всё равно откатывает его транзакцию (`UnexpectedRollbackException` при фиксации).
- Публикации нет: журналы не шлют событий Spring и SSE; читатели видят запись после фиксации транзакции вызывающего.
- `Instant` в SQL только как `OffsetDateTime` UTC; JSON в `jsonb` строкой с `?::jsonb`; `inet` строкой `getHostAddress()` с `?::inet`.
- `SecretFieldGuard` в `common`, а не в `audit`: его вызывает и `journal` (§2.2 п. 1).
- Хранение (удаление по сроку) не входит: `event` T3.16, `audit_log` T3.23 (§4.3).
- Помощники и тесты не меняют состояние общего контекста (§12.2). `Api` берёт настоящий токен CSRF (`csrfToken()`), а не `SecurityMockMvcRequestPostProcessors.csrf()`: тот навсегда заменяет хранилище токенов в `CsrfFilter` кешированного контекста, и следующие в том же контексте `CsrfTest`, `LoginTest` теряют cookie `XSRF-TOKEN` (проверено прототипом: без правки 8 падений в порядке `alphabetical`; с правкой 99 тестов зелёные в `alphabetical` и `reversealphabetical`). `@DirtiesContext` на `AuditApiTest` не ставится.

**Шаги:**
1. `SecretFieldGuard` по §7.2 «Запись журналов»: набор имён, сравнение без учёта регистра по `Locale.ROOT`, `strip` на глубокой копии (удаление полей у `ObjectNode`, обход значений `ObjectNode` и `ArrayNode`; в Jackson 3 `properties()` изменяемо, `removeIf` по ключу работает).
2. `EventJournalService` (`@Service`, пакетный класс, `implements EventJournal`; конструктор `(JdbcClient, JsonMapper)`): проверки `null` через `Objects.requireNonNull`; `details` = `null` или `writeValueAsString(SecretFieldGuard.strip(valueToTree(details)))`; один `insert into event(ts, device_id, type, source_ip, description, details) values (?, ?, ?, ?::inet, ?, ?::jsonb)`, `type` = `t.name()`.
3. `AuditRepository` (`@Repository`, пакетный, `JdbcClient`): `long insert(Instant ts, Actor a, String action, String objectType, String objectId, String oldJson, String newJson)` (`insert ... returning id`); `long count(Filter f)` и `List<AuditEntry> page(Filter f, PageQuery q)`, где `record Filter(Long userId, String objectType, Instant from, Instant to)` вложен в `AuditRepository`. Условие `where` собирается из постоянных фрагментов (`user_id = ?`, `object_type = ?`, `ts >= ?`, `ts < ?`) со значениями параметрами (§14 п. 1); `order by ` + `q.orderBy("id")` + ` limit ? offset ?` с `q.size()`, `q.offset()`. `old_value::text`, `new_value::text` читаются строкой и разбираются `JsonMapper.readTree`, `null` остаётся `null`.
4. `AuditEntry`: `public record AuditEntry(long id, Instant ts, Long userId, String userLogin, String action, String objectType, String objectId, JsonNode oldValue, JsonNode newValue)` (`tools.jackson.databind.JsonNode`); порядок компонентов задаёт порядок полей JSON §5.2.
5. `AuditLogService` (`@Service`, пакетный, `implements AuditLog`; конструктор `(AuditRepository, JsonMapper, Clock, EventJournal)`): проверки и шаблоны `action`/`objectType`, время, `deviceId`, `description`, `details` строго по §7.2 «Запись журналов»; порядок: проверки, `clock.instant()`, сериализация обоих значений, `insert`, `events.record(...)`.
6. `AuditController` (`@RestController`, пакетный): `@PreAuthorize("hasRole('ADMIN')") @GetMapping("/api/audit") PageResponse<AuditEntry> list(@RequestParam(required = false) String page, String size, String sort, Long userId, String objectType, Instant from, Instant to)` (каждый параметр `@RequestParam(required = false)`); сначала `PageQuery.parse(page, size, sort, Map.of("ts", "ts"), "ts,desc")`, затем при `from != null && to != null && from.isAfter(to)` `throw ApiException.validation(List.of(new FieldError(null, "Начало периода позже конца")))`, затем `count` и `page`, ответ `PageResponse.of(items, q, total)`.
7. `audit.csv`: заголовок `метод,путь,OPERATOR,ADMIN,ANON` и ровно одна строка `GET,/api/audit,deny,allow,deny`.
8. `Api` по §12.2 (строка `Api`): убрать статический импорт `SecurityMockMvcRequestPostProcessors.csrf` и `.with(csrf())` в `As.send`; добавить `private static final Set<HttpMethod> SAFE = Set.of(GET, HEAD, OPTIONS, TRACE)` и метод
   ```java
   public RequestPostProcessor csrfToken() throws Exception {
       Cookie c = mvc.perform(MockMvcRequestBuilders.get("/api/auth/csrf")).andReturn().getResponse()
               .getCookie("XSRF-TOKEN");
       if (c == null) {
           throw new IllegalStateException("GET /api/auth/csrf не выдал cookie XSRF-TOKEN");
       }
       return request -> {
           Cookie[] old = request.getCookies();
           Cookie[] all = old == null ? new Cookie[1] : Arrays.copyOf(old, old.length + 1);
           all[all.length - 1] = c;
           request.setCookies(all);
           request.addHeader("X-XSRF-TOKEN", c.getValue());
           return request;
       };
   }
   ```
   (`jakarta.servlet.http.Cookie`, `org.springframework.test.web.servlet.request.RequestPostProcessor`); в `As.send` после `.with(authentication(token(role)))`: `if (!SAFE.contains(method)) { b.with(csrfToken()); }`. Javadoc класса: изменяющий запрос несёт настоящий токен, `csrf()` не используется, потому что меняет общий контекст. В `ApiErrorsTest` обе `.with(csrf())` (методы `springErrors`, `unsupportedMediaTypeHasAccept`) заменить на `.with(api.csrfToken())`, статический импорт `csrf` удалить. С `AuditApiTest` снять `@DirtiesContext(AFTER_CLASS)`, его импорт и комментарий-обход.

**Обязательные тесты (имена методов обязательны; тело читать `getContentAsString(StandardCharsets.UTF_8)`; транзакции в тестах через `new TransactionTemplate(PlatformTransactionManager)`; пользователь для `Actor` создаётся `TestData.user(Role.ADMIN)`, иначе нарушается внешний ключ `audit_log.user_id`; время через `MutableClock.set`):**
- `SecretFieldGuardTest` (без Spring, `JsonMapper.builder().build()`):
  - `removesAtAnyDepth`: из `{"a":{"community":"S1","list":[{"authPassword":"S2","x":1},[{"privPassword":"S3"}]]},"Password":"S4","PASSWORD_HASH":"S5","ciphertext":"S6","oldPassword":"S7","newPassword":"S8","passwordHash":"S9"}` получается ровно `{"a":{"list":[{"x":1},[{}]]}}`.
  - `keepsOtherFieldsAndValues`: `{"note":"password","passwordPolicy":"community","user":"u"}` не меняется.
  - `doesNotMutateInput`: исходное дерево после `strip` равно своей копии, снятой до вызова.
  - `nullAndScalars`: `strip(null)` = `null`; строковый и числовой узел возвращаются равными исходным.
- `EventJournalWriteTest` (`@IntegrationTest`, внедрённый `EventJournal`):
  - `recordsAllColumns`: устройство через `TestData`, `record(TRAP, deviceId, InetAddress.getByAddress(new byte[]{10,1,2,3}), "описание", details, ts)`, где `details` = `HashMap` с `"k"→"v"` и `"n"→null`, `ts` = `2026-01-01T00:00:00.123456Z`: в строке `ts` равен `ts`, `device_id`, `type` `TRAP`, `host(source_ip)` = `10.1.2.3`, `description`, `details = '{"k":"v","n":null}'::jsonb` истинно.
  - `unknownDeviceAllowed` (LOG-01, неизвестный источник): `record(TRAP_UNKNOWN_SOURCE, null, null, "x", null, ts)` без исключения; `device_id`, `source_ip`, `details` в строке `null` (SQL `NULL`, `details is null`).
  - `detailsStripped`: `details` с вложенным `community` уникальной строкой: строка в `event` есть, уникальной строки в `t::text` нет (`select count(*) from event t where position(? in t::text) > 0` = 0).
  - `requiresTransaction`: вызов без транзакции даёт `IllegalTransactionStateException`, строк `event` 0.
  - `rollbackLeavesNothing`: запись и `setRollbackOnly()` в одной транзакции: строк `event` 0.
  - `requiredArguments`: `t`, `description`, `ts` равные `null` (каждый в своей транзакции) дают `NullPointerException`.
- `AuditLogTest` (`@IntegrationTest`, внедрённые `AuditLog`, `JdbcClient`, `PlatformTransactionManager`, `MutableClock`, `TestData`):
  - `recordStoresAllFields` (LOG-04): `clock.set(2026-01-02T03:04:05.123456Z)`, устройство через `TestData`, `record(new Actor(u, "adm-login"), "UPDATE", "device", String.valueOf(dev), Map.of("name","a"), Map.of("name","b"))`: в `audit_log` одна строка, `ts` равен установленному времени, `user_id` = `u`, `user_login` `adm-login`, `action`, `object_type`, `object_id`, `old_value = '{"name":"a"}'::jsonb` и `new_value = '{"name":"b"}'::jsonb` истинны. В `event` одна строка: `type` `USER_ACTION`, тот же `ts`, `device_id` = `dev`, `source_ip` `null`, `description` ровно `Действие пользователя adm-login: UPDATE device <dev>`, `details` равно jsonb `{"auditId":<id строки audit_log>,"userId":u,"login":"adm-login","action":"UPDATE","objectType":"device","objectId":"<dev>"}`.
  - `nonDeviceObjects`: `record(actor, "UPDATE", "setting", "auth.lock_attempts", …)` даёт `event.device_id` `null`; `record(actor, "CREATE", "user", null, null, null)` даёт `object_id`, `old_value`, `new_value` SQL `NULL` и `description` ровно `Действие пользователя adm-login: CREATE user` (без завершающего пробела).
  - `secretsNotStored` (LOG-04 «не секреты», §10.4): уникальные строки S1..S5 в `oldValue` = `Map.of("password","S1","nested",Map.of("community","S2"),"list",List.of(Map.of("authPassword","S3")))` и `newValue` = `Map.of("creds", new SnmpCredentials.V3("u-plain","SHA","S4","AES","S5"), "note","S1-copy")`: поиск `position(? in t::text) > 0` по `audit_log` и по `event` для S2..S5 даёт 0; строка `S1-copy` в `audit_log` находится (обычное поле не затронуто), `S1` отдельно не ищется, потому что входит в `S1-copy`. Поле `user` записи `V3` не проверяется (закрывает T1.22, §7.2).
  - `rollbackLeavesNothing`: в одной транзакции `insert into device_group(name) values (?)`, `record(...)`, `setRollbackOnly()`: строк `audit_log`, `event` и этой группы 0.
  - `commitKeepsBoth`: то же без отката: группа, одна строка `audit_log` и одна `event` есть.
  - `requiresTransaction`: вызов без транзакции даёт `IllegalTransactionStateException`; строк `audit_log` и `event` 0.
  - `failureRollsBackCaller`: в транзакции вставка группы, затем `record` с `action` `"bad action"`, исключение `IllegalArgumentException` вызывающий перехватывает; `execute` бросает `UnexpectedRollbackException`; группы нет. Фиксирует правило «ошибка журнала отменяет изменение» (§7.2).
  - `argumentChecks`: `action` `update`, `""`, `null`, `"A".repeat(65)` и `objectType` `Device`, `""`, `null` дают `IllegalArgumentException`; `a` = `null` даёт `NullPointerException`; в каждом случае строк нет.
- `AuditApiTest` (`@IntegrationTest`, `Api`):
  - `adminReadsEntry`: одна запись (`clock.set(2026-03-01T00:00:10Z)`, `"UPDATE"`, `"setting"`, `"k"`, `Map.of("v","<b>1</b>")`, `Map.of("v","2")`): `GET /api/audit` от `ADMIN` 200, тело ровно `{"items":[{"id":<id>,"ts":"2026-03-01T00:00:10Z","userId":<u>,"userLogin":"adm","action":"UPDATE","objectType":"setting","objectId":"k","oldValue":{"v":"<b>1</b>"},"newValue":{"v":"2"}}],"page":0,"size":50,"total":1}` (HTML хранится и отдаётся текстом JSON).
  - `stablePaging` (правило постраничных ресурсов): три записи при одном значении `MutableClock` и одна на 10 с позже; `size=1`, страницы 0..3 дают каждую из четырёх записей ровно один раз в порядке `ts desc, id desc`; `page=4&size=1` 200, `items` `[]`, `total` 4; `sort=ts,asc` даёт обратный порядок по `ts` и `id`.
  - `filters`: два пользователя, два `objectType`, записи на `2026-03-01T00:00:00Z` и `2026-03-01T00:00:10Z`: `userId`, `objectType`, `from` (включительно), `to` (исключительно) и их сочетание дают ожидаемые множества `id` и `total`; `from=…:10Z&to=…:10Z` даёт `total` 0; неизвестный `userId` 200 с `total` 0.
  - `badParameters`: `from=abc`, `from=2026-03-01` 400 `BAD_PARAMETER` с полем `from`; `to=abc` с полем `to`; `userId=x` с полем `userId`; `from=2026-03-02T00:00:00Z&to=2026-03-01T00:00:00Z` 400, тело ровно `{"code":"VALIDATION_FAILED","message":"Проверьте введённые данные","fields":[{"field":null,"message":"Начало периода позже конца"}]}`; `sort=id` и `size=0` 400 `VALIDATION_FAILED` с полем `sort` и `size`.
  - `rights`: `OPERATOR` 403, тело ровно `{"code":"FORBIDDEN","message":"Недостаточно прав","fields":[]}`; без сессии (бин `MockMvc` напрямую) 401 `AUTH_REQUIRED`.
- `ApiSelfTest` (`@IntegrationTest`, внедрённые `Api`, `MockMvc`):
  - `mutatingRequestCarriesRealCsrfToken`: `api.as(OPERATOR).post("/api/no-such-resource", "{}")` и `api.as(ADMIN).delete("/api/no-such-resource")` дают 404 (не 403 `CSRF_INVALID`).
  - `sharedContextCsrfIntact`: после `api.as(OPERATOR).post(...)` анонимный `GET /api/auth/csrf` через `MockMvc` даёт 204 и cookie `XSRF-TOKEN` не `null` (со старым `csrf()` cookie нет).
- `ArchitectureTest`: зелёный без правок (`audit` и `journal` зависят только от `contract` и `common`; контроллер с `@PreAuthorize`).
- Существующие тесты без правок зелёные (проверено прототипом: `TestDb.clean()` уже очищает `audit_log` и `event`).

**Команда проверки:** `mvn -B -pl server -am test -Dtest='SecretFieldGuardTest,EventJournalWriteTest,AuditLogTest,AuditApiTest,ApiSelfTest,ArchitectureTest'`; затем полный `mvn -B -pl server -am test -Dsurefire.runOrder=alphabetical` и то же с `reversealphabetical`, оба зелёные.

**Критерии готовности:** тесты зелёные, полный прогон зелёный в обоих порядках; в `server/src/test` нет вызовов `SecurityMockMvcRequestPostProcessors.csrf` (`grep -rn 'csrf()' server/src/test` находит только javadoc `Api`) и нет `@DirtiesContext`, кроме `SmokeTest`; запись участвует в транзакции вызывающего (`MANDATORY`, не `REQUIRES_NEW`); значения в журнал идут только через `SecretFieldGuard`; в отчёте перечислено непроверенное.

**Запрещено:** правка `contract/` и миграций; `SecurityMockMvcRequestPostProcessors.csrf()` и `@DirtiesContext` как обход побочных эффектов в тестах; запись журналов вне транзакции вызывающего или после фиксации; копирование `oldValue`/`newValue` в `event.details`; свой `JsonMapper` вместо бина; поиск по журналу событий и `GET /api/events` (T3.15); удаление по сроку (T3.16, T3.23); страница (T1.32); запись журнала при входе и выходе (ТЗ не требует).

---

## T1.07. Настройки системы

**Цель:** настройки читаются модулями через `Settings`, правятся администратором через API, сохраняются и применяются без перезапуска; заодно закрываются дешёвые замечания ревью T1.06 к журналам.
**Исполнитель:** backend-core. **Зависит от:** T1.06. **Фичи:** OPS-08 (хранение и правка в API; применение в модулях: T1.08, T1.09, T3.05, T3.16, T4.11), LOG-04 (журнал изменения настроек; доработка `SecretFieldGuard`), LOG-01 (запись события не падает на NUL и зоне IPv6).

**Файлы:**
- Новые в `main`: `S/settings/SettingDefinitions.java`, `S/settings/SettingView.java`, `S/settings/SettingUpdate.java`, `S/settings/SettingsService.java`, `S/settings/SettingsController.java`, `S/common/json/NulGuard.java`, `S/common/web/PeriodParams.java`.
- Правятся в `main` (шаг 7, ревью T1.06): `S/common/json/SecretFieldGuard.java`, `S/journal/EventJournalService.java`, `S/audit/AuditLogService.java`, `S/audit/AuditController.java`.
- Тесты новые: `T/settings/SettingsApiTest.java`, `T/settings/SettingsServiceTest.java`, `T/settings/SettingChangedEventTest.java`, `server/src/test/java/nmtest/settings/SettingEventsProbe.java`, `server/src/test/resources/security-matrix/settings.csv`.
- Тесты правятся (шаг 7, только добавление методов и одна замена входа): `T/common/SecretFieldGuardTest.java`, `T/journal/EventJournalWriteTest.java`, `T/audit/AuditLogTest.java`, `T/audit/AuditApiTest.java`.
- Не правятся: `contract/`, миграции, `SecurityConfig`, `ApiErrorHandler`, `ApiException`, `T/testsupport/` (помощники уже умеют `restoreSettings()`), `auth.csv`, `audit.csv`, остальные тесты. Зависимости не добавляются.

**Контракты:** §5.2 (`/api/settings`, абзац «Настройки»; абзац «Журнал действий и журнал событий»), §5.1 (ошибки, `PeriodParams`), §7.2 (`Settings`, `SettingChanged`, абзацы «Запись журналов», «Замок строки устройства», «Настройки»), §13.2 (ключи, границы, когда применяется), §4.2 (`system_setting`), §2.1, §2.2, §12.2 (снимок `TestDb`, `restoreSettings()`, `RestartableApp`, тестовые бины в `nmtest`), §12.3, §14.

**Решения (проверены прототипом в копии проекта, `-o`, `offline/m2`; полный `mvn -pl server -am test` зелёный в порядках `alphabetical` и `reversealphabetical`, 122 теста):**
- Кэша нет (§7.2): чтение по первичному ключу на каждый вызов. Причина: горячего пути нет, а кэш устаревал бы после отката, после `TestDb.restoreSettings()` прямым SQL и в другом контексте на той же БД. Тест `persistsAcrossRestartAndOtherContext` это закрепляет.
- `SettingChanged` публикуется `ApplicationEventPublisher` внутри транзакции изменения, подписчики `@TransactionalEventListener(AFTER_COMMIT)`: откат события не даёт; исключение подписчика Spring 7.0 логирует `ERROR` и не пробрасывает, ответ 200.
- `{key}` в `@PutMapping("/api/settings/{key}")` захватывает весь сегмент с точками; число JSON в `value` Jackson приводит к строке.
- Версии нет: правка одного значения под `select ... for update`, журнал пишет действительное старое значение.
- Тестовый подписчик лежит в `nmtest.settings` и подключается `@Import` только к `SettingChangedEventTest` (одна лишняя конфигурация контекста, по §12.2 это нужда, а не прихоть). `SettingsApiTest` и `SettingsServiceTest` работают в общем контексте `@IntegrationTest` и меняют только БД, которую возвращают в `@AfterEach`.

**Шаги:**
1. `SettingDefinitions` (`final class`, пакетный, без экземпляров): вложенный `record Definition(String key, int min, int max)` с `String message()` = `"Целое число от " + min + " до " + max` и `Optional<String> violation(String value)`: `null` или несовпадение с `[1-9][0-9]{0,9}` (предкомпилированный `Pattern`) дают `Optional.of(message())`; иначе `Long.parseLong` и проверка `[min, max]`. `static final List<Definition> ALL` ровно шесть строк §13.2 в порядке `String.compareTo` ключа: `auth.lock_attempts` 1..100, `auth.lock_minutes` 1..1440, `retention.journal_days` 1..3650, `retention.results_days` 1..3650, `session.timeout_minutes` 1..43200, `traps.udp_port` 1..65535. `static Optional<Definition> find(String key)`: точное совпадение.
2. `SettingView`: `public record SettingView(String key, String value, int min, int max)`; `SettingUpdate`: `public record SettingUpdate(String value)`. Порядок компонентов задаёт порядок полей JSON.
3. `SettingsService` (`@Service`, пакетный, `implements Settings`; конструктор `(JdbcClient, Clock, AuditLog, ApplicationEventPublisher)`), строго по §7.2 «Настройки»:
   - `public String get(String key)`, `public int getInt(String key)`: тексты исключений из §7.2 дословно.
   - `List<SettingView> list()`: один `select key, value from system_setting`, результат в порядке `SettingDefinitions.ALL`; определённого ключа нет в строках: `IllegalStateException` «Нет строки system_setting для ключа <key>».
   - `@Transactional SettingView update(String key, String value, Actor actor)`: нет определения `throw ApiException.notFound()`; `violation` даёт `throw ApiException.validation(List.of(new FieldError("value", сообщение)))`; затем замок, сравнение, `update`, журнал, событие; ответ `new SettingView(key, value, min, max)` и при равном значении.
4. `SettingsController` (`@RestController`, пакетный): `@PreAuthorize("hasRole('ADMIN')") @GetMapping("/api/settings") List<SettingView> list()`; `@PreAuthorize("hasRole('ADMIN')") @PutMapping("/api/settings/{key}") SettingView update(@PathVariable String key, @RequestBody SettingUpdate body, @AuthenticationPrincipal CurrentUser user)` вызывает `service.update(key, body.value(), user.actor())`. Без `@Valid` и аннотаций Bean Validation: проверку делает `SettingDefinitions`.
5. `settings.csv`: заголовок `метод,путь,OPERATOR,ADMIN,ANON`, строки `GET,/api/settings,deny,allow,deny` и `PUT,/api/settings/{key},deny,allow,deny`.
6. `SettingEventsProbe` (`nmtest.settings`, `public class`, `@Component`): `public final List<SettingChanged> events = new CopyOnWriteArrayList<>()`, `public volatile boolean fail`; метод `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT) void on(SettingChanged e)` добавляет событие и при `fail` бросает `IllegalStateException("probe failure")`.
7. Замечания ревью T1.06 (по §5.1 `PeriodParams`, §7.2 «Запись журналов»):
   - `SecretFieldGuard.isSecretName`: нормализация и подстроки по §7.2; `strip` без изменений.
   - `NulGuard` по §7.2. `EventJournalService`: `details` = `writeValueAsString(NulGuard.tree(SecretFieldGuard.strip(valueToTree(details))))`, `description` через `NulGuard.text`, адрес приватным `static String host(InetAddress ip)`: `null` даёт `null`, иначе `getHostAddress()` до первого `%`. `AuditLogService`: тот же порядок в `toJson`, `objectId` в `insert` через `NulGuard.text` (в `details` события и в `description` он попадает исходным и чистится в `EventJournal`).
   - `PeriodParams` по §5.1. `AuditController`: прежний блок `from.isAfter(to)` заменить вызовом `PeriodParams.check(from, to)` после `PageQuery.parse`.

**Обязательные тесты (имена методов обязательны; тело читать `getContentAsString(StandardCharsets.UTF_8)`; транзакции через `new TransactionTemplate(PlatformTransactionManager)`; `Actor` из `TestData.user(Role.ADMIN)`; каждый класс, меняющий `system_setting`, несёт `@AfterEach void restore() { db.restoreSettings(); }` с внедрённым `TestDb`, без `try`/`finally` в теле метода):**
- `SettingsApiTest` (`@IntegrationTest`; внедрены `Api`, `MockMvc`, `Settings`, `JdbcClient`, `TestDb`):
  - `listsAllDefinedKeys`: `GET` от `ADMIN` 200, тело ровно `[{"key":"auth.lock_attempts","value":"5","min":1,"max":100},{"key":"auth.lock_minutes","value":"15","min":1,"max":1440},{"key":"retention.journal_days","value":"365","min":1,"max":3650},{"key":"retention.results_days","value":"30","min":1,"max":3650},{"key":"session.timeout_minutes","value":"720","min":1,"max":43200},{"key":"traps.udp_port","value":"162","min":1,"max":65535}]`.
  - `updateAppliesImmediately` (OPS-08): `PUT /api/settings/auth.lock_attempts` `{"value":"7"}` 200, тело ровно `{"key":"auth.lock_attempts","value":"7","min":1,"max":100}`; сразу `settings.getInt(...)` = 7 и `settings.get(...)` = `"7"`; `updated_by` строки равен `id` пользователя `test-admin`.
  - `bounds`: для каждого из шести ключей минимум и максимум дают 200 и читаются `getInt`; значения `"min-1"`, `"max+1"`, `""`, `"abc"`, `" 5"`, `"05"`, `"+5"`, `"-1"`, `"1.0"`, `"99999999999"`, JSON `null` и тело `{}` дают 400, тело ровно `{"code":"VALIDATION_FAILED","message":"Проверьте введённые данные","fields":[{"field":"value","message":"Целое число от <min> до <max>"}]}`; после них значение осталось максимумом.
  - `bodyForms`: `{"value":8}` 200 и `value` `"8"`; `{"value":"9","extra":1}` 200; `{"value":true}` и `{"value":8.0}` 400 `VALIDATION_FAILED` с полем `value`; `{"value":{}}`, тело `[]` и запрос без тела 400 `BAD_JSON`; `PUT /api/settings/auth.lock_attempts.json` и `/api/settings/AUTH.LOCK_ATTEMPTS` 404 `NOT_FOUND`; `GET /api/settings/auth.lock_attempts` 405.
  - `unknownKey`: `PUT /api/settings/no.such_key` с `{"value":"1"}` и с `{"value":"abc"}` 404, тело ровно `{"code":"NOT_FOUND","message":"Объект не найден","fields":[]}`; строк `system_setting` 6, `audit_log` 0.
  - `auditRecordsOldAndNew` (LOG-04): `retention.results_days` `"45"` дважды подряд; в `audit_log` ровно одна строка: `user_login` `test-admin`, `action` `UPDATE`, `object_type` `setting`, `object_id` `retention.results_days`, `old_value::text` `{"value": "30"}`, `new_value::text` `{"value": "45"}`; в `event` одна строка `USER_ACTION` с `device_id is null`.
  - `rights`: `OPERATOR` `GET` 403, тело ровно `{"code":"FORBIDDEN","message":"Недостаточно прав","fields":[]}`; `OPERATOR` `PUT` 403; без сессии (`MockMvc` напрямую) `GET` 401 и `PUT` с `.with(api.csrfToken())` 401; значение не изменилось, `audit_log` 0.
  - `persistsAcrossRestartAndOtherContext` (OPS-08 «сохраняется», без перезапуска): `try (RestartableApp app = new RestartableApp(Map.of()).start())`: `Settings` из `app.context()` даёт 720; `PUT session.timeout_minutes` `"30"` через `Api` общего контекста; тот же бин `RestartableApp` сразу даёт 30; `app.restart()`, новый бин даёт 30.
- `SettingsServiceTest` (`@IntegrationTest`, тот же пакет; внедрены `SettingsService`, `JdbcClient`, `PlatformTransactionManager`, `TestData`, `TestDb`):
  - `rollbackKeepsValue`: в транзакции `update("auth.lock_minutes", "60", a)`, внутри неё `getInt` = 60, `setRollbackOnly()`; после неё `getInt` = 15, `audit_log` 0.
  - `unknownKeyForReaders`: `get("no.such")` и `getInt("no.such")` дают `IllegalArgumentException`.
  - `invalidStoredValue`: SQL `update system_setting set value = '0' where key = 'auth.lock_attempts'`; `getInt` даёт `IllegalStateException` с сообщением ровно `Недопустимое значение настройки auth.lock_attempts в БД`; `get` даёт `"0"`.
  - `missingRow`: SQL удаляет строку `auth.lock_attempts`; `getInt`, `list()` и `update(..., "3", a)` дают `IllegalStateException`.
  - `validationThrowsApiException`: `update("auth.lock_attempts", "0", a)` даёт `ApiException`; `update("nope", "1", a)` даёт `ApiException` с `code()` `NOT_FOUND`.
  - `concurrentUpdatesSerialized`: поток 1 в транзакции `update(..., "10", a)` и ждёт `CountDownLatch`; после его записи поток 2 вызывает `update(..., "20", a)`; ожидание опросом с предельным сроком 10 с, пока в `pg_stat_activity` нет строки с `wait_event_type = 'Lock'` и `query like 'select value from system_setting%'` (без `sleep`); затем отпустить поток 1; оба `Future` завершаются; `audit_log` по `id` даёт ровно `5>10`, `10>20` (`old_value->>'value' || '>' || (new_value->>'value')`), `getInt` = 20. Пул потоков закрывается в `finally` (`shutdownNow`), защёлка отпускается и там.
- `SettingChangedEventTest` (`@IntegrationTest` и `@Import(SettingEventsProbe.class)`; `@BeforeEach` очищает `probe.events` и `probe.fail = false`; `@AfterEach` `probe.fail = false` и `restoreSettings()`):
  - `publishedAfterCommit`: `PUT traps.udp_port` `"10162"` 200; `probe.events` ровно `[new SettingChanged("traps.udp_port", "10162")]`.
  - `notPublishedOnRollback`: в транзакции `update(...)`, внутри неё `probe.events` пуст, `setRollbackOnly()`; после неё пуст.
  - `notPublishedForSameValue`: `PUT traps.udp_port` `"162"` 200, событий нет.
  - `listenerFailureDoesNotFailRequest`: `probe.fail = true`, `PUT traps.udp_port` `"10163"` 200, тело `{"key":"traps.udp_port","value":"10163","min":1,"max":65535}`, `Settings.get` даёт `"10163"`.
- Шаг 7, добавления в существующие классы:
  - `SecretFieldGuardTest.keepsOtherFieldsAndValues`: вход заменить на `{"note":"password","description":"token secret","user":"u","key":"k","pairKey":"p","keyVersion":1,"snmpVersion":"V3","authProtocol":"SHA"}` (не меняется; прежний `passwordPolicy` теперь секретный). Новый `normalizedNames`: `isSecretName` истинно для `auth_password`, `priv-password`, `snmpCommunity`, `communityString`, `secret`, `token`, `apiKey`, `authKey`, `privKey`, `passphrase`, `privateKey`, `masterKey`, `passwordPolicy`, `PASS_WORD`, `x-Api-Key`, `Ciphertext`, `csrfToken`, `clientSecret`, `AUTH-KEY`; ложно для `key`, `pairKey`, `keyVersion`, `user`, `note`, `description`, `authProtocol`, `privProtocol`, `login`, `value`, `authKeyVersion`, `monkey`. `removesAtAnyDepth` без правок.
  - `EventJournalWriteTest.ipv6ScopeDropped`: `Inet6Address.getByAddress(null, <16 байт fe80::1>, 7)` как `sourceIp` даёт `host(source_ip)` = `fe80::1`; адрес `2001:db8::2` без зоны даёт `2001:db8::2`. `EventJournalWriteTest.nulReplaced`: `description` `"d\u0000e"`, `details` `{"v":"a\u0000b","k\u0000":["\u0000"]}`; строка записана, `description` = `"d�e"`, `details = '{"v":"a�b","k�":["�"]}'::jsonb` истинно.
  - `AuditLogTest.nulReplaced`: `record(actor, "UPDATE", "setting", "k\u0000", Map.of("v", "a\u0000"), null)`: `object_id` = `"k�"`, `old_value = '{"v":"a�"}'::jsonb`, `event.description` ровно `Действие пользователя adm-login: UPDATE setting k�`.
  - `AuditApiTest.periodOutOfRange`: `get("/api/audit?from={f}", "+10000-01-01T00:00:00Z")`, то же с `"+100000-01-01T00:00:00Z"`, `?from=99999999999999999`, `?from=0000-12-31T23:59:59.999999Z` дают 400, тело ровно `{"code":"VALIDATION_FAILED","message":"Проверьте введённые данные","fields":[{"field":"from","message":"Дата вне диапазона 0001-01-01 .. 9999-12-31"}]}`; `get("/api/audit?to={t}", "+100000-01-01T00:00:00Z")` то же с полем `to`; `from={f}&to={t}` оба вне диапазона: два поля, `from` затем `to`; `?from=0001-01-01T00:00:00Z&to=9999-12-31T23:59:59.999999Z` 200. Прежний `badParameters` без правок проходит.
- `ArchitectureTest`: зелёный без правок (`settings` зависит только от `contract` и `common`).

**Команда проверки:** `mvn -B -pl server -am test -Dtest='SettingsApiTest,SettingsServiceTest,SettingChangedEventTest,SecretFieldGuardTest,EventJournalWriteTest,AuditLogTest,AuditApiTest,ArchitectureTest'`; затем полный `mvn -B -pl server -am test -Dsurefire.runOrder=alphabetical` и то же с `reversealphabetical`, оба зелёные.

**Критерии готовности:** тесты зелёные в обоих порядках; ключи без определения не принимаются и не показываются; у `SettingsService` нет полей-кэшей значений; подписчик в тестах только `@TransactionalEventListener(AFTER_COMMIT)`; каждый тестовый класс, пишущий в `system_setting`, возвращает настройки в `@AfterEach`; в `server/src/test` нет `@DirtiesContext`, кроме `SmokeTest`; в отчёте перечислено непроверенное.

**Запрещено:** кэш значений настроек; оптимистическая блокировка и миграции; применение настроек в чужих модулях (сессия T1.08, блокировка T1.09, порт трапов T4.11, сроки T3.05, T3.16, T3.23); `@EventListener` вместо `@TransactionalEventListener` для `SettingChanged`; Bean Validation на `SettingUpdate`; страница настроек (T1.32); изменение текстов и полей ответа `/api/audit`, кроме ошибки диапазона дат.

---

## T1.08. Истечение сессии

**Цель:** сессия истекает через настраиваемый срок простоя, сервер умеет завершать сессии пользователя; заодно закрываются дешёвые замечания ревью T1.05 и T1.07 к тестам.
**Исполнитель:** backend-core. **Зависит от:** T1.07. **Фичи:** AUTH-07 (истечение, срок настраивается; выход сделан в T1.05), OPS-08 (применение `session.timeout_minutes`).

**Файлы:**
- Новые в `main`: `S/auth/SessionTimeoutApplier.java`, `S/auth/UserSessionRegistry.java`.
- Правка в `main`: `S/auth/JsonAuthHandlers.java` (конструктор и начало `onAuthenticationSuccess`, остальное без изменений).
- Тесты новые: `T/auth/SessionExpiryTest.java`, `server/src/test/java/nmtest/auth/SlowSessionController.java`.
- Тесты правятся: `T/auth/LoginTest.java` (только шаг 4), `T/settings/SettingsServiceTest.java` (только шаг 5).
- Не правятся: `SecurityConfig`, `application.yml`, `application-test.yml`, `ApiErrorHandler`, `contract/`, миграции, `T/testsupport/`, `security-matrix/*.csv` (эндпоинтов нет), остальные тесты. Зависимости не добавляются.

**Контракты:** §5.1 («Безопасность», «Сессии»), §6 (проверка сессии, для связи с T1.24), §7.2 «Настройки», §12.2 («Сессии на Tomcat», «Общая БД JVM», тестовые бины в `nmtest`, помощники не меняют общий контекст), §13.1 (`nm.session.timeout-seconds-override`), §13.2 (`session.timeout_minutes`: следующий вход), §14.

**Решения (проверены прототипом в копии проекта, `-o`, `offline/m2`; полный `mvn -pl server -am test` зелёный в порядках `alphabetical` и `reversealphabetical`, 126 тестов с пробным классом):**
- Срок пишется в сессию при входе `setMaxInactiveInterval(минуты * 60)`; смена настройки действует на следующий вход, открытая сессия хранит свой (прототип: 43200 у прежней, 1800 у новой после `PUT 30`).
- Истечение делает Tomcat по системному времени, `MutableClock` его не двигает, `MockHttpSession` не истекает. Поэтому срок из настройки проверяется в `MockMvc` через `getMaxInactiveInterval()`, а истечение только на `RestartableApp` с переопределением 2 с и `server.tomcat.background-processor-delay=1s`: фоновая проверка снимает сессию за 2..8 с (прототип 5,7 с), тест ждёт опросом `sessionCount`, не трогая сессию. Ленивое истечение при обращении тоже даёт 401 и снимает запись (прототип), отдельным тестом не закрепляется: без `sleep` его не дождаться.
- Реестр снимает запись через `HttpSessionBindingListener`, а не `HttpSessionListener` (§5.1 «Сессии»): работает и в `MockMvc` (прототип: выход и `invalidate` снимают запись), и при повторном входе другим пользователем в ту же сессию.
- `terminateAll` внутри транзакции действует после фиксации, при откате сессии живы (прототип). `invalidate()` из чужого потока безопасен: запрос в полёте завершается 200 без новой сессии, следующий 401 (прототип).

**Шаги:**
1. `SessionTimeoutApplier` строго по §5.1 «Сессии» (константа `OVERRIDE` = `"nm.session.timeout-seconds-override"`, тексты исключений дословно).
2. `UserSessionRegistry` строго по §5.1 «Сессии»: поля, `Binding`, методы `register`, `terminateAll`, `terminateOthers`, `sessionCount`, `userCount`, откладывание до `afterCommit`. Никаких других полей и кэшей, никакой записи в БД.
3. `JsonAuthHandlers`: конструктор `(JsonMapper json, SessionTimeoutApplier timeout, UserSessionRegistry registry)`; в начале `onAuthenticationSuccess` после чтения `CurrentUser`: `HttpSession session = request.getSession(false)` (`null`: `IllegalStateException` «Нет сессии после входа»), затем в `try` `timeout.apply(session)` и `registry.register(user.id(), session)`, в `catch (RuntimeException e)` `session.invalidate()` и `throw e`. Получение токена CSRF и тело 200 без изменений.
4. Сообщения `LoginTest` (ревью T1.05), других изменений `LoginTest` нет: (1) `passwordStoredAsBcrypt`: `assertThat(hash).startsWith("$2a$10$")` заменить на `assertThat(hash.startsWith("$2a$10$")).as("password_hash начинается с $2a$10$").isTrue()`, чтобы упавшая проверка не печатала хэш; (2) `setCookie`: сообщение `AssertionError` перечисляет только имена cookie: `"нет Set-Cookie " + name + ": " + all.stream().map(v -> v.split("=", 2)[0]).toList()`, без значений.
5. `SettingsServiceTest.concurrentUpdatesSerialized` (ревью T1.07, п. 3), других изменений нет: (1) запрос опроса `select count(*) from pg_stat_activity where datname = current_database() and pid <> pg_backend_pid() and wait_event_type = 'Lock' and query like 'select value from system_setting%'`; (2) в потоке 1 результат ожидания проверяется: `if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("release не дождался");` (транзакция потока 1 откатывается, `f1.get` падает, тест не проходит молча).
6. `SlowSessionController` (`nmtest.auth`, `public class`, `@RestController`): `public static volatile CountDownLatch entered`, `release`; `@PreAuthorize("hasAnyRole('OPERATOR','ADMIN')") @GetMapping("/api/test-session/slow") String slow(HttpServletRequest r)`: `entered.countDown()`, `release.await(10, SECONDS)` (результат `false`: `IllegalStateException`), ответ `"{\"session\":" + (r.getSession(false) != null) + "}"`. Подключается только `extraSources` `RestartableApp` в `SessionExpiryTest` (§12.2); тест перед использованием присваивает обоим полям новые `CountDownLatch(1)`.

**Обязательные тесты (имена методов обязательны; тело читать `getContentAsString(StandardCharsets.UTF_8)` или `HttpResponse.body()`; пользователи `OPERATOR` с паролем вставляются через `JdbcClient` и `PasswordEncoder`, как в `LoginTest`; вход формой с cookie и заголовком из `GET /api/auth/csrf`; на Tomcat `java.net.http.HttpClient` на `http://127.0.0.1:<port>`, cookie заголовком `Cookie` вручную; `AUTH_REQUIRED` = тело ровно `{"code":"AUTH_REQUIRED","message":"Требуется вход в систему","fields":[]}`; ожидания только опросом условия с предельным сроком 20 с через `Instant.now()` теста и `Thread.onSpinWait()`, без `sleep`):**
- `SessionExpiryTest` (`@IntegrationTest`, пакет `ru.netmon.auth`; внедрены `MockMvc`, `JdbcClient`, `PasswordEncoder`, `UserSessionRegistry`, `Api`, `TestDb`, `PlatformTransactionManager`; `@AfterEach void restore() { db.restoreSettings(); }`, без `try`/`finally` ради настроек в теле методов). `MockMvc` и бин реестра общего контекста:
  - `timeoutFromSettingAtLogin` (AUTH-07, OPS-08): вход A: `getMaxInactiveInterval()` = 43200; `api.as(Role.ADMIN).put("/api/settings/session.timeout_minutes", "{\"value\":\"30\"}")` 200; у A по-прежнему 43200; вход B того же пользователя: 1800; `sessionCount(id)` = 2.
  - `invalidStoredTimeoutRejectsLogin`: SQL `update system_setting set value = '0' where key = 'session.timeout_minutes'`; вход с переданной `new MockHttpSession()`: `perform` бросает `IllegalStateException` с сообщением ровно `Недопустимое значение настройки session.timeout_minutes в БД`; переданная сессия `isInvalid()`; `sessionCount(id)` = 0.
  - `reloginMovesRegistration`: вход пользователя 1 даёт сессию S; вход пользователя 2 с `.session(S)`: `sessionCount(id1)` = 0, `sessionCount(id2)` = 1; повторный вход пользователя 2 с S: `sessionCount(id2)` = 1.
  - `terminateAllAfterCommit`: две сессии пользователя 1, одна пользователя 2. В `TransactionTemplate` `terminateAll(id1)` и `setRollbackOnly()`: обе сессии живы, `GET /api/me` 200. В `TransactionTemplate` без отката `terminateAll(id1)`: внутри транзакции сессия жива (`isInvalid()` ложно); после неё обе `isInvalid()`, `GET /api/me` с каждой 401 и `AUTH_REQUIRED`, `sessionCount(id1)` = 0; сессия пользователя 2 даёт 200, `sessionCount(id2)` = 1. Вне транзакции `terminateAll(id2)` действует сразу.
  - `terminateOthersKeepsCurrent`: сессии A и B пользователя; `terminateOthers(id, A)`: A 200, B 401, `sessionCount(id)` = 1. `terminateAll` для пользователя без сессий не бросает.
  - `logoutUnregisters`: вход, `POST /api/auth/logout` 204, `sessionCount(id)` = 0.
  - `overrideOnlyInTestProfile` (без контекста, `org.springframework.mock.env.MockEnvironment`, `Settings` = `null`): с `OVERRIDE` = `"2"` без профилей конструктор даёт `IllegalStateException` с сообщением ровно `nm.session.timeout-seconds-override допустимо только в профиле test`; с `setActiveProfiles("test")` создаётся; `"0"` и `"3601"` в профиле `test`: сообщение ровно `nm.session.timeout-seconds-override: целое число от 1 до 3600`.
- Tomcat (`try (RestartableApp app = new RestartableApp(Map.of("nm.session.timeout-seconds-override", "2", "server.tomcat.background-processor-delay", "1s"), SlowSessionController.class).start())`; реестр `app.context().getBean(UserSessionRegistry.class)`, не внедрённый):
  - `idleSessionExpiresOnTomcat` (AUTH-07): вход; `sessionCount(id)` = 1; опрос до `sessionCount(id) == 0` без запросов этой сессией; затем `GET /api/me` с ней 401 и `AUTH_REQUIRED`; `userCount()` = 0 (реестр не накапливает истекшие).
  - `activityExtendsSession`: два входа одного пользователя, B и C; цикл: `GET /api/me` с B даёт 200, пока `sessionCount(id) == 2` (предельный срок 20 с); после цикла B 200, C 401, время от входа B больше 2 с (`System.nanoTime()` теста). На прототипе около 4000 запросов за 6 с; это допустимо.
  - `terminateAllOnTomcat`: две сессии пользователя 1, одна пользователя 2; `terminateAll(id1)` из потока теста: обе 401, сессия пользователя 2 200, `sessionCount(id1)` = 0. Запрос в полёте: новая сессия E пользователя 1, `HttpClient.sendAsync` на `/api/test-session/slow`, `entered.await(10, SECONDS)` истинно, `terminateAll(id1)`, `release.countDown()`: ответ 200, тело ровно `{"session":false}`, в ответе нет `Set-Cookie` с `JSESSIONID=`; затем E 401. В конце `app.stop()`: `sessionCount(id2)` = 0, `userCount()` = 0 у того же объекта реестра.
- `LoginTest`, `CsrfTest`, `SettingsServiceTest`, `SettingsApiTest`: зелёные (прототип: изменение входа их не задевает; `Api` входит через `authentication(...)` и реестр не наполняет).
- `ArchitectureTest`: зелёный без правок (`SlowSessionController` вне `ru.netmon`).

**Команда проверки:** `mvn -B -pl server -am test -Dtest='SessionExpiryTest,LoginTest,CsrfTest,SettingsServiceTest,SettingsApiTest,ArchitectureTest'`; затем полный `mvn -B -pl server -am test -Dsurefire.runOrder=alphabetical` и то же с `reversealphabetical`, оба зелёные.

**Критерии готовности:** тесты зелёные в обоих порядках; `sleep` в тестах нет; `SecurityConfig` и `application*.yml` не изменены; `nm.session.timeout-seconds-override` встречается только в `SessionTimeoutApplier` и тестах; у `SessionTimeoutApplier` нет поля со значением настройки; в отчёте перечислено непроверенное (Windows, поведение сессий за обратным прокси, долгий простой 720 минут на реальном сервере).

**Запрещено:** хранение сессий в БД и Spring Session; любые правки `SecurityConfig` (в том числе `sessionManagement`, `maximumSessions`, `SessionRegistryImpl`, `HttpSessionEventPublisher`, `invalidSessionUrl`); `server.servlet.session.timeout` и переопределение срока в `application*.yml`; бин `HttpSessionListener`; кэш значения настройки; `@DirtiesContext`; запросы проверяемой сессией во время ожидания её истечения.

---

## T1.09. Временная блокировка

**Цель:** после заданного числа неудач вход временно отклоняется, включая одновременные попытки.
**Исполнитель:** backend-core. **Зависит от:** T1.08.

**Файлы:** `S/auth/LoginAttemptService.java`, правка `AppUserRepository.java`, `JsonAuthHandlers.java`, `T/auth/LoginLockTest.java`.

**Контракты:** §4.2 (`failed_attempts`, `locked_until`, настройки `auth.*`), §5.2 (`AUTH_LOCKED`).

**Шаги:** увеличение одним `UPDATE ... SET failed_attempts = failed_attempts + 1, locked_until = CASE ... RETURNING`, без чтения перед записью. Успех сбрасывает счётчик. Срок считается по `Clock`. Заблокированный вход не проверяет пароль и счётчик не увеличивает. `auth.lock_attempts` и `auth.lock_minutes` читаются `Settings.getInt` при каждой неудаче (T1.07, без кэша); значения передаются в `UPDATE` параметрами; новая настройка не пересчитывает уже записанный `locked_until` (§13.2). Тест, меняющий `auth.*`, помечается `@IntegrationTest` и возвращает настройки в `@AfterEach` через `TestDb.restoreSettings()`.

**Обязательные тесты:**
- Граница: `N-1` неудач вход разрешён, `N`-я блокирует; верный пароль при блокировке даёт `AUTH_LOCKED`.
- Точная граница срока по `MutableClock`: за миллисекунду до конца блокировка, в момент конца вход разрешён.
- Успешный вход обнуляет счётчик.
- Гонка: 20 одновременных неверных попыток при `N=5` дают `failed_attempts >= 5` и блокировку, без 500.
- Неизвестный логин не вызывает ошибку и не создаёт строк.
- Другой пользователь не затронут.
- Вход с паролем 73 байта (`"a".repeat(73)`) даёт 401 `AUTH_FAILED`, не 500 (`matches` даёт `false`, проверено прототипом T1.06), и считается неудачей.

**Команда проверки:** `mvn -B -pl server -am test -Dtest='LoginLockTest,ArchitectureTest'`

**Критерии готовности:** тесты зелёные, настройки `auth.*` читаются через `Settings`.

**Запрещено:** блокировка по IP; постоянная блокировка.

---

## T1.10. Первый администратор

**Цель:** первый администратор создаётся только из явно переданных при установке данных, общего пароля по умолчанию нет.
**Исполнитель:** backend-core. **Зависит от:** T1.09.

**Файлы:** `S/auth/AdminBootstrap.java`, `S/auth/PasswordPolicy.java`, `T/auth/AdminBootstrapTest.java`, `T/auth/PasswordPolicyTest.java`.

**Контракты:** §13 (`nm.bootstrap.*`), §5.2 «Пароли», AUTH-06.

**Шаги:** `ApplicationRunner`: нет пользователей и заданы логин и файл пароля: создать ADMIN. Содержимое файла: UTF-8, BOM и завершающий перевод строки отбрасываются. `PasswordPolicy` по §5.2 «Пароли» (не меньше 8 символов, не больше 72 байт UTF-8; ревью T1.05: `encode` бросает `IllegalArgumentException` на пароле длиннее 72 байт); нарушение прерывает запуск сообщением `"Пароль первого администратора: " + сообщение политики`.

**Обязательные тесты:**
- Файл задан, пользователей нет: администратор создан, вход работает.
- Пользователи уже есть: файл не применяется, существующий пароль не меняется.
- Файла нет и нет настроек: приложение запускается, в логе инструкция, вход невозможен.
- Файл указан, но отсутствует; файл пустой; пароль 7 символов; пароль 73 байта: запуск прерывается с понятным сообщением.
- `PasswordPolicyTest` (без Spring): `null`, пусто, 7 символов дают «Не менее 8 символов»; 8 символов, 72 `a`, 36 `я` проходят; 73 `a`, 37 `я` дают «Не более 72 байт в UTF-8 (72 латинских или 36 русских букв)»; 8 символов из суррогатных пар (`"😀".repeat(8)`, 32 байта) проходят.
- Файл с BOM и CRLF: пароль читается верно.
- Пароль не появляется в логе (`LogCapture`).
- В `R/` и `application.yml` нет `nm.bootstrap.*` со значением (поиск по ресурсам из теста).

**Команда проверки:** `mvn -B -pl server -am test -Dtest='AdminBootstrapTest,PasswordPolicyTest,ArchitectureTest'`

**Критерии готовности:** тесты зелёные. Запрос значений у установщика: T8.06.

**Запрещено:** пароль в коде, ресурсах, значениях по умолчанию.

---

## T1.11. Управление пользователями

**Цель:** администратор создаёт пользователя, меняет роль, сбрасывает пароль и отключает, не теряя записей журнала и последнего администратора.
**Исполнитель:** backend-core. **Зависит от:** T1.10.

**Файлы:** `S/auth/UserAdminService.java`, `UserController.java`, `T/auth/UserAdminTest.java`, дополнение `security-matrix/auth.csv`. `PasswordPolicy` (T1.10) используется без изменений.

**Контракты:** §5.1 (`CurrentUser` есть снимок), §5.2 (`/api/users*`, «Пароли», «Журнал действий и журнал событий»), §7.2 «Запись журналов», §15 A-3.

**Шаги:** отключение, смена роли (ревью T1.05: `CurrentUser` в сессии хранит роль на момент входа, понижение иначе не действовало бы до выхода) и сброс пароля вызывают `UserSessionRegistry.terminateAll(id)` (§5.1 «Сессии»); вызов внутри транзакции изменения допустим, реестр сам откладывает завершение до фиксации. Пароль проверяет `PasswordPolicy`: 400 `VALIDATION_FAILED`, поле `password` (создание) или `newPassword` (сброс). Запись в журнал действий без пароля и хэша. Проверка последнего администратора под блокировкой строк `SELECT ... WHERE role='ADMIN' AND active FOR UPDATE`.

**Обязательные тесты:**
- Создать, сменить роль, сбросить пароль: старый пароль не работает, новый работает.
- AUTH-04: отключённый не входит, его активная сессия получает 401, его записи `audit_log` остались.
- Дубль логина без учёта регистра 409; пароль 7 символов 400, 8 проходит; 73 байта (`"a".repeat(73)`, `"я".repeat(37)`) 400 `VALIDATION_FAILED` с полем, не 500, 72 байта проходят; то же для сброса; пустая и неизвестная роль 400.
- Смена роли: действующая сессия пользователя (вход до смены) получает 401; после нового входа `GET /api/me` показывает новую роль; понижение `ADMIN` до `OPERATOR` сразу закрывает ему `GET /api/audit` (401, затем после входа 403).
- Нельзя отключить или понизить последнего действующего администратора: 409 `LAST_ADMIN`.
- Гонка: два администратора одновременно отключают друг друга, остаётся хотя бы один действующий.
- В ответах API нет `password`, `passwordHash`.
- Дежурный 403 на всех операциях; несуществующий id 404.

**Команда проверки:** `mvn -B -pl server -am test -Dtest='UserAdminTest,ArchitectureTest'`

**Критерии готовности:** тесты зелёные, пользователи физически не удаляются.

**Запрещено:** эндпоинт удаления пользователя; возврат хэша.

---

## T1.12. Смена своего пароля

**Цель:** пользователь меняет свой пароль, старый сразу перестаёт работать.
**Исполнитель:** backend-core. **Зависит от:** T1.11.

**Файлы:** `S/auth/OwnPasswordController.java`, `T/auth/OwnPasswordTest.java`, дополнение `auth.csv`.

**Контракты:** §5.2 (`/api/me/password`).

**Шаги:** проверить старый, применить `PasswordPolicy`, новый хэш, завершить прочие сессии пользователя `UserSessionRegistry.terminateOthers(user.id(), request.getSession(false))` (§5.1 «Сессии»), текущая остаётся. Журнал действий без значений.

**Обязательные тесты:**
- AUTH-05: после смены старый пароль не входит, новый входит. Обе роли.
- Неверный старый 400 `OLD_PASSWORD_INVALID`, пароль не изменён.
- Слабый новый 400; новый 73 байта 400 `VALIDATION_FAILED` с полем `newPassword`, не 500; новый равный старому 400.
- Другая сессия этого пользователя получает 401, текущая работает.
- Неверный старый не считается попыткой блокировки входа (проверка счётчика).
- Тело запроса и значения паролей нет в логах.

**Команда проверки:** `mvn -B -pl server -am test -Dtest='OwnPasswordTest,ArchitectureTest'`

**Критерии готовности:** тесты зелёные.

**Запрещено:** правка чужого пароля этим эндпоинтом.

---

## T1.13. Типы и группы устройств

**Цель:** типы и группы ведутся данными, новый тип добавляется без изменения кода.
**Исполнитель:** backend-core. **Зависит от:** T1.07.

**Файлы:** `S/device/DeviceTypeController.java`, `DeviceTypeRepository.java`, `DeviceGroupController.java`, `DeviceGroupRepository.java`, `T/device/TypesGroupsTest.java`, `security-matrix/device.csv`.

**Контракты:** §5.2, §4.2, §4.3 (правила удаления).

**Шаги:** CRUD, журнал действий, понятные сообщения при нарушении ограничений.

**Обязательные тесты:**
- DEV-06: создать новый тип через API и создать им устройство (прямой вставкой `TestData`).
- DEV-07: группа создаётся и правится.
- Дубль имени 409; пустое имя, имя 129 символов 400; значок с недопустимыми символами (`../x`, `<b>`) 400.
- Несуществующий профиль по умолчанию 400.
- Удаление типа при любом устройстве, включая удалённое: 409. Удаление группы при устройстве или секрете: 409. Без ссылок: 204.
- Дежурный: чтение 200, правка 403.

**Команда проверки:** `mvn -B -pl server -am test -Dtest='TypesGroupsTest,ArchitectureTest'`

**Критерии готовности:** тесты зелёные.

**Запрещено:** список типов в коде (enum); правка `device`.

---

## T1.14. Устройство: создание, просмотр, правка

**Цель:** все поля модели устройства сохраняются и читаются, неверные IP и координаты отклоняются понятно.
**Исполнитель:** backend-core. **Зависит от:** T1.13.

**Файлы:** `S/device/DeviceController.java`, `DeviceService.java`, `DeviceRepository.java`, `DeviceValidator.java`, `T/device/DeviceCrudTest.java`, `T/device/DeviceValidatorTest.java`, дополнение `device.csv`.

**Контракты:** §5.2, §5.4, §7.2 (`DeviceCatalog.find`, `create`), §4.2, события `DeviceChanged`, `catalog-changed`.

**Шаги:**
1. Единый `DeviceValidator` используется API и `DeviceCatalog.create` (импорт T7.06 использует его же).
2. Профиль по умолчанию берётся из типа. Правка по `version`. `status` через API не меняется.
3. `PATCH /location`.
4. Журнал действий со старым и новым значением.
5. После фиксации событие `DeviceChanged` и публикация `catalog-changed`.

**Обязательные тесты:**
- DEV-01: все поля §5.4 записываются и читаются; время в UTC.
- Проверка IP (таблично): проходят `10.0.0.1`, `0.0.0.0`, `255.255.255.255`; отклоняются `256.1.1.1`, `10.0.0`, `10.0.0.1/24`, `010.0.0.1`, `abc`, `::1`, пусто, пробелы.
- Координаты (таблично): проходят границы ±90, ±180; отклоняются 90.000001, -180.000001, не числа, отсутствие. DEV-03: сообщение на русском и `fields[].field`.
- Имя пустое и 256 символов отклоняются; HTML в имени сохраняется как есть (текст).
- Несуществующие `typeId`, `groupId`, `profileId`: 400. Тип без профиля по умолчанию и без `profileId`: 400.
- Устаревшая `version`: 409, данные не изменены. Несуществующий id: 404.
- Попытка передать `status` и `deletedAt` не влияет.
- Журнал содержит старое и новое. Откат не публикует событий.
- Дежурный: чтение 200, правка 403.

**Команда проверки:** `mvn -B -pl server -am test -Dtest='DeviceCrudTest,DeviceValidatorTest,ArchitectureTest'`

**Критерии готовности:** тесты зелёные, валидатор нигде не продублирован.

**Запрещено:** писать столбцы статуса; дубль IP и удаление (следующие карточки).

---

## T1.15. Уникальность IP

**Цель:** дубль IP среди действующих отклоняется понятной ошибкой, даже при одновременных запросах.
**Исполнитель:** backend-core. **Зависит от:** T1.14.

**Файлы:** правка `DeviceRepository.java`, `DeviceService.java`, `T/device/DeviceIpUniquenessTest.java`.

**Контракты:** §4.3, §5.2 (`DEVICE_IP_DUPLICATE`).

**Шаги:** перехват нарушения `ux_device_ip_active` (по имени ограничения) и преобразование в 409 с `fields[ip]`. Предварительный SELECT не использовать.

**Обязательные тесты:**
- Создание дубля 409; правка на занятый IP 409; правка без смены IP проходит.
- Написание с разной записью, эквивалентное значение `inet`, не проходит как новое.
- Гонка: 10 одновременных созданий с одним IP дают ровно 1 успех и 9 ответов 409, ни одного 500.
- Гонка: одновременная правка двух устройств на один IP: одна проходит.
- Прочие ограничения БД не маскируются под дубль IP.

**Команда проверки:** `mvn -B -pl server -am test -Dtest='DeviceIpUniquenessTest,ArchitectureTest'`

**Критерии готовности:** тесты зелёные, повтор прогона 3 раза без сбоев (в отчёте).

**Запрещено:** блокировки на уровне приложения.

---

## T1.16. Мягкое удаление

**Цель:** удалённое устройство исчезает из всех списков, история остаётся, IP снова свободен.
**Исполнитель:** backend-core. **Зависит от:** T1.15.

**Файлы:** правка `DeviceService.java`, `DeviceRepository.java`, `DeviceController.java`, `T/device/DeviceSoftDeleteTest.java`.

**Контракты:** §4.3, §5.2, §7.2 (`DeviceDeleted`).

**Шаги:** одна транзакция: `deleted_at`, обнуление `profile_id` и `group_id`, `DeviceDeleted` внутри транзакции, журнал действий, публикация `catalog-changed` с `DELETED` после фиксации.

**Обязательные тесты:**
- DEV-02: после удаления 404 на чтение, нет в `GET /api/devices` и `/api/devices/map`; его строки `event` и `alarm` на месте (вставлены `TestData`), событие читается прямым запросом.
- DEV-04: IP можно использовать снова; повторное создание и удаление три раза подряд работают.
- Повторное удаление, правка и `PATCH /location` удалённого: 404.
- Гонка: удаление и правка одновременно не дают устройства «живого и удалённого».
- Устаревшие строки: удалённое устройство не блокирует удаление его группы и профиля.
- Подписчик `DeviceDeleted`, бросивший исключение, откатывает удаление (проверка транзакции).
- Дежурный 403.

**Команда проверки:** `mvn -B -pl server -am test -Dtest='DeviceSoftDeleteTest,ArchitectureTest'`

**Критерии готовности:** тесты зелёные, физического удаления строк нет.

**Запрещено:** восстановление удалённых, закрытие тревог (T6.17), удаление секретов здесь (T1.22 подписывается).

---

## T1.17. Таблица устройств и данные карты

**Цель:** таблица ищет, сортирует, фильтрует и делит на страницы одним `DeviceFilter`, общим с выгрузкой и картой.
**Исполнитель:** backend-core. **Зависит от:** T1.16.

**Файлы:** `S/device/DeviceQuery.java`, правка `DeviceController.java`, `DeviceService.java`, `T/device/DeviceListTest.java`.

**Контракты:** §5.1 (страница), §5.2 (`/api/devices`, `/api/devices/map`), §5.3, §7.2 (`DeviceCatalog.streamActive`).

**Шаги:** сборка SQL с параметрами и белым списком сортировки. `streamActive` чтением порциями по 500 без накопления в памяти. Ответ карты содержит `seq` из `StreamPublisher.currentSeq()`, прочитанный до запроса.

**Обязательные тесты:**
- DEV-05: каждый фильтр отдельно и все сразу; подстрока без учёта регистра, кириллица; поиск по IP.
- Спецсимволы в `q`: `%`, `_`, `'`, `\`, `'; drop table device;--` ищутся буквально, не ломают запрос.
- Сортировка каждым полем в обе стороны; стабильный порядок при равных значениях (между страницами нет дублей и пропусков).
- Постраничность: последняя страница, страница за концом пусто, `total` верный.
- Неизвестный `status`, нечисловой `typeId`: 400.
- `streamActive` возвращает то же, что таблица, при том же фильтре (1200 устройств).
- `/api/devices/map`: только нужные поля, без адреса и примечания; `seq` присутствует.
- Удалённые отсутствуют всюду.

**Команда проверки:** `mvn -B -pl server -am test -Dtest='DeviceListTest,ArchitectureTest'`

**Критерии готовности:** тесты зелёные.

**Запрещено:** сортировка в памяти; страница интерфейса.

---

## T1.18. Проверка формата профиля

**Цель:** чистый валидатор принимает только корректный профиль §9 и требует источник или пометку «не подтверждено».
**Исполнитель:** backend-core. **Зависит от:** T1.07.

**Файлы:** `S/profile/ProfileInput.java`, `CheckInput.java`, `ProfileValidator.java`, `T/profile/ProfileValidatorTest.java`.

**Контракты:** §9.1, §9.2.

**Шаги:** без БД и Spring. Возвращает список `FieldError`, адрес поля вида `checks[1].params.port`.

**Обязательные тесты (табличные, без оборудования: OID в тестах вымышлены явно и помечены как тестовые):**
- Ровно одна основная: 0 и 2 отклоняются. Основная необязательная или выключенная отклоняется.
- Дубль `code`; недопустимый `code`.
- Границы `intervalSec` 4/5/86400/86401, `timeoutMs` 99/100/120000/120001, `failCount` и `successCount` 0.
- `warn > crit` при ABOVE и `warn < crit` при BELOW отклоняются; отрицательный гистерезис.
- TCP порт 0, 1, 65535, 65536, нечисло, отсутствие.
- SCPI: нет команды, нет порта, шаблон не компилируется.
- SNMP: неизвестный `op`; `PERCENT_USED` без `freeOid`; `WALK_AGG` без `aggregate`; `filterRegex` не компилируется; OID с буквами.
- Источник: параметр с OID, портом или командой без `source` и без `unconfirmed` отклоняется; `unconfirmed=true` без примечания отклоняется; PING без источника проходит.
- Пустой OID при `enabled=true` отклоняется; при `enabled=false, unconfirmed=true` проходит.
- Лишние поля `params` отклоняются (опечатка не теряется молча).

**Команда проверки:** `mvn -B -pl server -am test -Dtest='ProfileValidatorTest,ArchitectureTest'`

**Критерии готовности:** тесты зелёные, класс без зависимостей от БД.

**Запрещено:** значения оборудования в `main`.

---

## T1.19. API профилей

**Цель:** администратор правит профиль без перезапуска, все поля сохраняются, используемый профиль не удаляется.
**Исполнитель:** backend-core. **Зависит от:** T1.18.

**Файлы:** `S/profile/ProfileController.java`, `ProfileService.java`, `ProfileRepository.java`, `T/profile/ProfileApiTest.java`, `security-matrix/profile.csv`.

**Контракты:** §5.2, §9.1, §4.3, §7.2 (`ProfileCatalog`, `ProfileChanged`).

**Шаги:** правка в транзакции по `version`; проверки сопоставляются по `code`: совпавшая сохраняет `id`, новая добавляется, отсутствующая удаляется. `ProfileChanged` после фиксации. Журнал действий.

**Обязательные тесты:**
- PRF-02: все поля проверки записываются и читаются точно, включая `params`.
- PRF-04: `unconfirmed` и примечание возвращаются в API.
- PRF-05: смена `intervalSec` видна через `ProfileCatalog.checks` сразу.
- PRF-01: `ProfileChanged` публикуется только после фиксации, без перезапуска значение читается новым.
- Правка сохраняет `id` при том же `code`; `check_state` этой проверки на месте; удалённая проверка исчезает с её `check_state`.
- Смена основной проверки с одной на другую в одной правке не нарушает `ux_profile_check_primary`.
- PRF-06: удаление используемого 409 со списком устройств и типов; с устройством только удалённым 204; без ссылок 204.
- Устаревшая `version` 409. Неверный профиль: 400 с адресом поля, данные не изменены.
- Дубль имени 409. Дежурный: чтение 200, правка 403.

**Команда проверки:** `mvn -B -pl server -am test -Dtest='ProfileApiTest,ArchitectureTest'`

**Критерии готовности:** тесты зелёные. Применение к планировщику проверяется в T3.12.

**Запрещено:** правила трапов (T4.13); обращение к планировщику.

---

## T1.20. Начальные профили

**Цель:** при установке появляются восемь профилей и типов, правки администратора не перезаписываются.
**Исполнитель:** backend-core. **Зависит от:** T1.19.

**Файлы:** `R/seed/profiles/` восемь файлов §9.4, `S/profile/ProfileSeeder.java`, `T/profile/ProfileSeederTest.java`.

**Контракты:** §9.1, §9.4, раздел 6 ТЗ (умолчания). Содержимое на этом шаге: каждый профиль содержит одну основную проверку `PING` с интервалом 60 с, таймаутом 2 с, N=3, M=2. Остальные проверки дополняют T3.18, T4.08–T4.10, T5.05.

**Шаги:** загрузка через `ProfileValidator`; неверный файл прерывает запуск с именем файла. Создание типа с тем же именем и профилем по умолчанию. В одной транзакции на профиль. Журнал действий не пишется (§7.2).

**Обязательные тесты:**
- PRF-03: созданы ровно 8 профилей и 8 типов с заданными ключами.
- Повторный запуск (`RestartableApp`) не дублирует.
- Правка администратора сохраняется после перезапуска; удалённый администратором профиль после перезапуска не возвращается.
- Сломанный файл из тестового каталога: сообщение с именем файла.
- В `seed/` на этом шаге нет ни одного OID (поиск `1.3.6.1`).
- Значения умолчания взяты из JSON: тест правит копию файла и видит новое значение.

**Команда проверки:** `mvn -B -pl server -am test -Dtest='ProfileSeederTest,ArchitectureTest'`

**Критерии готовности:** тесты зелёные.

**Запрещено:** заполнять OID, порты R&S, команды SCPI: это T4 и T5.

---

## T1.21. Шифрование секретов и ключ

**Цель:** секрет в БД зашифрован AES-256-GCM, ключ лежит в файле вне БД, потеря ключа не приводит к его подмене.
**Исполнитель:** backend-core. **Зависит от:** T1.07.

**Файлы:** `S/secret/SecretCipher.java`, `SecretKeyFile.java`, `SecretKeyStartupCheck.java`, `T/secret/SecretCipherTest.java`, `T/secret/SecretKeyFileTest.java`, `T/secret/SecretKeyTestDirTest.java`, `T/testsupport/TestPostgres.java` (только добавление `nm.data-dir`).

**Контракты:** §10 пункты 1, 2; §12.2 «Каталог данных в тестах»; §13.

**Шаги:**
1. До первого контекста, который создаёт ключ: в `TestPostgres.properties()` добавить `nm.data-dir` на временный каталог JVM (`Files.createTempDirectory("nm-test-data-")`, один на процесс, удаление хуком завершения). `application-test.yml` не меняется.
2. Реализовать правила §10.2 точно. Путь ключа только из `nm.secret-key-file`. Запись нового ключа: во временный файл, затем атомарное переименование. Проверка при старте смотрит `count(*)` в `access_secret`.
3. Модульные тесты `SecretKeyFile` работают на `@TempDir` и не поднимают контекст.

**Обязательные тесты:**
- Шифрование и расшифровка, кириллица, пустая строка, длина 10 КБ.
- Два шифрования одного текста дают разный результат (случайный nonce).
- Изменённый байт шифртекста, тега, nonce; слишком короткий массив: типизированная ошибка, без раскрытия значения в сообщении.
- Другой ключ: та же ошибка.
- Нет файла, нет секретов: файл создан, 32 байта после Base64. Повторный запуск не меняет ключ.
- Нет файла, секреты есть: запуск прерывается, новый файл НЕ создан.
- Файл с неверным Base64, 31 байтом, пустой: запуск прерывается. Файл с завершающим переводом строки читается.
- SEC-02: шифртекст в таблице не содержит открытого значения в байтах.
- Ключ и его значение не появляются в логах (`LogCapture`).
- `SecretKeyTestDirTest` (`@IntegrationTest`): файл по `nm.secret-key-file` существует, лежит внутри `nm.data-dir`, а `nm.data-dir` внутри `java.io.tmpdir`; путь ключа не лежит внутри рабочего каталога процесса (`user.dir`), то есть не в дереве исходников; `RestartableApp` после перезапуска читает тот же ключ (расшифровывает шифртекст первого запуска).

**Команда проверки:** `mvn -B -pl server -am test -Dtest='SecretCipherTest,SecretKeyFileTest,SecretKeyTestDirTest,ArchitectureTest'`

**Критерии готовности:** тесты зелёные. Права на файл ключа в Windows: [проверка человеком], T8.06.

**Запрещено:** ключ в БД, настройках или `application.yml`; ключ на основе пароля; свои криптопримитивы; AES-CBC.

---

## T1.22. API секретов

**Цель:** секрет принимается только на запись с привязкой к устройству или группе, а опрос получает его через `SecretResolver`.
**Исполнитель:** backend-core. **Зависит от:** T1.21.

**Файлы:** `S/secret/SecretController.java`, `SecretService.java`, `SecretRepository.java`, `SecretJacksonConfig.java`, `T/secret/SecretApiTest.java`, `T/secret/SecretResolverTest.java`, `security-matrix/secret.csv`.

**Контракты:** §5.2, §7.2 (`SecretResolver`, `DeviceDeleted`), §10.3, §10.4.

**Шаги:** метаданные и значения в разных классах ответа. Журнал действий только `{id,snmpVersion,deviceId,groupId}`. Бин Jackson `Module` в `S/secret/SecretJacksonConfig.java` сериализует любой `SnmpCredentials` строкой `"***"` (§10.4). Подписка `DeviceDeleted` удаляет секрет устройства. Нарушение ссылки на устройство или группу: 400; удалённое устройство: 400 (проверка `deleted_at`).

**Обязательные тесты:**
- SEC-01: дежурный 403 на всех методах, включая чтение метаданных; секреты только в `access_secret`.
- DEV-07: секрет группы применяется к устройству группы.
- SEC-03: ответы POST, PUT, GET не содержат значения и тех полей (поиск уникальной строки во всём теле); `ciphertext` тоже не возвращается.
- Привязка: и к устройству и к группе, ни к чему: 400. Второй секрет на тот же объект: 409.
- Поля версий: V2C без `community`, V3 без `user`: 400, в сообщении ошибки нет значения других полей.
- Разрешение: секрет устройства приоритетнее группового; нет секрета: пусто; повреждённый шифртекст: пусто и компонент DEGRADED, значения в логе нет.
- `DeviceDeleted` удаляет секрет устройства и не трогает секрет группы.
- `audit_log` не содержит уникальной строки секрета.
- Внедрённый `ObjectMapper` приложения сериализует `V2c` и `V3` с уникальными строками в `"***"`, уникальных строк в выводе нет.

**Команда проверки:** `mvn -B -pl server -am test -Dtest='SecretApiTest,SecretResolverTest,ArchitectureTest'`

**Критерии готовности:** тесты зелёные; `SecretResolver` не доступен контроллерам других модулей.

**Запрещено:** любой путь чтения значения через API; `toString` со значением.

---

## T1.23. Секреты в логах и ответах: сквозная проверка

**Цель:** автотест доказывает, что тестовый секрет и пароли не встречаются ни в логах, ни в ответах API.
**Исполнитель:** backend-core. **Зависит от:** T1.22, T1.12.

**Файлы:** `T/secret/SecretLeakSweepTest.java`, `R/logback-spring.xml` (маска имён параметров, только если тест покажет необходимость).

**Контракты:** §10.4, SEC-03, SEC-04.

**Шаги:** уникальные строки для community, паролей v3 и пароля пользователя. Прогнать через все операции секретов и пользователей с уровнем логирования `DEBUG` для `ru.netmon` и `org.springframework.web`, заданным через `LogCapture.level(...)` (не свойством `logging.level.*`, §12.2). В том числе ошибочные запросы.

**Обязательные тесты:**
- Поиск уникальных строк в `LogCapture` после всех операций, включая 400 и 409 на этих эндпоинтах.
- Поиск во всех ответах этих операций.
- Поиск по всем текстовым столбцам `audit_log` и `event`.
- Неверный JSON с секретом в теле: тело не попадает в сообщение об ошибке и лог.
- Тест контроля: запись той же строки в лог заставляет проверку упасть (проверка самого поиска).
- `loginPasswordNotLogged` (ревью T1.05): `LogCapture.level("org.springframework.security", Level.DEBUG)` и `level("ru.netmon", Level.DEBUG)`; удачный и неудачный вход формой с уникальным паролем (в теле и, отдельным запросом, в строке запроса `/api/auth/login?username=…&password=…`): пароля нет в `LogCapture.text()`; положительный контроль, что `DEBUG` Spring Security пишется (в выводе есть строка логгера `org.springframework.security`).

**Команда проверки:** `mvn -B -pl server -am test -Dtest='SecretLeakSweepTest,ArchitectureTest'`

**Критерии готовности:** тест зелёный на `DEBUG`. Логи SNMP проверяет T4.19.

**Запрещено:** снижать уровень логирования ради прохождения теста.

---

## T1.24. Поток SSE

**Цель:** подключённый браузер получает события после фиксации транзакции, а поток не вредит серверу при разрывах и истечении сессии.
**Исполнитель:** backend-core. **Зависит от:** T1.08.

**Файлы:** `S/stream/StreamController.java`, `StreamHub.java`, `StreamClient.java`, замена `SeqStreamPublisher.java` на `TransactionalStreamPublisher.java`, `T/stream/StreamHubTest.java`, `T/stream/StreamHttpTest.java`, `security-matrix/stream.csv`; по шагу «Начатый ответ»: правка `S/common/web/ApiErrorHandler.java`, новые `T/common/CommittedResponseTest.java`, `server/src/test/java/nmtest/common/CommittedErrorsTestController.java`.

**Контракты:** §6, §7.2 (`StreamPublisher`), §5.1 (строки таблицы `ApiErrorHandler` с пометкой «с T1.24»).

**Шаги:** `SseEmitter` с собственной ограниченной очередью и отправляющим потоком на клиента, без блокировки издателя. Heartbeat 15 с с проверкой сессии строго по §6 (объект `HttpSession`, сохранённый при подключении: `IllegalStateException` из `getLastAccessedTime()` или простой по внедрённому `Clock` не меньше `getMaxInactiveInterval()` закрывают поток; T1.08). Период heartbeat настраивается для теста. Тесты истечения и `terminateAll` идут на `RestartableApp` по §12.2 «Сессии на Tomcat»; `terminateAll` вызывается у бина `UserSessionRegistry` из `app.context()` (класс публичный, T1.08).

**Шаг «Начатый ответ» (повторное ревью T1.04, Н-б, Н-в, Н-г; делается до задачи 7):** `ApiErrorHandler` по строкам §5.1 с пометкой «с T1.24»: (1) `AsyncRequestNotUsableException` ищется и в цепочке `getCause()` тем же обходом, что исключения безопасности (не глубже 20 звеньев, без зацикливания), в том числе при неначатом ответе; (2) при `isCommitted()` обрыв клиента (`AsyncRequestTimeoutException`; в цепочке `org.apache.catalina.connector.ClientAbortException` или `AsyncRequestNotUsableException`) даёт `DEBUG` и `return null`; (3) иное при `isCommitted()`, включая серверное `IOException`, даёт `ERROR` со стеком и `throw e`. Порядок проверок §5.1 сохраняется.

**Обязательные тесты:**
- `hello` при подключении, `heartbeat` по таймеру; `seq` строго возрастает.
- Публикация внутри транзакции: при фиксации приходит, при откате не приходит.
- Без сессии 401; дежурный и администратор подключаются.
- Пятый поток одной сессии закрывает старейший; поток другой сессии не затронут.
- Разрыв клиента: ресурс освобождён, подсчёт активных потоков возвращается к нулю; ошибок уровня ERROR в логе нет.
- (из ревью T1.04, Н1) `SseEmitter` создаётся с явным таймаутом (`0L`, без истечения: поток закрывают heartbeat с проверкой сессии и пределы §6), `onTimeout`, `onError` и `onCompletion` снимают клиента; ошибка записи в отправляющем потоке закрывает клиента через `completeWithError` или `complete` без `ERROR` в логе. `ApiErrorHandler` для начатого ответа JSON не пишет (§5.1); тест: после разрыва и после закрытия по переполнению в `LogCapture` нет пути `/api/stream` на уровне `ERROR` и нет `text/event-stream`-ответа с JSON ошибки.
- `CommittedResponseTest` (шаг «Начатый ответ»; `RestartableApp` с `extraSources` `nmtest.common.CommittedErrorsTestController`, `java.net.http.HttpClient` на `http://127.0.0.1:<port>`, пути `/api/test-committed/**`, запросы с сессией после входа по T1.05): (а) метод пишет 64 КиБ `application/octet-stream`, делает `flushBuffer()` и бросает `IllegalStateException(<маркер>)`: чтение тела клиентом завершается `IOException` (ответ не завершён штатно), в прочитанных байтах нет `"code"`, в `LogCapture` `ERROR` с маркером; (б) то же с `new IOException(<маркер>)` (серверное, не `ClientAbortException`): тоже обрыв у клиента и `ERROR`; (в) клиент закрывает соединение посреди длинного ответа: в логе нет `ERROR` по этому пути; (г) неначатый ответ, метод бросает `IllegalStateException(new AsyncRequestNotUsableException(<маркер>))`: нет `ERROR`, нет JSON `INTERNAL_ERROR` в теле. Если (а) или (б) на Tomcat версии §1 не дают обрыва у клиента, тест не ослабляется: остановиться и сообщить в отчёте (тогда решение меняет architect, вариант буферизации выгрузки).
- Медленный клиент: очередь 1000 переполнена, поток закрыт, издатель не задержан (ограничение по времени на 100 000 публикаций), другие клиенты получили всё.
- Истечение сессии закрывает поток за один период heartbeat; повторное подключение 401.
- `terminateAll` закрывает поток за один период.
- Поток не продлевает сессию.
- Остановка приложения завершается за ограниченное время при открытых потоках.

**Команда проверки:** `mvn -B -pl server -am test -Dtest='StreamHubTest,StreamHttpTest,CommittedResponseTest,ApiErrorsTest,ApiErrorsQaTest,ArchitectureTest'`

**Критерии готовности:** тесты зелёные, повтор 3 раза без сбоев (в отчёте). Браузерная часть MAP-03 и MAP-04: T2.05, T2.06.

**Запрещено:** повтор пропущенных событий; блокирующая запись в потоке издателя.

---

## T1.25. Заголовки безопасности

**Цель:** каждый ответ несёт строгий CSP и защитные заголовки, запрещающие внешние ресурсы.
**Исполнитель:** backend-core. **Зависит от:** T1.07.

**Файлы:** `S/common/web/SecurityHeadersFilter.java`, `T/common/SecurityHeadersTest.java`.

**Контракты:** §11 (заголовки).

**Шаги:** фильтр выставляет заголовки дословно. API отдаёт `application/json`, не HTML.

**Обязательные тесты:**
- Заголовки присутствуют на статике, API, ошибках 401, 403, 404, и на публичной странице входа.
- В CSP нет `unsafe-inline`, `unsafe-eval`, `*`, `http:`, `https:`.
- SEC-05: устройство с именем `<script>alert(1)</script>` возвращается в JSON как строка с верным типом содержимого; нет ни одного ответа `text/html` с пользовательскими данными (ошибка с таким значением в параметре).
- Поток SSE сохраняет `text/event-stream` с заголовками.

**Команда проверки:** `mvn -B -pl server -am test -Dtest='SecurityHeadersTest,ArchitectureTest'`

**Критерии готовности:** тесты зелёные. Совместимость с Leaflet подтверждает T2.03.

**Запрещено:** ослаблять CSP.

---

## T1.26. Матрица прав

**Цель:** тест проверяет каждую строку матрицы раздела 7 ТЗ на каждом эндпоинте и падает на эндпоинте без матрицы или без `@PreAuthorize`.
**Исполнитель:** backend-core. **Зависит от:** T1.12, T1.17, T1.20, T1.23, T1.24.

**Файлы:** `T/auth/SecurityMatrixTest.java`, `T/auth/EndpointInventory.java`, дополнения в `security-matrix/*.csv`, правка `T/ArchitectureTest.java` (активировать правило 3 без `allowEmptyShould`).

**Контракты:** раздел 7 ТЗ, §5.1, §5.2, §12.3.

**Шаги:** инвентарь из `RequestMappingHandlerMapping`. Для каждой строки CSV и роли запрос с подставленными идентификаторами. `allow` значит не 401 и не 403 (404 и 400 допускаются), `deny` значит 403, `public` значит без сессии не 401. Без роли: 401 для всех непубличных. Инвентарь не включает `/error` (`ApiErrorController`), вход и выход не методы контроллеров (§12.3); правило 3 сохраняет отбор `ApiErrorController` (§2.2). Анонимный изменяющий запрос без токена получает 403 `CSRF_INVALID` раньше проверки сессии (§5.1), поэтому проверка «без роли» шлёт верный токен из `/api/auth/csrf`.

**Обязательные тесты:**
- AUTH-03: все эндпоинты задачи 1 трижды (дежурный, администратор, без входа).
- Читаются все файлы `security-matrix/*.csv`, в том числе `auth.csv` (T1.05), `audit.csv` (T1.06), `settings.csv` (T1.07): до T1.26 их ничто не читает (ревью T1.07, п. 1). Файл без заголовка `метод,путь,OPERATOR,ADMIN,ANON`, с другим числом столбцов или значением вне `allow|deny|public`: падение с именем файла и номером строки. Число файлов и строк в отчёте.
- Эндпоинт вне матрицы: падение (проверено тестовым контроллером вне основного пакета).
- Строка матрицы без реального эндпоинта: падение (устаревшая запись).
- Контроллер без `@PreAuthorize`: падение `ArchitectureTest`.
- POST без CSRF: 403 на каждом изменяющем эндпоинте.
- Отдельные строки раздела 7 ТЗ: дежурный читает профили, таблицу, выгружает; не правит устройства и профили, не читает секреты, пользователей, настройки, журнал действий.

**Команда проверки:** `mvn -B -pl server -am test -Dtest='SecurityMatrixTest,ArchitectureTest'`

**Критерии готовности:** тесты зелёные, CSV содержат все эндпоинты задачи 1.

**Запрещено:** менять прикладные права ради прохождения: расхождение с разделом 7 ТЗ это отчёт.

---

## T1.27. Окружение браузерных тестов

**Цель:** один пробный браузерный тест проходит в Firefox 115 ESR и Chrome без обращения в интернет после подготовки.
**Исполнитель:** map-frontend. **Зависит от:** T1.07.

**Файлы:** `scripts/prepare-browsers.sh`, `.ps1`, профили `ui` и `load` в `server/pom.xml`, зависимость `selenium-java` в области `test`, `T/ui/BrowserFactory.java`, `T/ui/UiTestBase.java`, `T/ui/SmokeUiIT.java`, `server/src/test/resources/ui-smoke/` (тестовая страница).

**Контракты:** §1, §12.1, §12.4.

**Шаги:**
1. Скрипт скачивает точные версии §1 в `tools/browsers/`, повторный запуск ничего не качает, суммы SHA-256 записываются и сверяются. Внешние адреса разрешены только в этом скрипте как подготовка на машине с интернетом; в отчёте показать это `compliance-auditor`.
2. `BrowserFactory`: режим без окна, пути из §12.4, свойство `SE_MANAGER` не используется; при отсутствии файла ошибка с указанием на скрипт.
3. `UiTestBase` поднимает `RestartableApp` на тестовой БД. Сбор ошибок консоли браузера: любая ошибка роняет тест.

**Обязательные тесты:**
- `SmokeUiIT`: страница открывается, `navigator.userAgent` содержит `Firefox/115` или `Chrome/156`.
- Негатив: неверный путь к браузеру даёт понятное сообщение, а не зависание.
- Тест без сети: Selenium Manager не обращается в сеть (свойство отключения, проверка в журнале запуска).

**Команда проверки:** `scripts/prepare-browsers.sh && mvn -B -pl server -am verify -Pui -Dit.test=SmokeUiIT -Dnm.ui.browser=firefox && mvn -B -pl server -am verify -Pui -Dit.test=SmokeUiIT -Dnm.ui.browser=chrome`

**Критерии готовности:** тест проходит в обоих браузерах; обычный `mvn -B verify` браузеров не требует. Настоящий Edge, Windows 7: [проверка человеком].

**Запрещено:** Playwright; скачивание браузеров из тестов.

---

## T1.28. Оболочка, ядро JS и вход

**Цель:** после входа открывается одностраничная оболочка с навигацией по ролям и заглушками всех страниц, код ядра работает в Firefox 115.
**Исполнитель:** map-frontend. **Зависит от:** T1.27.

**Файлы:** `R/static/login.html`, `R/static/login/login.js`, `login.css`, `R/static/index.html`, `R/static/css/app.css`, `R/static/js/app.js`, `js/core/{api,router,dom,sse,format}.js`, `js/components/{status,alarm-badge}.js`, `js/pages/*.js` 12 заглушек, `T/ui/LoginUiIT.java`, `T/common/FrontendRulesTest.java`. Заглушки и ядро не считаются прикладным объёмом для ограничения по числу файлов.

**Контракты:** §5.1, §6, §11, раздел 5 ТЗ.

**Шаги:**
1. `api.js` по §5.1, включая получение CSRF и единый разбор ошибки.
2. `status.js`: цвет, значок, русская подпись для `CRITICAL`, `WARNING`, `NO_DATA`, `OK`. Цвет не единственный признак.
3. `format.js`: время Москвы через `Intl.DateTimeFormat` с `Europe/Moscow`.
4. Все тексты русские. Значки встроенным SVG-файлом или символами, без внешних шрифтов.

**Обязательные тесты:**
- UI (оба браузера): верный вход ведёт в оболочку; неверный показывает сообщение; 401 при истёкшей сессии ведёт на вход; выход возвращает на вход.
- Дежурный не видит пунктов «Пользователи», «Секреты», «Журнал действий», «Настройки».
- `format`: показ UTC-времени как московского на границе суток, в браузере с другой зоной.
- Консоль без ошибок и нарушений CSP.
- `FrontendRulesTest` (без браузера): в `static/js` нет `innerHTML`, `insertAdjacentHTML`, `document.write`, `eval`, `new Function`; в `static/` нет `http://`, `https://`, `//cdn` кроме `static/vendor/` лицензий; нет встроенных `<script>` без `src` и атрибутов `style=` в HTML.
- Значение логина с HTML-разметкой показывается текстом (SEC-05).

**Команда проверки:** `mvn -B -pl server -am test -Dtest=FrontendRulesTest && mvn -B -pl server -am verify -Pui -Dit.test=LoginUiIT -Dnm.ui.browser=firefox && mvn -B -pl server -am verify -Pui -Dit.test=LoginUiIT -Dnm.ui.browser=chrome`

**Критерии готовности:** тесты зелёные; переход между заглушками не перезагружает страницу.

**Запрещено:** npm и сборка; внешние шрифты; бизнес-логика на клиенте вместо проверок API.

---

## T1.29. Страница «Оборудование»

**Цель:** таблица устройств работает с поиском, фильтрами, сортировкой и страницами, администратор правит и удаляет.
**Исполнитель:** map-frontend. **Зависит от:** T1.28, T1.17.

**Файлы:** `R/static/js/pages/devices.js`, `R/static/css/devices.css`, `T/ui/DevicesUiIT.java`.

**Контракты:** §5.2–§5.4, §11, раздел 5 ТЗ.

**Шаги:** состояние фильтров в хэше URL. Ошибки API показываются у поля по `fields[]`. Кнопка выгрузки появится в T7.09. Удаление с подтверждением.

**Обязательные тесты:**
- Администратор: создание, правка, удаление. Устройство исчезает из таблицы.
- Дежурный: нет кнопок правки; страница работает.
- Неверный IP и дубль: сообщение у поля, форма не закрывается, введённое не теряется.
- Конфликт `version`: понятное сообщение.
- Фильтр и сортировка с сохранением при обновлении страницы.
- SEC-05: `<img src=x onerror=...>` в названии и примечании показывается текстом, обработчик не срабатывает.
- 1000 устройств: первая страница не загружает их все (проверка `size`).

**Команда проверки:** `mvn -B -pl server -am verify -Pui -Dit.test=DevicesUiIT -Dnm.ui.browser=firefox && mvn -B -pl server -am verify -Pui -Dit.test=DevicesUiIT -Dnm.ui.browser=chrome`

**Критерии готовности:** тесты зелёные в двух браузерах.

**Запрещено:** файлы других страниц, бэкенд.

---

## T1.30. Страница «Профили»

**Цель:** администратор правит профиль в интерфейсе, все видят проверки и пометку «не подтверждено».
**Исполнитель:** map-frontend. **Зависит от:** T1.28, T1.19.

**Файлы:** `R/static/js/pages/profiles.js`, `R/static/css/profiles.css`, `T/ui/ProfilesUiIT.java`.

**Контракты:** §5.2, §9.1, §9.2, §11.

**Шаги:** формы параметров по виду проверки строятся по §9.2. Значений по умолчанию на клиенте нет: поля новой проверки пусты.

**Обязательные тесты:**
- PRF-01: правка порта или интервала в форме сохраняется, API возвращает новое значение без перезапуска.
- PRF-04: пометка «не подтверждено» заметна и текстом, и значком в списке и карточке.
- Ошибка с адресом `checks[1].params.port` показывается у нужного поля.
- Удаление используемого показывает список устройств (PRF-06).
- Дежурный видит всё только на чтение.
- Значение с HTML показывается текстом.

**Команда проверки:** `mvn -B -pl server -am verify -Pui -Dit.test=ProfilesUiIT -Dnm.ui.browser=firefox && mvn -B -pl server -am verify -Pui -Dit.test=ProfilesUiIT -Dnm.ui.browser=chrome`

**Критерии готовности:** тесты зелёные в двух браузерах. Часть PRF-04 «видна в отчёте»: `tech-writer` перечисляет неподтверждённые параметры в отчёте задачи и в реестре допущений.

**Запрещено:** значения оборудования в JS; правила трапов (T4.18).

---

## T1.31. Страницы пользователей, секретов и своего пароля

**Цель:** администратор ведёт пользователей и секреты, любой пользователь меняет свой пароль, а поля секрета после сохранения не показывают значений.
**Исполнитель:** map-frontend. **Зависит от:** T1.28, T1.12, T1.22.

**Файлы:** `R/static/js/pages/users.js`, `secrets.js`, `account.js`, `R/static/css/admin.css`, `T/ui/AdminPagesUiIT.java`.

**Контракты:** §5.2, §10.3, §11.

**Шаги:** поля секретов и паролей `type=password`, `autocomplete=new-password`, очищаются сразу после отправки. Не хранить значения в переменных вне обработчика и в `localStorage`. Показ секрета: только метаданные.

**Обязательные тесты:**
- Создание, смена роли, отключение; сброс пароля; ошибка последнего администратора показывается.
- SEC-03: после сохранения поле пустое, в DOM, `localStorage` и `sessionStorage` нет значения, таблица его не показывает.
- Смена своего пароля дежурным; неверный старый пароль показывает сообщение.
- Прямой переход дежурного на `#/secrets` показывает отказ, а данных нет.

**Команда проверки:** `mvn -B -pl server -am verify -Pui -Dit.test=AdminPagesUiIT -Dnm.ui.browser=firefox && mvn -B -pl server -am verify -Pui -Dit.test=AdminPagesUiIT -Dnm.ui.browser=chrome`

**Критерии готовности:** тесты зелёные в двух браузерах.

**Запрещено:** файлы других страниц; показ или копирование значений.

---

## T1.32. Страницы журнала действий и настроек

**Цель:** администратор просматривает журнал действий и правит настройки в интерфейсе.
**Исполнитель:** map-frontend. **Зависит от:** T1.28, T1.06, T1.07.

**Файлы:** `R/static/js/pages/audit.js`, `settings.js`, `R/static/css/audit.css`, `T/ui/AuditSettingsUiIT.java`.

**Контракты:** §5.2, §11, OPS-08.

**Шаги:** подписи ключей настроек на русском в файле страницы; неизвестный ключ показывается как есть, а не теряется. Значения старое и новое показываются как текст. Границы поля ввода берутся из `min`/`max` ответа `GET /api/settings` (§5.2 «Настройки»), а не из кода страницы; сообщение об ошибке берётся из `fields[0].message` ответа 400.

**Обязательные тесты:**
- Правка настройки сохраняется и видна после обновления страницы; значение за границей даёт сообщение.
- Изменение устройства появляется в журнале действий со старым и новым значением; фильтр по периоду показывает время Москвы.
- Значение с HTML показывается текстом.
- Дежурный: отказ и отсутствие данных.

**Команда проверки:** `mvn -B -pl server -am verify -Pui -Dit.test=AuditSettingsUiIT -Dnm.ui.browser=firefox && mvn -B -pl server -am verify -Pui -Dit.test=AuditSettingsUiIT -Dnm.ui.browser=chrome`

**Критерии готовности:** тесты зелёные в двух браузерах.

**Запрещено:** файлы других страниц, бэкенд.

---

## T1.33. Приёмка задачи 1

**Цель:** сквозной сценарий подтверждает критерий приёмки задачи 1 на настоящем Tomcat, а данные и секреты переживают перезапуск.
**Исполнитель:** backend-core. **Зависит от:** T1.25, T1.26, T1.29, T1.30, T1.31, T1.32.

**Файлы:** `T/acceptance/Task1AcceptanceTest.java`, `T/acceptance/Task1RestartTest.java`.

**Контракты:** все §, раздел 11 ТЗ (приёмка задачи 1).

**Шаги:** только API и `RestartableApp`, с реальными сессиями и CSRF.

**Обязательные тесты:**
- Вход первого администратора из файла; создание дежурного; вход дежурного.
- Создание типа, профиля, устройства; правка; дежурный видит, но не правит; удаление; журнал действий и история на месте.
- Перезапуск: пользователи, устройства, профили, настройки, журналы на месте (NFR-03 в этой части); старая сессия требует нового входа; расшифровка секрета через `SecretResolver` даёт исходное значение.
- Запуск без файла ключа при наличии секретов прерывается с сообщением, ключ не создан (SEC-02).
- Дамп `access_secret` не содержит открытого значения.
- Сервер без интернета: при запуске нет обращений к адресам вне loopback (проверка: `ProxySelector` с записью, тест падает при обращении).

**Команда проверки:** `mvn -B verify && scripts/offline-verify.sh`

**Критерии готовности:** вся сборка зелёная, офлайн-сборка зелёная. В отчёте для `qa-verifier`: таблица фич задачи 1 и пометка: Windows, Firefox на Windows 7, настоящий Edge, PowerShell-скрипты: [проверка человеком].

**Запрещено:** правка прикладного кода в этой карточке. Найденный дефект возвращается владельцу карточки.
