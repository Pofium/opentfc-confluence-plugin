import { ColumnDef, ColumnType, FilterState, RowData, TableData } from '../core/table-model';

interface ExtendedColumnDef extends ColumnDef {
  currencySuffix?: string;
}

interface SavedProfile {
  id: number;
  pageId: number;
  macroId: string;
  name: string;
  stateJson: string;
}

export class TableFilterComponent {
  private container: HTMLElement;
  private config: any;
  private tableData: {
    id: string;
    columns: ExtendedColumnDef[];
    rows: RowData[];
    totalRows: number;
  };
  private filteredRows: RowData[] = [];
  private state: FilterState;
  private activeProfileName: string = '';
  private savedProfiles: SavedProfile[] = [];

  constructor(container: HTMLElement) {
    this.container = container;
    const configRaw = container.getAttribute('data-opentfc-config') || '{}';
    try {
      this.config = JSON.parse(configRaw);
    } catch {
      this.config = {};
    }

    // Default configuration normalization
    if (this.config.showSearchBox === undefined) this.config.showSearchBox = true;
    if (this.config.showColumnFilters === undefined) {
      this.config.showColumnFilters = this.config.filterStatus !== undefined ? this.config.filterStatus : true;
    }
    if (this.config.stickyHeader === undefined) {
      this.config.stickyHeader = this.config.freezeHeader !== undefined ? this.config.freezeHeader : true;
    }
    if (this.config.showTotals === undefined) {
      this.config.showTotals = this.config.totalsRow !== undefined ? this.config.totalsRow : false;
    }
    if (this.config.allowHideColumns === undefined) this.config.allowHideColumns = true;
    if (this.config.showExportButtons === undefined) this.config.showExportButtons = true;
    if (this.config.enablePagination === undefined) this.config.enablePagination = true;

    const pageSizeConfig = this.config.pageSize || '25';
    const initPageSize = pageSizeConfig === 'all' ? 'all' : parseInt(pageSizeConfig, 10);

    this.state = {
      globalSearch: '',
      columnFilters: {},
      sorts: [],
      hiddenColumns: [],
      currentPage: 1,
      pageSize: initPageSize
    };

    this.tableData = this.parseDOMTable();

    // Check if initial filter state passed in URL
    this.loadStateFromUrl();

    this.initUI();
    this.loadProfiles();
    this.applyFilters();
  }

