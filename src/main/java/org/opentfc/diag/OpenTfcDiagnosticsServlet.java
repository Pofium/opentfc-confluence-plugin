package org.opentfc.diag;

import com.atlassian.sal.api.ApplicationProperties;
import com.atlassian.sal.api.component.ComponentLocator;
import com.atlassian.sal.api.user.UserManager;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Browser diagnostics page: /plugins/servlet/opentfc/diagnostics
 *
 * Renders with hand-built HTML (no velocity decorators, no AUI) so it stays readable
 * even on an instance whose rendering pipeline is broken. Shows OpenTFC events plus
 * WARN/ERROR entries captured from the host logging system, with download and clear.
 */
public class OpenTfcDiagnosticsServlet extends HttpServlet {

    static final String VERSION = "1.0.4";

    private static volatile String salError = "";
    private static volatile String homeSource = "none";

    // ===== Файлы журналов сервера =====
    // Читаются только файлы из фактического листинга каталога <home>/logs;
    // параметры запроса в файловые операции не передаются.

    private static File logsDir() {
        try {
            ApplicationProperties props = ComponentLocator.getComponent(ApplicationProperties.class);
            if (props != null) {
                File home = null;
                java.nio.file.Path local = props.getLocalHomeDirectory() != null
                        ? props.getLocalHomeDirectory().orElse(null) : null;
                if (local != null) {
                    home = local.toFile();
                } else if (props.getHomeDirectory() != null) {
                    home = props.getHomeDirectory();
                }
                if (home != null) {
                    File dir = new File(home, "logs");
                    if (dir.isDirectory()) {
                        homeSource = "sal";
                        return dir;
                    }
                }
            }
        } catch (Throwable e) {
            salError = e.getClass().getName() + ": " + e.getMessage();
        }
        Path probe = HomeDirProbe.logsDirFromSysProp();
        if (probe != null) {
            homeSource = "sysprop";
            return probe.toFile();
        }
        homeSource = "none";
        return null;
    }

    /** {имя, размер КБ, дата изменения, хвост} самых свежих журналов. Максимум 4 файла, хвост 300 КБ. */
    private static List<String[]> journalFiles() {
        List<String[]> out = new ArrayList<>();
        try {
            File dir = logsDir();
            if (dir == null) {
                return out;
            }
            File[] files = dir.listFiles((d, n) -> n.matches("[A-Za-z0-9._ -]{1,80}\\.log(\\.[0-9]+)?"));
            if (files == null) {
                return out;
            }
            Arrays.sort(files, Comparator.comparingLong(File::lastModified).reversed());
            int count = 0;
            for (File f : files) {
                String tail;
                try {
                    tail = readTail(f, 300_000);
                } catch (Throwable e) {
                    tail = "ошибка чтения: " + e;
                }
                out.add(new String[]{f.getName(), String.valueOf(f.length() / 1024),
                        new java.util.Date(f.lastModified()).toString(), tail});
                if (++count >= 4) {
                    break;
                }
            }
        } catch (Throwable ignored) {
            // пустой список + статус
        }
        return out;
    }

    /** Хвост самого свежего журнала (для скачивания), или null если журналов нет. */
    private static String mainJournalTail(int maxBytes) {
        try {
            File dir = logsDir();
            File[] files = dir != null ? dir.listFiles((d, n) -> n.matches("[A-Za-z0-9._ -]{1,80}\\.log(\\.[0-9]+)?")) : null;
            if (files == null || files.length == 0) {
                return null;
            }
            Arrays.sort(files, Comparator.comparingLong(File::lastModified).reversed());
            return readTail(files[0], maxBytes);
        } catch (Throwable e) {
            return "ошибка чтения: " + e;
        }
    }

    private static String readTail(File f, int maxBytes) throws IOException {
        long len = f.length();
        long start = Math.max(0, len - maxBytes);
        byte[] buf = new byte[(int) (len - start)];
        try (RandomAccessFile raf = new RandomAccessFile(f, "r")) {
            raf.seek(start);
            raf.readFully(buf);
        }
        String s = new String(buf, StandardCharsets.UTF_8);
        if (start > 0) {
            int nl = s.indexOf('\n');
            s = "(…обрезано начало…)\n" + (nl >= 0 ? s.substring(nl + 1) : s);
        }
        return s;
    }

