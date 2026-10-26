import { ColumnDef, RowData } from '../core/table-model';

export type AggregateFn = 'sum' | 'count' | 'min' | 'max' | 'avg' | 'percent_row' | 'percent_col';

export class PivotRenderer {
  private container: HTMLElement;
  private config: any;
  private currentColumns: ColumnDef[] = [];
  private currentRows: RowData[] = [];

  private selectedRows: number[] = [0]; // default row field index
  private selectedCols: number[] = [];  // col field index
  private selectedVal: number = 1;      // value field index
  private currentAgg: AggregateFn = 'sum';

  constructor(container: HTMLElement) {
    this.container = container;
    const raw = container.getAttribute('data-opentfc-config') || '{}';
    try {
      this.config = JSON.parse(raw);
    } catch {
      this.config = {};
    }

    this.currentAgg = (this.config.aggregateFunction as AggregateFn) || 'sum';

    this.parseInlineSource();
    this.bindUpstream();
  }

  private parseInlineSource() {
    const sourceEl = this.container.querySelector('.opentfc-pivot-source table');
    if (!sourceEl) return;

    const cols: ColumnDef[] = [];
    sourceEl.querySelectorAll('thead th, tr:first-child th').forEach((th, idx) => {
      cols.push({ index: idx, name: th.textContent?.trim() || `Col ${idx + 1}`, type: 'TEXT' });
    });

    const rows: RowData[] = [];
    sourceEl.querySelectorAll('tbody tr').forEach(tr => {
      const cells: any[] = [];
      tr.querySelectorAll('td, th').forEach(td => {
        cells.push({ val: td.textContent?.trim() || '', type: 'TEXT' });
      });
      rows.push({ cells });
    });

    if (cols.length > 0 && rows.length > 0) {
      this.setData(cols, rows);
    }
  }

  private bindUpstream() {
    document.addEventListener('opentfc:data-updated', (e: Event) => {
      const custom = e as CustomEvent;
      if (!this.config.sourceMacroId || custom.detail.macroId === this.config.sourceMacroId) {
        this.setData(custom.detail.columns, custom.detail.rows);
      }
    });
  }

  public setData(columns: ColumnDef[], rows: RowData[]) {
    this.currentColumns = columns;
    this.currentRows = rows;
    if (this.selectedVal >= columns.length) {
      this.selectedVal = Math.min(1, columns.length - 1);
    }
    this.renderControls();
    this.computeAndRenderPivot();
  }

  private renderControls() {
    const controls = this.container.querySelector('.opentfc-pivot-controls');
    if (!controls) return;

    controls.innerHTML = `
      <div style="display:flex; flex-wrap:wrap; gap:10px; align-items:center; padding:10px; background:var(--ds-surface-sunken, #f4f5f7); border-radius:4px 4px 0 0; border:1px solid var(--ds-border, #dfe1e6);">
        <div style="display:flex; align-items:center; gap:4px;">
          <label style="font-size:12px; font-weight:600;">Строки:</label>
          <select class="opentfc-input opentfc-pivot-row-select" style="width:auto; padding:2px 8px;">
            ${this.currentColumns.map((c, i) => `<option value="${i}" ${this.selectedRows.includes(i) ? 'selected' : ''}>${this.escapeHtml(c.name)}</option>`).join('')}
          </select>
        </div>

        <div style="display:flex; align-items:center; gap:4px;">
          <label style="font-size:12px; font-weight:600;">Значения:</label>
          <select class="opentfc-input opentfc-pivot-val-select" style="width:auto; padding:2px 8px;">
            ${this.currentColumns.map((c, i) => `<option value="${i}" ${this.selectedVal === i ? 'selected' : ''}>${this.escapeHtml(c.name)}</option>`).join('')}
          </select>
        </div>

        <div style="display:flex; align-items:center; gap:4px;">
          <label style="font-size:12px; font-weight:600;">Агрегация:</label>
          <select class="opentfc-input opentfc-pivot-agg-select" style="width:auto; padding:2px 8px;">
            <option value="sum" ${this.currentAgg === 'sum' ? 'selected' : ''}>Сумма (SUM)</option>
            <option value="count" ${this.currentAgg === 'count' ? 'selected' : ''}>Количество (COUNT)</option>
            <option value="avg" ${this.currentAgg === 'avg' ? 'selected' : ''}>Среднее (AVG)</option>
            <option value="min" ${this.currentAgg === 'min' ? 'selected' : ''}>Минимум (MIN)</option>
            <option value="max" ${this.currentAgg === 'max' ? 'selected' : ''}>Максимум (MAX)</option>
          </select>
        </div>

        <button class="opentfc-btn opentfc-btn-export-pivot-csv" style="margin-left:auto;">Экспорт в CSV</button>
      </div>
    `;

    controls.querySelector('.opentfc-pivot-row-select')?.addEventListener('change', (e: any) => {
      this.selectedRows = [parseInt(e.target.value, 10)];
      this.computeAndRenderPivot();
    });

    controls.querySelector('.opentfc-pivot-val-select')?.addEventListener('change', (e: any) => {
      this.selectedVal = parseInt(e.target.value, 10);
      this.computeAndRenderPivot();
    });

    controls.querySelector('.opentfc-pivot-agg-select')?.addEventListener('change', (e: any) => {
      this.currentAgg = e.target.value as AggregateFn;
      this.computeAndRenderPivot();
    });

    controls.querySelector('.opentfc-btn-export-pivot-csv')?.addEventListener('click', () => {
      this.exportPivotCsv();
    });
  }

