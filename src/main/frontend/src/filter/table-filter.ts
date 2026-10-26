import { ColumnDef, ColumnType, FilterState, RowData, TableData } from '../core/table-model';

export class TableFilterComponent {
  private container: HTMLElement;
  private config: any;
  private tableData: TableData;
  private filteredRows: RowData[] = [];
  private state: FilterState;

  constructor(container: HTMLElement) {
    this.container = container;
    const configRaw = container.getAttribute('data-opentfc-config') || '{}';
    try {
      this.config = JSON.parse(configRaw);
    } catch {
      this.config = {};
    }

    this.state = {
      globalSearch: '',
      columnFilters: {},
      sorts: [],
      hiddenColumns: [],
      currentPage: 1,
      pageSize: this.config.pageSize === 'all' ? 'all' : parseInt(this.config.pageSize || '25', 10)
    };

    this.tableData = this.parseDOMTable();
    this.initUI();
    this.applyFilters();
  }

  private parseDOMTable(): TableData {
    const tableEl = this.container.querySelector('table');
    if (!tableEl) {
      return { id: this.config.macroId, columns: [], rows: [], totalRows: 0 };
    }

    const columns: ColumnDef[] = [];
    const headers = tableEl.querySelectorAll('thead th, thead td, tr:first-child th, tr:first-child td');
    headers.forEach((th, idx) => {
      columns.push({
        index: idx,
        name: th.textContent?.trim() || `Col ${idx + 1}`,
        type: 'TEXT',
        visible: true
      });
    });

    const rows: RowData[] = [];
    const trEls = tableEl.querySelectorAll('tbody tr, tr');
    trEls.forEach((tr, rIdx) => {
      // Skip header if it was parsed from first row
      if (rIdx === 0 && tableEl.querySelector('thead') === null) return;
      const cells = tr.querySelectorAll('td, th');
      if (cells.length === 0) return;

      const rowCells: any[] = [];
      columns.forEach((col, cIdx) => {
        const td = cells[cIdx];
        if (td) {
          rowCells.push({
            val: td.textContent?.trim() || '',
            html: td.innerHTML,
            type: 'TEXT'
          });
        } else {
          rowCells.push({ val: '', html: '', type: 'TEXT' });
        }
      });
      rows.push({ cells: rowCells });
    });

    // Detect column types
    this.detectColumnTypes(columns, rows);

    return {
      id: this.config.macroId,
      columns,
      rows,
      totalRows: rows.length
    };
  }

  private detectColumnTypes(columns: ColumnDef[], rows: RowData[]) {
    columns.forEach((col, cIdx) => {
      let numCount = 0;
      let nonBlank = 0;
      const uniqueVals = new Set<string>();

      rows.forEach(r => {
        const val = r.cells[cIdx]?.val;
        if (val !== undefined && val !== '') {
          nonBlank++;
          uniqueVals.add(val);
          if (!isNaN(Number(val.replace(/[,\s$€₽%]/g, '')))) {
            numCount++;
          }
        }
      });

      if (nonBlank > 0 && numCount / nonBlank >= 0.8) {
        col.type = 'NUMBER';
      } else if (uniqueVals.size > 0 && uniqueVals.size <= 25 && nonBlank > 5) {
        col.type = 'SELECT';
        col.options = Array.from(uniqueVals);
      } else {
        col.type = 'TEXT';
      }
    });
  }

  private initUI() {
    const toolbar = this.container.querySelector('.opentfc-toolbar');
    if (!toolbar) return;

    toolbar.innerHTML = `
      <div class="opentfc-search-box">
        <input type="text" class="opentfc-input opentfc-search-input" placeholder="Поиск по таблице..." />
      </div>
      <div class="opentfc-actions" style="margin-left: auto; display: flex; gap: 6px;">
        <button class="opentfc-btn opentfc-btn-export-csv" title="Экспорт в CSV">CSV</button>
        <button class="opentfc-btn opentfc-btn-reset" title="Сбросить фильтры">Сброс</button>
      </div>
    `;

    const searchInput = toolbar.querySelector('.opentfc-search-input') as HTMLInputElement;
    searchInput?.addEventListener('input', () => {
      this.state.globalSearch = searchInput.value.trim().toLowerCase();
      this.state.currentPage = 1;
      this.applyFilters();
    });

    const exportCsvBtn = toolbar.querySelector('.opentfc-btn-export-csv');
    exportCsvBtn?.addEventListener('click', () => this.exportCsv());

    const resetBtn = toolbar.querySelector('.opentfc-btn-reset');
    resetBtn?.addEventListener('click', () => {
      this.state.globalSearch = '';
      this.state.columnFilters = {};
      this.state.sorts = [];
      if (searchInput) searchInput.value = '';
      this.state.currentPage = 1;
      this.applyFilters();
    });

    // Add sort and click listeners to table headers
    const thEls = this.container.querySelectorAll('.opentfc-table-wrapper th');
    thEls.forEach((th, cIdx) => {
      th.addEventListener('click', (e: Event) => {
        const mouseEvent = e as MouseEvent;
        this.handleHeaderClick(cIdx, mouseEvent.shiftKey);
      });
    });
  }

  private handleHeaderClick(columnIndex: number, isMulti: boolean) {
    const existing = this.state.sorts.find(s => s.columnIndex === columnIndex);
    if (!existing) {
      if (!isMulti) this.state.sorts = [];
      this.state.sorts.push({ columnIndex, direction: 'asc' });
    } else if (existing.direction === 'asc') {
      existing.direction = 'desc';
    } else {
      this.state.sorts = this.state.sorts.filter(s => s.columnIndex !== columnIndex);
    }
    this.applyFilters();
  }

