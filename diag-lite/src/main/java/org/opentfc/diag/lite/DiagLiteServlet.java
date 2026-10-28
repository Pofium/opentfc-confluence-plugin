package org.opentfc.diag.lite;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

/**
 * Diag Lite: /plugins/servlet/diag-lite
 *
 * Чистый servlet-api (никаких Spring/хост-классов в сигнатурах): живёт даже когда
 * основной плагин не активируется. Показывает состояние OpenTFC, список отключённых
 * приложений (каскад) и хвосты файлов журналов сервера — «полную информацию из
 * журналов», когда доступа к серверу нет.
 *
 * Безопасность: доступ только авторизованным (аноним и недоступный SAL — 403);
 * журналы читаются только через перечисление каталога logs, параметры запроса
 * в файловые операции не передаются; редиректы — константные.
 */
public class DiagLiteServlet extends HttpServlet {

    private static final String PAGE_URL = "/plugins/servlet/diag-lite";
    private static final int JOURNAL_FILES_SHOWN = 4;
    private static final int TAIL_BYTES_PER_FILE = 300_000;

    @Override
    public void init() throws ServletException {
        super.init();
        LiteCapture.tryInstall();
        HostBridgeLite.tryRegisterEvents();
        LiteLogStore.info("diag-lite", "Diag Lite готов: " + PAGE_URL);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String username = HostBridgeLite.currentUsername();
        // При мёртвом SAL ("!sal") пускаем по наличию сессионной cookie Seraph —
        // это мягкая проверка «вошёл ли пользователь», без неё на сломанном
        // инстансе журналы недоступны вовсе.
        boolean salDown = "!sal".equals(username);
        if (username == null) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN,
                    "Требуется вход в Confluence (страница диагностики недоступна анонимно)");
            return;
        }
        if (salDown && !hasSessionCookie(req)) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN,
                    "SAL недоступен и сессионная cookie не найдена — войдите в Confluence и повторите");
            return;
        }
        LiteCapture.tryInstall();

        // Скачивание хвоста самого свежего журнала: имя файла фиксировано,
        // параметрами запроса не управляется.
        if ("1".equals(req.getParameter("dl"))) {
            String tail = HostBridgeLite.mainJournalTail(5_000_000);
            if (tail == null) {
                resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Журналы не найдены");
                return;
            }
            resp.setContentType("text/plain; charset=utf-8");
            resp.setHeader("Content-Disposition", "attachment; filename=\"confluence-journal-tail.log\"");
            resp.getWriter().write(tail);
            return;
        }
        if ("txt".equals(req.getParameter("format"))) {
            resp.setContentType("text/plain; charset=utf-8");
            resp.setHeader("Content-Disposition", "attachment; filename=\"diag-lite.log\"");
            resp.getWriter().write(LiteLogStore.asText());
            return;
        }
        sendHtml(resp, salDown);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String username = HostBridgeLite.currentUsername();
        if (username == null || (salDownFor(username) && !hasSessionCookie(req))) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        if ("clear".equals(req.getParameter("action"))) {
            LiteLogStore.clear();
        }
        // Константный редирект (URI запроса не отражаем)
        resp.sendRedirect(PAGE_URL);
    }

    private static boolean salDownFor(String username) {
        return "!sal".equals(username);
    }

    private static boolean hasSessionCookie(HttpServletRequest req) {
        javax.servlet.http.Cookie[] cookies = req.getCookies();
        if (cookies == null) {
            return false;
        }
        for (javax.servlet.http.Cookie c : cookies) {
            if ("seraph.confluence".equals(c.getName()) && c.getValue() != null && !c.getValue().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private void sendHtml(HttpServletResponse resp, boolean salDown) throws IOException {
        List<LiteLogStore.Entry> entries = LiteLogStore.snapshot();

        StringBuilder h = new StringBuilder(64 * 1024);
        h.append("<!DOCTYPE html><html lang=\"ru\"><head><meta charset=\"utf-8\"><title>OpenTFC Diag Lite</title>");
        h.append("<style>")
         .append("body{font-family:-apple-system,Segoe UI,Arial,sans-serif;margin:0;background:#f4f5f7;color:#172b4d}")
         .append("header{background:#0747a6;color:#fff;padding:12px 20px;display:flex;gap:16px;align-items:center}")
         .append("header h1{font-size:17px;margin:0}")
         .append(".tb a,.tb button{display:inline-block;margin-right:8px;padding:4px 10px;border-radius:4px;background:#dfe1e6;color:#172b4d;border:1px solid #c1c7d0;font-size:13px;text-decoration:none;cursor:pointer}")
         .append("main{padding:16px 20px;max-width:1400px}")
         .append("table{border-collapse:collapse;width:100%;font-size:13px;margin-bottom:16px}")
         .append("td,th{border:1px solid #c1c7d0;padding:4px 10px;text-align:left;vertical-align:top}")
         .append("td.k{background:#fafbfc;font-weight:600;width:230px}")
         .append("table.log{font-size:12px}")
         .append("table.log th{background:#ebecf0;position:sticky;top:0}")
         .append("tr.ERROR td.lvl{color:#bf2600;font-weight:700}")
         .append("tr.WARN td.lvl{color:#974f0c;font-weight:700}")
         .append("td.msg{font-family:Consolas,Menlo,monospace;white-space:pre-wrap;word-break:break-word}")
         .append("details{margin-bottom:14px}")
         .append("summary{cursor:pointer;font-weight:600;padding:6px 10px;background:#ebecf0;border:1px solid #c1c7d0;border-radius:4px}")
         .append("details pre{max-height:520px;overflow:auto;margin:0;padding:10px;background:#fff;border:1px solid #dfe1e6;border-top:none;")
         .append("font-size:11px;white-space:pre-wrap;word-break:break-word;font-family:Consolas,Menlo,monospace}")
         .append(".note{background:#deebff;border:1px solid #b3d4ff;border-radius:4px;padding:10px 14px;font-size:13px;margin-bottom:16px}")
         .append(".bad{color:#bf2600;font-weight:600}.good{color:#006644;font-weight:600}")
         .append("</style></head><body>");

        h.append("<header><h1>OpenTFC Diag Lite v1.0.4</h1><div class=\"tb\">")
         .append("<a href=\"").append(PAGE_URL).append("\">Обновить</a>")
         .append("<a href=\"").append(PAGE_URL).append("?dl=1\">Скачать хвост журнала</a>")
         .append("<a href=\"").append(PAGE_URL).append("?format=txt\">Скачать события diag-lite</a>")
         .append("<form method=\"post\" style=\"display:inline\" onsubmit=\"return confirm('Очистить?')\">")
         .append("<input type=\"hidden\" name=\"action\" value=\"clear\"><button>Очистить</button></form>")
         .append("</div></header><main>");

        h.append("<div class=\"note\"><b>Зачем эта страница:</b> UPM пишет «не удалось активировать, смотрите журналы» — ")
         .append("а журналов у вас нет. Ниже — сами журналы: хвосты файлов из &lt;home&gt;/logs, состояние OpenTFC и события ")
         .append("plugin framework. Порядок: установите/обновите основной плагин opentfc-confluence-plugin через UPM → ")
         .append("вернитесь сюда и нажмите «Обновить» → причина будет в журнале и в событиях ENABLED/DISABLED. ")
         .append("Кнопка «Скачать хвост журнала» отдаёт файл для пересылки разработчику.</div>");

        String cap = LiteLogStore.isHostCaptureInstalled()
                ? "<span class=\"good\">установлен</span>"
                : "<span class=\"bad\">недоступен</span> — log4j-core не экспортируется плагинам на Confluence 8.5; вместо него ниже — чтение файлов журналов";

        h.append("<table>")
         .append("<tr><td class=\"k\">Время сервера</td><td>").append(Instant.now()).append("</td>")
         .append("<td class=\"k\">Перехват лога (log4j2)</td><td>").append(cap).append("</td></tr>")
         .append("<tr><td class=\"k\">OpenTFC (осн. плагин)</td><td colspan=\"3\">").append(esc(HostBridgeLite.openTfcState())).append("</td></tr>")
         .append("<tr><td class=\"k\">События ENABLED/DISABLED</td><td colspan=\"3\">").append(esc(HostBridgeLite.tryRegisterEvents())).append("</td></tr>")
         .append("<tr><td class=\"k\">Записей / вытеснено</td><td>").append(entries.size()).append(" / ").append(LiteLogStore.droppedCount()).append("</td>")
         .append("<td class=\"k\">Каталог журналов</td><td>")
         .append("источник: ").append(esc(HostBridgeLite.homeSource())).append(" (sal / sysprop / none)</td></tr>")
         .append("<tr><td class=\"k\">SAL</td><td colspan=\"3\">")
         .append(salDown
                 ? "<span class=\"bad\">недоступен</span>: " + esc(HostBridgeLite.salErrorText()) + " — журналы ищутся через confluence.home"
                 : "ок")
         .append("</td></tr>")
         .append("</table>");

        // ===== Журналы сервера (главный источник причин отказов активации) =====
        h.append("<h2 style=\"font-size:15px\">Журналы сервера (&lt;home&gt;/logs)</h2>");
        List<HostBridgeLite.JournalEntry> journals = HostBridgeLite.journalTails(JOURNAL_FILES_SHOWN, TAIL_BYTES_PER_FILE);
        if (journals.isEmpty()) {
            h.append("<p><i>каталог logs не найден или пуст (источник: ").append(esc(HostBridgeLite.homeSource())).append(")</i></p>");
        }
        for (HostBridgeLite.JournalEntry j : journals) {
            h.append("<details><summary>").append(esc(j.name))
             .append(" — ").append(j.sizeKb).append(" КБ, изменён ").append(esc(j.modified))
             .append("</summary><pre>").append(esc(j.tail)).append("</pre></details>");
        }

        // ===== События =====
        h.append("<h2 style=\"font-size:15px\">События (ring buffer diag-lite)</h2>");
        h.append("<table class=\"log\"><tr><th style=\"width:190px\">Время</th><th style=\"width:60px\">Уровень</th>")
         .append("<th style=\"width:230px\">Источник</th><th>Сообщение</th></tr>");
        int shown = 0;
        for (int i = entries.size() - 1; i >= 0; i--) {
            LiteLogStore.Entry e = entries.get(i);
            shown++;
            if (shown > 800) {
                h.append("<tr><td colspan=\"4\"><i>… показаны последние 800 записей, полное — в «Скачать события diag-lite»</i></td></tr>");
                break;
            }
            h.append("<tr class=\"").append(e.level).append("\"><td>").append(Instant.ofEpochMilli(e.timestamp))
             .append("</td><td class=\"lvl\">").append(e.level)
             .append("</td><td>").append(esc(e.source))
             .append("</td><td class=\"msg\">").append(esc(e.message)).append("</td></tr>");
        }
        if (entries.isEmpty()) {
            h.append("<tr><td colspan=\"4\"><i>Пусто. Установите основной плагин и обновите страницу — ошибки активации появятся здесь.</i></td></tr>");
        }
        h.append("</table></main></body></html>");

        resp.setContentType("text/html; charset=utf-8");
        resp.setHeader("Cache-Control", "no-store");
        byte[] body = h.toString().getBytes(StandardCharsets.UTF_8);
        resp.setContentLength(body.length);
        resp.getWriter().write(h.toString());
    }

    private static String esc(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
