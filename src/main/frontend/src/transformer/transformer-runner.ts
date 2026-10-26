import { ColumnDef, RowData, TableData } from '../core/table-model';

export class TransformerRunner {
  private container: HTMLElement;
  private config: any;
  private sourceTables: TableData[] = [];

  constructor(container: HTMLElement) {
    this.container = container;
    const raw = container.getAttribute('data-opentfc-config') || '{}';
    try {
      this.config = JSON.parse(raw);
    } catch {
      this.config = {};
    }

    this.parseInlineSources();
    this.initUI();
    this.executeQuery();
  }

  private parseInlineSources() {
    const sourcesWrapper = this.container.querySelector('.opentfc-transformer-sources');
    if (!sourcesWrapper) return;

    const tables = sourcesWrapper.querySelectorAll('table');
    tables.forEach((tableEl, tIdx) => {
      const cols: ColumnDef[] = [];
      tableEl.querySelectorAll('thead th, tr:first-child th').forEach((th, cIdx) => {
        cols.push({ index: cIdx, name: th.textContent?.trim() || `Col ${cIdx + 1}`, type: 'TEXT' });
      });

      const rows: RowData[] = [];
      tableEl.querySelectorAll('tbody tr').forEach(tr => {
        const cells: any[] = [];
        tr.querySelectorAll('td, th').forEach(td => {
          cells.push({ val: td.textContent?.trim() || '', type: 'TEXT' });
        });
        rows.push({ cells });
      });

      this.sourceTables.push({
        id: `T${tIdx + 1}`,
        columns: cols,
        rows,
        totalRows: rows.length
      });
    });
  }

  private initUI() {
    const output = this.container.querySelector('.opentfc-transformer-output');
    if (!output) return;

    output.innerHTML = `
      <div style="border: 1px solid var(--ds-border, #dfe1e6); border-radius: 4px; overflow: hidden; margin-bottom: 12px;">
        <div style="display:flex; justify-content:space-between; align-items:center; padding:8px 12px; background:var(--ds-surface-sunken, #f4f5f7); border-bottom:1px solid var(--ds-border, #dfe1e6);">
          <div style="font-weight:600; font-size:13px;">SQL Запрос (DuckDB)</div>
          <div style="display:flex; gap:6px;">
            <select class="opentfc-input opentfc-preset-selector" style="width:auto; padding:2px 8px; font-size:12px;">
              <option value="">-- Пресеты --</option>
              <option value="select_all">Показать всё (SELECT * FROM T1)</option>
              <option value="count_dup">Посчитать дубликаты</option>
              <option value="filter_sum">Фильтр и сумма</option>
            </select>
            <button class="opentfc-btn opentfc-btn-primary opentfc-btn-run-sql">Выполнить</button>
          </div>
        </div>
        <div style="padding:8px 12px; background:#fafbfc;">
          <textarea class="opentfc-input opentfc-sql-textarea" style="width:100%; min-height:60px; font-family:monospace; font-size:13px; resize:vertical;">${this.config.sqlQuery || 'SELECT * FROM T1'}</textarea>
        </div>
      </div>
      <div class="opentfc-transformer-result-grid"></div>
    `;

    const textarea = output.querySelector('.opentfc-sql-textarea') as HTMLTextAreaElement;
    output.querySelector('.opentfc-preset-selector')?.addEventListener('change', (e: any) => {
      const val = e.target.value;
      if (val === 'select_all') {
        textarea.value = 'SELECT * FROM T1';
      } else if (val === 'count_dup') {
        textarea.value = 'SELECT "Column 1", COUNT(*) as count FROM T1 GROUP BY "Column 1" HAVING COUNT(*) > 1';
      } else if (val === 'filter_sum') {
        textarea.value = 'SELECT * FROM T1 LIMIT 10';
      }
    });

    output.querySelector('.opentfc-btn-run-sql')?.addEventListener('click', () => {
      this.config.sqlQuery = textarea.value;
      this.executeQuery();
    });
  }

  public executeQuery() {
    const grid = this.container.querySelector('.opentfc-transformer-result-grid');
    if (!grid) return;

    grid.innerHTML = '<div style="padding:12px; color:#6b778c;">Выполнение SQL-запроса...</div>';

    const pageId = (window as any).AJS?.params?.pageId || 0;
    const payload = {
      sql: this.config.sqlQuery || 'SELECT * FROM T1',
      tables: this.sourceTables
    };

    fetch(`/rest/opentfc/1.0/transformer/${pageId}/${this.config.macroId}/query`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-Atlassian-Token': 'no-check'
      },
      body: JSON.stringify(payload)
    })
      .then(res => res.json())
      .then(data => {
        if (!data.success) {
          grid.innerHTML = `<div style="padding:12px; color:#de350b; background:#ffebe6; border-radius:3px;"><strong>Ошибка SQL:</strong> ${this.escapeHtml(data.errorMessage)}</div>`;
          return;
        }

        const table = data.table;
        if (!table || !table.columns || table.columns.length === 0) {
          grid.innerHTML = '<div style="padding:12px; color:#6b778c;">Результат запроса пуст.</div>';
          return;
        }

        grid.innerHTML = `
          <div style="font-size:12px; color:#6b778c; margin-bottom:6px;">Время выполнения: ${data.executionTimeMs} мс | Строк: ${table.rows.length}</div>
          <div style="overflow-x:auto; border:1px solid var(--ds-border, #dfe1e6); border-radius:4px;">
            <table class="confluenceTable tablesorter" style="width:100%; border-collapse:collapse;">
              <thead>
                <tr>
                  ${table.columns.map((c: any) => `<th class="confluenceTh">${this.escapeHtml(c.name)}</th>`).join('')}
                </tr>
              </thead>
              <tbody>
                ${table.rows.map((r: any) => `
                  <tr>
                    ${r.cells.map((c: any) => `<td class="confluenceTd">${this.escapeHtml(c.val)}</td>`).join('')}
                  </tr>
                `).join('')}
              </tbody>
            </table>
          </div>
        `;

        // Notify downstream macros about transformed data
        const event = new CustomEvent('opentfc:data-updated', {
          bubbles: true,
          detail: {
            macroId: this.config.macroId,
            columns: table.columns,
            rows: table.rows
          }
        });
        this.container.dispatchEvent(event);
      })
      .catch(err => {
        grid.innerHTML = `<div style="padding:12px; color:#de350b; background:#ffebe6; border-radius:3px;">Ошибка запроса: ${err.message}</div>`;
      });
  }

  private escapeHtml(str: any): string {
    if (str === null || str === undefined) return '';
    return String(str)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#039;');
  }
}
