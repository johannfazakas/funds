import { handleApiError } from './apiUtils';

export type TimeGranularity = 'DAILY' | 'WEEKLY' | 'MONTHLY' | 'YEARLY';

export interface ReportRequest {
    granularity: TimeGranularity;
    from: string;
    to: string;
    fundIds?: string[];
    units?: { type: string; value: string }[];
    targetCurrency: string;
}

export interface ReportBucket {
    dateTime: string;
    value: string;
}

export interface ReportResponse {
    granularity: TimeGranularity;
    buckets: ReportBucket[];
}

declare const window: Window & {
    FUNDS_CONFIG?: { analyticsServiceUrl?: string };
};

function getBaseUrl(): string {
    const url = window.FUNDS_CONFIG?.analyticsServiceUrl;
    if (!url) {
        throw new Error('FUNDS_CONFIG.analyticsServiceUrl is not configured');
    }
    return url;
}

const BASE_PATH = '/funds-api/analytics/v1';

export async function getBalanceReport(
    userId: string,
    request: ReportRequest
): Promise<ReportResponse> {
    const response = await fetch(`${getBaseUrl()}${BASE_PATH}/reports/balance`, {
        method: 'POST',
        headers: {
            'FUNDS_USER_ID': userId,
            'Content-Type': 'application/json'
        },
        body: JSON.stringify(request)
    });
    if (!response.ok) await handleApiError(response, 'Failed to load balance report');
    return response.json();
}

export async function getNetChangeReport(
    userId: string,
    request: ReportRequest
): Promise<ReportResponse> {
    const response = await fetch(`${getBaseUrl()}${BASE_PATH}/reports/net-change`, {
        method: 'POST',
        headers: {
            'FUNDS_USER_ID': userId,
            'Content-Type': 'application/json'
        },
        body: JSON.stringify(request)
    });
    if (!response.ok) await handleApiError(response, 'Failed to load net change report');
    return response.json();
}
