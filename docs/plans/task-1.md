# Задача 1. Каркас и база: карточки микрозадач

Фичи: AUTH-01..08, DEV-01..07, PRF-01..06, SEC-01..05, LOG-04. Приёмка ТЗ: устройство создаётся, правится, удаляется, права по ролям работают. Контракты: `docs/architecture.md` (далее «§»). Порядок и параллельность: `docs/plans/backlog.md`.

## Правила для всех карточек

**Запрещено всегда:** править `docs/TZ.md`, `docs/architecture.md`, `CLAUDE.md`, `.claude/`; делать операции git; добавлять зависимости вне T1.01 и T1.27; менять `contract/` после T1.03 и применённые миграции после T1.02; вводить OID, порты и команды оборудования; обращаться к интернету из кода приложения. Нужно изменение вне зоны карточки: остановиться на этом месте, сообщить в отчёте.

**Готово значит:** команда проверки карточки завершается успешно, в отчёте перечислено непроверенное. С T1.03 команда включает `ArchitectureTest`.

**Постраничные ресурсы (с T1.04, круг 1 ревью):** `ORDER BY` строится только из `PageQuery.orderBy(tieBreaker)` с уникальным столбцом (`id`) и подставляется как есть, `LIMIT ?`/`OFFSET ?` параметрами из `size()` и `offset()`. Обязательный тест каждого постраничного эндпоинта: не меньше трёх записей с одинаковым значением поля сортировки, `size=1` (или меньше их числа), обход всех страниц даёт каждую запись ровно один раз; страница за последней 200 с пустым `items` и настоящим `total`.

**Тела запросов и ограничения (с T1.05, повторное ревью T1.04 Н-а, Н-е):** тело `@RequestBody` верхнего уровня всегда объект JSON, не коллекция, не `Map` и не массив (§2.2 п. 5, проверяет `ArchitectureTest`); списки передаются полем объекта. Кросс-параметрическое ограничение метода контроллера допускается только с явным `message` на русском; его ошибка приходит с `field: null` (§5.1). Правки `ApiErrorHandler` после T1.04 делают только карточки, где это прямо записано (T1.05, T1.24).

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
**Исполнитель:** backend-core. **Зависит от:** T1.04.

**Файлы:** `S/auth/SecurityConfig.java`, `AppUserRepository.java`, `AppUserDetailsService.java`, `JsonAuthHandlers.java`, `MeController.java`, `S/common/web/ApiErrorController.java`, `T/auth/LoginTest.java`, `T/auth/CsrfTest.java`, `T/common/ErrorPageTest.java`, `server/src/test/java/nmtest/common/FailingFilter.java`, `server/src/test/resources/security-matrix/auth.csv`; по шагу 6: правка `S/common/web/ApiErrorHandler.java`, `T/ArchitectureTest.java`, `T/common/ApiErrorsTest.java`, новые `server/src/test/java/nmtest/common/ErrorsExtraTestController.java` и `nmtest/common/OrderedRange.java` (тестовое кросс-параметрическое ограничение с валидатором; `ErrorsTestController` и `ApiErrorsQaTest` не правятся); временный `R/static/login.html` не создаётся, его делает T1.28.

**Контракты:** §5.1, §5.2 (auth, me), §12.3.

**Шаги:**
1. `SecurityFilterChain`: `formLogin` с `loginProcessingUrl=/api/auth/login`, обработчики JSON, `logoutUrl=/api/auth/logout`, CSRF по §5.1, `@EnableMethodSecurity`, запрет всего, кроме публичного списка §5.1. Для `/api/**` без сессии 401 JSON, иначе перенаправление.
2. `BCryptPasswordEncoder` стойкостью 10. `DaoAuthenticationProvider` с проверкой `active`.
3. `GET /api/auth/csrf`, `GET /api/me`.
4. Заполнить `auth.csv` эндпоинтами этой карточки.
5. (из ревью T1.04, Н4) `ApiErrorController` по абзацу §5.1 под таблицей: реализует `ErrorController` Spring Boot (пакет интерфейса взять из версии Spring Boot §1, проверить по классам в `~/.m2`), один метод на `/error` для всех методов HTTP, ответ `ResponseEntity` JSON. Функция кода 4xx выносится из `ApiErrorHandler` в общую пакетную статическую функцию без изменения поведения. В `SecurityConfig` диспетчеризация `DispatcherType.ERROR` разрешена (`dispatcherTypeMatchers(ERROR).permitAll()`), иначе ответ `/error` снова проходит проверку сессии. Свойства `server.error.*` не задаются. При `response.isCommitted()` (Tomcat делает `include` страницы ошибки в начатый ответ) `ApiErrorController` тело не пишет и в лог не пишет (§5.1, абзац под таблицей).
6. (повторное ревью T1.04, Н-а, Н-д, Н-е, Н-ж) Доработка `ApiErrorHandler` и правил по §5.1 и §2.2: (а) правило §2.2 п. 5 в `ArchitectureTest` (тело верхнего уровня не коллекция); (б) для `ErrorResponse` 4xx заголовки `getHeaders()` копируются в ответ, кроме `Content-Type` и `Content-Length`; (в) `getCrossParameterValidationResults()` дают поля `field=null` с `getDefaultMessage()` после ошибок параметров. Остальное поведение `ApiErrorHandler` не меняется.

