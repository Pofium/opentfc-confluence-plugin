import * as echarts from 'echarts';
import { ColumnDef, RowData } from '../core/table-model';

export class ChartRenderer {
  private container: HTMLElement;
  private canvas: HTMLElement | null;
  private chartInstance: echarts.ECharts | null = null;
  private config: any;
  private currentColumns: ColumnDef[] = [];
  private currentRows: RowData[] = [];

  constructor(container: HTMLElement) {
    this.container = container;
    this.canvas = container.querySelector('.opentfc-chart-canvas');
    const raw = container.getAttribute('data-opentfc-config') || '{}';
    try {
      this.config = JSON.parse(raw);
    } catch {
      this.config = {};
    }

    if (this.canvas) {
      this.chartInstance = echarts.init(this.canvas);
      window.addEventListener('resize', () => this.chartInstance?.resize());
    }

    this.initToolbar();
    this.parseInlineSource();
    this.bindUpstream();
  }

  private initToolbar() {
    const toolbar = this.container.querySelector('.opentfc-chart-toolbar');
    if (!toolbar) return;

    toolbar.innerHTML = `
      <div style="display:flex; justify-content: space-between; align-items:center; padding: 6px 10px; background: var(--ds-surface-sunken, #f4f5f7); border-bottom: 1px solid var(--ds-border, #dfe1e6);">
        <div style="font-weight: 600; font-size: 13px;">${this.config.chartTitle || 'Диаграмма OpenTFC'}</div>
        <div style="display:flex; gap: 6px; align-items: center;">
          <select class="opentfc-input opentfc-chart-type-selector" style="width: auto; padding: 2px 6px; font-size: 12px;">
            <option value="column" ${this.config.chartType === 'column' ? 'selected' : ''}>Гистограмма (Столбцы)</option>
            <option value="stacked-column" ${this.config.chartType === 'stacked-column' ? 'selected' : ''}>С накоплением</option>
            <option value="bar" ${this.config.chartType === 'bar' ? 'selected' : ''}>Линейчатая (Горизонт.)</option>
            <option value="line" ${this.config.chartType === 'line' ? 'selected' : ''}>График (Линии)</option>
            <option value="area" ${this.config.chartType === 'area' ? 'selected' : ''}>С областями (Area)</option>
            <option value="pie" ${this.config.chartType === 'pie' ? 'selected' : ''}>Круговая (Pie)</option>
            <option value="donut" ${this.config.chartType === 'donut' ? 'selected' : ''}>Кольцевая (Donut)</option>
            <option value="scatter" ${this.config.chartType === 'scatter' ? 'selected' : ''}>Точечная (Scatter)</option>
            <option value="radar" ${this.config.chartType === 'radar' ? 'selected' : ''}>Лепестковая (Radar)</option>
            <option value="funnel" ${this.config.chartType === 'funnel' ? 'selected' : ''}>Воронка (Funnel)</option>
            <option value="gantt" ${this.config.chartType === 'gantt' ? 'selected' : ''}>Диаграмма Ганта (Gantt)</option>
          </select>
          <button class="opentfc-btn opentfc-btn-export-png" title="Скачать как изображение">PNG</button>
        </div>
      </div>
    `;

    toolbar.querySelector('.opentfc-chart-type-selector')?.addEventListener('change', (e: any) => {
      this.config.chartType = e.target.value;
      if (this.currentColumns.length > 0 && this.currentRows.length > 0) {
        this.renderChart(this.currentColumns, this.currentRows);
      }
    });

    toolbar.querySelector('.opentfc-btn-export-png')?.addEventListener('click', () => {
      if (this.chartInstance) {
        const url = this.chartInstance.getDataURL({ type: 'png', pixelRatio: 2, backgroundColor: '#fff' });
        const a = document.createElement('a');
        a.href = url;
        a.download = `chart-${this.config.macroId || 'export'}.png`;
        a.click();
      }
    });
  }

  private parseInlineSource() {
    const sourceEl = this.container.querySelector('.opentfc-chart-source table');
    if (!sourceEl) return;

    const cols: ColumnDef[] = [];
    sourceEl.querySelectorAll('thead th, tr:first-child th').forEach((th, idx) => {
      cols.push({ index: idx, name: th.textContent?.trim() || `Col ${idx + 1}`, type: 'TEXT' });
    });

    const rows: RowData[] = [];
    sourceEl.querySelectorAll('tbody tr').forEach(tr => {
      const cells: any[] = [];
      tr.querySelectorAll('td, th').forEach((td, idx) => {
        cells.push({ val: td.textContent?.trim() || '', type: 'TEXT' });
      });
      rows.push({ cells });
    });

    if (cols.length > 0 && rows.length > 0) {
      this.renderChart(cols, rows);
    }
  }

  private bindUpstream() {
    document.addEventListener('opentfc:data-updated', (e: Event) => {
      const custom = e as CustomEvent;
      if (!this.config.sourceMacroId || custom.detail.macroId === this.config.sourceMacroId) {
        this.renderChart(custom.detail.columns, custom.detail.rows);
      }
    });
  }