    /** Resolved lazily via SAL: an OSGi service reference in the constructor would
     * stall plugin activation if the SAL service were unavailable. */
    private UserManager userManager() {
        try {
            return ComponentLocator.getComponent(UserManager.class);
        } catch (Throwable e) {
            return null;
        }
    }

    @Override
    public void init() throws ServletException {
        super.init();
        HostCapture.tryInstall();
        OpenTfcLogStore.info("diag", "Diagnostics servlet ready at /plugins/servlet/opentfc/diagnostics");
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if (!hasAccess(req, resp)) {
            return;
        }
        HostCapture.tryInstall(); // self-heal if capture missed earlier install points

        // Скачивание хвоста самого свежего журнала: имя файла фиксировано,
        // параметрами запроса не управляется.
        if ("1".equals(req.getParameter("dl"))) {
            String tail = mainJournalTail(5_000_000);
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
            sendTextDownload(resp);
            return;
        }
        sendHtml(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if (!hasAccess(req, resp)) {
            return;
        }
        if ("clear".equals(req.getParameter("action"))) {
            OpenTfcLogStore.clear();
        }
        resp.sendRedirect(req.getRequestURI());
    }

    private boolean hasAccess(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        UserManager userManager = userManager();
        if (userManager == null) {
            // SAL недоступен: пустим только с сессионной cookie Seraph (мягкая
            // проверка входа), иначе страница с журналами будет публичной.
            if (hasSessionCookie(req)) {
                return true;
            }
            resp.sendError(HttpServletResponse.SC_FORBIDDEN,
                    "SAL недоступен и сессионная cookie не найдена — войдите в Confluence и повторите");
            return false;
        }
        try {
            String username = userManager.getRemoteUsername();
            if (username != null && !username.isEmpty()) {
                return true;
            }
        } catch (Throwable ignored) {
            return hasSessionCookie(req); // SAL есть, но сломан — мягкая проверка
        }
        resp.sendError(HttpServletResponse.SC_FORBIDDEN, "Login required for OpenTFC diagnostics");
        return false;
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

    private void sendTextDownload(HttpServletResponse resp) throws IOException {
        resp.setContentType("text/plain; charset=utf-8");
        resp.setHeader("Content-Disposition", "attachment; filename=\"opentfc-diagnostics.log\"");
        byte[] body = OpenTfcLogStore.asText().getBytes(StandardCharsets.UTF_8);
        resp.setContentLength(body.length);
        try (PrintWriter w = resp.getWriter()) {
            w.write(OpenTfcLogStore.asText());
        }
    }

    private void sendHtml(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String level = req.getParameter("level");
        if (level == null || level.isEmpty()) {
            level = "ALL";
        }
        String auto = req.getParameter("auto");
        List<OpenTfcLogStore.Entry> entries = OpenTfcLogStore.snapshot();

        resp.setContentType("text/html; charset=utf-8");
        resp.setHeader("Cache-Control", "no-store");
        resp.setDateHeader("Expires", 0);

        StringBuilder h = new StringBuilder(64 * 1024);
        h.append("<!DOCTYPE html><html lang=\"ru\"><head><meta charset=\"utf-8\">");
        h.append("<title>OpenTFC Diagnostics</title>");
        if (auto != null) {
            h.append("<meta http-equiv=\"refresh\" content=\"").append(escape(auto)).append("\">");
        }
        h.append("<style>")
         .append("body{font-family:-apple-system,Segoe UI,Arial,sans-serif;margin:0;background:#f4f5f7;color:#172b4d}")
         .append("header{background:#172b4d;color:#fff;padding:12px 20px;display:flex;align-items:center;gap:16px}")
         .append("header h1{font-size:18px;margin:0}")
         .append(".toolbar a,.toolbar button{display:inline-block;margin-right:8px;padding:5px 10px;border-radius:4px;")
         .append("background:#dfe1e6;color:#172b4d;border:1px solid #c1c7d0;font-size:13px;text-decoration:none;cursor:pointer}")
         .append("main{padding:16px 20px;max-width:1400px}")
         .append("table.status{border-collapse:collapse;margin-bottom:16px;font-size:13px}")
         .append("table.status td{border:1px solid #c1c7d0;padding:4px 10px;background:#fff}")
         .append("table.status td.k{background:#fafbfc;font-weight:600}")
         .append("table.log{border-collapse:collapse;width:100%;font-size:12px}")
         .append("table.log th{background:#ebecf0;text-align:left;padding:6px 8px;border:1px solid #c1c7d0;position:sticky;top:0}")
         .append("table.log td{border:1px solid #dfe1e6;padding:4px 8px;vertical-align:top}")
         .append("tr.ERROR td.lvl{color:#bf2600;font-weight:700}")
         .append("tr.WARN td.lvl{color:#974f0c;font-weight:700}")
         .append("td.msg{font-family:Consolas,Menlo,monospace;white-space:pre-wrap;word-break:break-word}")
         .append(".note{background:#deebff;border:1px solid #b3d4ff;border-radius:4px;padding:10px 14px;font-size:13px;margin-bottom:16px}")
         .append(".bad{color:#bf2600;font-weight:600}.good{color:#006644;font-weight:600}")
         .append("</style></head><body>");

        h.append("<header><h1>OpenTFC Diagnostics v").append(VERSION).append("</h1><div class=\"toolbar\">")
         .append("<a href=\"?\">Обновить</a>")
         .append("<a href=\"?auto=10\">Авто-обновление 10с</a>")
         .append("<a href=\"?\">Выкл.</a>")
         .append("<a href=\"?format=txt\">Скачать .log</a>")
         .append("<form method=\"post\" style=\"display:inline\" onSubmit=\"return confirm('Очистить буфер логов?')\">")
         .append("<input type=\"hidden\" name=\"action\" value=\"clear\"><button type=\"submit\">Очистить</button></form>")
         .append("</div></header><main>");

        h.append("<div class=\"note\"><b>Страница работает без velocity-декораторов и AUI</b> — доступна даже на сломанном инстансе. ")
         .append("Здесь: события самого OpenTFC, события plugin framework (ENABLED/DISABLED всех приложений) и перехваченные WARN/ERROR ")
         .append("общего лога Confluence. Если OpenTFC отключить/удалить — страница пропадёт. ")
         .append("UPM (Управление приложениями): <a href=\"/plugins/servlet/upm\">/plugins/servlet/upm</a>. ")
         .append("Компаньон при полном отказе активации: <a href=\"/plugins/servlet/diag-lite\">/plugins/servlet/diag-lite</a>. ")
         .append("Ссылки-пробы ядра: <a href=\"/login.action\">/login.action</a>, <a href=\"/status\">/status</a>.</div>");

        String capState = OpenTfcLogStore.isHostCaptureInstalled()
                ? "<span class=\"good\">установлен</span>"
                : (HostCapture.wasAttempted() ? "<span class=\"bad\">НЕ установлен</span>" : "ещё не выполнялся");
        h.append("<table class=\"status\">")
         .append("<tr><td class=\"k\">Время сервера</td><td>").append(Instant.now()).append("</td>")
         .append("<td class=\"k\">Перехват лога хоста</td><td>").append(capState).append("</td></tr>")
         .append("<tr><td class=\"k\">Spring/lifecycle bean</td><td>")
         .append(OpenTfcLogStore.isLifecycleUp() ? "<span class=\"good\">поднят</span>" : "<span class=\"bad\">НЕ поднят</span>")
         .append("</td>")
         .append("<td class=\"k\">Записей / вытеснено</td><td>").append(entries.size())
         .append(" / ").append(OpenTfcLogStore.droppedCount()).append("</td></tr>")
         .append("<tr><td class=\"k\">Версия Confluence</td><td>").append(escape(confluenceVersion())).append("</td>")
         .append("<tr><td class=\"k\">SAL UserManager</td><td colspan=\"3\">")
         .append(userManager() != null ? "подключён" : "<span class=\"bad\">недоступен: " + escape(salError) + "</span> — журналы ищутся через confluence.home (источник: " + escape(homeSource) + ")")
         .append("</td></tr>")
         .append("</table>");

        // ===== Файлы журналов сервера (причины отказов активации — здесь) =====
        h.append("<h2 style=\"font-size:15px;margin:18px 0 8px\">Журналы сервера (&lt;home&gt;/logs) ")
         .append("<a style=\"font-size:12px;font-weight:400\" href=\"?dl=1\">[скачать хвост самого свежего]</a></h2>");
        List<String[]> journals = journalFiles();
        if (journals.isEmpty()) {
            h.append("<p><i>каталог logs не найден или пуст</i></p>");
        }
        for (String[] j : journals) {
            h.append("<details><summary>").append(escape(j[0])).append(" — ").append(j[1]).append(" КБ, изменён ")
             .append(escape(j[2])).append("</summary><pre style=\"max-height:520px;overflow:auto;font-size:11px;white-space:pre-wrap;word-break:break-word;margin:4px 0 10px\">")
             .append(escape(j[3])).append("</pre></details>");
        }

        h.append("<p>Фильтр: ");
        for (String[] opt : new String[][]{{"ALL", "Все"}, {"INFO", "INFO+"}, {"WARN", "WARN+"}, {"ERROR", "Только ERROR"}}) {
            boolean active = opt[0].equals(level);
            h.append(active ? "<b>" : "").append("<a href=\"?level=").append(opt[0]).append("\">").append(opt[1]).append("</a>").append(active ? "</b>" : "").append(" &nbsp; ");
        }
        h.append("</p>");

        h.append("<table class=\"log\"><tr><th style=\"width:190px\">Время</th><th style=\"width:60px\">Уровень</th>")
         .append("<th style=\"width:220px\">Источник</th><th>Сообщение</th></tr>");

        int shown = 0;
        for (int i = entries.size() - 1; i >= 0; i--) {
            OpenTfcLogStore.Entry e = entries.get(i);
            if (!passesFilter(e.level, e.source, level)) {
                continue;
            }
            shown++;
            if (shown > 1000) {
                h.append("<tr><td colspan=\"4\"><i>… показаны последние 1000 записей, остальное — в «Скачать .log»</i></td></tr>");
                break;
            }
            h.append("<tr class=\"").append(e.level).append("\"><td>").append(Instant.ofEpochMilli(e.timestamp))
             .append("</td><td class=\"lvl\">").append(e.level)
             .append("</td><td>").append(escape(e.source))
             .append("</td><td class=\"msg\">").append(escape(e.message)).append("</td></tr>");
        }
        if (shown == 0) {
            h.append("<tr><td colspan=\"4\"><i>Записей нет. Если плагин только что установлен — подождите пару секунд и обновите страницу.</i></td></tr>");
        }
        h.append("</table></main></body></html>");

        byte[] body = h.toString().getBytes(StandardCharsets.UTF_8);
        resp.setContentLength(body.length);
        try (PrintWriter w = resp.getWriter()) {
            w.write(h.toString());
        }
    }

    private static boolean passesFilter(String entryLevel, String source, String filter) {
        switch (filter) {
            case "ERROR": return "ERROR".equals(entryLevel);
            case "WARN": return "ERROR".equals(entryLevel) || "WARN".equals(entryLevel);
            case "INFO": return !"TRACE".equals(entryLevel) && !"DEBUG".equals(entryLevel);
            default: return true;
        }
    }

    private static String confluenceVersion() {
        try {
            Class<?> bu = Class.forName("com.atlassian.confluence.util.BuildUtils");
            return String.valueOf(bu.getMethod("getVersion").invoke(null));
        } catch (Throwable e) {
            return "неизвестна (BuildUtils недоступен)";
        }
    }

    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