**Обязательные тесты:**
- AUTH-01: 401 на `/api/me` и любой другой путь `/api/**`; статический `/index.html` без сессии перенаправляется на вход; `/login.html` доступен (404 здесь не считается отказом доступа).
- Верный вход: 200 и `{login,role}`; неверный пароль, неизвестный логин и отключённый пользователь дают одинаковый 401 `AUTH_FAILED`.
- AUTH-02: в `app_user.password_hash` значение с префиксом BCrypt, открытый пароль в таблице не найден поиском по всем её столбцам.
- Выход: после `POST /api/auth/logout` прежняя сессия получает 401.
- Сессия меняется при входе (защита от фиксации).
- SEC-05 CSRF: POST без `X-XSRF-TOKEN` даёт 403; с верным токеном проходит; с чужим 403.
- Пустой логин, логин 1000 символов: 401 без 500.
- `ErrorPageTest` (Н4 ревью T1.04). `MockMvc` не выполняет переадресацию на `/error`, поэтому: (1) `GET /error` с атрибутами запроса `jakarta.servlet.error.status_code` = 500 и `jakarta.servlet.error.exception` = `IllegalStateException(<маркер>)` даёт 500 и тело ровно `{"code":"INTERNAL_ERROR","message":"Внутренняя ошибка сервера","fields":[]}`, маркер в логе есть, в теле нет; статус 404 даёт `NOT_FOUND`, 413 `PAYLOAD_TOO_LARGE`; (2) `RestartableApp` с `extraSources` `nmtest.common.FailingFilter` (`OncePerRequestFilter`, на пути `/api/test-filter-fail` бросает `IllegalStateException(<маркер>)`, иначе пропускает) и `java.net.http.HttpClient` на `http://127.0.0.1:<port>`: ответ 500, тело по §5.1 `INTERNAL_ERROR`, без маркера и без `timestamp`, `trace`, `exception`.
- Шаг 6 (`ApiErrorsTest`, `ErrorsExtraTestController` на путях `/api/test-errors-extra/**`): 405 (`DELETE` на путь только с `GET`) несёт `Allow` со значением `GET` (и `HEAD`, если Spring его добавит; сверять, что `GET` есть, а `DELETE` нет), тело `METHOD_NOT_ALLOWED`; 415 (`POST` с `text/plain` на метод с `consumes = application/json`) несёт `Accept`, содержащий `application/json`, тело `UNSUPPORTED_MEDIA_TYPE`; `Content-Type` ответа в обоих случаях `application/json`. Кросс-параметрическое ограничение `@OrderedRange` (`from <= to` для двух `@RequestParam int`, `message` «Начало диапазона больше конца»): `from=5&to=1` даёт 400 `VALIDATION_FAILED`, `fields` ровно `[{"field":null,"message":"Начало диапазона больше конца"}]`; вместе с нарушением ограничения параметра (`@Min(0)` на `from`, `from=-1&to=-2`) поле параметра идёт первым. `ArchitectureTest`: правило п. 5 ловит нарушение на тестовом наборе классов (`ClassFileImporter` по классу-нарушителю в `nmtest`, метод `@PostMapping` с `@RequestBody List<String>`), на `ru.netmon` проходит. `pageLimits` дополняется `size=%205` (пробел, закодированный в URL) даёт 400 с полем `size` (ревью T1.04 Н-ж; разбор тот же, что у `page=%205`).

