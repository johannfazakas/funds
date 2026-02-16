import { PageResponse, PaginationParams, SortParams, RecordSortField } from './types';
import { handleApiError } from './apiUtils';

export interface Record {
    type: 'CURRENCY' | 'INSTRUMENT';
    id: string;
    transactionId: string;
    dateTime: string;
    accountId: string;
    fundId: string;
    amount: string;
    unit: string;
    labels: string[];
}

export interface RecordFilter {
    accountId?: string;
    fundId?: string;
    unit?: string;
    label?: string;
    fromDate?: string;
    toDate?: string;
}

export interface ListRecordsParams {
    pagination?: PaginationParams;
    sort?: SortParams<RecordSortField>;
    filter?: RecordFilter;
}

export interface ListRecordsResult {
    items: Record[];
    total: number;
}

declare const window: Window & {
    FUNDS_CONFIG?: { fundServiceUrl?: string };
};

function getBaseUrl(): string {
    const url = window.FUNDS_CONFIG?.fundServiceUrl;
    if (!url) {
        throw new Error('FUNDS_CONFIG.fundServiceUrl is not configured');
    }
    return url;
}

const BASE_PATH = '/funds-api/fund/v1';

export async function listRecords(
    userId: string,
    params?: ListRecordsParams
): Promise<ListRecordsResult> {
    const queryParams = new URLSearchParams();

    if (params?.pagination) {
        queryParams.set('offset', params.pagination.offset.toString());
        queryParams.set('limit', params.pagination.limit.toString());
    }

    if (params?.sort) {
        queryParams.set('sort', params.sort.field);
        queryParams.set('order', params.sort.order);
    }

    if (params?.filter) {
        if (params.filter.accountId) {
            queryParams.set('accountId', params.filter.accountId);
        }
        if (params.filter.fundId) {
            queryParams.set('fundId', params.filter.fundId);
        }
        if (params.filter.unit) {
            queryParams.set('unit', params.filter.unit);
        }
        if (params.filter.label) {
            queryParams.set('label', params.filter.label);
        }
        if (params.filter.fromDate) {
            queryParams.set('fromDate', params.filter.fromDate);
        }
        if (params.filter.toDate) {
            queryParams.set('toDate', params.filter.toDate);
        }
    }

    const queryString = queryParams.toString();
    const url = `${getBaseUrl()}${BASE_PATH}/records${queryString ? `?${queryString}` : ''}`;

    const response = await fetch(url, {
        headers: { 'FUNDS_USER_ID': userId }
    });
    if (!response.ok) await handleApiError(response, 'Failed to load records');
    const data: PageResponse<Record> = await response.json();
    return { items: data.items, total: data.total };
}
