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
 * приложений (каскад) и перехваченные WARN/ERROR host-лога — то самое «полная
 * информация в журналах», когда доступа к журналам нет.
 */
public class DiagLiteServlet extends HttpServlet {

    @Override
    public void init() throws ServletException {
        super.init();
        LiteCapture.tryInstall();
        HostBridgeLite.tryRegisterEvents();
        LiteLogStore.info("diag-lite", "Diag Lite готов: /plugins/servlet/diag-lite");
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String username = HostBridgeLite.currentUsername();
        boolean salDown = "".equals(username);
        if (username == null) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN, "Требуется вход в Confluence");
            return;
        }
        LiteCapture.tryInstall();

        if ("txt".equals(req.getParameter("format"))) {
            resp.setContentType("text/plain; charset=utf-8");
            resp.setHeader("Content-Disposition", "attachment; filename=\"diag-lite.log\"");
            resp.getWriter().write(LiteLogStore.asText());
            return;
        }
        sendHtml(req, resp, salDown);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if (HostBridgeLite.currentUsername() == null) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        if ("clear".equals(req.getParameter("action"))) {
            LiteLogStore.clear();
        }
        resp.sendRedirect(req.getRequestURI());
    }

    private void sendHtml(HttpServletRequest req, HttpServletResponse resp, boolean salDown) throws IOException {
        List<LiteLogStore.Entry> entries = LiteLogStore.snapshot();
        String auto = req.getParameter("auto");

        StringBuilder h = new StringBuilder(48 * 1024);
        h.append("<!DOCTYPE html><html lang=\"ru\"><head><meta charset=\"utf-8\"><title>OpenTFC Diag Lite</title>");
        if (auto != null) {
            h.append("<meta http-equiv=\"refresh\" content=\"").append(esc(auto)).append("\">");
        }
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
         .append(".note{background:#deebff;border:1px solid #b3d4ff;border-radius:4px;padding:10px 14px;font-size:13px;margin-bottom:16px}")
         .append(".bad{color:#bf2600;font-weight:600}.good{color:#006644;font-weight:600}")
         .append("</style></head><body>");

        h.append("<header><h1>OpenTFC Diag Lite v1.0.2</h1><div class=\"tb\">")
         .append("<a href=\"?\">Обновить</a>")
         .append("<a href=\"?auto=10\">Авто 10с</a>")
         .append("<a href=\"?format=txt\">Скачать .log</a>")
         .append("<form method=\"post\" style=\"display:inline\" onsubmit=\"return confirm('Очистить?')\">")
         .append("<input type=\"hidden\" name=\"action\" value=\"clear\"><button>Очистить</button></form>")
         .append("</div></header><main>");

        h.append("<div class=\"note\"><b>Зачем эта страница:</b> UPM пишет «не удалось активировать, смотрите журналы» — ")
         .append("а журналов у вас нет. Здесь те самые записи: ошибки активации приложений перехватываются из общего лога ")
         .append("и показываются ниже. Порядок действий: 1) установите/обновите основной плагин ")
         .append("opentfc-confluence-plugin-1.0.2.jar через UPM; 2) вернитесь сюда и нажмите «Обновить»; ")
         .append("3) пришлите разработчику содержимое страницы или скачанный .log.</div>");

        String cap = LiteLogStore.isHostCaptureInstalled()
                ? "<span class=\"good\">установлен</span>"
                : "<span class=\"bad\">НЕ установлен</span> — серверные ошибки не видны, только события";

        h.append("<table>")
         .append("<tr><td class=\"k\">Время сервера</td><td>").append(Instant.now()).append("</td>")
         .append("<td class=\"k\">Перехват лога хоста</td><td>").append(cap).append("</td></tr>")
         .append("<tr><td class=\"k\">OpenTFC (осн. плагин)</td><td colspan=\"3\">").append(esc(HostBridgeLite.openTfcState())).append("</td></tr>")
         .append("<tr><td class=\"k\">События ENABLED/DISABLED</td><td colspan=\"3\">").append(esc(HostBridgeLite.tryRegisterEvents())).append("</td></tr>")
         .append("<tr><td class=\"k\">Записей / вытеснено</td><td>").append(entries.size()).append(" / ").append(LiteLogStore.droppedCount()).append("</td>")
         .append("<td class=\"k\">SAL</td><td>").append(salDown ? "<span class=\"bad\">недоступен — degraded mode, доступ открыт</span>" : "ок, доступ по логину").append("</td></tr>")
         .append("</table>");

        List<String> disabled = HostBridgeLite.disabledPlugins();
        h.append("<table><tr><th>Отключённые приложения (каскад DISABLED — ").append(disabled.size()).append(" шт.)</th></tr>");
        if (disabled.isEmpty()) {
            h.append("<tr><td><i>ни одного (или список недоступен)</i></td></tr>");
        } else {
            for (String s : disabled) {
                h.append("<tr><td class=\"msg\">").append(esc(s)).append("</td></tr>");
            }
        }
        h.append("</table>");

        h.append("<table class=\"log\"><tr><th style=\"width:190px\">Время</th><th style=\"width:60px\">Уровень</th>")
         .append("<th style=\"width:230px\">Источник</th><th>Сообщение</th></tr>");
        int shown = 0;
        for (int i = entries.size() - 1; i >= 0; i--) {
            LiteLogStore.Entry e = entries.get(i);
            shown++;
            if (shown > 800) {
                h.append("<tr><td colspan=\"4\"><i>… показаны последние 800 записей, полное — в «Скачать .log»</i></td></tr>");
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