  private parseDOMTable() {
    const tableEl = this.container.querySelector('table');
    if (!tableEl) {
      return { id: this.config.macroId, columns: [], rows: [], totalRows: 0 };
    }

    const columns: ExtendedColumnDef[] = [];
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

  private detectColumnTypes(columns: ExtendedColumnDef[], rows: RowData[]) {
    const dateRegex = /^\d{4}-\d{2}-\d{2}$|^\d{2}\.\d{2}\.\d{4}$|^\d{2}\/\d{2}\/\d{4}$/;

    columns.forEach((col, cIdx) => {
      let numCount = 0;
      let dateCount = 0;
      let nonBlank = 0;
      const uniqueVals = new Set<string>();
      const currencySuffixes: Record<string, number> = {};

      rows.forEach(r => {
        const val = r.cells[cIdx]?.val;
        if (val !== undefined && val !== '') {
          nonBlank++;
          uniqueVals.add(val);

          // Detect currency symbol or suffix (e.g. ₽, $, €, руб, etc.)
          const strVal = String(val).trim();
          const currMatch = strVal.match(/([₽$€¥]|руб\.?|р\.?)$/i);
          if (currMatch) {
            const sym = ' ' + currMatch[1];
            currencySuffixes[sym] = (currencySuffixes[sym] || 0) + 1;
          }

          const cleaned = strVal.replace(/[,\s$€₽¥руб\.р]/gi, '');
          if (!isNaN(Number(cleaned)) && cleaned.trim() !== '') {
            numCount++;
          } else if (dateRegex.test(strVal)) {
            dateCount++;
          }
        }
      });

      if (nonBlank > 0) {
        if (numCount / nonBlank >= 0.7) {
          col.type = 'NUMBER';
          // Find most popular currency suffix if any
          let maxSuffix = '';
          let maxCount = 0;
          for (const [sfx, count] of Object.entries(currencySuffixes)) {
            if (count > maxCount) {
              maxCount = count;
              maxSuffix = sfx;
            }
          }
          if (maxCount > 0) col.currencySuffix = maxSuffix;
        } else if (dateCount / nonBlank >= 0.7) {
          col.type = 'DATE';
        } else if (uniqueVals.size > 0 && uniqueVals.size <= 50) {
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
      <div class="opentfc-toolbar-primary">
        ${this.config.showSearchBox ? `
          <div class="opentfc-search-box">
            <input type="text" class="opentfc-input opentfc-search-input" placeholder="Поиск по всем колонкам..." value="${this.escapeHtml(this.state.globalSearch)}" />
          </div>
        ` : ''}

        ${this.config.allowHideColumns ? `
          <div class="opentfc-dropdown opentfc-dropdown-cols">
            <button class="opentfc-btn opentfc-btn-cols">Колонки ▾</button>
            <div class="opentfc-dropdown-menu opentfc-cols-menu">
              <div class="opentfc-dropdown-header">Отображение колонок</div>
              <div class="opentfc-dropdown-divider"></div>
              <div class="opentfc-cols-list" style="max-height: 220px; overflow-y: auto; padding: 4px 8px; display: flex; flex-direction: column; gap: 4px;">
                ${this.tableData.columns.map((c, idx) => `
                  <label style="display: flex; align-items: center; gap: 6px; font-size: 13px; cursor: pointer;">
                    <input type="checkbox" class="opentfc-col-cb" data-col-idx="${idx}" ${!this.state.hiddenColumns.includes(idx) ? 'checked' : ''} />
                    <span>${this.escapeHtml(c.name)}</span>
                  </label>
                `).join('')}
              </div>
              <div class="opentfc-dropdown-divider"></div>
              <div style="padding: 4px 8px; display: flex; justify-content: space-between; font-size: 11px;">
                <a href="javascript:void(0)" class="opentfc-cols-show-all" style="color: #0052cc;">Все</a>
                <a href="javascript:void(0)" class="opentfc-cols-reset" style="color: #6b778c;">Сбросить</a>
              </div>
            </div>
          </div>
        ` : ''}

        ${this.config.showExportButtons ? `
          <div class="opentfc-dropdown opentfc-dropdown-export">
            <button class="opentfc-btn opentfc-btn-export">Экспорт ▾</button>
            <div class="opentfc-dropdown-menu opentfc-export-menu">
              <div class="opentfc-dropdown-item opentfc-export-xlsx">Excel (XLSX)</div>
              <div class="opentfc-dropdown-item opentfc-export-csv">CSV</div>
            </div>
          </div>
        ` : ''}

        <div class="opentfc-dropdown opentfc-dropdown-views">
          <button class="opentfc-btn opentfc-btn-views">Виды ▾</button>
          <div class="opentfc-dropdown-menu opentfc-views-menu">
            <div class="opentfc-dropdown-header">Сохраненные виды</div>
            <div class="opentfc-views-list">
              <div style="padding: 6px 12px; color: #6b778c; font-size: 12px;">Нет сохраненных видов</div>
            </div>
            <div class="opentfc-dropdown-divider"></div>
            <div class="opentfc-dropdown-item opentfc-view-save" style="color: #0052cc; font-weight: 500;">+ Сохранить текущий вид...</div>
          </div>
        </div>

        <button class="opentfc-btn opentfc-btn-reset" title="Сбросить все фильтры" style="margin-left: auto;">Сбросить фильтры</button>
      </div>

      ${this.config.showColumnFilters ? `
        <div class="opentfc-toolbar-secondary"></div>
      ` : ''}
    `;

    // Global search input listener
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

    // Reset button
    toolbar.querySelector('.opentfc-btn-reset')?.addEventListener('click', () => this.resetFilters());

    // Dropdown toggle logic
    this.setupDropdown(toolbar.querySelector('.opentfc-dropdown-cols'), toolbar.querySelector('.opentfc-btn-cols'), toolbar.querySelector('.opentfc-cols-menu'));
    this.setupDropdown(toolbar.querySelector('.opentfc-dropdown-export'), toolbar.querySelector('.opentfc-btn-export'), toolbar.querySelector('.opentfc-export-menu'));
    this.setupDropdown(toolbar.querySelector('.opentfc-dropdown-views'), toolbar.querySelector('.opentfc-btn-views'), toolbar.querySelector('.opentfc-views-menu'));

    // Columns visibility listeners
    toolbar.querySelectorAll('.opentfc-col-cb').forEach(cb => {
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
        this.applyFrozenColumns();
      });
    });

    toolbar.querySelector('.opentfc-cols-show-all')?.addEventListener('click', () => {
      this.state.hiddenColumns = [];
      toolbar.querySelectorAll('.opentfc-col-cb').forEach((cb: any) => cb.checked = true);
      this.renderTableHeaderVisibility();
      this.renderTableBody();
      this.renderColumnFilterControls();
      this.applyFrozenColumns();
    });

    toolbar.querySelector('.opentfc-cols-reset')?.addEventListener('click', () => {
      this.state.hiddenColumns = [];
      toolbar.querySelectorAll('.opentfc-col-cb').forEach((cb: any) => cb.checked = true);
      this.renderTableHeaderVisibility();
      this.renderTableBody();
      this.renderColumnFilterControls();
      this.applyFrozenColumns();
    });

    // Export listeners
    toolbar.querySelector('.opentfc-export-csv')?.addEventListener('click', () => this.exportCsv());
    toolbar.querySelector('.opentfc-export-xlsx')?.addEventListener('click', () => this.exportXlsx());

    // Save view listener
    toolbar.querySelector('.opentfc-view-save')?.addEventListener('click', () => this.promptSaveProfile());

    // Secondary Filter Row
    if (this.config.showColumnFilters) {
      this.renderColumnFilterControls();
    }

    // Sticky Header
    if (this.config.stickyHeader) {
      const tableWrapper = this.container.querySelector('.opentfc-table-wrapper');
      tableWrapper?.classList.add('opentfc-sticky-header');
    }

    // Freeze Columns
    this.applyFrozenColumns();

    // Table header sort listeners
    this.bindHeaderSorts();

    // Close open dropdowns on click outside
    document.addEventListener('click', (e: MouseEvent) => {
      const target = e.target as HTMLElement;
      if (!this.container.contains(target)) {
        this.closeAllDropdowns();
      }
    });
  }

  private setupDropdown(container: Element | null, trigger: Element | null, menu: Element | null) {
    if (!container || !trigger || !menu) return;
    trigger.addEventListener('click', (e) => {
      e.stopPropagation();
      const isShown = menu.classList.contains('show');
      this.closeAllDropdowns();
      if (!isShown) {
        menu.classList.add('show');
      }
    });
  }

  private closeAllDropdowns() {
    this.container.querySelectorAll('.opentfc-dropdown-menu.show, .opentfc-multiselect-panel.show').forEach(el => {
      el.classList.remove('show');
    });
  }

  private renderColumnFilterControls() {
    const filterContainer = this.container.querySelector('.opentfc-toolbar-secondary');
    if (!filterContainer) return;
    filterContainer.innerHTML = '';

    this.tableData.columns.forEach((col, idx) => {
      if (this.state.hiddenColumns.includes(idx)) return;

      if (col.type === 'SELECT' && col.options && col.options.length > 0) {
        const group = document.createElement('div');
        group.className = 'opentfc-filter-group opentfc-filter-select';

        const label = document.createElement('span');
        label.className = 'opentfc-filter-label';
        label.textContent = `${col.name}:`;
        group.appendChild(label);

        const currentFilter = this.state.columnFilters[idx];
        const selectedList: string[] = Array.isArray(currentFilter)
          ? currentFilter
          : typeof currentFilter === 'string' && currentFilter ? [currentFilter] : [];

        let summaryText = 'Все';
        if (selectedList.length === 1) {
          summaryText = selectedList[0];
        } else if (selectedList.length > 1) {
          summaryText = selectedList.length <= 2 ? selectedList.join(', ') : `${selectedList.length} выбрано`;
        }

        const trigger = document.createElement('button');
        trigger.className = 'opentfc-select-trigger';
        trigger.innerHTML = `[ ${this.escapeHtml(summaryText)} ▾ ]`;

        const panel = document.createElement('div');
        panel.className = 'opentfc-multiselect-panel';
        panel.innerHTML = `
          <div class="opentfc-multiselect-actions">
            <span class="opentfc-multiselect-link opentfc-select-all">Выбрать все</span>
            <span class="opentfc-multiselect-link opentfc-clear-all" style="color:#6b778c;">Очистить</span>
          </div>
          <div class="opentfc-multiselect-options">
            ${col.options.map(opt => `
              <label class="opentfc-multiselect-option">
                <input type="checkbox" value="${this.escapeHtml(opt)}" ${selectedList.includes(opt) ? 'checked' : ''} />
                <span>${this.escapeHtml(opt)}</span>
              </label>
            `).join('')}
          </div>
        `;

        trigger.addEventListener('click', (e) => {
          e.stopPropagation();
          const isShown = panel.classList.contains('show');
          this.closeAllDropdowns();
          if (!isShown) panel.classList.add('show');
        });

        panel.addEventListener('click', (e) => e.stopPropagation());

        const updateSelection = () => {
          const checked = Array.from(panel.querySelectorAll('input[type="checkbox"]:checked')).map((cb: any) => cb.value);
          if (checked.length === 0 || checked.length === col.options?.length) {
            delete this.state.columnFilters[idx];
            trigger.innerHTML = `[ Все ▾ ]`;
          } else {
            this.state.columnFilters[idx] = checked;
            trigger.innerHTML = `[ ${this.escapeHtml(checked.length <= 2 ? checked.join(', ') : `${checked.length} выбрано`)} ▾ ]`;
          }
          this.state.currentPage = 1;
          this.applyFilters();
        };

        panel.querySelectorAll('input[type="checkbox"]').forEach(cb => {
          cb.addEventListener('change', updateSelection);
        });

        panel.querySelector('.opentfc-select-all')?.addEventListener('click', () => {
          panel.querySelectorAll('input[type="checkbox"]').forEach((cb: any) => cb.checked = true);
          delete this.state.columnFilters[idx];
          trigger.innerHTML = `[ Все ▾ ]`;
          this.state.currentPage = 1;
          this.applyFilters();
        });

        panel.querySelector('.opentfc-clear-all')?.addEventListener('click', () => {
          panel.querySelectorAll('input[type="checkbox"]').forEach((cb: any) => cb.checked = false);
          this.state.columnFilters[idx] = [];
          trigger.innerHTML = `[ Пусто ▾ ]`;
          this.state.currentPage = 1;
          this.applyFilters();
        });

        group.appendChild(trigger);
        group.appendChild(panel);
        filterContainer.appendChild(group);

      } else if (col.type === 'NUMBER') {
        const group = document.createElement('div');
        group.className = 'opentfc-filter-group opentfc-filter-number';

        const label = document.createElement('span');
        label.className = 'opentfc-filter-label';
        label.textContent = `${col.name}:`;
        group.appendChild(label);

        const current = (this.state.columnFilters[idx] as any) || {};
        const minVal = current.min !== undefined ? current.min : '';
        const maxVal = current.max !== undefined ? current.max : '';

        const minInput = document.createElement('input');
        minInput.type = 'number';
        minInput.className = 'opentfc-input opentfc-input-num opentfc-num-min';
        minInput.placeholder = 'От';
        minInput.value = minVal;

        const sep = document.createElement('span');
        sep.className = 'opentfc-range-sep';
        sep.textContent = '—';

        const maxInput = document.createElement('input');
        maxInput.type = 'number';
        maxInput.className = 'opentfc-input opentfc-input-num opentfc-num-max';
        maxInput.placeholder = 'До';
        maxInput.value = maxVal;

        const updateNumberFilter = () => {
          const min = minInput.value.trim() !== '' ? parseFloat(minInput.value) : undefined;
          const max = maxInput.value.trim() !== '' ? parseFloat(maxInput.value) : undefined;

          if (min !== undefined || max !== undefined) {
            this.state.columnFilters[idx] = { min, max };
          } else {
            delete this.state.columnFilters[idx];
          }
          this.state.currentPage = 1;
          this.applyFilters();
        };

        minInput.addEventListener('input', updateNumberFilter);
        maxInput.addEventListener('input', updateNumberFilter);

        group.appendChild(minInput);
        group.appendChild(sep);
        group.appendChild(maxInput);
        filterContainer.appendChild(group);

      } else if (col.type === 'DATE') {
        const group = document.createElement('div');
        group.className = 'opentfc-filter-group opentfc-filter-date';

        const label = document.createElement('span');
        label.className = 'opentfc-filter-label';
        label.textContent = `${col.name}:`;
        group.appendChild(label);

        const current = (this.state.columnFilters[idx] as any) || {};
        const fromVal = current.from || '';
        const toVal = current.to || '';

        const fromInput = document.createElement('input');
        fromInput.type = 'date';
        fromInput.className = 'opentfc-input opentfc-input-date opentfc-date-from';
        fromInput.value = fromVal;
        fromInput.title = 'Начальная дата';

        const sep = document.createElement('span');
        sep.className = 'opentfc-range-sep';
        sep.textContent = '—';

        const toInput = document.createElement('input');
        toInput.type = 'date';
        toInput.className = 'opentfc-input opentfc-input-date opentfc-date-to';
        toInput.value = toVal;
        toInput.title = 'Конечная дата';

        const updateDateFilter = () => {
          const from = fromInput.value.trim() || undefined;
          const to = toInput.value.trim() || undefined;

          if (from || to) {
            this.state.columnFilters[idx] = { from, to };
          } else {
            delete this.state.columnFilters[idx];
          }
          this.state.currentPage = 1;
          this.applyFilters();
        };

        fromInput.addEventListener('change', updateDateFilter);
        toInput.addEventListener('change', updateDateFilter);

        group.appendChild(fromInput);
        group.appendChild(sep);
        group.appendChild(toInput);
        filterContainer.appendChild(group);

      } else {
        // Text Column
        const group = document.createElement('div');
        group.className = 'opentfc-filter-group opentfc-filter-text';

        const label = document.createElement('span');
        label.className = 'opentfc-filter-label';
        label.textContent = `${col.name}:`;
        group.appendChild(label);

        const txtInput = document.createElement('input');
        txtInput.type = 'text';
        txtInput.className = 'opentfc-input opentfc-input-txt';
        txtInput.placeholder = 'Поиск...';
        txtInput.value = typeof this.state.columnFilters[idx] === 'string' ? (this.state.columnFilters[idx] as string) : '';

        let txtDebounce: any;
        txtInput.addEventListener('input', () => {
          clearTimeout(txtDebounce);
          txtDebounce = setTimeout(() => {
            const val = txtInput.value.trim();
            if (val) {
              this.state.columnFilters[idx] = val;
            } else {
              delete this.state.columnFilters[idx];
            }
            this.state.currentPage = 1;
            this.applyFilters();
          }, 200);
        });

        group.appendChild(txtInput);
        filterContainer.appendChild(group);
      }
    });
  }

  private applyFrozenColumns() {
    const freezeCount = Math.max(0, parseInt(this.config.freezeColumns || '0', 10));
    const tableWrapper = this.container.querySelector('.opentfc-table-wrapper');
    if (!tableWrapper) return;

    const thEls = tableWrapper.querySelectorAll('th');
    const trEls = tableWrapper.querySelectorAll('tbody tr');

    // Reset previous frozen classes and styles
    thEls.forEach(th => {
      th.classList.remove('opentfc-frozen-cell', 'opentfc-frozen-last');
      th.style.left = '';
    });
    trEls.forEach(tr => {
      tr.querySelectorAll('td').forEach(td => {
        td.classList.remove('opentfc-frozen-cell', 'opentfc-frozen-last');
        td.style.left = '';
      });
    });

    if (freezeCount <= 0) return;

    // Calculate left positions
    let currentLeft = 0;
    let visibleFrozenCount = 0;

    this.tableData.columns.forEach((col, idx) => {
      if (this.state.hiddenColumns.includes(idx)) return;
      if (visibleFrozenCount >= freezeCount) return;

      const th = thEls[visibleFrozenCount] as HTMLElement;
      if (th) {
        const width = th.offsetWidth || 100;
        th.classList.add('opentfc-frozen-cell');
        th.style.left = `${currentLeft}px`;

        trEls.forEach(tr => {
          const td = tr.children[visibleFrozenCount] as HTMLElement;
          if (td) {
            td.classList.add('opentfc-frozen-cell');
            td.style.left = `${currentLeft}px`;
          }
        });

        if (visibleFrozenCount === freezeCount - 1) {
          th.classList.add('opentfc-frozen-last');
          trEls.forEach(tr => {
            const td = tr.children[visibleFrozenCount] as HTMLElement;
            if (td) td.classList.add('opentfc-frozen-last');
          });
        }

        currentLeft += width;
        visibleFrozenCount++;
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
    const existingIdx = this.state.sorts.findIndex(s => s.columnIndex === columnIndex);
    if (existingIdx === -1) {
      if (!isMulti) this.state.sorts = [];
      this.state.sorts.push({ columnIndex, direction: 'asc' });
    } else {
      const existing = this.state.sorts[existingIdx];
      if (existing.direction === 'asc') {
        existing.direction = 'desc';
      } else {
        this.state.sorts.splice(existingIdx, 1);
      }
    }
    this.updateSortHeaderIcons();
    this.applyFilters();
  }

  private updateSortHeaderIcons() {
    const thEls = this.container.querySelectorAll('.opentfc-table-wrapper th');
    thEls.forEach((th, cIdx) => {
      const sortIdx = this.state.sorts.findIndex(s => s.columnIndex === cIdx);
      let badge = th.querySelector('.opentfc-sort-badge');
      if (!badge) {
        badge = document.createElement('span');
        badge.className = 'opentfc-sort-badge';
        th.appendChild(badge);
      }

      if (sortIdx !== -1) {
        const sort = this.state.sorts[sortIdx];
        const arrow = sort.direction === 'asc' ? '▲' : '▼';
        const priorityHtml = this.state.sorts.length > 1
          ? `<span class="opentfc-sort-priority">${sortIdx + 1}</span>`
          : '';
        badge.innerHTML = `${arrow}${priorityHtml}`;
      } else {
        badge.innerHTML = '';
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

      if (Array.isArray(filterVal)) {
        // Multi-select checkboxes
        if (filterVal.length === 0) {
          // Explicitly matched nothing
          rows = [];
        } else {
          rows = rows.filter(r => {
            const cellVal = String(r.cells[colIdx]?.val || '').trim();
            return filterVal.includes(cellVal);
          });
        }
      } else if (typeof filterVal === 'string') {
        // Text search
        const match = filterVal.toLowerCase();
        rows = rows.filter(r => {
          const cellVal = String(r.cells[colIdx]?.val || '').toLowerCase();
          return cellVal.includes(match);
        });
      } else if (typeof filterVal === 'object' && filterVal !== null) {
        const range = filterVal as any;
        if (range.min !== undefined || range.max !== undefined) {
          // Numeric range
          rows = rows.filter(r => {
            const raw = r.cells[colIdx]?.val;
            const n = parseFloat(String(raw || '').replace(/[^\d.-]/g, ''));
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
            const nA = parseFloat(String(valA).replace(/[^\d.-]/g, '')) || 0;
            const nB = parseFloat(String(valB).replace(/[^\d.-]/g, '')) || 0;
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
    this.applyFrozenColumns();
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
    if (this.config.enablePagination && this.state.pageSize !== 'all') {
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

    if (this.config.showTotals) {
      this.renderTotalsRow(tbody);
    }
  }

  private renderTotalsRow(tbody: Element) {
    const tr = document.createElement('tr');
    tr.className = 'opentfc-totals-row';

    let firstVisible = true;
    const rowCount = this.filteredRows.length;

    // Russian pluralization helper
    const rowWord = this.pluralizeRows(rowCount);

    this.tableData.columns.forEach((col, idx) => {
      if (this.state.hiddenColumns.includes(idx)) return;

      const td = document.createElement('td');
      if (firstVisible) {
        td.innerHTML = `Итого: <strong>${rowCount} ${rowWord}</strong>`;
        firstVisible = false;
      } else if (col.type === 'NUMBER') {
        let sum = 0;
        let count = 0;
        this.filteredRows.forEach(r => {
          const raw = r.cells[idx]?.val;
          const n = parseFloat(String(raw || '').replace(/[^\d.-]/g, ''));
          if (!isNaN(n)) {
            sum += n;
            count++;
          }
        });

        const suffix = col.currencySuffix || '';
        const formattedSum = sum.toLocaleString('ru-RU') + suffix;
        const avg = count > 0 ? (sum / count).toLocaleString('ru-RU', { maximumFractionDigits: 1 }) + suffix : '0';

        td.innerHTML = `<span class="opentfc-totals-val">${formattedSum}</span><br><span class="opentfc-totals-sub">Ср: ${avg}</span>`;
      } else {
        td.textContent = '';
      }
      tr.appendChild(td);
    });
    tbody.appendChild(tr);
  }

  private pluralizeRows(n: number): string {
    const absN = Math.abs(n) % 100;
    const n1 = absN % 10;
    if (absN > 10 && absN < 20) return 'строк';
    if (n1 > 1 && n1 < 5) return 'строки';
    if (n1 === 1) return 'строка';
    return 'строк';
  }

  private renderPagination() {
    const bar = this.container.querySelector('.opentfc-pagination-bar');
    if (!bar) return;

    if (!this.config.enablePagination) {
      bar.innerHTML = '';
      return;
    }

    const total = this.filteredRows.length;
    const size = this.state.pageSize === 'all' ? total : (this.state.pageSize as number);
    const totalPages = Math.max(1, Math.ceil(total / (size || 1)));

    bar.innerHTML = `
      <div class="opentfc-pagination-info">
        Строк: <strong>${total}</strong> (из ${this.tableData.totalRows})
      </div>
      <div class="opentfc-pagination-controls">
        <label style="display:flex; align-items:center; gap:4px; font-size:12px;">
          На стр.:
          <select class="opentfc-input opentfc-pg-size" style="width: auto; padding: 2px 6px;">
            <option value="10" ${this.state.pageSize === 10 ? 'selected' : ''}>10</option>
            <option value="25" ${this.state.pageSize === 25 ? 'selected' : ''}>25</option>
            <option value="50" ${this.state.pageSize === 50 ? 'selected' : ''}>50</option>
            <option value="100" ${this.state.pageSize === 100 ? 'selected' : ''}>100</option>
            <option value="all" ${this.state.pageSize === 'all' ? 'selected' : ''}>Все</option>
          </select>
        </label>
        <button class="opentfc-btn opentfc-btn-sm opentfc-pg-prev" ${this.state.currentPage <= 1 ? 'disabled' : ''}>&lt;</button>
        <span style="font-size:12px;">${this.state.currentPage} / ${totalPages}</span>
        <button class="opentfc-btn opentfc-btn-sm opentfc-pg-next" ${this.state.currentPage >= totalPages ? 'disabled' : ''}>&gt;</button>
      </div>
    `;

    bar.querySelector('.opentfc-pg-size')?.addEventListener('change', (e: any) => {
      const val = e.target.value;
      this.state.pageSize = val === 'all' ? 'all' : parseInt(val, 10);
      this.state.currentPage = 1;
      this.renderTableBody();
      this.renderPagination();
      this.applyFrozenColumns();
    });

    bar.querySelector('.opentfc-pg-prev')?.addEventListener('click', () => {
      if (this.state.currentPage > 1) {
        this.state.currentPage--;
        this.renderTableBody();
        this.renderPagination();
        this.applyFrozenColumns();
      }
    });

    bar.querySelector('.opentfc-pg-next')?.addEventListener('click', () => {
      if (this.state.currentPage < totalPages) {
        this.state.currentPage++;
        this.renderTableBody();
        this.renderPagination();
        this.applyFrozenColumns();
      }
    });
  }

  private renderTableHeaderVisibility() {
    const thEls = this.container.querySelectorAll('.opentfc-table-wrapper th');
    thEls.forEach((th, cIdx) => {
      (th as HTMLElement).style.display = this.state.hiddenColumns.includes(cIdx) ? 'none' : '';
    });
  }

  private loadProfiles() {
    const pageId = (window as any).AJS?.params?.pageId || this.config.pageId || 0;
    const macroId = this.config.macroId;
    if (!macroId) return;

    fetch(`/rest/opentfc/1.0/profile/${pageId}/${macroId}`, {
      headers: { 'Accept': 'application/json' }
    })
      .then(res => res.json())
      .then((data: SavedProfile[]) => {
        this.savedProfiles = Array.isArray(data) ? data : [];
        this.renderProfilesMenu();
      })
      .catch(() => {
        // Silent fail or anonymous user
      });
  }

  private renderProfilesMenu() {
    const listEl = this.container.querySelector('.opentfc-views-list');
    if (!listEl) return;

    if (this.savedProfiles.length === 0) {
      listEl.innerHTML = `<div style="padding: 6px 12px; color: #6b778c; font-size: 12px;">Нет сохраненных видов</div>`;
      return;
    }

    listEl.innerHTML = this.savedProfiles.map(p => `
      <div class="opentfc-dropdown-item opentfc-view-item" data-profile-id="${p.id}">
        <span>${this.activeProfileName === p.name ? '✓ ' : ''}${this.escapeHtml(p.name)}</span>
        <span class="opentfc-view-delete" data-profile-id="${p.id}" title="Удалить вид" style="color: #6b778c; padding: 0 4px; font-weight: bold;">✕</span>
      </div>
    `).join('');

    listEl.querySelectorAll('.opentfc-view-item').forEach(item => {
      item.addEventListener('click', (e: Event) => {
        const target = e.target as HTMLElement;
        if (target.classList.contains('opentfc-view-delete')) {
          e.stopPropagation();
          const pId = parseInt(target.getAttribute('data-profile-id') || '0', 10);
          this.deleteProfile(pId);
          return;
        }

        const pId = parseInt(item.getAttribute('data-profile-id') || '0', 10);
        const profile = this.savedProfiles.find(p => p.id === pId);
        if (profile) {
          try {
            const savedState = JSON.parse(profile.stateJson);
            this.state.globalSearch = savedState.globalSearch || '';
            this.state.columnFilters = savedState.columnFilters || {};
            this.state.sorts = savedState.sorts || [];
            this.state.hiddenColumns = savedState.hiddenColumns || [];
            this.state.currentPage = 1;
            this.activeProfileName = profile.name;

            const searchInput = this.container.querySelector('.opentfc-search-input') as HTMLInputElement;
            if (searchInput) searchInput.value = this.state.globalSearch;

            this.renderTableHeaderVisibility();
            this.renderColumnFilterControls();
            this.updateSortHeaderIcons();
            this.renderProfilesMenu();
            this.applyFilters();
            this.closeAllDropdowns();
          } catch {}
        }
      });
    });
  }

  private promptSaveProfile() {
    this.closeAllDropdowns();

    const existingOverlay = this.container.querySelector('.opentfc-modal-overlay');
    if (existingOverlay) existingOverlay.remove();

    const overlay = document.createElement('div');
    overlay.className = 'opentfc-modal-overlay';
    overlay.innerHTML = `
      <div class="opentfc-modal-box">
        <h4 class="opentfc-modal-title">Сохранить вид</h4>
        <p style="font-size: 13px; color: #42526e; margin: 0 0 10px 0;">Текущие фильтры, сортировка и видимость колонок сохранятся в Confluence для вашего профиля.</p>
        <input type="text" class="opentfc-input opentfc-profile-name-input" placeholder="Имя вида (например: Только активные)" value="${this.escapeHtml(this.activeProfileName)}" />
        <div class="opentfc-modal-actions">
          <button class="opentfc-btn opentfc-btn-modal-cancel">Отмена</button>
          <button class="opentfc-btn opentfc-btn-primary opentfc-btn-modal-save">Сохранить</button>
        </div>
      </div>
    `;

    const input = overlay.querySelector('.opentfc-profile-name-input') as HTMLInputElement;
    const cancelBtn = overlay.querySelector('.opentfc-btn-modal-cancel');
    const saveBtn = overlay.querySelector('.opentfc-btn-modal-save');

    cancelBtn?.addEventListener('click', () => overlay.remove());
    saveBtn?.addEventListener('click', () => {
      const name = input.value.trim();
      if (!name) {
        input.focus();
        return;
      }
      this.saveProfile(name, overlay);
    });

    input.addEventListener('keydown', (e) => {
      if (e.key === 'Enter') saveBtn?.dispatchEvent(new MouseEvent('click'));
      if (e.key === 'Escape') overlay.remove();
    });

    document.body.appendChild(overlay);
    setTimeout(() => input.focus(), 50);
  }

  private saveProfile(name: string, overlay: HTMLElement) {
    const pageId = (window as any).AJS?.params?.pageId || this.config.pageId || 0;
    const macroId = this.config.macroId;

    const payload = {
      pageId,
      macroId,
      name,
      stateJson: JSON.stringify({
        globalSearch: this.state.globalSearch,
        columnFilters: this.state.columnFilters,
        sorts: this.state.sorts,
        hiddenColumns: this.state.hiddenColumns
      })
    };

    fetch(`/rest/opentfc/1.0/profile/${pageId}/${macroId}`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-Atlassian-Token': 'no-check'
      },
      body: JSON.stringify(payload)
    })
      .then(res => res.json())
      .then((saved: SavedProfile) => {
        this.activeProfileName = name;
        const existingIdx = this.savedProfiles.findIndex(p => p.name === name);
        if (existingIdx !== -1) {
          this.savedProfiles[existingIdx] = saved;
        } else {
          this.savedProfiles.push(saved);
        }
        this.renderProfilesMenu();
        overlay.remove();
      })
      .catch(() => {
        alert('Не удалось сохранить вид. Убедитесь, что вы авторизованы в Confluence.');
      });
  }

  private deleteProfile(profileId: number) {
    if (!confirm('Удалить этот сохраненный вид?')) return;

    fetch(`/rest/opentfc/1.0/profile/${profileId}`, {
      method: 'DELETE',
      headers: { 'X-Atlassian-Token': 'no-check' }
    })
      .then(res => {
        if (res.ok) {
          this.savedProfiles = this.savedProfiles.filter(p => p.id !== profileId);
          this.renderProfilesMenu();
        } else {
          alert('Не удалось удалить вид.');
        }
      })
      .catch(() => alert('Ошибка при удалении вида.'));
  }

  private resetFilters() {
    this.state.globalSearch = '';
    this.state.columnFilters = {};
    this.state.sorts = [];
    this.state.currentPage = 1;
    this.activeProfileName = '';

    const searchInput = this.container.querySelector('.opentfc-search-input') as HTMLInputElement;
    if (searchInput) searchInput.value = '';

    this.renderColumnFilterControls();
    this.updateSortHeaderIcons();
    this.renderProfilesMenu();
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
    this.closeAllDropdowns();
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
    this.closeAllDropdowns();
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
      .then(res => {
        if (!res.ok) throw new Error();
        return res.blob();
      })
      .then(blob => {
        const link = document.createElement('a');
        link.href = URL.createObjectURL(blob);
        link.download = `table-${this.config.macroId || 'export'}.xlsx`;
        link.click();
      })
      .catch(() => {
        // Client-side CSV fallback if backend export is not reached
        this.exportCsv();
      });
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
