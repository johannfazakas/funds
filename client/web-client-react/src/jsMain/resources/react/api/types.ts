export interface PageResponse<T> {
    items: T[];
    total: number;
}

export type SortOrder = 'asc' | 'desc';

export interface PaginationParams {
    offset: number;
    limit: number;
}

export interface SortParams<T extends string> {
    field: T;
    order: SortOrder;
}

export type FundSortField = 'name';
export type AccountSortField = 'name' | 'unit';