  private computeAndRenderPivot() {
    const grid = this.container.querySelector('.opentfc-pivot-grid');
    if (!grid || this.currentColumns.length === 0 || this.currentRows.length === 0) return;

    const rowColIdx = this.selectedRows[0] || 0;
    const rowColName = this.currentColumns[rowColIdx]?.name || 'Группа';
    const valColName = this.currentColumns[this.selectedVal]?.name || 'Значение';

    // Group rows by rowCol
    const groups = new Map<string, number[]>();
    this.currentRows.forEach(r => {
      const key = String(r.cells[rowColIdx]?.val || '(Пусто)');
      const rawVal = r.cells[this.selectedVal]?.val;
      const num = parseFloat(String(rawVal || '').replace(/[^0-9.-]/g, '')) || 0;

      if (!groups.has(key)) {
        groups.set(key, []);
      }
      groups.get(key)!.push(num);
    });

    // Compute aggregates
    const resultRows: Array<{ group: string; result: number }> = [];
    let grandTotal = 0;

    groups.forEach((values, groupKey) => {
      let aggVal = 0;
      if (this.currentAgg === 'sum') {
        aggVal = values.reduce((a, b) => a + b, 0);
      } else if (this.currentAgg === 'count') {
        aggVal = values.length;
      } else if (this.currentAgg === 'avg') {
        aggVal = values.length > 0 ? values.reduce((a, b) => a + b, 0) / values.length : 0;
      } else if (this.currentAgg === 'min') {
        aggVal = Math.min(...values);
      } else if (this.currentAgg === 'max') {
        aggVal = Math.max(...values);
      }
      resultRows.push({ group: groupKey, result: aggVal });
      grandTotal += aggVal;
    });

    // Sort by group name
    resultRows.sort((a, b) => a.group.localeCompare(b.group));

    grid.innerHTML = `
      <table class="confluenceTable tablesorter" style="width:100%; border-collapse:collapse;">
        <thead>
          <tr>
            <th class="confluenceTh">${this.escapeHtml(rowColName)}</th>
            <th class="confluenceTh">${this.escapeHtml(valColName)} (${this.currentAgg.toUpperCase()})</th>
          </tr>
        </thead>
        <tbody>
          ${resultRows.map(r => `
            <tr>
              <td class="confluenceTd">${this.escapeHtml(r.group)}</td>
              <td class="confluenceTd" style="text-align:right;">${r.result.toLocaleString(undefined, { maximumFractionDigits: 2 })}</td>
            </tr>
          `).join('')}
          <tr class="opentfc-totals-row">
            <td class="confluenceTd"><strong>Общий итог:</strong></td>
            <td class="confluenceTd" style="text-align:right;"><strong>${grandTotal.toLocaleString(undefined, { maximumFractionDigits: 2 })}</strong></td>
          </tr>
        </tbody>
      </table>
    `;
  }

  private exportPivotCsv() {
    const table = this.container.querySelector('.opentfc-pivot-grid table');
    if (!table) return;

    const rows: string[] = [];
    table.querySelectorAll('tr').forEach(tr => {
      const cells: string[] = [];
      tr.querySelectorAll('th, td').forEach(td => {
        cells.push(`"${td.textContent?.trim().replace(/"/g, '""') || ''}"`);
      });
      rows.push(cells.join(','));
    });

    const csvContent = '\uFEFF' + rows.join('\r\n');
    const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
    const a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = `pivot-${this.config.macroId || 'export'}.csv`;
    a.click();
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