  public applyFilters() {
    let rows = [...this.tableData.rows];

    // 1. Global text filter
    if (this.state.globalSearch) {
      const q = this.state.globalSearch;
      rows = rows.filter(r => r.cells.some(c => c.val && String(c.val).toLowerCase().includes(q)));
    }

    // 2. Sorting
    if (this.state.sorts.length > 0) {
      rows.sort((a, b) => {
        for (const s of this.state.sorts) {
          const valA = a.cells[s.columnIndex]?.val || '';
          const valB = b.cells[s.columnIndex]?.val || '';
          const colType = this.tableData.columns[s.columnIndex]?.type;

          let cmp = 0;
          if (colType === 'NUMBER') {
            const nA = parseFloat(String(valA).replace(/[^0-9.-]/g, '')) || 0;
            const nB = parseFloat(String(valB).replace(/[^0-9.-]/g, '')) || 0;
            cmp = nA - nB;
          } else {
            cmp = String(valA).localeCompare(String(valB));
          }

          if (cmp !== 0) {
            return s.direction === 'asc' ? cmp : -cmp;
          }
        }
        return 0;
      });
    }

    this.filteredRows = rows;
    this.renderTableBody();
    this.renderPagination();
    this.notifyDataUpdated();
  }

  private renderTableBody() {
    const tbody = this.container.querySelector('.opentfc-table-wrapper tbody');
    if (!tbody) return;

    let displayRows = this.filteredRows;
    if (this.state.pageSize !== 'all') {
      const size = this.state.pageSize as number;
      const start = (this.state.currentPage - 1) * size;
      displayRows = displayRows.slice(start, start + size);
    }

    tbody.innerHTML = '';
    displayRows.forEach(r => {
      const tr = document.createElement('tr');
      r.cells.forEach(c => {
        const td = document.createElement('td');
        td.innerHTML = c.html || c.val || '';
        tr.appendChild(td);
      });
      tbody.appendChild(tr);
    });

    // Render totals if enabled
    if (this.config.totalsRow) {
      this.renderTotalsRow(tbody);
    }
  }

  private renderTotalsRow(tbody: Element) {
    const tr = document.createElement('tr');
    tr.className = 'opentfc-totals-row';
    this.tableData.columns.forEach((col, idx) => {
      const td = document.createElement('td');
      if (idx === 0) {
        td.textContent = 'Итого:';
      } else if (col.type === 'NUMBER') {
        let sum = 0;
        this.filteredRows.forEach(r => {
          const n = parseFloat(String(r.cells[idx]?.val).replace(/[^0-9.-]/g, '')) || 0;
          sum += n;
        });
        td.textContent = sum.toLocaleString();
      } else {
        td.textContent = '';
      }
      tr.appendChild(td);
    });
    tbody.appendChild(tr);
  }

  private renderPagination() {
    const bar = this.container.querySelector('.opentfc-pagination-bar');
    if (!bar) return;

    const total = this.filteredRows.length;
    const size = this.state.pageSize === 'all' ? total : (this.state.pageSize as number);
    const totalPages = Math.max(1, Math.ceil(total / (size || 1)));

    bar.innerHTML = `
      <div class="opentfc-pagination-info">
        Всего: <strong>${total}</strong> строк
      </div>
      <div class="opentfc-pagination-controls">
        <button class="opentfc-btn opentfc-pg-prev" ${this.state.currentPage <= 1 ? 'disabled' : ''}>&lt;</button>
        <span>Стр. ${this.state.currentPage} из ${totalPages}</span>
        <button class="opentfc-btn opentfc-pg-next" ${this.state.currentPage >= totalPages ? 'disabled' : ''}>&gt;</button>
      </div>
    `;

    bar.querySelector('.opentfc-pg-prev')?.addEventListener('click', () => {
      if (this.state.currentPage > 1) {
        this.state.currentPage--;
        this.renderTableBody();
        this.renderPagination();
      }
    });

    bar.querySelector('.opentfc-pg-next')?.addEventListener('click', () => {
      if (this.state.currentPage < totalPages) {
        this.state.currentPage++;
        this.renderTableBody();
        this.renderPagination();
      }
    });
  }

  private exportCsv() {
    const headers = this.tableData.columns.map(c => `"${c.name.replace(/"/g, '""')}"`).join(',');
    const rows = this.filteredRows.map(r =>
      r.cells.map(c => `"${String(c.val || '').replace(/"/g, '""')}"`).join(',')
    );
    const csvContent = '\uFEFF' + [headers, ...rows].join('\r\n');
    const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
    const link = document.createElement('a');
    link.href = URL.createObjectURL(blob);
    link.download = `table-export-${this.config.macroId}.csv`;
    link.click();
  }

  private notifyDataUpdated() {
    const event = new CustomEvent('opentfc:data-updated', {
      bubbles: true,
      detail: {
        macroId: this.config.macroId,
        columns: this.tableData.columns,
        rows: this.filteredRows
      }
    });
    this.container.dispatchEvent(event);
  }

  public getData(): TableData {
    return {
      id: this.config.macroId,
      columns: this.tableData.columns,
      rows: this.filteredRows,
      totalRows: this.filteredRows.length
    };
  }
}
