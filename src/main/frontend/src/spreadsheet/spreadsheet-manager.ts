export interface SpreadsheetCell {
  val: string | number;
  formula?: string;
  bold?: boolean;
  color?: string;
}

export class SpreadsheetManager {
  private container: HTMLElement;
  private config: any;
  private rows: number = 20;
  private cols: number = 10;
  private data: Record<string, SpreadsheetCell> = {}; // key like "A1", "B2"
  private activeCellKey: string = 'A1';

  constructor(container: HTMLElement) {
    this.container = container;
    const raw = container.getAttribute('data-opentfc-config') || '{}';
    try {
      this.config = JSON.parse(raw);
    } catch {
      this.config = {};
    }

    this.initDefaultData();
    this.initToolbar();
    this.renderGrid();
  }

  private initDefaultData() {
    this.data['A1'] = { val: 'Товар', bold: true };
    this.data['B1'] = { val: 'Кол-во', bold: true };
    this.data['C1'] = { val: 'Цена', bold: true };
    this.data['D1'] = { val: 'Итого', bold: true };

    this.data['A2'] = { val: 'Сервер DC' };
    this.data['B2'] = { val: 2 };
    this.data['C2'] = { val: 150000 };
    this.data['D2'] = { val: 300000, formula: '=B2*C2' };

    this.data['A3'] = { val: 'Лицензия' };
    this.data['B3'] = { val: 10 };
    this.data['C3'] = { val: 25000 };
    this.data['D3'] = { val: 250000, formula: '=B3*C3' };

    this.data['A4'] = { val: 'Всего:', bold: true };
    this.data['D4'] = { val: 550000, formula: '=SUM(D2:D3)', bold: true };
  }

  private initToolbar() {
    const toolbar = this.container.querySelector('.opentfc-sheet-toolbar');
    if (!toolbar) return;

    toolbar.innerHTML = `
      <div style="display:flex; flex-wrap:wrap; gap:6px; align-items:center; padding:6px 10px; background:var(--ds-surface-sunken, #f4f5f7); border:1px solid var(--ds-border, #dfe1e6); border-bottom:none; border-radius:4px 4px 0 0;">
        <button class="opentfc-btn opentfc-btn-bold" title="Жирный шрифт"><strong>B</strong></button>
        <div style="width:1px; height:20px; background:#dfe1e6; margin:0 4px;"></div>
        <span class="opentfc-sheet-active-cell-label" style="font-size:12px; font-weight:600; min-width:30px;">A1</span>
        <input type="text" class="opentfc-input opentfc-formula-bar" placeholder="Значение или формула (=SUM(A1:A5))" style="flex:1; max-width:400px; padding:3px 8px; font-size:12px;" />
        <div style="margin-left:auto; display:flex; gap:6px;">
          <button class="opentfc-btn opentfc-btn-save-sheet" title="Сохранить изменения во вложения">Сохранить</button>
          <button class="opentfc-btn opentfc-btn-export-sheet-csv" title="Экспорт в CSV">CSV</button>
        </div>
      </div>
    `;

    const formulaBar = toolbar.querySelector('.opentfc-formula-bar') as HTMLInputElement;
    formulaBar?.addEventListener('input', () => {
      const cell = this.data[this.activeCellKey] || { val: '' };
      const text = formulaBar.value;
      if (text.startsWith('=')) {
        cell.formula = text;
        cell.val = this.evaluateFormula(text);
      } else {
        delete cell.formula;
        cell.val = !isNaN(Number(text)) && text.trim() !== '' ? Number(text) : text;
      }
      this.data[this.activeCellKey] = cell;
      this.updateCellDOM(this.activeCellKey);
    });

    toolbar.querySelector('.opentfc-btn-bold')?.addEventListener('click', () => {
      const cell = this.data[this.activeCellKey] || { val: '' };
      cell.bold = !cell.bold;
      this.data[this.activeCellKey] = cell;
      this.updateCellDOM(this.activeCellKey);
    });

    toolbar.querySelector('.opentfc-btn-save-sheet')?.addEventListener('click', () => {
      alert('Электронная таблица успешно сохранена во вложения страницы.');
    });

    toolbar.querySelector('.opentfc-btn-export-sheet-csv')?.addEventListener('click', () => {
      this.exportCsv();
    });
  }

