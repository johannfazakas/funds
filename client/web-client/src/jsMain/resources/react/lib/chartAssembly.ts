import { useEffect, useState } from 'react';
import { GroupBy, MetricsReport, MetricsStreamBuckets, MetricsStreamValue, MetricUnit, TimeGranularity } from '../api/analyticsApi';
import { Fund } from '../api/fundApi';
import { Account } from '../api/accountApi';
import { ChartLine, MultiSeriesChartDataPoint } from '../components/MultiSeriesChart';

const metricLabels: Record<string, string> = {
    BALANCE: 'Balance',
    NET_CHANGE: 'Net Change',
    TOTAL_INVESTMENT: 'Total Investment',
    CURRENT_INVESTMENT: 'Current Investment',
    TOTAL_INSTRUMENT_VALUE: 'Total Instrument Value',
    CURRENCY_VALUE: 'Currency Value',
    TOTAL_PROFIT: 'Total Profit',
    CURRENT_PROFIT: 'Current Profit',
    TOTAL_INTEREST_RATE: 'Total Interest Rate',
    CURRENT_INTEREST_RATE: 'Current Interest Rate',
};

export function metricLabel(name: string): string {
    return metricLabels[name] ?? name;
}

export const groupByLabels: Record<string, string> = {
    FINANCIAL_UNIT: 'Financial Unit',
    ACCOUNT: 'Account',
    FUND: 'Fund',
    CATEGORY: 'Category',
};

export const granularityOptions: { value: TimeGranularity; label: string }[] = [
    { value: 'DAILY', label: 'Daily' },
    { value: 'WEEKLY', label: 'Weekly' },
    { value: 'MONTHLY', label: 'Monthly' },
    { value: 'YEARLY', label: 'Yearly' },
];

export const groupByOptions: { value: string; label: string }[] = [
    { value: 'NONE', label: 'None' },
    ...Object.entries(groupByLabels).map(([value, label]) => ({ value, label })),
];

export const lookbackUnitOptions: { value: 'DAY' | 'WEEK' | 'MONTH' | 'YEAR'; label: string }[] = [
    { value: 'DAY', label: 'Days' },
    { value: 'WEEK', label: 'Weeks' },
    { value: 'MONTH', label: 'Months' },
    { value: 'YEAR', label: 'Years' },
];

// validated categorical palettes (muted hues; fixed slot order maximizes colorblind separation)
const QUERY_HUES_LIGHT = [
    '#2a78d6', '#1baf7a', '#eda100', '#e34948',
    '#eb6834', '#e87ba4', '#008300', '#4a3aa7',
];
const QUERY_HUES_DARK = [
    '#3987e5', '#199e70', '#c98500', '#e66767',
    '#d95926', '#d55181', '#008300', '#9085e9',
];

export function queryHue(index: number, darkTheme: boolean): string {
    const hues = darkTheme ? QUERY_HUES_DARK : QUERY_HUES_LIGHT;
    return hues[index % hues.length];
}

export function useIsDarkTheme(): boolean {
    const [dark, setDark] = useState(() => document.documentElement.classList.contains('dark'));
    useEffect(() => {
        const observer = new MutationObserver(() =>
            setDark(document.documentElement.classList.contains('dark')));
        observer.observe(document.documentElement, { attributes: true, attributeFilter: ['class'] });
        return () => observer.disconnect();
    }, []);
    return dark;
}

function shadeColor(hex: string, factor: number): string {
    const channel = (offset: number) => {
        const value = parseInt(hex.slice(offset, offset + 2), 16);
        const shaded = factor >= 0
            ? Math.round(value + (255 - value) * factor)
            : Math.round(value * (1 + factor));
        return Math.min(255, Math.max(0, shaded)).toString(16).padStart(2, '0');
    };
    return `#${channel(1)}${channel(3)}${channel(5)}`;
}

function groupShade(hue: string, groupIndex: number, groupCount: number): string {
    if (groupCount <= 1) return hue;
    // spread group lines from a darker to a lighter variant of the query hue
    const spread = Math.min(0.75, 0.25 * (groupCount - 1));
    const factor = -spread / 2 + (spread * groupIndex) / (groupCount - 1);
    return shadeColor(hue, factor);
}

export function formatBucketLabel(dateTime: string, granularity: TimeGranularity): string {
    const date = new Date(dateTime);
    switch (granularity) {
        case 'DAILY':
            return date.toLocaleDateString(undefined, { month: 'short', day: 'numeric' });
        case 'WEEKLY':
            return date.toLocaleDateString(undefined, { month: 'short', day: 'numeric' });
        case 'MONTHLY':
            return date.toLocaleDateString(undefined, { year: 'numeric', month: 'short' });
        case 'YEARLY':
            return date.getFullYear().toString();
    }
}