**Команда проверки:** `mvn -B -pl server -am test -Dtest='LoginTest,CsrfTest,ErrorPageTest,ApiErrorsTest,ApiErrorsQaTest,ArchitectureTest'`

**Критерии готовности:** тесты зелёные; `SecurityConfig` не содержит правил по отдельным ресурсам; в логах нет сгенерированного пароля Spring.

**Запрещено:** правила `requestMatchers` по ресурсам вместо `@PreAuthorize`; блокировка (T1.09), срок сессии (T1.08).

---

## T1.06. Журнал действий и запись событий

**Цель:** каждое изменение данных можно записать в журнал действий и журнал событий в той же транзакции и прочитать администратором.
**Исполнитель:** backend-core. **Зависит от:** T1.05.

**Файлы:** `S/audit/AuditLogService.java`, `AuditController.java`, `SecretFieldGuard.java`, `S/journal/EventJournalService.java`, `T/audit/AuditLogTest.java`, `T/journal/EventJournalWriteTest.java`, `security-matrix/audit.csv`.

**Контракты:** §5.2 (`/api/audit`), §7.2 (`AuditLog`, `EventJournal`), §10.4.

**Шаги:** `AuditLog.record` сериализует старое и новое значения в JSON. `SecretFieldGuard` удаляет из значений поля с именами `password`, `community`, `authPassword`, `privPassword`, `passwordHash`, `ciphertext` на любой глубине. Запись `USER_ACTION` в `event`.

**Обязательные тесты:**
- Запись содержит автора, время из `Clock`, действие, объект, старое и новое значения (LOG-04).
- `SecretFieldGuard`: поля удалены на вложенном уровне и в массиве; значение с тем же текстом в обычном поле не затронуто. Запись со значением секрета в таблице не находится.
- Откат транзакции не оставляет записи.
- `GET /api/audit`: фильтры, постраничность; дежурный 403.
- `details` в событии: неизвестное устройство (`null`) допускается и не вызывает ошибку.

**Команда проверки:** `mvn -B -pl server -am test -Dtest='AuditLogTest,EventJournalWriteTest,ArchitectureTest'`

**Критерии готовности:** тесты зелёные; запись не нарушает транзакцию вызывающего.

**Запрещено:** поиск по журналу событий (T3.15); страница (T1.32).

---

## T1.07. Настройки системы

**Цель:** настройки читаются модулями через `Settings`, правятся администратором и применяются без перезапуска.
**Исполнитель:** backend-core. **Зависит от:** T1.06.

**Файлы:** `S/settings/SettingsService.java`, `SettingDefinitions.java`, `SettingsController.java`, `T/settings/SettingsApiTest.java`, `security-matrix/settings.csv`.

**Контракты:** §5.2 (`/api/settings`), §7.2 (`Settings`, событие `SettingChanged`), §4.2 ключи.

**Шаги:** `SettingDefinitions` задаёт тип и границы каждого ключа: `retention.results_days` 1..3650, `retention.journal_days` 1..3650, `traps.udp_port` 1..65535, `session.timeout_minutes` 1..43200, `auth.lock_attempts` 1..100, `auth.lock_minutes` 1..1440. Кэш в памяти, обновление только после фиксации. `SettingChanged` после фиксации. Изменение в журнале действий.

**Обязательные тесты:**
- Правка сохраняется, читается через `Settings` сразу и после `RestartableApp` (OPS-08).
- Границы: минимум и максимум проходят; значение за границей, не число, пустое: 400. Неизвестный ключ: 404.
- Откат транзакции не меняет кэш и не шлёт событие.
- Дежурный 403; запись в `audit_log` со старым и новым значением.
- Возврат настроек: `SettingsApiTest` (`@IntegrationTest`) в `@AfterEach` вызывает восстановление настроек `TestDb`, в том числе после `RestartableApp` и после упавшего теста. Причина: очистка `@IntegrationTest` идёт только перед тестом, а классы вне него (`SmokeTest`) и `RestartableApp` читают `system_setting` той же БД JVM. Это же правило для любого теста, меняющего настройки (§12.2).

**Команда проверки:** `mvn -B -pl server -am test -Dtest='SettingsApiTest,ArchitectureTest'`

