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

export interface MetricQuery {
    id: string;
    metric: string;
    grouping?: GroupBy;
    filter?: ReportFilter;
}

export interface MetricsReportRequest {
    interval: ReportInterval;
    targetCurrency: string;
    queries: MetricQuery[];
}

export interface MetricSeriesGroup {
    groupKey: string;
    values: string[];
}

export interface MetricSeries {
    queryId: string;
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

export interface MetricsStreamBuckets {
    granularity: TimeGranularity;
    buckets: string[];
}

export interface MetricsStreamValue {
    queryId: string;
    bucket: string;
    values: Record<string, string>;
}

export interface MetricsStreamHandlers {
    onBuckets: (buckets: MetricsStreamBuckets) => void;
    onValue: (value: MetricsStreamValue) => void;
    onComplete: () => void;
    onError: (message: string) => void;
}

function dispatchSseFrame(frame: string, handlers: MetricsStreamHandlers): void {
    const lines = frame.split('\n');
    const event = lines.find(l => l.startsWith('event:'))?.slice('event:'.length).trim();
    const data = lines
        .filter(l => l.startsWith('data:'))
        .map(l => l.slice('data:'.length).trim())
        .join('\n');
    switch (event) {
        case 'buckets':
            handlers.onBuckets(JSON.parse(data));
            break;
        case 'value':
            handlers.onValue(JSON.parse(data));
            break;
        case 'complete':
            handlers.onComplete();
            break;
        case 'error':
            handlers.onError(JSON.parse(data).message ?? 'Metric resolution failed');
            break;
    }
}

export async function streamMetricsReport(
    userId: string,
    request: MetricsReportRequest,
    handlers: MetricsStreamHandlers,
    signal: AbortSignal
): Promise<void> {
    const response = await fetch(`${getBaseUrl()}${METRICS_PATH}/stream`, {
        method: 'POST',
        headers: { 'FUNDS_USER_ID': userId, 'Content-Type': 'application/json' },
        body: JSON.stringify(request),
        signal
    });
    if (!response.ok) await handleApiError(response, 'Failed to stream metrics report');
    if (!response.body) throw new Error('Streaming is not supported by this browser');
    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    let buffer = '';
    for (;;) {
        const { done, value } = await reader.read();
        if (done) break;
        buffer += decoder.decode(value, { stream: true }).replace(/\r\n/g, '\n');
        let frameEnd;
        while ((frameEnd = buffer.indexOf('\n\n')) >= 0) {
            const frame = buffer.slice(0, frameEnd);
            buffer = buffer.slice(frameEnd + 2);
            if (frame.trim()) dispatchSseFrame(frame, handlers);
        }
    }
}
