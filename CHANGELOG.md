# Changelog

All notable changes to OpenTFC are documented here.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.6] - 2026-11-09

### Fixed
- **Экспорт в Excel не работал (всегда скачивался CSV)**. Три независимые причины HTTP 500 на
  `/rest/opentfc/1.0/export/xlsx`:
  - REST-ресурсы принимали тело как POJO, его разбирал *платформенный* Jackson, который не видит
    аннотации бандла — `Unrecognized field "visible"`. Теперь тело приходит как `String` и
    разбирается/сериализуется собственным `ObjectMapper` (`OpenTfcJson`) внутри бандла.
  - POI 5.2.5 вызывает `UnsynchronizedByteArrayOutputStream.builder()` (появился в commons-io 2.12),
    а Confluence экспортирует более старую копию — `NoSuchMethodError` при записи workbook.
    commons-io вложен в бандл, пакет исключён из `Import-Package`, для AMPS добавлен точечный
    `banningExcludes` (вместо отключения проверки бана целиком).
  - `Sheet.autoSizeColumn()` измеряет текст через AWT-шрифты и падает на headless-сервере —
    ширина считается вручную по длине текста (не больше 60 символов).
- **Молчаливая подмена формата**: при ошибке Excel-экспорта CSV больше не скачивается «вместо»;
  причина сервера показывается тостом над таблицей, CSV остаётся отдельной кнопкой.
- **Определение типов колонок**: текст с цифрой внутри («Фамилия1», «1С:Предприятие», «ОКПД 2»,
  «12,5 шт») больше не считается числом — раньше такие колонки получали фильтр «От/До», который
  отсеивал всё подряд. Даты проверяются раньше чисел («12.01.2024» читалось как 12012024),
  список чекбоксов теперь предлагается только для повторяющихся коротких значений, а не для любой
  колонки с ≤50 униками.
- **Фильтрация**: фильтры вынесены в отдельную строку заголовка (кнопки перекрывали соседние
  колонки и были некликабельны), поповер списков рендерится на уровне страницы и не обрезается
  скролл-контейнером, «От/До» — обычные текстовые поля (нативный `input[type=number]` отдаёт
  пустое значение для «1 234,56», из-за чего диапазон молча не фильтровал), нечитаемый ввод
  подсвечивается и не сбрасывает фильтр, снятие всех галочек означает «Ничего», а не «Все».
- **Разметка таблицы**: у таблицы с явным `<thead>` первая строка тела тоже попадала в заголовки —
  колонки удваивались, рядом с каждой строкой рисовались пустые ячейки; строка заголовков
  попадала в данные (портила «Итого» и поиск); вложенные таблицы больше не читаются как ячейки;
  строка итогов и ранее отрендеренные строки не пере-парсятся при повторной инициализации.
- **Сортировка**: пустые значения и «—» всегда в конце — и по возрастанию, и по убыванию.
- **Ссылки на фильтр-состояние**: `btoa()` падал на кириллице, поэтому состояние не попадало в URL
  и переданная ссылка теряла фильтры; кодирование стало UTF-8-безопасным. Ключ `tf_<macroId>`,
  плюс фолбэк на единственный чужой `tf_otfc-*` в адресной строке.
- **macroId**: был новый UUID на каждый рендер — сохранённые виды и ссылки «протухали». Теперь id
  детерминирован (SHA-256 от pageId, имени макроса, содержимого и значимых параметров), явный
  параметр `macroId` как и раньше приоритетен.
- **Имя файла выгрузки**: санитайз (CR/LF/кавычки/разделители) и RFC 5987 `filename*` для
  не-ASCII имён.
- **Подсказки вместо «пустой таблицы»**: счётчик активных фильтров, предупреждение «N на скрытых
  колонках», ссылка «сбросить» и «Ни одна строка не подходит под фильтры».
- **Конфликт со сортировкой таблицы, наведённой извне** — прежде всего со штатным плагином
  Confluence `confluence-sortable-tables` (встроен в 8.5/9.x, подхватывает любую таблицу с
  `<th>`-строкой) и аналогичными jQuery-tablesorter плагинами: он кэширует `<tr>` при
  инициализации и возвращает их все в `<tbody>` на каждой сортировке — строки, только что
  отфильтрованные нашим плагином, «воскресали», а один клик по заголовку пересобирал таблицу
  дважды. Плюс клонированный `<thead>` (floating/sticky заголовок) дублировал фильтры мёртвыми
  копиями, а `user-select: none` / `unselectable="on"` на ячейках мешали вводу. Компонент теперь
  ставит таблице `data-sortable="false"` (штатный opt-out того плагина), снимает уже созданный
  экземпляр сортировщика (`destroy`, снятие классов `tablesorter`/`stickyTableHeaders`, перехват
  его событий на фазе захвата в `<thead>`), удаляет клонированные `<thead>` и повторяет очистку,
  если плагин хоста поднялся позже нас; сортировка остаётся одной — нашей.

