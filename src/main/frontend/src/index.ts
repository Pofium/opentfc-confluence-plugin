import './css/opentfc.css';
import { TableFilterComponent } from './filter/table-filter';
import { ChartRenderer } from './chart/chart-renderer';
import { PivotRenderer } from './pivot/pivot-renderer';
import { TransformerRunner } from './transformer/transformer-runner';
import { SpreadsheetManager } from './spreadsheet/spreadsheet-manager';

declare const AJS: any;

export class OpenTFCApp {
  public static init() {
    // 1. Table Filters
    document.querySelectorAll<HTMLElement>('.opentfc-table-filter').forEach(el => {
      if (!el.dataset.opentfcInitialized) {
        new TableFilterComponent(el);
        el.dataset.opentfcInitialized = 'true';
      }
    });

    // 2. Charts
    document.querySelectorAll<HTMLElement>('.opentfc-chart').forEach(el => {
      if (!el.dataset.opentfcInitialized) {
        new ChartRenderer(el);
        el.dataset.opentfcInitialized = 'true';
      }
    });

    // 3. Pivot Tables
    document.querySelectorAll<HTMLElement>('.opentfc-pivot').forEach(el => {
      if (!el.dataset.opentfcInitialized) {
        new PivotRenderer(el);
        el.dataset.opentfcInitialized = 'true';
      }
    });

    // 4. Table Transformers
    document.querySelectorAll<HTMLElement>('.opentfc-transformer').forEach(el => {
      if (!el.dataset.opentfcInitialized) {
        new TransformerRunner(el);
        el.dataset.opentfcInitialized = 'true';
      }
    });

    // 5. Spreadsheets
    document.querySelectorAll<HTMLElement>('.opentfc-spreadsheet').forEach(el => {
      if (!el.dataset.opentfcInitialized) {
        new SpreadsheetManager(el);
        el.dataset.opentfcInitialized = 'true';
      }
    });
  }
}

// Expose globally for Confluence runtime
(window as any).OpenTFCApp = OpenTFCApp;
(window as any).OpenTFC = OpenTFCApp;

const boot = () => {
  OpenTFCApp.init();
};

// Confluence AJS integration
if (typeof AJS !== 'undefined') {
  if (typeof AJS.toInit === 'function') {
    AJS.toInit(boot);
  }
  if (typeof AJS.bind === 'function') {
    AJS.bind('page-show', boot);
    AJS.bind('content-rendered', boot);
  }
}

// Standard browser DOM integration
if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', boot);
} else {
  boot();
}

export default OpenTFCApp;
