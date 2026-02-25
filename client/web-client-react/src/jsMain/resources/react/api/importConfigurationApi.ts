import { PageResponse, PaginationParams, SortParams } from './types';
import { handleApiError } from './apiUtils';

export type ImportConfigurationSortField = 'NAME' | 'CREATED_AT';

export interface AccountMatcher {
    type: string;
    importAccountNames: string[];
    accountName?: string;
}

export interface FundMatcher {
    type: string;
    fundName: string;
    importAccountNames?: string[];
    importLabels?: string[];
    initialFundName?: string;
}

export interface ExchangeMatcher {
    type: string;
    label: string;
}

export interface LabelMatcher {
    importLabels: string[];
    label: string;
}

export interface ImportConfiguration {
    importConfigurationId: string;
    name: string;
    accountMatchers: AccountMatcher[];
    fundMatchers: FundMatcher[];
    exchangeMatchers: ExchangeMatcher[];
    labelMatchers: LabelMatcher[];
    createdAt: string;
}

export interface CreateImportConfigurationRequest {
    name: string;
    accountMatchers?: AccountMatcher[];
    fundMatchers?: FundMatcher[];
    exchangeMatchers?: ExchangeMatcher[];
    labelMatchers?: LabelMatcher[];
}

export interface UpdateImportConfigurationRequest {
    name?: string;
    accountMatchers?: AccountMatcher[];
    fundMatchers?: FundMatcher[];
    exchangeMatchers?: ExchangeMatcher[];
    labelMatchers?: LabelMatcher[];
}

export interface ListImportConfigurationsParams {
    pagination?: PaginationParams;
    sort?: SortParams<ImportConfigurationSortField>;
}

export interface ListImportConfigurationsResult {
    items: ImportConfiguration[];
    total: number;
}

declare const window: Window & {
    FUNDS_CONFIG?: { importServiceUrl?: string };
};

function getBaseUrl(): string {
    const url = window.FUNDS_CONFIG?.importServiceUrl;
    if (!url) {
        throw new Error('FUNDS_CONFIG.importServiceUrl is not configured');
    }
    return url;
}

const BASE_PATH = '/funds-api/import/v1';

export async function listImportConfigurations(
    userId: string,
    params?: ListImportConfigurationsParams
): Promise<ListImportConfigurationsResult> {
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
    const url = `${getBaseUrl()}${BASE_PATH}/import-configurations${queryString ? `?${queryString}` : ''}`;

    const response = await fetch(url, {
        headers: { 'FUNDS_USER_ID': userId }
    });
    if (!response.ok) await handleApiError(response, 'Failed to load import configurations');
    const data: PageResponse<ImportConfiguration> = await response.json();
    return { items: data.items, total: data.total };
}

export async function createImportConfiguration(
    userId: string,
    request: CreateImportConfigurationRequest
): Promise<ImportConfiguration> {
    const response = await fetch(`${getBaseUrl()}${BASE_PATH}/import-configurations`, {
        method: 'POST',
        headers: {
            'FUNDS_USER_ID': userId,
            'Content-Type': 'application/json'
        },
        body: JSON.stringify(request)
    });
    if (!response.ok) await handleApiError(response, 'Failed to create import configuration');
    return response.json();
}

export async function getImportConfiguration(
    userId: string,
    importConfigurationId: string
): Promise<ImportConfiguration> {
    const response = await fetch(
        `${getBaseUrl()}${BASE_PATH}/import-configurations/${importConfigurationId}`,
        {
            headers: { 'FUNDS_USER_ID': userId }
        }
    );
    if (!response.ok) await handleApiError(response, 'Failed to get import configuration');
    return response.json();
}

export async function updateImportConfiguration(
    userId: string,
    importConfigurationId: string,
    request: UpdateImportConfigurationRequest
): Promise<ImportConfiguration> {
    const response = await fetch(
        `${getBaseUrl()}${BASE_PATH}/import-configurations/${importConfigurationId}`,
        {
            method: 'PUT',
            headers: {
                'FUNDS_USER_ID': userId,
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(request)
        }
    );
    if (!response.ok) await handleApiError(response, 'Failed to update import configuration');
    return response.json();
}

export async function deleteImportConfiguration(
    userId: string,
    importConfigurationId: string
): Promise<void> {
    const response = await fetch(
        `${getBaseUrl()}${BASE_PATH}/import-configurations/${importConfigurationId}`,
        {
            method: 'DELETE',
            headers: { 'FUNDS_USER_ID': userId }
        }
    );
    if (!response.ok) await handleApiError(response, 'Failed to delete import configuration');
}
