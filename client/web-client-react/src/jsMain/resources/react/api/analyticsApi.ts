import { handleApiError } from './apiUtils';

export type TimeGranularity = 'DAILY' | 'WEEKLY' | 'MONTHLY' | 'YEARLY';

export interface ValueReportRequest {
    granularity: TimeGranularity;
    from: string;
    to: string;
    fundIds?: string[];
    units?: object[];
}

export interface ValueBucket {
    dateTime: string;
    netChange: string;
    balance: string;
}

export interface ValueReportResponse {
    granularity: TimeGranularity;
    buckets: ValueBucket[];
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

export async function getValueReport(
    userId: string,
    request: ValueReportRequest
): Promise<ValueReportResponse> {
    const response = await fetch(`${getBaseUrl()}${BASE_PATH}/records/value-report`, {
        method: 'POST',
        headers: {
            'FUNDS_USER_ID': userId,
            'Content-Type': 'application/json'
        },
        body: JSON.stringify(request)
    });
    if (!response.ok) await handleApiError(response, 'Failed to load value report');
    return response.json();
}