### Changed
- Версия 1.0.6.
- Payload экспорта содержит только поля wire-модели (`index/name/type/options`, `val/html/type`),
  тип ячейки берётся из типа колонки — в Excel числа остаются числами (суммируются формулами).

## [1.0.5] - 2026-11-05

### Security
- **Утечка ExecutorService в SQL-движке**: `SqlTransformerEngine` создавал новый `ExecutorService` на каждый запрос — утечка потоков. Заменён на общий `static final` пул с shutdown hook.
- **Обход песочницы арифметики в электронных таблицах**: `Function()` в `spreadsheet-manager.ts` заменён на безопасный recursive-descent парсер (нет eval/Function).
- **Анонимный доступ к конфигу**: убрана аннотация `@AnonymousAllowed` с `ConfigRestResource` — теперь требуется аутентификация.
- **Утечка сообщений об ошибках**: `SourceRestResource` больше не раскрывает `e.getMessage()` в HTTP-ответе — только общее сообщение.

### Fixed
- **Необработанная кодировка**: `Charset.forName()` в `DataSourceResolver` обёрнут в try/catch с fallback на UTF-8.
- **Нет валидации имени профиля**: `ProfileRestResource` теперь проверяет длину (≤100) и допустимые символы имени профиля.
- **Гонка полей diagnostics servlet**: `volatile` поля `salError`/`homeSource` заменены на `AtomicReference`.
- **Неэффективный DateFormatter**: `SimpleDateFormat` per-call в `AttachmentTableMacro` заменён на потокобезопасный `static final DateTimeFormatter`.
- **Bottleneck синхронизации логов**: `synchronized` в `OpenTfcLogStore` заменён на `ConcurrentLinkedDeque` + `AtomicInteger`.
- **Нет клиентской валидации**: добавлена JavaScript-валидация на админ-странице (диапазоны, формат доменов).
- **Дубликат CSS**: `opentfc.bundle.css` помечен как GENERATED (источник — `opentfc.css`).

### Changed
- **Консолидация версии**: все хардкоженные `"1.0.4"` заменены на `OpenTfcVersion.VERSION` (единый источник истины).
- Версия обновлена до 1.0.5.

## [1.0.4] - 2026-10-28

### Fixed
- **Журналы были скрыты на инстансе с неработающим SAL**: каталог logs теперь ищется
  двумя путями — SAL ApplicationProperties и системное свойство `confluence.home`
  (NIO + normalize, только существующий каталог "logs"). На странице показывается,
  откуда взят каталог и текст ошибки SAL.
- **Авторизация при мёртвом SAL**: доступ по наличию сессионной cookie Seraph
  (аноним — 403). Ранее SAL-down означал либо полный запрет (журналы недостижимы),
  либо публичный доступ.

## [1.0.3] - 2026-10-28

### Added
- **Прямое чтение файлов журналов** `<home>/logs` на странице диагностики (и в diag-lite):
  хвосты самых свежих `*.log` прямо на странице + скачивание хвоста основного журнала
  (`?dl=1`). Поле боя против «Полную информацию вы найдете в журналах» без доступа к серверу.
  Читаются только файлы из фактического листинга каталога — параметры запроса в файловые
  операции не передаются.

### Fixed
- **Захват лога через log4j2 не работает на Confluence 8.5**: `log4j-core` не экспортируется
  плагинам (`ClassNotFoundException` в diag-lite на живом инстансе) — остаётся как
  best-effort статус, основным механизмом стало чтение файлов журналов.
- **Анонимный доступ к diag-lite закрыт**: в ряде версий SAL аноним возвращает пустую
  строку, а не null — трактуется как «требуется вход» (403). SAL-degraded тоже 403.

## [1.0.2] - 2026-10-28

### Fixed
- **Отказ активации «Не удалось активировать приложение» (v1.0.1)**: обязательный
  `Import-Package: com.atlassian.plugin.osgi.factory` (внутренний пакет plugin framework,
  попадал из atlassian-spring-scanner-runtime) сделан optional — без него бандл не резолвился
  в OSGi-реестре Confluence.

### Added
- **opentfc-diag-lite** — отдельный крошечный плагин-компаньон (`diag-lite/`, чистый jar без
  Spring/bnd): страница `/plugins/servlet/diag-lite` со статусом всех плагинов (в т.ч. список
  отключённых — каскад), перехватом WARN/ERROR host-лога и событиями включения/отключения.
  Предназначен для диагностики случаев, когда основной плагин не активируется.

## [1.0.1] - 2026-10-28

### Added
- **Браузерная диагностика** `/plugins/servlet/opentfc/diagnostics`: in-memory ring buffer (2000 записей),
  события plugin framework (ENABLED/DISABLED всех приложений — виден каскад отключений),
  перехват WARN/ERROR host-лога через log4j2-аппендер (guarded, опционально),
  события OpenTFC (Spring-контекст, первый рендер макросов, SQL-запросы, ошибки источников REST),
  фильтры уровней, авто-обновление, скачивание `.log`, очистка. Страница — чистый HTML сервлета
  без velocity-декораторов/AUI, доступна даже на инстансе со сломанным рендерингом.
