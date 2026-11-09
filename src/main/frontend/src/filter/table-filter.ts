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

/** Header events a third party table sorter binds on; they must never reach it. */
const HOST_SORTER_EVENTS = ['sort', 'mousedown', 'mouseup', 'selectstart', 'keyup'];

/** Classes added to the table by external sorter plugins. */
const HOST_SORTER_CLASSES = ['tablesorter', 'tablesorter-default', 'hasHeaders', 'stickyTableHeaders'];

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
  /** The table this component owns; the host page may append cloned header rows after it. */
  private tableEl: HTMLTableElement | null = null;
  /** URL parameter key the filter state was read from and is written back to. */
  private urlStateKey = '';

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

    // Wiki tables are very often decorated by an external sorter already present in the DOM, so it
    // has to be detached before the table is read - see detachHostSorter().
    this.tableEl = this.container.querySelector('table') as HTMLTableElement | null;
    this.detachHostSorter();
    this.stripHostHeaderClones();

    this.tableData = this.parseDOMTable();
    (this.container as any).__otfcTableData = this.tableData;
    this.urlStateKey = this.resolveUrlStateKey();

    // Check if initial filter state passed in URL
    this.loadStateFromUrl();

    this.initUI();
    this.loadProfiles();
    this.applyFilters();

    // The host re-clones table headers while scrolling (sticky headers of Confluence) and starts
    // its own sorter on the table; both would duplicate our filter row and resurrect the rows our
    // filters removed, so every foreign change to the table is undone as it happens.
    this.stripHostHeaderClones();
    if (typeof MutationObserver !== 'undefined' && this.tableEl) {
      const observer = new MutationObserver(() => {
        this.stripHostHeaderClones();
        if (this.hostSorterPresent()) this.detachHostSorter();
      });
      observer.observe(this.tableEl, { childList: true, subtree: true, attributes: true });
    }
  }

  private parseDOMTable() {
    const tableEl = this.container.querySelector('table') as HTMLTableElement | null;
    this.tableEl = tableEl;
    if (!tableEl) {
      return { id: this.config.macroId, columns: [], rows: [], totalRows: 0 };
    }

    // A component that is re-created over DOM it rendered itself (Confluence re-runs init after
    // an inline edit) would otherwise read back only the current page plus the totals row as the
    // whole dataset. The first parse keeps its data on the container and is reused.
    const cached = (this.container as any).__otfcTableData;
    if (cached && cached.columns && Array.isArray(cached.rows)) {
      return cached;
    }

    const columns: ExtendedColumnDef[] = [];
    // The header is exactly one row: the first row of <thead> when the source has one, otherwise
    // the first row of the table. The previous selector combined "thead th" with
    // "tr:first-child th, tr:first-child td", so for a table WITH <thead> it also matched the
    // first body row (it is tr:first-child of its tbody) - the component then built twice as many
    // columns as the table has and rendered empty cells next to every row.
    const headerRow = this.titleRow();
    const headers = headerRow
      ? Array.from(headerRow.querySelectorAll('th, td')).filter(el => (el as HTMLElement).closest('tr') === headerRow)
      : [];
    headers.forEach((th, idx) => {
      columns.push({
        index: idx,
        name: th.textContent?.trim() || `Колонка ${idx + 1}`,
        type: 'TEXT',
        visible: true
      });
    });

    const rows: RowData[] = [];
    const hasThead = this.headerSection() !== null;
    // Only body rows are data. The previous selector also matched <thead> rows, so on a table
    // without <thead> the title row was imported as a data row: it appeared in the totals, was
    // matched by the search and vanished as soon as any filter was applied.
    const trEls = Array.from(tableEl.querySelectorAll('tbody tr'))
      .filter(tr => !tr.closest('thead') && !tr.classList.contains('opentfc-filter-row')
        && !tr.classList.contains('opentfc-totals-row')
        && tr.closest('table') === tableEl);
    trEls.forEach((tr, rIdx) => {
      if (!hasThead && rIdx === 0) return;
      const cells = Array.from(tr.querySelectorAll('td, th')).filter(td => td.closest('tr') === tr);
      if (cells.length === 0) return;

      const rowCells: any[] = [];
      columns.forEach((col, cIdx) => {
        const td = cells[cIdx];
        if (td) {
          // No per-cell type: the column type is what the export coerces numbers by, and a hard
          // coded 'TEXT' here made every XLSX cell arrive as text (left-aligned, not summable).
          rowCells.push({
            val: td.textContent?.trim() || '',
            html: td.innerHTML
          });
        } else {
          rowCells.push({ val: '', html: '' });
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
    columns.forEach((col, cIdx) => {
      let numCount = 0;
      let dateCount = 0;
      let nonBlank = 0;
      let longest = 0;
      const uniqueVals = new Set<string>();
      const currencySuffixes: Record<string, number> = {};

      rows.forEach(r => {
        const val = r.cells[cIdx]?.val;
        if (val !== undefined && val !== '') {
          nonBlank++;
          uniqueVals.add(val);

          // Detect currency symbol or suffix (e.g. ₽, $, €, руб, etc.)
          const strVal = String(val).trim();
          longest = Math.max(longest, strVal.length);
          const currMatch = strVal.match(/([₽$€¥]|руб\.?|р\.?)$/i);
          if (currMatch) {
            const sym = ' ' + currMatch[1];
            currencySuffixes[sym] = (currencySuffixes[sym] || 0) + 1;
          }

          // Dates are checked first: "12.01.2024" is also a valid grouped number, and a date
          // column detected as NUMBER got "От/До" inputs instead of a date range, so filtering
          // it produced nothing.
          if (this.looksLikeDate(strVal)) {
            dateCount++;
          } else if (this.parseLooseNumber(strVal) !== null) {
            numCount++;
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
        } else if (this.isListColumn(uniqueVals.size, nonBlank, longest)) {
          col.type = 'SELECT';
          col.options = Array.from(uniqueVals).sort();
        } else {
          col.type = 'TEXT';
        }
      }
    });
  }

  /**
   * A column gets a checkbox list only when its values repeat and stay short.
   *
   * Previously any column with up to 50 distinct values became SELECT, so "ФИО сотрудника" or
   * "Комментарий" turned into an unreadable 40-item checklist whose exact-match filter could
   * never find a substring - which is what made filtering feel broken. Free text columns get a
   * search box instead.
   */
  private isListColumn(unique: number, nonBlank: number, longest: number): boolean {
    if (unique === 0 || unique > 50 || longest > 48) return false;
    return unique <= Math.max(12, Math.ceil(nonBlank * 0.6));
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

    // Header structure: title row gets sortable labels, filters go to their own row
    this.prepareHeader();
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
      // Column panels stop propagation themselves, so any click that reaches the document is
      // outside a panel; the toolbar dropdowns only close when the click leaves the component.
      this.closeColumnPanels();
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
    this.closeColumnPanels();
  }

  /** Cells of the title row — the row carrying column names and sort badges. */
  private titleCells(): HTMLElement[] {
    const row = this.titleRow();
    return row
      ? Array.from(row.querySelectorAll('th, td')).filter(cell => (cell as HTMLElement).closest('tr') === row) as HTMLElement[]
      : [];
  }

  /** The header section of our own table, ignoring sticky-header clones made by the host. */
  private headerSection(): HTMLTableSectionElement | null {
    if (!this.tableEl) return null;
    const sections = Array.from(this.tableEl.children).filter(el => el.nodeName === 'THEAD') as HTMLTableSectionElement[];
    if (sections.length === 0) return null;
    return sections.filter(section => !section.classList.contains('tableFloatingHeader'))[0] || sections[0];
  }

  private titleRow(): HTMLElement | null {
    if (!this.tableEl) return null;
    const thead = this.headerSection();
    const row = (thead && thead.querySelector('tr:not(.opentfc-filter-row)')) || this.tableEl.querySelector('tr');
    return row as HTMLElement | null;
  }

  /**
   * Detaches a third-party table sorter that runs on the table this component owns.
   *
   * Confluence ships a built-in "sortable tables" plugin (and wikis carry several similar ones):
   * it picks up any table whose first row is `<th>`, starts jQuery tablesorter on it (classes
   * "tablesorter"/"sortableHeader", header text wrapped in `headerButton` templates, cloned
   * floating headers) and that sorter caches the row nodes at init, re-appending all of them from
   * the cache on every sort. Rows our filters had just removed therefore came straight back into
   * the body, and one click on a header sorted the table behind our back while our own renderer
   * rebuilt it - the visible result being a table that "does not filter". Two sorters cannot share
   * one table, so the foreign instance is destroyed, the table is opted out of the built-in plugin
   * through its own documented `data-sortable="false"` flag, its classes are dropped and its header
   * handlers are silenced; sorting stays ours.
   */
  private detachHostSorter() {
    const table = this.tableEl;
    if (!table) return;
    // The switch the host plugin itself checks before touching a table.
    table.setAttribute('data-sortable', 'false');

    const jq: any = (window as any).jQuery || (window as any).$;
    if (typeof jq === 'function' && table.className.indexOf('tablesorter') !== -1) {
      try {
        if (jq.data && jq.data(table, 'tablesorter')) {
          jq(table).trigger('destroy');
          jq.removeData(table, 'tablesorter');
        }
        jq(table.querySelectorAll('thead th')).off(HOST_SORTER_EVENTS.join(' '));
      } catch { /* the host plugin is not part of our contract; init must survive it */ }
    }
    HOST_SORTER_CLASSES.forEach(cls => table.classList.remove(cls));

    // Whatever library is left, its header listeners never see the event: the capture phase stops
    // at the header section. Our own sorting reacts on "click", which is not intercepted.
    Array.from(table.children)
      .filter(el => el.nodeName === 'THEAD')
      .forEach(section => {
        if ((section as any).__otfcSilenced) return;
        (section as any).__otfcSilenced = true;
        HOST_SORTER_EVENTS.forEach(type => {
          section.addEventListener(type, (e: Event) => {
            const target = e.target as HTMLElement;
            // Our filter row and its popovers need the real mouse/keyboard behaviour: focus,
            // caret placement and text selection inside the inputs.
            if (target.closest && (target.closest('.opentfc-filter-row') || target.closest('.opentfc-multiselect-panel'))) return;
            e.stopImmediatePropagation();
          }, true);
        });
      });

    // Such plugins also mark header cells unselectable inline, which used to block typing into and
    // selecting text of any control rendered inside a header cell.
    this.titleCells().forEach(cell => {
      if (cell.getAttribute('unselectable') !== null) cell.removeAttribute('unselectable');
      if (cell.style.userSelect === 'none') cell.style.userSelect = '';
      if ((cell.style as any).webkitUserSelect === 'none') (cell.style as any).webkitUserSelect = '';
    });
  }

  /**
   * True while the table still carries traces of an external sorter.
   *
   * Host plugins are usually started on page load as well, so they can decorate the table after
   * this component has cleaned it up. The observer re-runs detachHostSorter only when something is
   * actually back, which keeps the mutation loop finite.
   */
  private hostSorterPresent(): boolean {
    const table = this.tableEl;
    if (!table) return false;
    if (table.getAttribute('data-sortable') !== 'false') return true;
    for (const cls of HOST_SORTER_CLASSES) {
      if (table.classList.contains(cls)) return true;
    }
    if (table.querySelector('thead th[unselectable]') !== null) return true;
    return Array.from(table.children).filter(el => el.nodeName === 'THEAD').length > 1;
  }

  /** Ensure the header row is inside <thead> so renderTableBody doesn't destroy it. */
  private prepareHeader() {
    if (!this.tableEl) return;
    let thead = this.headerSection();
    if (thead) return;
    const firstRow = this.titleRow();
    if (!firstRow) return;
    thead = document.createElement('thead');
    firstRow.parentNode!.removeChild(firstRow);
    thead.appendChild(firstRow);
    this.tableEl.insertBefore(thead, this.tableEl.firstChild);
  }

  /**
   * Returns the dedicated filter row cells, creating the row and syncing it with the column count.
   *
   * Filter controls used to be injected into the title <th> cells. Together with
   * `th { white-space: nowrap }` and the sticky frozen column, the control of one column was
   * painted over the inputs of the neighbour (they were literally unclickable) and the widened
   * header pushed a phantom horizontal scrollbar under the table. One row per column keeps every
   * control inside its own column width.
   */
  private ensureFilterRow(): HTMLElement[] {
    if (!this.tableEl) return [];
    let thead = this.headerSection();
    if (!thead) {
      thead = document.createElement('thead');
      this.tableEl.insertBefore(thead, this.tableEl.firstChild);
    }

    let row = thead.querySelector('tr.opentfc-filter-row') as HTMLElement | null;
    if (!row) {
      row = document.createElement('tr');
      row.className = 'opentfc-filter-row';
      thead.appendChild(row);
    }

    const columnCount = this.titleCells().length;
    while (row.children.length < columnCount) row.appendChild(document.createElement('td'));
    while (row.children.length > columnCount) row.removeChild(row.lastChild!);

    const cells = Array.from(row.children) as HTMLElement[];
    const titleHeight = this.titleCells()[0]?.offsetHeight || 0;
    cells.forEach((cell, idx) => {
      cell.className = 'opentfc-filter-cell';
      cell.style.display = this.state.hiddenColumns.includes(idx) ? 'none' : '';
      // Sticky filter row must sit under the title row, not at the top of the scroll container.
      if (this.config.stickyHeader && titleHeight) {
        cell.style.top = `${titleHeight}px`;
      }
    });
    return cells;
  }

  /**
   * Gets rid of the header clones that host sticky-header scripts add to our table.
   *
   * Only clones may be touched: the tablesorter "stickyHeaders" widget marks the REAL header with
   * tableFloatingHeaderOriginal, so hiding both classes makes the column titles disappear.
   * A table holds exactly one <thead>, so a second, cloned section inside it is dropped - it would
   * duplicate every filter control we render and swallow clicks meant for the originals.
   */
  private stripHostHeaderClones() {
    if (!this.tableEl) return;
    const sections = Array.from(this.tableEl.children).filter(el => el.nodeName === 'THEAD');
    if (sections.length > 1) {
      const keep = sections.filter(section => !section.classList.contains('tableFloatingHeader'))[0] || sections[0];
      sections.forEach(section => { if (section !== keep) section.remove(); });
    }
    this.container.querySelectorAll('.tableFloatingHeader:not(.tableFloatingHeaderOriginal)')
      .forEach(el => { (el as HTMLElement).style.display = 'none'; });
  }

  /**
   * Shows a column multiselect as a page level popover.
   *
   * The panel cannot live inside the table: the wrapper scrolls (overflow:auto) and would clip it
   * against the last visible row, and sticky frozen columns repaint over it.
   */
  private toggleColumnPanel(panel: HTMLElement, anchor: HTMLElement) {
    const wasOpen = panel.classList.contains('show');
    this.closeAllDropdowns();
    if (wasOpen) return;

    panel.setAttribute('data-opentfc-floating', '1');
    panel.classList.add('show');
    document.body.appendChild(panel);
    this.positionColumnPanel(panel, anchor);

    const reposition = () => this.positionColumnPanel(panel, anchor);
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') this.closeColumnPanels(); };
    window.addEventListener('scroll', reposition, true);
    window.addEventListener('resize', reposition);
    document.addEventListener('keydown', onKey);
    (panel as any).__otfcTeardown = () => {
      window.removeEventListener('scroll', reposition, true);
      window.removeEventListener('resize', reposition);
      document.removeEventListener('keydown', onKey);
    };
  }

  private positionColumnPanel(panel: HTMLElement, anchor: HTMLElement) {
    const rect = anchor.getBoundingClientRect();
    panel.style.position = 'fixed';
    panel.style.left = '0px';
    panel.style.top = '0px';
    const width = panel.offsetWidth || 240;
    const height = panel.offsetHeight || 160;
    const left = Math.max(8, Math.min(rect.left, window.innerWidth - width - 8));
    const below = rect.bottom + 4;
    const top = below + height > window.innerHeight - 8 ? Math.max(8, rect.top - height - 4) : below;
    panel.style.left = `${left}px`;
    panel.style.top = `${top}px`;
  }

  private closeColumnPanels() {
    document.querySelectorAll<HTMLElement>('.opentfc-multiselect-panel[data-opentfc-floating]').forEach(panel => {
      const teardown = (panel as any).__otfcTeardown as (() => void) | undefined;
      if (teardown) teardown();
      panel.classList.remove('show');
      panel.removeAttribute('data-opentfc-floating');
      panel.remove();
    });
  }

  /** Label for the select trigger; null means "no filter", an empty array means "match nothing". */
  private selectSummary(values: string[] | null, totalOptions: number): string {
    if (values === null || (totalOptions > 0 && values.length === totalOptions)) return 'Все';
    if (values.length === 0) return 'Ничего';
    if (values.length === 1) return values[0];
    return values.length <= 2 ? values.join(', ') : `${values.length} выбрано`;
  }

  /** Renders filter controls into the dedicated filter row, one cell per column. */
  private renderColumnFilterControls() {
    if (!this.tableEl) return;

    if (!this.config.showColumnFilters) {
      this.closeColumnPanels();
      this.headerSection()?.querySelector('tr.opentfc-filter-row')?.remove();
      return;
    }

    const cells = this.ensureFilterRow();
    if (cells.length === 0) return;

    this.closeColumnPanels();

    this.tableData.columns.forEach((col, idx) => {
      const cell = cells[idx] as HTMLElement;
      if (!cell) return;
      cell.innerHTML = '';

      // Create filter container
      const filterDiv = document.createElement('div');
      filterDiv.className = 'opentfc-th-filter';
      filterDiv.addEventListener('click', (e) => e.stopPropagation());

      if (col.type === 'SELECT' && col.options && col.options.length > 0) {
        const options = col.options;
        const currentFilter = this.state.columnFilters[idx];
        const selectedList: string[] | null = Array.isArray(currentFilter)
          ? currentFilter as string[]
          : (typeof currentFilter === 'string' && currentFilter ? [currentFilter] : null);

        const trigger = document.createElement('button');
        trigger.className = 'opentfc-select-trigger';
        trigger.innerHTML = `${this.escapeHtml(this.selectSummary(selectedList, options.length))} ▾`;

        const panel = document.createElement('div');
        panel.className = 'opentfc-multiselect-panel';
        panel.innerHTML = `
          <div class="opentfc-multiselect-actions">
            <span class="opentfc-multiselect-link opentfc-select-all">Выбрать все</span>
            <span class="opentfc-multiselect-link opentfc-clear-all" style="color:#6b778c;">Очистить</span>
          </div>
          <div class="opentfc-multiselect-options">
            ${options.map(opt => `
              <label class="opentfc-multiselect-option">
                <input type="checkbox" value="${this.escapeHtml(opt)}" ${selectedList && selectedList.includes(opt) ? 'checked' : ''} />
                <span>${this.escapeHtml(opt)}</span>
              </label>
            `).join('')}
          </div>
        `;

        trigger.addEventListener('click', (e) => {
          e.stopPropagation();
          this.toggleColumnPanel(panel, trigger);
        });

        panel.addEventListener('click', (e) => e.stopPropagation());

        // null clears the filter, [] keeps an explicit "match nothing" selection: previously
        // unchecking every box silently reset the column to "Все", so the table suddenly showed
        // all rows again and the filter looked broken.
        const applySelection = (values: string[] | null) => {
          if (values === null || (options.length > 0 && values.length === options.length)) {
            delete this.state.columnFilters[idx];
          } else {
            this.state.columnFilters[idx] = values;
          }
          trigger.innerHTML = `${this.escapeHtml(this.selectSummary(values === null || options.length === 0 ? null : values, options.length))} ▾`;
          this.state.currentPage = 1;
          this.applyFilters();
        };

        panel.querySelectorAll('input[type="checkbox"]').forEach(cb => {
          cb.addEventListener('change', () => {
            const checked = Array.from(panel.querySelectorAll<HTMLInputElement>('input[type="checkbox"]:checked')).map(input => input.value);
            applySelection(checked);
          });
        });

        panel.querySelector('.opentfc-select-all')?.addEventListener('click', () => {
          panel.querySelectorAll<HTMLInputElement>('input[type="checkbox"]').forEach(cb => cb.checked = true);
          applySelection(options);
        });

        panel.querySelector('.opentfc-clear-all')?.addEventListener('click', () => {
          panel.querySelectorAll<HTMLInputElement>('input[type="checkbox"]').forEach(cb => cb.checked = false);
          applySelection([]);
        });

        filterDiv.appendChild(trigger);

      } else if (col.type === 'NUMBER') {
        const current = (this.state.columnFilters[idx] as any) || {};
        const minVal = current.min !== undefined && current.min !== null ? current.min : '';
        const maxVal = current.max !== undefined && current.max !== null ? current.max : '';

        // type="text" + inputmode="decimal" on purpose: a native number input reports an EMPTY
        // value for "1 234,56" or "12,5", which are exactly the formats these tables display, so
        // the range filter silently did nothing.
        const minInput = document.createElement('input');
        minInput.type = 'text';
        minInput.inputMode = 'decimal';
        minInput.className = 'opentfc-input opentfc-input-num opentfc-num-min';
        minInput.placeholder = 'От';
        minInput.value = String(minVal);

        const sep = document.createElement('span');
        sep.className = 'opentfc-range-sep';
        sep.textContent = '—';

        const maxInput = document.createElement('input');
        maxInput.type = 'text';
        maxInput.inputMode = 'decimal';
        maxInput.className = 'opentfc-input opentfc-input-num opentfc-num-max';
        maxInput.placeholder = 'До';
        maxInput.value = String(maxVal);

        const updateNumberFilter = () => {
          const minText = minInput.value.trim();
          const maxText = maxInput.value.trim();
          const min = minText ? this.parseLooseNumber(minText) : undefined;
          const max = maxText ? this.parseLooseNumber(maxText) : undefined;
          const minBad = !!minText && min === null;
          const maxBad = !!maxText && max === null;
          minInput.classList.toggle('opentfc-input-invalid', minBad);
          maxInput.classList.toggle('opentfc-input-invalid', maxBad);
          // Unparsable text is neither "no filter" nor a bound to drop: the previous filter stays
          // until the value becomes readable, so typing does not silently widen the result set.
          if (minBad || maxBad) return;
          if (min !== undefined || max !== undefined) {
            this.state.columnFilters[idx] = { min: min ?? undefined, max: max ?? undefined };
          } else {
            delete this.state.columnFilters[idx];
          }
          this.state.currentPage = 1;
          this.applyFilters();
        };

        minInput.addEventListener('input', updateNumberFilter);
        maxInput.addEventListener('input', updateNumberFilter);

        filterDiv.appendChild(minInput);
        filterDiv.appendChild(sep);
        filterDiv.appendChild(maxInput);

      } else if (col.type === 'DATE') {
        const current = (this.state.columnFilters[idx] as any) || {};
        const fromVal = current.from || '';
        const toVal = current.to || '';

        const fromInput = document.createElement('input');
        fromInput.type = 'date';
        fromInput.className = 'opentfc-input opentfc-input-date opentfc-date-from';
        fromInput.value = fromVal;

        const sep = document.createElement('span');
        sep.className = 'opentfc-range-sep';
        sep.textContent = '—';

        const toInput = document.createElement('input');
        toInput.type = 'date';
        toInput.className = 'opentfc-input opentfc-input-date opentfc-date-to';
        toInput.value = toVal;

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

        filterDiv.appendChild(fromInput);
        filterDiv.appendChild(sep);
        filterDiv.appendChild(toInput);

      } else {
        // Text column
        const txtInput = document.createElement('input');
        txtInput.type = 'text';
        txtInput.className = 'opentfc-input opentfc-input-txt';
        txtInput.placeholder = 'Фильтр...';
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

        filterDiv.appendChild(txtInput);
      }

      cell.appendChild(filterDiv);
    });
  }

  private applyFrozenColumns() {
    const freezeCount = Math.max(0, parseInt(this.config.freezeColumns || '0', 10));
    const tableWrapper = this.container.querySelector('.opentfc-table-wrapper');
    if (!tableWrapper) return;

    const thead = this.headerSection();
    const headerRow = thead ? thead.querySelector('tr:not(.opentfc-filter-row)') : null;
    const thEls = headerRow ? headerRow.querySelectorAll('th, td') : [];
    const filterRow = thead ? thead.querySelector('tr.opentfc-filter-row') : null;
    const filterCells = filterRow ? Array.from(filterRow.children) : [];
    const trEls = tableWrapper.querySelectorAll('tbody tr');

    // Reset previous frozen classes and styles
    thEls.forEach(th => {
      th.classList.remove('opentfc-frozen-cell', 'opentfc-frozen-last');
      (th as HTMLElement).style.left = '';
    });
    filterCells.forEach(cell => {
      cell.classList.remove('opentfc-frozen-cell', 'opentfc-frozen-last');
      (cell as HTMLElement).style.left = '';
    });
    trEls.forEach(tr => {
      tr.querySelectorAll('td').forEach(td => {
        td.classList.remove('opentfc-frozen-cell', 'opentfc-frozen-last');
        (td as HTMLElement).style.left = '';
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

        // Filter cells freeze with their column, otherwise the frozen header slides over them
        const filterCell = filterCells[visibleFrozenCount] as HTMLElement | undefined;
        if (filterCell) {
          filterCell.classList.add('opentfc-frozen-cell');
          filterCell.style.left = `${currentLeft}px`;
          if (visibleFrozenCount === freezeCount - 1) filterCell.classList.add('opentfc-frozen-last');
        }

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
    const thead = this.headerSection();
    if (!thead) return;
    const headerRow = thead.querySelector('tr:not(.opentfc-filter-row)');
    if (!headerRow) return;
    const thEls = headerRow.querySelectorAll('th, td');
    thEls.forEach((th, cIdx) => {
      th.addEventListener('click', (e: Event) => {
        const target = e.target as HTMLElement;
        // Don't sort when clicking on filter controls
        if (target.closest('.opentfc-th-filter') || target.closest('.opentfc-multiselect-panel')) return;
        // A host sorter often delegates header clicks higher up the tree; our table is sorted by
        // this component only, so the click stops here.
        e.stopPropagation();
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
    const thead = this.headerSection();
    if (!thead) return;
    const headerRow = thead.querySelector('tr:not(.opentfc-filter-row)');
    if (!headerRow) return;
    const thEls = headerRow.querySelectorAll('th, td');
    thEls.forEach((th, cIdx) => {
      const sortIdx = this.state.sorts.findIndex(s => s.columnIndex === cIdx);
      const textEl = th.querySelector('.opentfc-th-text') || th;
      let badge = textEl.querySelector('.opentfc-sort-badge');
      if (!badge) {
        badge = document.createElement('span');
        badge.className = 'opentfc-sort-badge';
        textEl.appendChild(badge);
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

    // 1. Global search: every word has to occur somewhere in the row (in one cell together or in
    // different cells) - "ИТ Москва" is read as "the ИТ employee from Moscow", not as one phrase.
    if (this.state.globalSearch) {
      const terms = this.searchTerms(this.state.globalSearch);
      rows = rows.filter(r => terms.every(term => r.cells.some(c => {
        const text = String(c.val === undefined || c.val === null ? '' : c.val).toLowerCase();
        return text.indexOf(term) !== -1;
      })));
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
        // Text search: every word has to be present somewhere in the cell, so "отдел кадров"
        // finds the row even when the user types the words in another order.
        const terms = this.searchTerms(filterVal.toLowerCase());
        rows = rows.filter(r => {
          const text = String(r.cells[colIdx]?.val || '').toLowerCase();
          return terms.every(term => text.indexOf(term) !== -1);
        });
      } else if (typeof filterVal === 'object' && filterVal !== null) {
        const range = filterVal as any;
        if (range.min !== undefined || range.max !== undefined) {
          // Numeric range, parsed the same way as the cell text so "1 234,56" is comparable
          const min = this.parseLooseNumber(range.min);
          const max = this.parseLooseNumber(range.max);
          rows = rows.filter(r => {
            const n = this.parseLooseNumber(r.cells[colIdx]?.val);
            if (n === null) return false;
            if (min !== null && n < min) return false;
            if (max !== null && n > max) return false;
            return true;
          });
        } else if (range.from !== undefined || range.to !== undefined) {
          // Date range: a cell that is not a date cannot be inside the range
          rows = rows.filter(r => {
            const raw = String(r.cells[colIdx]?.val || '').trim();
            const cellDate = this.parseDate(raw);
            if (!cellDate) return false;
            if (range.from) {
              const fromDate = this.parseDate(range.from);
              if (fromDate && cellDate < fromDate) return false;
            }
            if (range.to) {
              // "До" is inclusive: the date input has no time part, so the whole last day counts
              const toDate = this.parseDate(range.to);
              if (toDate && cellDate > new Date(toDate.getFullYear(), toDate.getMonth(), toDate.getDate(), 23, 59, 59, 999)) return false;
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

          // Empty and unreadable cells are ordered last in BOTH directions: flipping the
          // comparator result also used to push every "—" to the top of a descending sort, which
          // looked like a broken sort on tables that use dashes for missing values.
          const missA = this.isMissingCell(valA, colType);
          const missB = this.isMissingCell(valB, colType);
          if (missA || missB) {
            if (missA && missB) continue;
            return missA ? 1 : -1;
          }

          let cmp = 0;
          if (colType === 'NUMBER') {
            cmp = this.compareNumbers(valA, valB);
          } else if (colType === 'DATE' || this.looksLikeDate(String(valA)) || this.looksLikeDate(String(valB))) {
            cmp = this.compareDates(valA, valB);
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

  /** Words of a free-text query; empty ones are ignored so "ИТ   кадр" still works. */
  private searchTerms(query: string): string[] {
    return query.split(/\s+/).filter(t => t.length > 0);
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

  /** Recognises the date shapes the tables use: ISO, dd.MM.yyyy, dd/MM/yyyy, with optional time. */
  private looksLikeDate(val: string): boolean {
    if (!val) return false;
    const text = val.trim();
    return /^\d{4}-\d{1,2}-\d{1,2}([T ]\d{1,2}:\d{2}(:\d{2})?)?$/.test(text)
      || /^\d{1,2}[./]\d{1,2}[./]\d{2,4}$/.test(text);
  }

  /**
   * Parses a displayed number: "1 234,56", "1.234,56 ₽", "-12,5", "(500)", "42%".
   * Returns null when the text is not a number.
   *
   * The previous `parseFloat(text.replace(/[^\d.-]/g, ''))` turned "1 234,56" into 123456 and
   * "12,5" into 125, so range filters dropped rows that plainly matched and sorting ordered the
   * column nonsense. Grouping/decimal ambiguity is resolved for the ru-RU display format this
   * component renders (comma = decimal, dot/space = thousands).
   */
  private parseLooseNumber(val: any): number | null {
    if (val === null || val === undefined) return null;
    if (typeof val === 'number') return isFinite(val) ? val : null;
    let text = String(val).replace(/[\s\u00a0\u202f]/g, '');
    if (!text) return null;

    let negative = false;
    const paren = text.match(/^\((.*)\)$/);
    if (paren) {
      negative = true;
      text = paren[1];
    }
    // Only the units this component renders may surround the digits. Everything else that contains
    // a letter has to stay text: the previous version stripped every non-digit, so "Фамилия1",
    // "Комментарий 12", "1С" or "ОКПД 2" parsed as numbers, whole text columns were typed NUMBER
    // and got an "От/До" filter that matched nonsense.
    text = text.replace(/(%|\u2030|\u20bd|\$|\u20ac|\u00a3|\u00a5|руб\.?|коп\.?|грн\.?)/gi, '');
    const sign = text.match(/^([+-])(.*)$/);
    if (sign) {
      if (sign[1] === '-') negative = true;
      text = sign[2];
    }
    if (!/^[0-9][0-9.,]*$/.test(text)) return null;

    const commas = (text.match(/,/g) || []).length;
    const dots = (text.match(/\./g) || []).length;
    if (commas > 0 && dots > 0) {
      // Both separators: the rightmost one is the decimal separator.
      if (text.lastIndexOf(',') > text.lastIndexOf('.')) {
        text = text.replace(/\./g, '').replace(/,/g, '.');
      } else {
        text = text.replace(/,/g, '');
      }
    } else if (commas > 1 || dots > 1) {
      // Several separators of one kind can only be digit grouping.
      text = text.replace(/[.,]/g, '');
    } else if (commas === 1) {
      text = text.replace(',', '.');
    } else if (dots === 1 && text.length - text.lastIndexOf('.') - 1 === 3 && /^\d{1,3}\.\d{3}$/.test(text)) {
      // "1.234" is ru-RU for 1234; three decimals after a single dot are written with more digits.
      text = text.replace('.', '');
    }

    const num = Number(text);
    if (isNaN(num) || !isFinite(num)) return null;
    return negative ? -num : num;
  }

  /** Ordering comparator that keeps non-numeric cells at the end instead of mixing them in. */
  private compareNumbers(a: any, b: any): number {
    const nA = this.parseLooseNumber(a);
    const nB = this.parseLooseNumber(b);
    if (nA === null && nB === null) return 0;
    if (nA === null) return 1;
    if (nB === null) return -1;
    return nA - nB;
  }

  /** Same for dates: unparsable/empty values sort last instead of collapsing to 1970. */
  private compareDates(a: any, b: any): number {
    const dA = this.parseDate(String(a || '').trim());
    const dB = this.parseDate(String(b || '').trim());
    if (!dA && !dB) return 0;
    if (!dA) return 1;
    if (!dB) return -1;
    return dA.getTime() - dB.getTime();
  }

  /** A cell that carries no value for the column type, including the "—" placeholder. */
  private isMissingCell(val: any, colType?: string): boolean {
    if (val === null || val === undefined) return true;
    const text = String(val).trim();
    if (!text || /^[\u2013\u2014-]+$/.test(text) || /^(н\s?\/\s?а|n\s?\/\s?a|нет|—)$/i.test(text)) return true;
    if (colType === 'NUMBER') return this.parseLooseNumber(text) === null;
    if (colType === 'DATE') return this.parseDate(text) === null;
    return false;
  }

  private renderTableBody() {
    let tbody = this.container.querySelector('.opentfc-table-wrapper tbody');
    if (!tbody && this.tableEl) {
      // A source table can be header-only (no <tbody> at all); without a body there would be
      // nothing to re-render and every filter would silently do nothing.
      tbody = document.createElement('tbody');
      this.tableEl.appendChild(tbody);
    }
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
          const n = this.parseLooseNumber(r.cells[idx]?.val);
          if (n !== null) {
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
    const summary = this.filterSummary();

    // An empty table after filtering looks like "the filter is broken" unless the component says
    // what is filtering - especially when the active filter sits on a hidden column.
    let hint = '';
    if (summary.total > 0) {
      hint = `<span class="opentfc-filter-hint">активных фильтров: ${summary.total}</span>`
        + (summary.hidden > 0
            ? `<span class="opentfc-filter-hint opentfc-filter-hint-warn">${summary.hidden} на скрытых колонках</span>`
            : '')
        + ` <a href="javascript:void(0)" class="opentfc-pg-reset">сбросить</a>`;
    }
    if (summary.total === 0 && this.state.hiddenColumns.length > 0) {
      hint = `<span class="opentfc-filter-hint">скрыто колонок: ${this.state.hiddenColumns.length}</span>`;
    }
    const emptyHint = total === 0 && summary.total > 0
      ? `<div class="opentfc-empty-hint">Ни одна строка не подходит под фильтры.</div>`
      : '';

    bar.innerHTML = `
      <div class="opentfc-pagination-info">
        Строк: <strong>${total}</strong> (из ${this.tableData.totalRows}) ${hint}
        ${emptyHint}
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

    bar.querySelector('.opentfc-pg-reset')?.addEventListener('click', () => this.resetFilters());

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

  /**
   * How many filters are currently restricting the rows, and how many of them belong to columns
   * that are hidden right now (the classic "the table is empty but I see no filter" case).
   */
  private filterSummary(): { total: number; hidden: number } {
    let total = 0;
    let hidden = 0;
    Object.keys(this.state.columnFilters).forEach(key => {
      const idx = parseInt(key, 10);
      if (isNaN(idx)) return;
      const val: any = (this.state.columnFilters as any)[idx];
      let active = false;
      if (typeof val === 'string') {
        active = val.length > 0;
      } else if (Array.isArray(val)) {
        // [] is an explicit "Ничего" selection, so it is active
        active = true;
      } else if (val && typeof val === 'object') {
        active = val.min !== undefined || val.max !== undefined || !!val.from || !!val.to;
      }
      if (!active) return;
      total++;
      if (this.state.hiddenColumns.indexOf(idx) !== -1) hidden++;
    });
    if (this.state.globalSearch) total++;
    return { total, hidden };
  }

  private renderTableHeaderVisibility() {
    const thead = this.container.querySelector('.opentfc-table-wrapper thead');
    if (!thead) return;
    const headerRow = thead.querySelector('tr');
    if (!headerRow) return;
    const thEls = headerRow.querySelectorAll('th, td');
    thEls.forEach((th, cIdx) => {
      (th as HTMLElement).style.display = this.state.hiddenColumns.includes(cIdx) ? 'none' : '';
    });

    // The filter row is a sibling row of the same header: hiding a column must hide its cell here
    // too, otherwise the controls shift by one column and filter the wrong column.
    const filterRow = thead.querySelector('tr.opentfc-filter-row');
    if (filterRow) {
      Array.from(filterRow.children).forEach((cell, cIdx) => {
        (cell as HTMLElement).style.display = this.state.hiddenColumns.includes(cIdx) ? 'none' : '';
      });
    }
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

  /**
   * URL parameter that carries this table's state.
   *
   * `tf_<macroId>` is the exact key. When the derived macro id rotates (the table content was
   * edited) an already shared link would silently stop working, so a single foreign `tf_otfc-*`
   * key is accepted as fallback; ambiguity (several tables) keeps the exact key.
   */
  private resolveUrlStateKey(): string {
    const exact = `tf_${this.config.macroId}`;
    try {
      const keys = Array.from(new URL(window.location.href).searchParams.keys());
      if (keys.indexOf(exact) !== -1) return exact;
      const foreign = keys.filter(k => k.indexOf('tf_otfc-') === 0);
      return foreign.length === 1 ? foreign[0] : exact;
    } catch {
      return exact;
    }
  }

  private syncStateToUrl() {
    try {
      const url = new URL(window.location.href);
      const key = this.urlStateKey || `tf_${this.config.macroId}`;
      if (this.state.globalSearch || Object.keys(this.state.columnFilters).length > 0 || this.state.sorts.length > 0) {
        url.searchParams.set(key, this.encodeState({
          q: this.state.globalSearch,
          f: this.state.columnFilters,
          s: this.state.sorts
        }));
      } else {
        url.searchParams.delete(key);
      }
      window.history.replaceState({}, '', url.toString());
    } catch {}
  }

  /**
   * UTF-8 safe base64 for the URL state.
   *
   * `btoa(JSON.stringify(...))` throws InvalidCharacterError on any Cyrillic value, which used to
   * abort the whole sync inside the empty catch: a link with a Russian filter value was never
   * written to the address bar, so shared links silently lost their filters.
   */
  private encodeState(obj: any): string {
    const json = JSON.stringify(obj);
    if (typeof TextEncoder !== 'undefined') {
      const bytes = new TextEncoder().encode(json);
      let binary = '';
      bytes.forEach(byte => { binary += String.fromCharCode(byte); });
      return btoa(binary);
    }
    return btoa(unescape(encodeURIComponent(json)));
  }

  private decodeState(param: string): any {
    const binary = atob(param);
    let json: string;
    if (typeof TextDecoder !== 'undefined') {
      const bytes = new Uint8Array(binary.length);
      for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
      json = new TextDecoder().decode(bytes);
    } else {
      json = decodeURIComponent(escape(binary));
    }
    return JSON.parse(json);
  }

  private loadStateFromUrl() {
    try {
      const url = new URL(window.location.href);
      const key = this.urlStateKey || `tf_${this.config.macroId}`;
      const param = url.searchParams.get(key);
      if (param) {
        const decoded = this.decodeState(param);
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
    this.downloadBlob(blob, `table-${this.config.macroId || 'export'}.csv`);
  }

  /**
   * Table payload for the server side exports.
   *
   * Only the fields the wire model declares are sent. The UI-only properties (visible,
   * currencySuffix) used to be posted as well, and the platform Jackson rejected them with
   * "Unrecognized field ... not marked as ignorable" - a 500 the browser then hid behind a CSV
   * download.
   */
  private buildExportPayload() {
    const visibleCols = this.tableData.columns.filter((c, idx) => !this.state.hiddenColumns.includes(idx));
    return {
      id: this.config.macroId || 'export',
      columns: visibleCols.map(c => ({
        index: c.index,
        name: c.name,
        type: c.type,
        options: c.options ? c.options.slice() : []
      })),
      rows: this.filteredRows.map(r => ({
        cells: visibleCols.map(c => {
          const cell = r.cells[c.index];
          return { val: cell && cell.val !== undefined && cell.val !== null ? cell.val : '', html: cell?.html || '', type: c.type };
        })
      })),
      totalRows: this.filteredRows.length
    };
  }

  private downloadBlob(blob: Blob, fileName: string) {
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = fileName;
    link.style.display = 'none';
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    setTimeout(() => URL.revokeObjectURL(url), 30000);
  }

  /** Extracts a human readable reason from an error response (JSON body or Confluence XML). */
  private serverErrorMessage(text: string): string {
    if (!text) return '';
    try {
      const data = JSON.parse(text);
      const message = data.message || data.error || data.errorMessage || '';
      const cause = data.cause || data.detail || '';
      return [message, cause].filter(Boolean).join(': ').slice(0, 300);
    } catch {
      const xml = /<Message>([\s\S]*?)<\/Message>/.exec(text);
      if (xml) return xml[1].replace(/<!\[CDATA\[|\]\]>/g, '').trim().slice(0, 300);
      // Our REST resources answer plain text for errors; Confluence pages answer HTML.
      if (text.charAt(0) !== '<') return text.trim().slice(0, 300);
    }
    return '';
  }

  /** Transient message inside the component; used for export failures so they are not silent. */
  private showNotice(message: string, isError: boolean = false) {
    const existing = this.container.querySelector('.opentfc-notice');
    if (existing) existing.remove();

    const notice = document.createElement('div');
    notice.className = 'opentfc-notice' + (isError ? ' opentfc-notice-error' : '');
    notice.setAttribute('role', isError ? 'alert' : 'status');
    notice.textContent = message;

    const toolbar = this.container.querySelector('.opentfc-toolbar');
    if (toolbar && toolbar.parentNode) {
      toolbar.parentNode.insertBefore(notice, toolbar.nextSibling);
    } else {
      this.container.insertBefore(notice, this.container.firstChild);
    }

    setTimeout(() => {
      if (notice.parentNode) notice.remove();
    }, isError ? 12000 : 4000);
  }

  private exportXlsx() {
    this.closeAllDropdowns();
    const fileName = `table-${this.config.macroId || 'export'}.xlsx`;
    const payload = this.buildExportPayload();

    fetch(`/rest/opentfc/1.0/export/xlsx?fileName=${encodeURIComponent(fileName)}`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Accept': 'application/json, application/octet-stream',
        'X-Atlassian-Token': 'no-check'
      },
      body: JSON.stringify(payload)
    })
      .then(res => {
        if (!res.ok) {
          return res.text().then(text => {
            throw new Error(this.serverErrorMessage(text) || `HTTP ${res.status}`);
          });
        }
        const ctype = res.headers.get('Content-Type') || '';
        // An error page would arrive as JSON/HTML with status 200 in some proxy setups
        if (ctype.indexOf('spreadsheet') === -1 && ctype.indexOf('octet-stream') === -1) {
          return res.text().then(text => {
            throw new Error(this.serverErrorMessage(text) || `неверный тип ответа (${ctype || 'пусто'})`);
          });
        }
        return res.blob();
      })
      .then(blob => {
        if (!blob || blob.size === 0) {
          throw new Error('сервер вернул пустой файл');
        }
        this.downloadBlob(blob, fileName);
        this.showNotice(`Excel-файл выгружен: ${fileName}`);
      })
      .catch(err => {
        // No silent CSV substitution: when Excel export fails the user must see the reason,
        // otherwise every broken export looks like "the plugin always downloads CSV".
        const reason = err && err.message ? err.message : String(err);
        this.showNotice(`Не удалось выгрузить Excel: ${reason}. Доступен экспорт в CSV.`, true);
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
