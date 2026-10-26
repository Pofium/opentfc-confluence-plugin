import * as echarts from 'echarts';
import { ColumnDef, RowData } from '../core/table-model';

export class ChartRenderer {
  private container: HTMLElement;
  private canvas: HTMLElement | null;
  private chartInstance: echarts.ECharts | null = null;
  private config: any;

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
    this.bindUpstream();
  }

  private initToolbar() {
    const toolbar = this.container.querySelector('.opentfc-chart-toolbar');
    if (!toolbar) return;

    toolbar.innerHTML = `
      <div style="display:flex; justify-content: flex-end; padding: 6px 10px; gap: 6px;">
        <button class="opentfc-btn opentfc-btn-export-png" title="Скачать как изображение">Сохранить PNG</button>
      </div>
    `;

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

  private bindUpstream() {
    // Listen for data updates on page
    document.addEventListener('opentfc:data-updated', (e: Event) => {
      const custom = e as CustomEvent;
      if (!this.config.sourceMacroId || custom.detail.macroId === this.config.sourceMacroId) {
        this.renderChart(custom.detail.columns, custom.detail.rows);
      }
    });
  }

  public renderChart(columns: ColumnDef[], rows: RowData[]) {
    if (!this.chartInstance || columns.length === 0 || rows.length === 0) return;

    const chartType = this.config.chartType || 'column';
    const title = this.config.chartTitle || '';

    // X axis values from column 0
    const xLabels = rows.map(r => String(r.cells[0]?.val || ''));

    if (chartType === 'pie' || chartType === 'donut') {
      const pieData = rows.map(r => ({
        name: String(r.cells[0]?.val || ''),
        value: parseFloat(String(r.cells[1]?.val || '0').replace(/[^0-9.-]/g, '')) || 0
      }));

      this.chartInstance.setOption({
        title: { text: title, left: 'center' },
        tooltip: { trigger: 'item' },
        legend: { orient: 'vertical', left: 'left' },
        series: [
          {
            type: 'pie',
            radius: chartType === 'donut' ? ['40%', '70%'] : '65%',
            data: pieData,
            emphasis: {
              itemStyle: {
                shadowBlur: 10,
                shadowOffsetX: 0,
                shadowColor: 'rgba(0, 0, 0, 0.5)'
              }
            }
          }
        ]
      }, true);
      return;
    }

    // Default Series (Column / Bar / Line / Area)
    const seriesList: any[] = [];
    for (let c = 1; c < columns.length; c++) {
      const col = columns[c];
      const seriesData = rows.map(r => {
        const raw = r.cells[c]?.val;
        return parseFloat(String(raw || '0').replace(/[^0-9.-]/g, '')) || 0;
      });

      seriesList.push({
        name: col.name,
        type: chartType === 'bar' ? 'bar' : chartType === 'line' || chartType === 'area' ? 'line' : 'bar',
        areaStyle: chartType === 'area' ? {} : undefined,
        data: seriesData
      });
    }

    this.chartInstance.setOption({
      title: { text: title },
      tooltip: { trigger: 'axis' },
      legend: { data: columns.slice(1).map(c => c.name) },
      grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
      xAxis: {
        type: chartType === 'bar' ? 'value' : 'category',
        data: chartType === 'bar' ? undefined : xLabels
      },
      yAxis: {
        type: chartType === 'bar' ? 'category' : 'value',
        data: chartType === 'bar' ? xLabels : undefined
      },
      series: seriesList
    }, true);
  }
}
