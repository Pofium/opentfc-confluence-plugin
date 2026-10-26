import './css/opentfc.css';
import { TableFilterComponent } from './filter/table-filter';
import { ChartRenderer } from './chart/chart-renderer';

export class OpenTFCApp {
  public static init() {
    // 1. Initialize Table Filters
    document.querySelectorAll<HTMLElement>('.opentfc-table-filter').forEach(el => {
      if (!el.dataset.opentfcInitialized) {
        new TableFilterComponent(el);
        el.dataset.opentfcInitialized = 'true';
      }
    });

    // 2. Initialize Charts
    document.querySelectorAll<HTMLElement>('.opentfc-chart').forEach(el => {
      if (!el.dataset.opentfcInitialized) {
        new ChartRenderer(el);
        el.dataset.opentfcInitialized = 'true';
      }
    });
  }
}

// Auto-boot on DOM ready
if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', () => OpenTFCApp.init());
} else {
  OpenTFCApp.init();
}

export default OpenTFCApp;
