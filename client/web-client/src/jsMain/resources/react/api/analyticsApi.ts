import { handleApiError } from './apiUtils';

export type TimeGranularity = 'DAILY' | 'WEEKLY' | 'MONTHLY' | 'YEARLY';
export type GroupBy = 'FINANCIAL_UNIT' | 'ACCOUNT' | 'FUND' | 'CATEGORY';
export type MetricUnit = 'CURRENCY' | 'PERCENTAGE';

export interface MetricInfo {
    metric: string;
    unit: MetricUnit;
}

export interface ReportInterval {
    granularity: TimeGranularity;
    from: string;
    to: string;
}

export interface ReportFilter {
    fundIds?: string[];
    units?: { type: string; value: string }[];
}

export interface MetricsReportRequest {
    metrics: string[];
    interval: ReportInterval;
    filter?: ReportFilter;
    targetCurrency: string;
    grouping?: GroupBy;
}

export interface MetricSeriesGroup {
    groupKey: string;
    values: string[];
}

export interface MetricSeries {
    metric: string;
    unit: MetricUnit;
    currency?: string | null;
    groups: MetricSeriesGroup[];
}

export interface MetricsReport {
    granularity: TimeGranularity;
    buckets: string[];
    series: MetricSeries[];
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

const METRICS_PATH = '/funds-api/analytics/v1/metrics';

export async function listMetrics(): Promise<MetricInfo[]> {
    const response = await fetch(`${getBaseUrl()}${METRICS_PATH}`);
    if (!response.ok) await handleApiError(response, 'Failed to load metrics');
    return response.json();
}

export async function getMetricsReport(
    userId: string,
    request: MetricsReportRequest
): Promise<MetricsReport> {
    const response = await fetch(`${getBaseUrl()}${METRICS_PATH}`, {
        method: 'POST',
        headers: { 'FUNDS_USER_ID': userId, 'Content-Type': 'application/json' },
        body: JSON.stringify(request)
    });
    if (!response.ok) await handleApiError(response, 'Failed to load metrics report');
    return response.json();
}
