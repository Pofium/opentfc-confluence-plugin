# OpenTFC (Open Table Filter, Charts & Spreadsheets for Confluence DC)

[![CI](https://github.com/Pofium/opentfc-confluence-plugin/actions/workflows/ci.yml/badge.svg)](https://github.com/Pofium/opentfc-confluence-plugin/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/Pofium/opentfc-confluence-plugin?include_prereleases&label=release)](https://github.com/Pofium/opentfc-confluence-plugin/releases/latest)
[![Coverage](https://img.shields.io/badge/coverage-94%25%20lines%20%C2%B7%2078%25%20branches-brightgreen)](https://github.com/Pofium/opentfc-confluence-plugin/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue)](LICENSE)

**OpenTFC** — полнофункциональный аналог плагина *Table Filter, Charts & Spreadsheets for Confluence* (Stiltsoft) для Atlassian Confluence Data Center / Server (версии 8.5 LTS – 9.x), созданный на базе открытых и лицензионно-чистых компонентов (Apache-2.0 / MIT).

👉 **[Подробное руководство пользователя по работе с макросами (USER_GUIDE.md)](USER_GUIDE.md)**
👉 **[Руководство по установке и администрированию (INSTALL.md)](INSTALL.md)**

---

## 🚀 Ключевые возможности

1. **Open Table Filter** (`opentfc-table-filter`):
   - Глобальный и поколоночный поиск/фильтрация
   - Автоопределение типов колонок (числа, даты, текст, категории)
   - Мультисортировка (включая Shift+клик)
   - Заморозка заголовков (sticky header) и первых колонок
   - Скрытие/показ колонок
   - Строка итогов (SUM, AVG, MIN, MAX, COUNT)
   - Персональные профили фильтрации с сохранением в Active Objects
   - Экспорт в CSV / XLSX

2. **Open Chart from Table** (`opentfc-chart`):
   - 15+ видов интерактивных диаграмм на базе Apache ECharts (Column, Bar, Stacked, Line, Area, Pie, Donut, Scatter, Radar, Funnel, Gantt)
   - Динамическое обновление при изменении фильтров
   - Экспорт в PNG/SVG

3. **Open Pivot Table** (`opentfc-pivot`):
   - Сводные таблицы с агрегацией по нескольким полям (SUM, COUNT, MIN, MAX, AVG)
   - Drag-and-drop группировка

4. **Open Table Transformer** (`opentfc-transformer`):
   - Встроенный in-memory SQL-движок на базе DuckDB JDBC
   - Поддержка `T1`, `T2`, JOIN, UNION, PIVOT, оконных функций
   - Песочница с защитой от DDL/DML операций и тайм-аутом выполнения

5. **Open Table Spreadsheet** (`opentfc-spreadsheet` & `opentfc-spreadsheet-include`):
   - Электронные таблицы прямо на страницах Confluence с формулами
   - Сохранение во вложения страницы в формате XLSX с версионностью Confluence
   - Мягкая блокировка сессий редактирования

6. **Внешние источники и вложения** (`opentfc-csv`, `opentfc-json`, `opentfc-attachment-table`):
   - Таблицы из CSV/TSV и JSON файлов
   - Автоматический листинг вложений страницы
   - Защита от SSRF для внешних URL

---

## 🛠️ Стек технологий

- **Backend:** Java 17, Atlassian SDK / AMPS, Confluence 8.5/9.x API, Active Objects, DuckDB JDBC, Apache POI, Apache Commons CSV, Jackson, Jsoup.
- **Frontend:** TypeScript, Webpack, Confluence AUI, AG Grid Community, Apache ECharts.
- **Хранение данных:** Storage format (параметры макросов), Active Objects (профили), вложения страниц (XLSX спредшиты).

---

## 📦 Сборка и установка

### Требования
- JDK 17
- Node.js 18+ и npm
- Atlassian Plugin SDK (`atlas-mvn` или Maven 3.8+)

### 1. Сборка фронтенда
```bash
npm install
npm run build
```

### 2. Проверка типов TypeScript
```bash
npm run check
```

### 3. Сборка P2-плагина
```bash
atlas-mvn package
# или стандартным Maven
mvn package
```
Собранный JAR плагина появится в каталоге `target/opentfc-confluence-plugin-1.0.1.jar`.

Готовый артефакт также опубликован в релизах: [v1.0.1](https://github.com/Pofium/opentfc-confluence-plugin/releases/tag/v1.0.1) (SHA-256 указан в описании релиза).

### 4. Запуск юнит-тестов
```bash
mvn test
```
109 тестов покрывают сервисы (извлечение таблиц и вывод типов, CSV/JSON-парсеры, SSRF-защита, SQL-движок DuckDB и песочницу), экспорт CSV/XLSX, рендер всех макросов (включая XSS-экранирование и export-режим PDF/Word), REST-слой (источники, трансформер, экспорт, профили с правами), админ-действие и консистентность i18n.

Покрытие (JaCoCo): **94 % строк / 78 % ветвей**, отчёт — `target/site/jacoco/index.html`.

`mvn verify` дополнительно собирает JAR, OBR-артефакт и валидирует манифест плагина. CI (GitHub Actions) прогоняет `npm run check`, сборку фронтенда и `mvn verify` на JDK 17.

### 5. Установка в Confluence Data Center
1. Войдите в Confluence с правами администратора.
2. Перейдите: **Настройки Confluence** (шестеренка) → **Управление приложениями** (Manage apps).
3. Нажмите **Загрузить приложение** (Upload app) и выберите собранный JAR файл.
4. Убедитесь, что статус плагина — **Включено** (Enabled).