- `PluginLifecycleLogger` — Spring-bean, публикующий события plugin framework в буфер.
- Unit test suite for backend services (109 tests, JaCoCo line coverage 94 %, branch 78 %):
  - `TableExtractorImpl`: headerless tables, column type inference (number/date/select),
    HTML entities, missing-cell padding.
  - `DataSourceResolver`: CSV separators and cp1251, JSON array-of-objects and array-of-arrays,
    SSRF guard (localhost/RFC1918/link-local/any-local/non-HTTP protocols).
  - `SqlTransformerEngine`: JOIN, GROUP BY, UNION, window functions, quoted identifiers
    with spaces, sandbox keyword scan with comment/literal stripping (no false positives).
  - `ExportService`: CSV quoting, XLSX round-trip verified through Apache POI.
  - Macro render tests: parameter mapping into `data-opentfc-config`, HTML escaping (XSS),
    static PDF/Word/email export fallback, JSON source, spreadsheet include, attachment
    listing (metadata, include/exclude filters, size formatting).
  - REST layer: `/source` (attachments, CSV/JSON, SSRF), `/transformer`, `/export`, `/config`,
    `/profile` (create/update/delete plus unauthenticated 401 guards, foreign-profile 404).
  - Admin action: default limits, save/load round-trip, bad stored values.
  - i18n consistency: all descriptor keys present in ru/en, identical key sets, real translation.
  - Model JSON contract used between backend and browser bundle.
- JaCoCo coverage report (`mvn test` → `target/site/jacoco/index.html`).
- CI workflow (GitHub Actions): JDK 17 + Node 20, frontend tsc/webpack, Maven `verify`.
- `LICENSE` (Apache-2.0), `CHANGELOG.md`.

### Fixed
- **Причина поломки инстанса при установке v1.0.0 (инцидент 27–28.10.2026)**:
  - `atlassian-plugin.xml`: web-resource больше не инжектится в контекст `atl.general`
    (1 МБ JS/CSS грузился на каждой странице инстанса, ломая интерактив при ошибке);
  - удалены конфликтующие алиасы макросов без префикса `open-*` (`table-filter`,
    `chart`, `pivot`, `csv`, `json`, `chart-from-table`, `pivot-table`, `table-transformer`,
    `table-spreadsheet`, `attachment-table` и др.) — дубли со встроенным Chart-макросом
    Confluence и макросами сторонних приложений срывали активацию;
  - `pom.xml`: явные обязательные `Import-Package` вместо `*;resolution:="optional"` для
    хостовых API (fail-fast на ошибках wiring); optional оставлены только для
    `org.springframework.*` и `org.apache.logging.log4j.*` (гардируются в рантайме).
- i18n: missing `opentfc.admin.title` key referenced by the plugin descriptor's admin web-item.
- SQL sandbox: queries containing keywords inside string literals, comments or quoted
  identifiers are no longer falsely rejected (e.g. `SELECT 'DROP' AS msg FROM T1`).
- Table extraction: when a table has no `<thead>`, the row used to derive headers is no longer
  emitted as a data row (Jsoup auto-wraps rows in a synthetic `<tbody>`).
- CSV parsing: column order now follows the file (`getHeaderNames()`) instead of a HashMap,
  and JSON array-of-arrays is supported (positional `Column N` headers).
- Attachment table: extensionless files no longer slip through the `includeExtensions` filter,
  and file sizes are formatted with `Locale.ROOT` (no decimal comma under ru_RU).

## [1.0.0-SNAPSHOT] - 2026-10-26

### Added
- P2 plugin skeleton for Confluence DC/Server 8.5–9.x (Java 17, Atlassian SDK).
- Table Filter macro (`opentfc-table-filter`) with interactive filtering, multi-sort,
  frozen header/columns, totals, pagination and CSV/XLSX export.
- Chart from Table macro (`opentfc-chart`) with 15+ ECharts types including Gantt.
- Pivot Table macro (`opentfc-pivot`) with multi-field aggregation.
- Table Transformer macro (`opentfc-transformer`) with in-memory DuckDB SQL sandbox.
- Table Spreadsheet macro (`opentfc-spreadsheet`) + Spreadsheet Include (`opentfc-spreadsheet-include`).
- Attachment Table macro (`opentfc-attachment-table`).
- CSV / JSON source macros (`opentfc-csv`, `opentfc-json`) with SSRF-protected URL fetching.
- REST API v1 (`/rest/opentfc/1.0`): source, transformer, profile CRUD, export, config.
- Active Objects entities: `FilterProfile`, `SpreadsheetMeta`, `UserSettings`.
- Admin configuration page (SQL timeout, result row limit, URL whitelist).
- i18n resources (ru/en).
- Installation & acceptance guide (`INSTALL.md`).
