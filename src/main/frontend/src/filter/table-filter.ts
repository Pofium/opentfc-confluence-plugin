import { ColumnDef, ColumnType, FilterState, RowData, TableData } from '../core/table-model';

export class TableFilterComponent {
  private container: HTMLElement;
  private config: any;
  private tableData: TableData;
  private filteredRows: RowData[] = [];
  private state: FilterState;
  private activeProfileName: string = '';

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

    // Check if initial filter state passed in URL
    this.loadStateFromUrl();

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
        name: th.textContent?.trim() || `Колонка ${idx + 1}`,
        type: 'TEXT',
        visible: true
      });
    });

    const rows: RowData[] = [];
    const trEls = tableEl.querySelectorAll('tbody tr, tr');
    trEls.forEach((tr, rIdx) => {
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

    this.detectColumnTypes(columns, rows);

    return {
      id: this.config.macroId,
      columns,
      rows,
      totalRows: rows.length
    };
  }

  private detectColumnTypes(columns: ColumnDef[], rows: RowData[]) {
    const dateRegex = /^\d{4}-\d{2}-\d{2}$|^\d{2}\.\d{2}\.\d{4}$|^\d{2}\/\d{2}\/\d{4}$/;

    columns.forEach((col, cIdx) => {
      let numCount = 0;
      let dateCount = 0;
      let nonBlank = 0;
      const uniqueVals = new Set<string>();

      rows.forEach(r => {
        const val = r.cells[cIdx]?.val;
        if (val !== undefined && val !== '') {
          nonBlank++;
          uniqueVals.add(val);
          const cleaned = String(val).replace(/[,\s$€₽%]/g, '');
          if (!isNaN(Number(cleaned)) && cleaned.trim() !== '') {
            numCount++;
          } else if (dateRegex.test(String(val).trim())) {
            dateCount++;
          }
        }
      });

      if (nonBlank > 0) {
        if (numCount / nonBlank >= 0.8) {
          col.type = 'NUMBER';
        } else if (dateCount / nonBlank >= 0.8) {
          col.type = 'DATE';
        } else if (uniqueVals.size > 0 && uniqueVals.size <= 30 && nonBlank > 3) {
          col.type = 'SELECT';
          col.options = Array.from(uniqueVals).sort();
        } else {
          col.type = 'TEXT';
        }
      }
    });
  }

  private initUI() {
    const toolbar = this.container.querySelector('.opentfc-toolbar');
    if (!toolbar) return;

    toolbar.innerHTML = `
      <div class="opentfc-search-box">
        <input type="text" class="opentfc-input opentfc-search-input" placeholder="Поиск по таблице..." value="${this.escapeHtml(this.state.globalSearch)}" />
      </div>

      <div class="opentfc-filters-dropdowns" style="display:flex; flex-wrap:wrap; gap:6px; align-items:center;">
        <!-- Dynamic column filter buttons -->
      </div>

      <div class="opentfc-actions" style="margin-left: auto; display: flex; gap: 6px; align-items: center;">
        <button class="opentfc-btn opentfc-btn-columns" title="Настройка видимости колонок">Колонки</button>
        <button class="opentfc-btn opentfc-btn-profiles" title="Профили фильтров">Профили</button>
        <button class="opentfc-btn opentfc-btn-export-csv" title="Экспорт в CSV">CSV</button>
        <button class="opentfc-btn opentfc-btn-export-xlsx" title="Экспорт в Excel">XLSX</button>
        <button class="opentfc-btn opentfc-btn-reset" title="Сбросить все фильтры">Сброс</button>
      </div>
    `;

    // Render filter controls for columns
    this.renderColumnFilterControls();

    // Bind event handlers
    const searchInput = toolbar.querySelector('.opentfc-search-input') as HTMLInputElement;
    let debounceTimer: any;
    searchInput?.addEventListener('input', () => {
      clearTimeout(debounceTimer);
      debounceTimer = setTimeout(() => {
        this.state.globalSearch = searchInput.value.trim().toLowerCase();
        this.state.currentPage = 1;
        this.applyFilters();
      }, 200);
    });

    toolbar.querySelector('.opentfc-btn-columns')?.addEventListener('click', () => this.toggleColumnsModal());
    toolbar.querySelector('.opentfc-btn-profiles')?.addEventListener('click', () => this.toggleProfilesModal());
    toolbar.querySelector('.opentfc-btn-export-csv')?.addEventListener('click', () => this.exportCsv());
    toolbar.querySelector('.opentfc-btn-export-xlsx')?.addEventListener('click', () => this.exportXlsx());
    toolbar.querySelector('.opentfc-btn-reset')?.addEventListener('click', () => this.resetFilters());

    // Table header sort listeners
    this.bindHeaderSorts();
  }

  private renderColumnFilterControls() {
    const filterContainer = this.container.querySelector('.opentfc-filters-dropdowns');
    if (!filterContainer) return;
    filterContainer.innerHTML = '';

    this.tableData.columns.forEach((col, idx) => {
      if (this.state.hiddenColumns.includes(idx)) return;

      if (col.type === 'SELECT' && col.options && col.options.length > 0) {
        const select = document.createElement('select');
        select.className = 'opentfc-input opentfc-col-select';
        select.style.maxWidth = '160px';
        select.innerHTML = `<option value="">${this.escapeHtml(col.name)}: Все</option>` +
          col.options.map(opt => `<option value="${this.escapeHtml(opt)}">${this.escapeHtml(opt)}</option>`).join('');

        select.addEventListener('change', () => {
          if (select.value) {
            this.state.columnFilters[idx] = select.value;
          } else {
            delete this.state.columnFilters[idx];
          }
          this.state.currentPage = 1;
          this.applyFilters();
        });
        filterContainer.appendChild(select);
      } else if (col.type === 'NUMBER') {
        const btn = document.createElement('button');
        btn.className = 'opentfc-btn';
        btn.textContent = `${col.name} (диапазон)`;
        btn.addEventListener('click', () => {
          const current = (this.state.columnFilters[idx] as any) || {};
          const minVal = prompt(`Минимум для "${col.name}":`, current.min !== undefined ? current.min : '');
          const maxVal = prompt(`Максимум для "${col.name}":`, current.max !== undefined ? current.max : '');

          const numMin = minVal && minVal.trim() !== '' ? parseFloat(minVal) : undefined;
          const numMax = maxVal && maxVal.trim() !== '' ? parseFloat(maxVal) : undefined;

          if (numMin !== undefined || numMax !== undefined) {
            this.state.columnFilters[idx] = { min: numMin, max: numMax };
          } else {
            delete this.state.columnFilters[idx];
          }
          this.applyFilters();
        });
        filterContainer.appendChild(btn);
      } else if (col.type === 'DATE') {
        const btn = document.createElement('button');
        btn.className = 'opentfc-btn';
        btn.textContent = `${col.name} (дата)`;
        btn.addEventListener('click', () => {
          const current = (this.state.columnFilters[idx] as any) || {};
          const fromVal = prompt(`Дата от (ГГГГ-ММ-ДД) для "${col.name}":`, current.from || '');
          const toVal = prompt(`Дата до (ГГГГ-ММ-ДД) для "${col.name}":`, current.to || '');

          if (fromVal || toVal) {
            this.state.columnFilters[idx] = { from: fromVal || undefined, to: toVal || undefined };
          } else {
            delete this.state.columnFilters[idx];
          }
          this.applyFilters();
        });
        filterContainer.appendChild(btn);
      }
    });
  }

  private bindHeaderSorts() {
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
    this.updateSortHeaderIcons();
    this.applyFilters();
  }

  private updateSortHeaderIcons() {
    const thEls = this.container.querySelectorAll('.opentfc-table-wrapper th');
    thEls.forEach((th, cIdx) => {
      const sort = this.state.sorts.find(s => s.columnIndex === cIdx);
      let icon = th.querySelector('.opentfc-sort-icon');
      if (!icon) {
        icon = document.createElement('span');
        icon.className = 'opentfc-sort-icon';
        th.appendChild(icon);
      }
      if (sort) {
        icon.textContent = sort.direction === 'asc' ? ' ▲' : ' ▼';
      } else {
        icon.textContent = '';
      }
    });
  }

  public applyFilters() {
    let rows = [...this.tableData.rows];

    // 1. Global search
    if (this.state.globalSearch) {
      const q = this.state.globalSearch;
      rows = rows.filter(r => r.cells.some(c => c.val && String(c.val).toLowerCase().includes(q)));
    }

    // 2. Column filters
    for (const [colIdxStr, filterVal] of Object.entries(this.state.columnFilters)) {
      const colIdx = parseInt(colIdxStr, 10);
      const col = this.tableData.columns[colIdx];
      if (!col) continue;

      if (typeof filterVal === 'string') {
        // String or Select filter
        const match = filterVal.toLowerCase();
        rows = rows.filter(r => {
          const cellVal = String(r.cells[colIdx]?.val || '').toLowerCase();
          return cellVal === match || cellVal.includes(match);
        });
      } else if (typeof filterVal === 'object' && filterVal !== null) {
        const range = filterVal as any;
        if (range.min !== undefined || range.max !== undefined) {
          // Numeric range
          rows = rows.filter(r => {
            const raw = r.cells[colIdx]?.val;
            const n = parseFloat(String(raw || '').replace(/[^0-9.-]/g, ''));
            if (isNaN(n)) return false;
            if (range.min !== undefined && n < range.min) return false;
            if (range.max !== undefined && n > range.max) return false;
            return true;
          });
        } else if (range.from !== undefined || range.to !== undefined) {
          // Date range
          rows = rows.filter(r => {
            const raw = String(r.cells[colIdx]?.val || '').trim();
            const cellDate = this.parseDate(raw);
            if (!cellDate) return false;
            if (range.from) {
              const fromDate = this.parseDate(range.from);
              if (fromDate && cellDate < fromDate) return false;
            }
            if (range.to) {
              const toDate = this.parseDate(range.to);
              if (toDate && cellDate > toDate) return false;
            }
            return true;
          });
        }
      }
    }

    // 3. Multi-sorting
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
          } else if (colType === 'DATE') {
            const dA = this.parseDate(String(valA))?.getTime() || 0;
            const dB = this.parseDate(String(valB))?.getTime() || 0;
            cmp = dA - dB;
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
    this.syncStateToUrl();
  }

  private parseDate(val: string): Date | null {
    if (!val) return null;
    const parts = val.split(/[.-/]/);
    if (parts.length === 3) {
      if (parts[0].length === 4) {
        // yyyy-MM-dd
        return new Date(parseInt(parts[0], 10), parseInt(parts[1], 10) - 1, parseInt(parts[2], 10));
      } else {
        // dd.MM.yyyy
        return new Date(parseInt(parts[2], 10), parseInt(parts[1], 10) - 1, parseInt(parts[0], 10));
      }
    }
    const d = new Date(val);
    return isNaN(d.getTime()) ? null : d;
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
      r.cells.forEach((c, cIdx) => {
        if (this.state.hiddenColumns.includes(cIdx)) return;
        const td = document.createElement('td');
        td.innerHTML = c.html || this.escapeHtml(c.val) || '';
        tr.appendChild(td);
      });
      tbody.appendChild(tr);
    });

    if (this.config.totalsRow) {
      this.renderTotalsRow(tbody);
    }
  }

  private renderTotalsRow(tbody: Element) {
    const tr = document.createElement('tr');
    tr.className = 'opentfc-totals-row';

    let firstVisible = true;
    this.tableData.columns.forEach((col, idx) => {
      if (this.state.hiddenColumns.includes(idx)) return;

      const td = document.createElement('td');
      if (firstVisible) {
        td.innerHTML = `<strong>Итого (${this.filteredRows.length} строк):</strong>`;
        firstVisible = false;
      } else if (col.type === 'NUMBER') {
        let sum = 0;
        let count = 0;
        this.filteredRows.forEach(r => {
          const raw = r.cells[idx]?.val;
          const n = parseFloat(String(raw || '').replace(/[^0-9.-]/g, ''));
          if (!isNaN(n)) {
            sum += n;
            count++;
          }
        });
        const avg = count > 0 ? (sum / count).toFixed(2) : '0';
        td.innerHTML = `Σ: <strong>${sum.toLocaleString()}</strong><br><span style="font-size:11px;color:#6b778c;">Avg: ${avg}</span>`;
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
        Всего строк: <strong>${total}</strong> (из ${this.tableData.totalRows})
      </div>
      <div class="opentfc-pagination-controls">
        <select class="opentfc-input opentfc-pg-size" style="width: auto; padding: 2px 6px;">
          <option value="10" ${this.state.pageSize === 10 ? 'selected' : ''}>10</option>
          <option value="25" ${this.state.pageSize === 25 ? 'selected' : ''}>25</option>
          <option value="50" ${this.state.pageSize === 50 ? 'selected' : ''}>50</option>
          <option value="100" ${this.state.pageSize === 100 ? 'selected' : ''}>100</option>
          <option value="all" ${this.state.pageSize === 'all' ? 'selected' : ''}>Все</option>
        </select>
        <button class="opentfc-btn opentfc-pg-prev" ${this.state.currentPage <= 1 ? 'disabled' : ''}>&lt;</button>
        <span>Стр. ${this.state.currentPage} из ${totalPages}</span>
        <button class="opentfc-btn opentfc-pg-next" ${this.state.currentPage >= totalPages ? 'disabled' : ''}>&gt;</button>
      </div>
    `;

    bar.querySelector('.opentfc-pg-size')?.addEventListener('change', (e: any) => {
      const val = e.target.value;
      this.state.pageSize = val === 'all' ? 'all' : parseInt(val, 10);
      this.state.currentPage = 1;
      this.renderTableBody();
      this.renderPagination();
    });

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

  private toggleColumnsModal() {
    let modal = this.container.querySelector('.opentfc-columns-modal') as HTMLElement;
    if (modal) {
      modal.remove();
      return;
    }

    modal = document.createElement('div');
    modal.className = 'opentfc-columns-modal';
    modal.style.cssText = `
      position: absolute; right: 20px; top: 50px; z-index: 100;
      background: #ffffff; border: 1px solid #dfe1e6; border-radius: 4px;
      box-shadow: 0 4px 8px rgba(0,0,0,0.15); padding: 12px; min-width: 200px;
    `;

    modal.innerHTML = `
      <div style="font-weight:600; margin-bottom:8px;">Отображение колонок:</div>
      <div class="opentfc-col-list" style="display:flex; flex-direction:column; gap:4px; max-height:220px; overflow-y:auto;">
        ${this.tableData.columns.map((c, idx) => `
          <label style="display:flex; align-items:center; gap:6px; font-size:13px; cursor:pointer;">
            <input type="checkbox" data-col-idx="${idx}" ${!this.state.hiddenColumns.includes(idx) ? 'checked' : ''} />
            ${this.escapeHtml(c.name)}
          </label>
        `).join('')}
      </div>
      <div style="margin-top:10px; text-align:right;">
        <button class="opentfc-btn opentfc-btn-primary opentfc-btn-close-col-modal">Закрыть</button>
      </div>
    `;

    modal.querySelectorAll('input[type="checkbox"]').forEach(cb => {
      cb.addEventListener('change', (e: any) => {
        const cIdx = parseInt(e.target.getAttribute('data-col-idx'), 10);
        if (e.target.checked) {
          this.state.hiddenColumns = this.state.hiddenColumns.filter(i => i !== cIdx);
        } else {
          if (!this.state.hiddenColumns.includes(cIdx)) {
            this.state.hiddenColumns.push(cIdx);
          }
        }
        this.renderTableHeaderVisibility();
        this.renderTableBody();
        this.renderColumnFilterControls();
      });
    });

    modal.querySelector('.opentfc-btn-close-col-modal')?.addEventListener('click', () => modal.remove());
    this.container.style.position = 'relative';
    this.container.appendChild(modal);
  }

  private renderTableHeaderVisibility() {
    const thEls = this.container.querySelectorAll('.opentfc-table-wrapper th');
    thEls.forEach((th, cIdx) => {
      (th as HTMLElement).style.display = this.state.hiddenColumns.includes(cIdx) ? 'none' : '';
    });
  }

  private toggleProfilesModal() {
    const pageId = (window as any).AJS?.params?.pageId || 0;
    const macroId = this.config.macroId;

    const profileName = prompt('Введите имя профиля для сохранения текущих фильтров:', this.activeProfileName || 'Мой профиль');
    if (!profileName) return;

    const payload = {
      pageId,
      macroId,
      name: profileName,
      state: JSON.stringify(this.state)
    };

    fetch(`/rest/opentfc/1.0/profile/${pageId}/${macroId}`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-Atlassian-Token': 'no-check'
      },
      body: JSON.stringify(payload)
    }).then(res => {
      if (res.ok) {
        this.activeProfileName = profileName;
        alert(`Профиль "${profileName}" успешно сохранен!`);
      } else {
        alert('Ошибка при сохранении профиля.');
      }
    }).catch(() => {
      alert('Сетевая ошибка при сохранении профиля.');
    });
  }

  private resetFilters() {
    this.state.globalSearch = '';
    this.state.columnFilters = {};
    this.state.sorts = [];
    this.state.currentPage = 1;

    const searchInput = this.container.querySelector('.opentfc-search-input') as HTMLInputElement;
    if (searchInput) searchInput.value = '';

    this.container.querySelectorAll('.opentfc-col-select').forEach((sel: any) => {
      sel.value = '';
    });

    this.updateSortHeaderIcons();
    this.applyFilters();
  }

  private syncStateToUrl() {
    try {
      const url = new URL(window.location.href);
      const key = `tf_${this.config.macroId}`;
      if (this.state.globalSearch || Object.keys(this.state.columnFilters).length > 0 || this.state.sorts.length > 0) {
        url.searchParams.set(key, btoa(JSON.stringify({
          q: this.state.globalSearch,
          f: this.state.columnFilters,
          s: this.state.sorts
        })));
      } else {
        url.searchParams.delete(key);
      }
      window.history.replaceState({}, '', url.toString());
    } catch {}
  }

  private loadStateFromUrl() {
    try {
      const url = new URL(window.location.href);
      const key = `tf_${this.config.macroId}`;
      const param = url.searchParams.get(key);
      if (param) {
        const decoded = JSON.parse(atob(param));
        if (decoded.q) this.state.globalSearch = decoded.q;
        if (decoded.f) this.state.columnFilters = decoded.f;
        if (decoded.s) this.state.sorts = decoded.s;
      }
    } catch {}
  }

  private exportCsv() {
    const visibleCols = this.tableData.columns.filter((c, idx) => !this.state.hiddenColumns.includes(idx));
    const headers = visibleCols.map(c => `"${c.name.replace(/"/g, '""')}"`).join(',');
    const rows = this.filteredRows.map(r =>
      visibleCols.map(c => `"${String(r.cells[c.index]?.val || '').replace(/"/g, '""')}"`).join(',')
    );
    const csvContent = '\uFEFF' + [headers, ...rows].join('\r\n');
    const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
    const link = document.createElement('a');
    link.href = URL.createObjectURL(blob);
    link.download = `table-${this.config.macroId || 'export'}.csv`;
    link.click();
  }

  private exportXlsx() {
    // Send filtered model to backend XLSX export endpoint
    const visibleCols = this.tableData.columns.filter((c, idx) => !this.state.hiddenColumns.includes(idx));
    const payload = {
      id: this.config.macroId,
      columns: visibleCols,
      rows: this.filteredRows.map(r => ({
        cells: visibleCols.map(c => r.cells[c.index])
      })),
      totalRows: this.filteredRows.length
    };

    fetch('/rest/opentfc/1.0/export/xlsx', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-Atlassian-Token': 'no-check'
      },
      body: JSON.stringify(payload)
    })
      .then(res => res.blob())
      .then(blob => {
        const link = document.createElement('a');
        link.href = URL.createObjectURL(blob);
        link.download = `table-${this.config.macroId || 'export'}.xlsx`;
        link.click();
      })
      .catch(() => alert('Ошибка при экспорте в Excel.'));
  }

  private notifyDataUpdated() {
    const event = new CustomEvent('opentfc:data-updated', {
      bubbles: true,
      detail: {
        macroId: this.config.macroId,
        columns: this.tableData.columns.filter((c, idx) => !this.state.hiddenColumns.includes(idx)),
        rows: this.filteredRows
      }
    });
    this.container.dispatchEvent(event);
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