  public renderChart(columns: ColumnDef[], rows: RowData[]) {
    if (!this.chartInstance || columns.length === 0 || rows.length === 0) return;

    this.currentColumns = columns;
    this.currentRows = rows;

    const chartType = this.config.chartType || 'column';
    const title = this.config.chartTitle || '';

    // 1. Gantt Chart (CT-02)
    if (chartType === 'gantt') {
      this.renderGanttChart(columns, rows, title);
      return;
    }

    // 2. Pie and Donut
    if (chartType === 'pie' || chartType === 'donut') {
      const pieData = rows.map(r => ({
        name: String(r.cells[0]?.val || ''),
        value: parseFloat(String(r.cells[1]?.val || '0').replace(/[^0-9.-]/g, '')) || 0
      }));

      this.chartInstance.setOption({
        title: { text: title, left: 'center' },
        tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
        legend: { orient: 'vertical', left: 'left' },
        series: [
          {
            type: 'pie',
            radius: chartType === 'donut' ? ['45%', '70%'] : '65%',
            data: pieData,
            label: { show: true, formatter: '{b}: {d}%' }
          }
        ]
      }, true);
      return;
    }

    // 3. Funnel
    if (chartType === 'funnel') {
      const funnelData = rows.map(r => ({
        name: String(r.cells[0]?.val || ''),
        value: parseFloat(String(r.cells[1]?.val || '0').replace(/[^0-9.-]/g, '')) || 0
      })).sort((a, b) => b.value - a.value);

      this.chartInstance.setOption({
        title: { text: title, left: 'center' },
        tooltip: { trigger: 'item' },
        series: [{ type: 'funnel', left: '10%', top: 60, bottom: 60, width: '80%', data: funnelData }]
      }, true);
      return;
    }

    // 4. Radar
    if (chartType === 'radar') {
      const indicators = rows.map(r => ({
        name: String(r.cells[0]?.val || ''),
        max: Math.max(...rows.map(ro => parseFloat(String(ro.cells[1]?.val || '0').replace(/[^0-9.-]/g, '')) || 100)) * 1.2
      }));

      const radarSeries: any[] = [];
      for (let c = 1; c < columns.length; c++) {
        radarSeries.push({
          value: rows.map(r => parseFloat(String(r.cells[c]?.val || '0').replace(/[^0-9.-]/g, '')) || 0),
          name: columns[c].name
        });
      }

      this.chartInstance.setOption({
        title: { text: title },
        tooltip: {},
        legend: { data: columns.slice(1).map(c => c.name) },
        radar: { indicator: indicators },
        series: [{ type: 'radar', data: radarSeries }]
      }, true);
      return;
    }

    // 5. Standard Grid Charts: Column, Stacked Column, Bar, Line, Area
    const xLabels = rows.map(r => String(r.cells[0]?.val || ''));
    const isStacked = chartType === 'stacked-column';
    const isBar = chartType === 'bar';
    const isLine = chartType === 'line' || chartType === 'area';

    const seriesList: any[] = [];
    for (let c = 1; c < columns.length; c++) {
      const col = columns[c];
      const seriesData = rows.map(r => {
        const raw = r.cells[c]?.val;
        return parseFloat(String(raw || '0').replace(/[^0-9.-]/g, '')) || 0;
      });

      seriesList.push({
        name: col.name,
        type: isBar ? 'bar' : isLine ? 'line' : 'bar',
        stack: isStacked ? 'total' : undefined,
        areaStyle: chartType === 'area' ? { opacity: 0.3 } : undefined,
        smooth: isLine,
        label: { show: false },
        data: seriesData
      });
    }

    this.chartInstance.setOption({
      title: { text: title },
      tooltip: { trigger: 'axis' },
      legend: { data: columns.slice(1).map(c => c.name) },
      grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
      xAxis: {
        type: isBar ? 'value' : 'category',
        data: isBar ? undefined : xLabels
      },
      yAxis: {
        type: isBar ? 'category' : 'value',
        data: isBar ? xLabels : undefined
      },
      series: seriesList
    }, true);
  }

  private renderGanttChart(columns: ColumnDef[], rows: RowData[], title: string) {
    if (!this.chartInstance) return;

    // Col 0: Task, Col 1: Start Date (yyyy-MM-dd), Col 2: End Date (yyyy-MM-dd)
    const tasks = rows.map(r => String(r.cells[0]?.val || 'Task'));
    const baseTimes: number[] = [];
    const durations: number[] = [];

    rows.forEach(r => {
      const startStr = String(r.cells[1]?.val || '2026-01-01');
      const endStr = String(r.cells[2]?.val || '2026-01-02');
      const startTime = new Date(startStr).getTime() || Date.now();
      const endTime = new Date(endStr).getTime() || (startTime + 86400000);
      baseTimes.push(startTime);
      durations.push(Math.max(86400000, endTime - startTime));
    });

    this.chartInstance.setOption({
      title: { text: title || 'Диаграмма Ганта' },
      tooltip: {
        formatter: (params: any) => {
          const idx = params.dataIndex;
          const start = new Date(baseTimes[idx]).toISOString().split('T')[0];
          const days = Math.round(durations[idx] / 86400000);
          return `<strong>${tasks[idx]}</strong><br>Начало: ${start}<br>Длительность: ${days} дн.`;
        }
      },
      grid: { left: '15%', right: '5%', bottom: '5%', top: '15%' },
      xAxis: {
        type: 'time'
      },
      yAxis: {
        type: 'category',
        data: tasks,
        inverse: true
      },
      series: [
        {
          name: 'Offset',
          type: 'bar',
          stack: 'gantt',
          itemStyle: { borderColor: 'transparent', color: 'transparent' },
          data: baseTimes
        },
        {
          name: 'Длительность',
          type: 'bar',
          stack: 'gantt',
          itemStyle: { color: '#0052cc', borderRadius: 3 },
          data: durations
        }
      ]
    }, true);
  }
}