export function formatValue(rawValue: string | undefined, unit: string): number {
    const value = rawValue !== undefined ? parseFloat(rawValue) : 0;
    return unit === 'PERCENTAGE' ? Math.round(value * 100) / 100 : Math.round(value);
}

export interface ChartQueryView {
    id: string;
    label: string;
    metric: string;
    grouping: GroupBy | null;
    fundIds: string[];
    unitValues: string[];
    visible?: boolean;
}

export function emptyStreamReport(
    buckets: MetricsStreamBuckets,
    queries: ChartQueryView[],
    unitOf: (metric: string) => MetricUnit,
    targetCurrency: string
): MetricsReport {
    return {
        granularity: buckets.granularity,
        buckets: buckets.buckets,
        series: queries.map(query => ({
            queryId: query.id,
            metric: query.metric,
            unit: unitOf(query.metric),
            currency: unitOf(query.metric) === 'CURRENCY' ? targetCurrency : null,
            groups: [],
        })),
    };
}

export function mergeStreamValue(report: MetricsReport, value: MetricsStreamValue): MetricsReport {
    const bucketIndex = report.buckets.indexOf(value.bucket);
    if (bucketIndex < 0) return report;
    return {
        ...report,
        series: report.series.map(series => {
            if (series.queryId !== value.queryId) return series;
            const groups = series.groups.map(g => ({ ...g, values: [...g.values] }));
            for (const [groupKey, groupValue] of Object.entries(value.values)) {
                let group = groups.find(g => g.groupKey === groupKey);
                if (!group) {
                    group = { groupKey, values: report.buckets.map(() => '0') };
                    groups.push(group);
                    groups.sort((a, b) => a.groupKey.localeCompare(b.groupKey));
                }
                group.values[bucketIndex] = groupValue;
            }
            return { ...series, groups };
        }),
    };
}

export function makeGroupNameResolver(funds: Fund[], accounts: Account[]) {
    return (grouping: GroupBy | null, key: string): string => {
        if (key === 'UNGROUPED') return 'Total';
        if (grouping === 'FUND') {
            const fund = funds.find(f => f.id === key);
            return fund ? fund.name : key;
        }
        if (grouping === 'ACCOUNT') {
            const account = accounts.find(a => a.id === key);
            return account ? account.name : key;
        }
        return key;
    };
}

export function autoQueryLabel(
    query: { metric: string; grouping: GroupBy | null; fundIds: string[]; unitValues: string[] },
    fundName: (id: string) => string
): string {
    const filterParts: string[] = [];
    if (query.fundIds.length > 0) {
        const names = query.fundIds.map(fundName);
        filterParts.push(names.length <= 2 ? names.join(', ') : `${names.length} funds`);
    }
    if (query.unitValues.length > 0) {
        filterParts.push(query.unitValues.length <= 2 ? query.unitValues.join(', ') : `${query.unitValues.length} units`);
    }
    const filterSuffix = filterParts.length > 0 ? ` (${filterParts.join(' · ')})` : '';
    const groupingSuffix = query.grouping ? ` by ${groupByLabels[query.grouping] ?? query.grouping}` : '';
    return `${metricLabel(query.metric)}${groupingSuffix}${filterSuffix}`;
}

export function buildChartModel(
    report: MetricsReport,
    queries: ChartQueryView[],
    funds: Fund[],
    accounts: Account[],
    darkTheme: boolean
): { data: MultiSeriesChartDataPoint[]; lines: ChartLine[] } {
    const resolveGroupName = makeGroupNameResolver(funds, accounts);
    const lines: ChartLine[] = [];
    const data: MultiSeriesChartDataPoint[] = report.buckets.map(bucket => ({
        label: formatBucketLabel(bucket, report.granularity),
    }));
    queries.forEach((query, queryIndex) => {
        if (query.visible === false) return;
        const series = report.series.find(s => s.queryId === query.id);
        if (!series) return;
        const hue = queryHue(queryIndex, darkTheme);
        const label = query.label;
        const groups = [...series.groups].sort((a, b) => a.groupKey.localeCompare(b.groupKey));
        groups.forEach((group, groupIndex) => {
            const lineKey = `${query.id}:${group.groupKey}`;
            const groupName = resolveGroupName(query.grouping, group.groupKey);
            lines.push({
                key: lineKey,
                name: group.groupKey === 'UNGROUPED' ? label : `${label} — ${groupName}`,
                color: groupShade(hue, groupIndex, groups.length),
                unit: series.unit,
            });
            group.values.forEach((value, bucketIndex) => {
                data[bucketIndex][lineKey] = formatValue(value, series.unit);
            });
        });
    });
    return { data, lines };
}
