import { handleApiError } from './apiUtils';

export type TimeGranularity = 'DAILY' | 'WEEKLY' | 'MONTHLY' | 'YEARLY';
export type GroupBy = 'FINANCIAL_UNIT' | 'ACCOUNT' | 'FUND' | 'CATEGORY';

export interface ReportRequest {
    granularity: TimeGranularity;
    from: string;
    to: string;
    fundIds?: string[];
    units?: { type: string; value: string }[];
    targetCurrency: string;
    groupBy?: GroupBy;
}

export interface GroupBucket<T = string> {
    groupKey: string | null;
    value: T;
}

export interface ReportBucket<T = string> {
    dateTime: string;
    groups: GroupBucket<T>[];
}

export interface ReportResponse<T = string> {
    granularity: TimeGranularity;
    buckets: ReportBucket<T>[];
}

export interface PerformanceData {
    totalInvestment: string;
    currentInvestment: string;
    totalProfit: string;
    currentProfit: string;
    totalInstrumentValue: string;
    currencyValue: string;
}

export interface InterestRateData {
    totalInterestRate: string;
    currentInterestRate: string;
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
        headers: { 'FUNDS_USER_ID': userId, 'Content-Type': 'application/json' },
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
        headers: { 'FUNDS_USER_ID': userId, 'Content-Type': 'application/json' },
        body: JSON.stringify(request)
    });
    if (!response.ok) await handleApiError(response, 'Failed to load net change report');
    return response.json();
}

export async function getPerformanceReport(
    userId: string,
    request: ReportRequest
): Promise<ReportResponse<PerformanceData>> {
    const response = await fetch(`${getBaseUrl()}${BASE_PATH}/reports/performance`, {
        method: 'POST',
        headers: { 'FUNDS_USER_ID': userId, 'Content-Type': 'application/json' },
        body: JSON.stringify(request)
    });
    if (!response.ok) await handleApiError(response, 'Failed to load performance report');
    return response.json();
}

export async function getInterestRateReport(
    userId: string,
    request: ReportRequest
): Promise<ReportResponse<InterestRateData>> {
    const response = await fetch(`${getBaseUrl()}${BASE_PATH}/reports/interest-rate`, {
        method: 'POST',
        headers: { 'FUNDS_USER_ID': userId, 'Content-Type': 'application/json' },
        body: JSON.stringify(request)
    });
    if (!response.ok) await handleApiError(response, 'Failed to load interest rate report');
    return response.json();
}

export function extractMetric<T>(
    report: ReportResponse<T>,
    metric: keyof T,
): ReportResponse {
    return {
        granularity: report.granularity,
        buckets: report.buckets.map(bucket => ({
            dateTime: bucket.dateTime,
            groups: bucket.groups.map(group => ({
                groupKey: group.groupKey,
                value: String(group.value[metric]),
            })),
        })),
    };
}
