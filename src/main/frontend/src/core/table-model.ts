export type ColumnType = 'TEXT' | 'NUMBER' | 'DATE' | 'SELECT' | 'LINK' | 'BOOLEAN';

export interface ColumnDef {
  index: number;
  name: string;
  type: ColumnType;
  options?: string[];
  visible?: boolean;
}

export interface CellData {
  val: any;
  html?: string;
  type?: ColumnType;
}

export interface RowData {
  cells: CellData[];
}

export interface TableData {
  id: string;
  columns: ColumnDef[];
  rows: RowData[];
  totalRows: number;
}

export interface FilterState {
  globalSearch: string;
  columnFilters: Record<number, string | { min?: number; max?: number } | { from?: string; to?: string } | string[]>;
  sorts: Array<{ columnIndex: number; direction: 'asc' | 'desc' }>;
  hiddenColumns: number[];
  currentPage: number;
  pageSize: number | 'all';
}
