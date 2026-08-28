import { handleApiError } from './apiUtils';
import { GroupBy, MetricQuery, ReportFilter, TimeGranularity } from './analyticsApi';

export type LookbackUnit = 'DAY' | 'WEEK' | 'MONTH' | 'YEAR';

export interface DashboardLookback {
    amount: number;
    unit: LookbackUnit;
}

export interface DashboardQuery {
    id: string;
    label: string;
    metric: string;
    grouping?: GroupBy;
    filter?: ReportFilter;
}

export interface DashboardChart {
    id: string;
    name: string;
    position: number;
    queries: DashboardQuery[];
}

export function toMetricQuery(query: DashboardQuery): MetricQuery {
    return {
        id: query.id,
        metric: query.metric,
        grouping: query.grouping,
        filter: query.filter,
    };
}

export interface Dashboard {
    id: string;
    name: string;
    position: number;
    defaultGranularity: TimeGranularity;
    defaultLookback: DashboardLookback;
    defaultTargetCurrency: string;
    charts: DashboardChart[];
}

export interface CreateDashboardChart {
    id?: string;
    name: string;
    queries: DashboardQuery[];
}

export interface UpdateDashboardChart {
    name: string;
    queries: DashboardQuery[];
}

export interface CreateDashboard {
    name: string;
    defaultGranularity: TimeGranularity;
    defaultLookback: DashboardLookback;
    defaultTargetCurrency: string;
    charts?: CreateDashboardChart[];
}

export interface UpdateDashboard {
    name: string;
    defaultGranularity: TimeGranularity;
    defaultLookback: DashboardLookback;
    defaultTargetCurrency: string;
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

const DASHBOARDS_PATH = '/funds-api/analytics/v1/dashboards';

function headers(userId: string): Record<string, string> {
    return { 'FUNDS_USER_ID': userId, 'Content-Type': 'application/json' };
}

export async function listDashboards(userId: string): Promise<Dashboard[]> {
    const response = await fetch(`${getBaseUrl()}${DASHBOARDS_PATH}`, { headers: headers(userId) });
    if (!response.ok) await handleApiError(response, 'Failed to load dashboards');
    return response.json();
}

export async function getDashboard(userId: string, dashboardId: string): Promise<Dashboard> {
    const response = await fetch(`${getBaseUrl()}${DASHBOARDS_PATH}/${dashboardId}`, { headers: headers(userId) });
    if (!response.ok) await handleApiError(response, 'Failed to load dashboard');
    return response.json();
}

export async function createDashboard(userId: string, write: CreateDashboard): Promise<Dashboard> {
    const response = await fetch(`${getBaseUrl()}${DASHBOARDS_PATH}`, {
        method: 'POST',
        headers: headers(userId),
        body: JSON.stringify(write),
    });
    if (!response.ok) await handleApiError(response, 'Failed to create dashboard');
    return response.json();
}

export async function updateDashboard(userId: string, dashboardId: string, write: UpdateDashboard): Promise<Dashboard> {
    const response = await fetch(`${getBaseUrl()}${DASHBOARDS_PATH}/${dashboardId}`, {
        method: 'PUT',
        headers: headers(userId),
        body: JSON.stringify(write),
    });
    if (!response.ok) await handleApiError(response, 'Failed to update dashboard');
    return response.json();
}

export async function reorderDashboards(userId: string, dashboardIds: string[]): Promise<Dashboard[]> {
    const response = await fetch(`${getBaseUrl()}${DASHBOARDS_PATH}/positions`, {
        method: 'PUT',
        headers: headers(userId),
        body: JSON.stringify({ dashboardIds }),
    });
    if (!response.ok) await handleApiError(response, 'Failed to reorder dashboards');
    return response.json();
}

export async function deleteDashboard(userId: string, dashboardId: string): Promise<void> {
    const response = await fetch(`${getBaseUrl()}${DASHBOARDS_PATH}/${dashboardId}`, {
        method: 'DELETE',
        headers: headers(userId),
    });
    if (!response.ok) await handleApiError(response, 'Failed to delete dashboard');
}

export async function appendDashboardChart(
    userId: string,
    dashboardId: string,
    chart: CreateDashboardChart
): Promise<DashboardChart> {
    const response = await fetch(`${getBaseUrl()}${DASHBOARDS_PATH}/${dashboardId}/charts`, {
        method: 'POST',
        headers: headers(userId),
        body: JSON.stringify(chart),
    });
    if (!response.ok) await handleApiError(response, 'Failed to add chart to dashboard');
    return response.json();
}

export async function updateDashboardChart(
    userId: string,
    dashboardId: string,
    chartId: string,
    write: UpdateDashboardChart
): Promise<DashboardChart> {
    const response = await fetch(`${getBaseUrl()}${DASHBOARDS_PATH}/${dashboardId}/charts/${chartId}`, {
        method: 'PUT',
        headers: headers(userId),
        body: JSON.stringify(write),
    });
    if (!response.ok) await handleApiError(response, 'Failed to update chart');
    return response.json();
}

export async function deleteDashboardChart(userId: string, dashboardId: string, chartId: string): Promise<void> {
    const response = await fetch(`${getBaseUrl()}${DASHBOARDS_PATH}/${dashboardId}/charts/${chartId}`, {
        method: 'DELETE',
        headers: headers(userId),
    });
    if (!response.ok) await handleApiError(response, 'Failed to delete chart');
}

export async function reorderDashboardCharts(
    userId: string,
    dashboardId: string,
    chartIds: string[]
): Promise<DashboardChart[]> {
    const response = await fetch(`${getBaseUrl()}${DASHBOARDS_PATH}/${dashboardId}/charts/positions`, {
        method: 'PUT',
        headers: headers(userId),
        body: JSON.stringify({ chartIds }),
    });
    if (!response.ok) await handleApiError(response, 'Failed to reorder charts');
    return response.json();
}

export const DASHBOARDS_CHANGED_EVENT = 'funds:dashboards-changed';

export function notifyDashboardsChanged(): void {
    window.dispatchEvent(new Event(DASHBOARDS_CHANGED_EVENT));
}