**Критерии готовности:** тесты зелёные; ключи без определения не принимаются.

**Запрещено:** применение настроек в чужих модулях.

---

## T1.08. Истечение сессии

**Цель:** сессия истекает через настраиваемый срок простоя, и сервер умеет завершать сессии пользователя.
**Исполнитель:** backend-core. **Зависит от:** T1.07.

**Файлы:** `S/auth/SessionTimeoutApplier.java`, `UserSessionRegistry.java`, правка `JsonAuthHandlers.java`, `T/auth/SessionExpiryTest.java`.

**Контракты:** §5.1, §4.2 (`session.timeout_minutes`), §13.

**Шаги:** при успешном входе `setMaxInactiveInterval(минуты*60)`. Реестр: `userId -> HttpSession`, очистка в `HttpSessionListener`, метод `terminateAll(userId)`. Секундное переопределение для теста: свойство `nm.session.timeout-seconds-override` только в профиле `test`.

**Обязательные тесты (реальный Tomcat, `RestartableApp`):**
- После срока с переопределением 2 с запрос даёт 401, ожидание опросом условия.
- Активность продлевает сессию.
- Смена настройки применяется к новому входу, существующая сессия сохраняет свой срок.
- `terminateAll`: все сессии пользователя получают 401, сессия другого пользователя жива.
- Реестр не накапливает завершённые сессии (проверка размера после истечения).

**Команда проверки:** `mvn -B -pl server -am test -Dtest='SessionExpiryTest,ArchitectureTest'`

**Критерии готовности:** тесты зелёные, ожиданий через `sleep` нет.

**Запрещено:** хранение сессий в БД; изменение `SecurityConfig` сверх подключения слушателя.

---

## T1.09. Временная блокировка

**Цель:** после заданного числа неудач вход временно отклоняется, включая одновременные попытки.
**Исполнитель:** backend-core. **Зависит от:** T1.08.

**Файлы:** `S/auth/LoginAttemptService.java`, правка `AppUserRepository.java`, `JsonAuthHandlers.java`, `T/auth/LoginLockTest.java`.

**Контракты:** §4.2 (`failed_attempts`, `locked_until`, настройки `auth.*`), §5.2 (`AUTH_LOCKED`).

**Шаги:** увеличение одним `UPDATE ... SET failed_attempts = failed_attempts + 1, locked_until = CASE ... RETURNING`, без чтения перед записью. Успех сбрасывает счётчик. Срок считается по `Clock`. Заблокированный вход не проверяет пароль и счётчик не увеличивает.

**Обязательные тесты:**
- Граница: `N-1` неудач вход разрешён, `N`-я блокирует; верный пароль при блокировке даёт `AUTH_LOCKED`.
- Точная граница срока по `MutableClock`: за миллисекунду до конца блокировка, в момент конца вход разрешён.
- Успешный вход обнуляет счётчик.
- Гонка: 20 одновременных неверных попыток при `N=5` дают `failed_attempts >= 5` и блокировку, без 500.
- Неизвестный логин не вызывает ошибку и не создаёт строк.
- Другой пользователь не затронут.

**Команда проверки:** `mvn -B -pl server -am test -Dtest='LoginLockTest,ArchitectureTest'`

**Критерии готовности:** тесты зелёные, настройки `auth.*` читаются через `Settings`.

**Запрещено:** блокировка по IP; постоянная блокировка.

---

## T1.10. Первый администратор

**Цель:** первый администратор создаётся только из явно переданных при установке данных, общего пароля по умолчанию нет.
**Исполнитель:** backend-core. **Зависит от:** T1.09.

**Файлы:** `S/auth/AdminBootstrap.java`, `T/auth/AdminBootstrapTest.java`.

**Контракты:** §13 (`nm.bootstrap.*`), AUTH-06.

**Шаги:** `ApplicationRunner`: нет пользователей и заданы логин и файл пароля: создать ADMIN. Содержимое файла: UTF-8, BOM и завершающий перевод строки отбрасываются. Минимальная длина 8.