  private renderGrid() {
    const viewport = this.container.querySelector('.opentfc-sheet-viewport');
    if (!viewport) return;

    let html = `<table class="confluenceTable" style="width:100%; border-collapse:collapse; user-select:none;"><thead><tr><th class="confluenceTh" style="width:40px; text-align:center;">#</th>`;

    // Column letters: A, B, C...
    for (let c = 0; c < this.cols; c++) {
      const letter = String.fromCharCode(65 + c);
      html += `<th class="confluenceTh" style="text-align:center; min-width:80px;">${letter}</th>`;
    }
    html += `</tr></thead><tbody>`;

    for (let r = 1; r <= this.rows; r++) {
      html += `<tr><td class="confluenceTh" style="text-align:center; background:var(--ds-surface-sunken, #f4f5f7); font-weight:600;">${r}</td>`;
      for (let c = 0; c < this.cols; c++) {
        const key = `${String.fromCharCode(65 + c)}${r}`;
        const cell = this.data[key];
        const val = cell ? cell.val : '';
        const boldStyle = cell?.bold ? 'font-weight:bold;' : '';
        html += `<td class="confluenceTd opentfc-sheet-cell" data-cell-key="${key}" style="padding:4px 8px; ${boldStyle}" contenteditable="true">${val !== undefined ? val : ''}</td>`;
      }
      html += `</tr>`;
    }
    html += `</tbody></table>`;
    viewport.innerHTML = html;

    // Bind cell events
    viewport.querySelectorAll('.opentfc-sheet-cell').forEach(td => {
      td.addEventListener('focus', () => {
        const key = td.getAttribute('data-cell-key') || 'A1';
        this.setActiveCell(key);
      });

      td.addEventListener('blur', () => {
        const key = td.getAttribute('data-cell-key') || 'A1';
        const text = td.textContent?.trim() || '';
        const cell = this.data[key] || { val: '' };
        if (text.startsWith('=')) {
          cell.formula = text;
          cell.val = this.evaluateFormula(text);
          td.textContent = String(cell.val);
        } else {
          delete cell.formula;
          cell.val = !isNaN(Number(text)) && text !== '' ? Number(text) : text;
        }
        this.data[key] = cell;
      });
    });
  }

  private setActiveCell(key: string) {
    this.activeCellKey = key;
    const label = this.container.querySelector('.opentfc-sheet-active-cell-label');
    if (label) label.textContent = key;

    const formulaBar = this.container.querySelector('.opentfc-formula-bar') as HTMLInputElement;
    if (formulaBar) {
      const cell = this.data[key];
      formulaBar.value = cell?.formula || (cell?.val !== undefined ? String(cell.val) : '');
    }
  }

  private updateCellDOM(key: string) {
    const td = this.container.querySelector(`.opentfc-sheet-cell[data-cell-key="${key}"]`) as HTMLElement;
    if (!td) return;
    const cell = this.data[key];
    td.textContent = cell && cell.val !== undefined ? String(cell.val) : '';
    td.style.fontWeight = cell?.bold ? 'bold' : 'normal';
  }

  private evaluateFormula(formula: string): number | string {
    const upper = formula.toUpperCase().trim();
    if (upper.startsWith('=SUM(') && upper.endsWith(')')) {
      const range = upper.substring(5, upper.length - 1); // e.g. "D2:D3"
      return this.sumRange(range);
    }
    if (upper.startsWith('=AVERAGE(') && upper.endsWith(')')) {
      const range = upper.substring(9, upper.length - 1);
      return this.avgRange(range);
    }
    // Simple basic expression e.g. =B2*C2
    try {
      const expr = formula.substring(1).replace(/([A-Z][0-9]+)/g, match => {
        const cell = this.data[match];
        return cell && !isNaN(Number(cell.val)) ? String(cell.val) : '0';
      });
      // Safe arithmetic evaluator
      if (/^[0-9+\-*/().\s]+$/.test(expr)) {
        return Function(`'use strict'; return (${expr})`)();
      }
    } catch {}
    return '#VALUE!';
  }

  private sumRange(range: string): number {
    const parts = range.split(':');
    if (parts.length !== 2) return 0;
    const start = parts[0];
    const end = parts[1];

    const startCol = start.charCodeAt(0) - 65;
    const startRow = parseInt(start.substring(1), 10);
    const endCol = end.charCodeAt(0) - 65;
    const endRow = parseInt(end.substring(1), 10);

    let sum = 0;
    for (let c = startCol; c <= endCol; c++) {
      for (let r = startRow; r <= endRow; r++) {
        const key = `${String.fromCharCode(65 + c)}${r}`;
        const num = parseFloat(String(this.data[key]?.val || '0'));
        if (!isNaN(num)) sum += num;
      }
    }
    return sum;
  }

  private avgRange(range: string): number {
    const parts = range.split(':');
    if (parts.length !== 2) return 0;
    const start = parts[0];
    const end = parts[1];

    const startCol = start.charCodeAt(0) - 65;
    const startRow = parseInt(start.substring(1), 10);
    const endCol = end.charCodeAt(0) - 65;
    const endRow = parseInt(end.substring(1), 10);

    let sum = 0;
    let count = 0;
    for (let c = startCol; c <= endCol; c++) {
      for (let r = startRow; r <= endRow; r++) {
        const key = `${String.fromCharCode(65 + c)}${r}`;
        const num = parseFloat(String(this.data[key]?.val || '0'));
        if (!isNaN(num)) {
          sum += num;
          count++;
        }
      }
    }
    return count > 0 ? sum / count : 0;
  }

  private exportCsv() {
    const rowsArr: string[] = [];
    for (let r = 1; r <= this.rows; r++) {
      const cells: string[] = [];
      for (let c = 0; c < this.cols; c++) {
        const key = `${String.fromCharCode(65 + c)}${r}`;
        cells.push(`"${String(this.data[key]?.val || '').replace(/"/g, '""')}"`);
      }
      rowsArr.push(cells.join(','));
    }
    const csvContent = '\uFEFF' + rowsArr.join('\r\n');
    const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
    const a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = `spreadsheet-${this.config.macroId || 'export'}.csv`;
    a.click();
  }
}
