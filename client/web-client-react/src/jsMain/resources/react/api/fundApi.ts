import { PageResponse, PaginationParams, SortParams, FundSortField } from './types';
import { handleApiError } from './apiUtils';

export interface Fund {
    id: string;
    name: string;
}

export interface ListFundsParams {
    pagination?: PaginationParams;
    sort?: SortParams<FundSortField>;
}

export interface ListFundsResult {
    items: Fund[];
    total: number;
}

interface CreateFundRequest {
    name: string;
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

export async function listFunds(
    userId: string,
    params?: ListFundsParams
): Promise<ListFundsResult> {
    const queryParams = new URLSearchParams();

    if (params?.pagination) {
        queryParams.set('offset', params.pagination.offset.toString());
        queryParams.set('limit', params.pagination.limit.toString());
    }

    if (params?.sort) {
        queryParams.set('sort', params.sort.field);
        queryParams.set('order', params.sort.order);
    }

    const queryString = queryParams.toString();
    const url = `${getBaseUrl()}${BASE_PATH}/funds${queryString ? `?${queryString}` : ''}`;

    const response = await fetch(url, {
        headers: { 'FUNDS_USER_ID': userId }
    });
    if (!response.ok) await handleApiError(response, 'Failed to load funds');
    const data: PageResponse<Fund> = await response.json();
    return { items: data.items, total: data.total };
}

export async function createFund(userId: string, name: string): Promise<Fund> {
    const response = await fetch(`${getBaseUrl()}${BASE_PATH}/funds`, {
        method: 'POST',
        headers: {
            'FUNDS_USER_ID': userId,
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({ name } as CreateFundRequest)
    });
    if (!response.ok) await handleApiError(response, 'Failed to create fund');
    return response.json();
}

export async function deleteFund(userId: string, fundId: string): Promise<void> {
    const response = await fetch(`${getBaseUrl()}${BASE_PATH}/funds/${fundId}`, {
        method: 'DELETE',
        headers: { 'FUNDS_USER_ID': userId }
    });
    if (!response.ok) await handleApiError(response, 'Failed to delete fund');
}

export async function updateFund(userId: string, fundId: string, name: string): Promise<Fund> {
    const response = await fetch(`${getBaseUrl()}${BASE_PATH}/funds/${fundId}`, {
        method: 'PATCH',
        headers: {
            'FUNDS_USER_ID': userId,
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({ name })
    });
    if (!response.ok) await handleApiError(response, 'Failed to update fund');
    return response.json();
}