**Обязательные тесты:**
- Файл задан, пользователей нет: администратор создан, вход работает.
- Пользователи уже есть: файл не применяется, существующий пароль не меняется.
- Файла нет и нет настроек: приложение запускается, в логе инструкция, вход невозможен.
- Файл указан, но отсутствует; файл пустой; пароль 7 символов: запуск прерывается с понятным сообщением.
- Файл с BOM и CRLF: пароль читается верно.
- Пароль не появляется в логе (`LogCapture`).
- В `R/` и `application.yml` нет `nm.bootstrap.*` со значением (поиск по ресурсам из теста).

**Команда проверки:** `mvn -B -pl server -am test -Dtest='AdminBootstrapTest,ArchitectureTest'`

**Критерии готовности:** тесты зелёные. Запрос значений у установщика: T8.06.

**Запрещено:** пароль в коде, ресурсах, значениях по умолчанию.

---

## T1.11. Управление пользователями

**Цель:** администратор создаёт пользователя, меняет роль, сбрасывает пароль и отключает, не теряя записей журнала и последнего администратора.
**Исполнитель:** backend-core. **Зависит от:** T1.10.

**Файлы:** `S/auth/UserAdminService.java`, `UserController.java`, `PasswordPolicy.java`, `T/auth/UserAdminTest.java`, дополнение `security-matrix/auth.csv`.

**Контракты:** §5.2 (`/api/users*`), §15 A-3.

**Шаги:** отключение и сброс пароля вызывают `UserSessionRegistry.terminateAll`. Запись в журнал действий без пароля и хэша. Проверка последнего администратора под блокировкой строк `SELECT ... WHERE role='ADMIN' AND active FOR UPDATE`.

**Обязательные тесты:**
- Создать, сменить роль, сбросить пароль: старый пароль не работает, новый работает.
- AUTH-04: отключённый не входит, его активная сессия получает 401, его записи `audit_log` остались.
- Дубль логина без учёта регистра 409; пароль 7 символов 400, 8 проходит; пустая и неизвестная роль 400.
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

**Шаги:** проверить старый, применить `PasswordPolicy`, новый хэш, завершить прочие сессии пользователя, текущая остаётся. Журнал действий без значений.

**Обязательные тесты:**
- AUTH-05: после смены старый пароль не входит, новый входит. Обе роли.
- Неверный старый 400 `OLD_PASSWORD_INVALID`, пароль не изменён.
- Слабый новый 400; новый равный старому 400.
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

**Команда проверки:** `mvn -B -pl server -am test -Dtest='SecretLeakSweepTest,ArchitectureTest'`

**Критерии готовности:** тест зелёный на `DEBUG`. Логи SNMP проверяет T4.19.

**Запрещено:** снижать уровень логирования ради прохождения теста.

---

## T1.24. Поток SSE

**Цель:** подключённый браузер получает события после фиксации транзакции, а поток не вредит серверу при разрывах и истечении сессии.
**Исполнитель:** backend-core. **Зависит от:** T1.08.

**Файлы:** `S/stream/StreamController.java`, `StreamHub.java`, `StreamClient.java`, замена `SeqStreamPublisher.java` на `TransactionalStreamPublisher.java`, `T/stream/StreamHubTest.java`, `T/stream/StreamHttpTest.java`, `security-matrix/stream.csv`; по шагу «Начатый ответ»: правка `S/common/web/ApiErrorHandler.java`, новые `T/common/CommittedResponseTest.java`, `server/src/test/java/nmtest/common/CommittedErrorsTestController.java`.

**Контракты:** §6, §7.2 (`StreamPublisher`), §5.1 (строки таблицы `ApiErrorHandler` с пометкой «с T1.24»).

**Шаги:** `SseEmitter` с собственной ограниченной очередью и отправляющим потоком на клиента, без блокировки издателя. Heartbeat 15 с с проверкой сессии (доступ к объекту `HttpSession`, переданному при подключении). Период heartbeat настраивается для теста.

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

**Шаги:** инвентарь из `RequestMappingHandlerMapping`. Для каждой строки CSV и роли запрос с подставленными идентификаторами. `allow` значит не 401 и не 403 (404 и 400 допускаются), `deny` значит 403, `public` значит без сессии не 401. Без роли: 401 для всех непубличных.

**Обязательные тесты:**
- AUTH-03: все эндпоинты задачи 1 трижды (дежурный, администратор, без входа).
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

**Шаги:** подписи ключей настроек на русском в файле страницы; неизвестный ключ показывается как есть, а не теряется. Значения старое и новое показываются как текст.

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
