import { useEffect, useRef, useState } from 'react';
import { listMetrics, getMetricsReport, TimeGranularity, GroupBy, MetricInfo, MetricsReport } from '../api/analyticsApi';
import { listFunds, Fund } from '../api/fundApi';
import { listAccounts, Account } from '../api/accountApi';
import MultiSeriesChart, { ChartLine, MultiSeriesChartDataPoint } from '../components/MultiSeriesChart';
import MetricQueryEditor, { QueryState } from '../components/MetricQueryEditor';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '../components/ui/select';
import { MultiSelectOption, MultiSelectGroup } from '../components/ui/multi-select';
import { Button } from '../components/ui/button';
import { Card, CardContent } from '../components/ui/card';
import { DatePicker } from '../components/ui/date-picker';
import { Loader2, Plus } from 'lucide-react';

interface AnalyticsPageProps {
    userId: string;
}

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

function metricLabel(name: string): string {
    return metricLabels[name] ?? name;
}

const granularityOptions: { value: TimeGranularity; label: string }[] = [
    { value: 'DAILY', label: 'Daily' },
    { value: 'WEEKLY', label: 'Weekly' },
    { value: 'MONTHLY', label: 'Monthly' },
    { value: 'YEARLY', label: 'Yearly' },
];

const groupByOptions: { value: string; label: string }[] = [
    { value: 'NONE', label: 'None' },
    { value: 'FINANCIAL_UNIT', label: 'Financial Unit' },
    { value: 'ACCOUNT', label: 'Account' },
    { value: 'FUND', label: 'Fund' },
    { value: 'CATEGORY', label: 'Category' },
];

const QUERY_HUES = [
    '#2563eb', '#dc2626', '#16a34a', '#d97706', '#7c3aed',
    '#0891b2', '#db2777', '#65a30d', '#ea580c', '#6366f1',
    '#0d9488', '#ca8a04',
];

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

function generateQueryId(): string {
    return `q-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 8)}`;
}

function defaultQuery(): QueryState {
    return {
        id: generateQueryId(),
        metric: 'BALANCE',
        groupBy: 'NONE',
        fundIds: [],
        units: [],
        visible: true,
        collapsed: false,
    };
}

function queryLetter(index: number): string {
    return index < 26 ? String.fromCharCode(65 + index) : `Q${index + 1}`;
}

function defaultFromDate(): string {
    const date = new Date();
    date.setFullYear(date.getFullYear() - 1);
    return date.toISOString().slice(0, 10);
}

function defaultToDate(): string {
    const date = new Date();
    date.setDate(date.getDate() - 1);
    return date.toISOString().slice(0, 10);
}

const STORAGE_KEY = 'analytics.page.state';

interface PersistedState {
    granularity: TimeGranularity;
    fromDate: string;
    toDate: string;
    targetCurrency: string;
    queries: QueryState[];
}

function loadPersistedState(): PersistedState | null {
    try {
        const raw = localStorage.getItem(STORAGE_KEY);
        if (!raw) return null;
        const state = JSON.parse(raw) as PersistedState;
        if (!Array.isArray(state.queries) || state.queries.length === 0) return null;
        if (state.queries.some(q => !q.id || !q.metric)) return null;
        return state;
    } catch {
        return null;
    }
}

function formatBucketLabel(dateTime: string, granularity: TimeGranularity): string {
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

function formatValue(rawValue: string | undefined, unit: string): number {
    const value = rawValue !== undefined ? parseFloat(rawValue) : 0;
    return unit === 'PERCENTAGE' ? Math.round(value * 100) / 100 : Math.round(value);
}

function toLocalDateTime(dateStr: string): string {
    return `${dateStr}T00:00:00`;
}

function shiftDay(dateStr: string, days: number): string {
    const date = new Date(`${dateStr}T00:00:00`);
    date.setDate(date.getDate() + days);
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
}

function AnalyticsPage({ userId }: AnalyticsPageProps) {
    const persisted = useRef(loadPersistedState()).current;

    const [report, setReport] = useState<MetricsReport | null>(null);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [metrics, setMetrics] = useState<MetricInfo[]>([]);
    const [granularity, setGranularity] = useState<TimeGranularity>(persisted?.granularity ?? 'MONTHLY');
    const [fromDate, setFromDate] = useState(persisted?.fromDate ?? defaultFromDate());
    const [toDate, setToDate] = useState(persisted?.toDate ?? defaultToDate());
    const [targetCurrency, setTargetCurrency] = useState<string>(persisted?.targetCurrency ?? '');
    const [queries, setQueries] = useState<QueryState[]>(persisted?.queries ?? [defaultQuery()]);

    const [funds, setFunds] = useState<Fund[]>([]);
    const [accounts, setAccounts] = useState<Account[]>([]);
    const [unitGroups, setUnitGroups] = useState<MultiSelectGroup[]>([]);

    useEffect(() => {
        const state: PersistedState = { granularity, fromDate, toDate, targetCurrency, queries };
        try {
            localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
        } catch {
            // persistence is best-effort
        }
    }, [granularity, fromDate, toDate, targetCurrency, queries]);

    useEffect(() => {
        async function loadMetricOptions() {
            try {
                setMetrics(await listMetrics());
            } catch {
                // metric options are best-effort
            }
        }
        loadMetricOptions();
    }, []);

    useEffect(() => {
        async function loadFilterOptions() {
            try {
                const [fundsResult, accountsResult] = await Promise.all([
                    listFunds(userId),
                    listAccounts(userId),
                ]);
                setFunds(fundsResult.items);
                setAccounts(accountsResult.items);
                const seen = new Set<string>();
                const currencyUnits: MultiSelectOption[] = [];
                const instrumentUnits: MultiSelectOption[] = [];
                const currencies: string[] = [];
                for (const account of accountsResult.items) {
                    const key = `${account.unit.type}:${account.unit.value}`;
                    if (!seen.has(key)) {
                        seen.add(key);
                        const option = { value: key, label: account.unit.value };
                        if (account.unit.type === 'currency') {
                            currencyUnits.push(option);
                            currencies.push(account.unit.value);
                        } else {
                            instrumentUnits.push(option);
                        }
                    }
                }
                currencyUnits.sort((a, b) => a.label.localeCompare(b.label));
                instrumentUnits.sort((a, b) => a.label.localeCompare(b.label));
                currencies.sort();
                const groups: MultiSelectGroup[] = [];
                if (currencyUnits.length > 0) groups.push({ label: 'Currencies', options: currencyUnits });
                if (instrumentUnits.length > 0) groups.push({ label: 'Instruments', options: instrumentUnits });
                setUnitGroups(groups);
                if (currencies.length > 0 && !targetCurrency) {
                    setTargetCurrency(currencies[0]);
                }
            } catch {
                // filter options are best-effort
            }
        }
        loadFilterOptions();
    }, [userId]);

    const resolveGroupName = (groupBy: string, key: string): string => {
        if (key === 'UNGROUPED') return 'Total';
        if (groupBy === 'FUND') {
            const fund = funds.find(f => f.id === key);
            return fund ? fund.name : key;
        }
        if (groupBy === 'ACCOUNT') {
            const account = accounts.find(a => a.id === key);
            return account ? account.name : key;
        }
        return key;
    };

    const loadData = async () => {
        if (!targetCurrency) return;
        setLoading(true);
        setError(null);
        try {
            const data = await getMetricsReport(userId, {
                interval: {
                    granularity,
                    from: toLocalDateTime(fromDate),
                    to: toLocalDateTime(toDate),
                },
                targetCurrency,
                queries: queries.map(query => {
                    const units = query.units.map(key => {
                        const [type, value] = key.split(':');
                        return { type, value };
                    });
                    return {
                        id: query.id,
                        metric: query.metric,
                        grouping: query.groupBy !== 'NONE' ? query.groupBy as GroupBy : undefined,
                        filter: {
                            fundIds: query.fundIds.length > 0 ? query.fundIds : undefined,
                            units: units.length > 0 ? units : undefined,
                        },
                    };
                }),
            });
            setReport(data);
        } catch (err) {
            setReport(null);
            setError('Failed to load analytics data: ' + (err instanceof Error ? err.message : 'Unknown error'));
        } finally {
            setLoading(false);
        }
    };

    const initialLoadDone = useRef(false);
    useEffect(() => {
        if (targetCurrency && !initialLoadDone.current) {
            initialLoadDone.current = true;
            loadData();
        }
    }, [targetCurrency]);

    const updateQuery = (updated: QueryState) => {
        setQueries(prev => prev.map(q => (q.id === updated.id ? updated : q)));
    };

    const addQuery = () => {
        setQueries(prev => [...prev, defaultQuery()]);
    };

    const duplicateQuery = (id: string) => {
        setQueries(prev => {
            const index = prev.findIndex(q => q.id === id);
            if (index < 0) return prev;
            const copy = { ...prev[index], id: generateQueryId() };
            return [...prev.slice(0, index + 1), copy, ...prev.slice(index + 1)];
        });
    };

    const removeQuery = (id: string) => {
        setQueries(prev => (prev.length > 1 ? prev.filter(q => q.id !== id) : prev));
    };

    const chart: { data: MultiSeriesChartDataPoint[]; lines: ChartLine[] } | null = (() => {
        if (!report) return null;
        const lines: ChartLine[] = [];
        const data: MultiSeriesChartDataPoint[] = report.buckets.map(bucket => ({
            label: formatBucketLabel(bucket, report.granularity),
        }));
        queries.forEach((query, queryIndex) => {
            if (!query.visible) return;
            const series = report.series.find(s => s.queryId === query.id);
            if (!series) return;
            const hue = QUERY_HUES[queryIndex % QUERY_HUES.length];
            const filterParts: string[] = [];
            if (query.fundIds.length > 0) {
                const names = query.fundIds.map(id => funds.find(f => f.id === id)?.name ?? id);
                filterParts.push(names.length <= 2 ? names.join(', ') : `${names.length} funds`);
            }
            if (query.units.length > 0) {
                const values = query.units.map(key => key.split(':')[1]);
                filterParts.push(values.length <= 2 ? values.join(', ') : `${values.length} units`);
            }
            const filterSuffix = filterParts.length > 0 ? ` (${filterParts.join(' · ')})` : '';
            const groupingSuffix = query.groupBy !== 'NONE'
                ? ` by ${groupByOptions.find(g => g.value === query.groupBy)?.label ?? query.groupBy}`
                : '';
            const label = `${queryLetter(queryIndex)}: ${metricLabel(series.metric)}${groupingSuffix}${filterSuffix}`;
            const groups = [...series.groups].sort((a, b) => a.groupKey.localeCompare(b.groupKey));
            groups.forEach((group, groupIndex) => {
                const lineKey = `${query.id}:${group.groupKey}`;
                const groupName = resolveGroupName(query.groupBy, group.groupKey);
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
    })();

    const fundMultiSelectOptions: MultiSelectOption[] = funds.map(f => ({ value: f.id, label: f.name }));
    const currencyOptions = unitGroups
        .flatMap(g => g.options)
        .filter(u => u.value.startsWith('currency:'))
        .map(u => ({ value: u.value.split(':')[1], label: u.value.split(':')[1] }));

    return (
        <div>
            <h1 className="text-2xl font-bold mb-6">Analytics</h1>

            <Card className="mb-6">
                <CardContent className="pt-6">
                    <div className="flex flex-wrap items-end gap-4">
                        <div className="flex flex-col gap-1">
                            <label className="text-sm text-muted-foreground">Granularity</label>
                            <Select value={granularity} onValueChange={(v) => setGranularity(v as TimeGranularity)}>
                                <SelectTrigger className="w-[140px] h-9">
                                    <SelectValue />
                                </SelectTrigger>
                                <SelectContent>
                                    {granularityOptions.map(g => (
                                        <SelectItem key={g.value} value={g.value}>{g.label}</SelectItem>
                                    ))}
                                </SelectContent>
                            </Select>
                        </div>
                        <div className="flex flex-col gap-1">
                            <label className="text-sm text-muted-foreground">Report currency</label>
                            <Select value={targetCurrency} onValueChange={setTargetCurrency}>
                                <SelectTrigger className="w-[140px] h-9">
                                    <SelectValue />
                                </SelectTrigger>
                                <SelectContent>
                                    {currencyOptions.map(c => (
                                        <SelectItem key={c.value} value={c.value}>{c.label}</SelectItem>
                                    ))}
                                </SelectContent>
                            </Select>
                        </div>
                        <div className="flex flex-col gap-1">
                            <label className="text-sm text-muted-foreground">From</label>
                            <DatePicker value={fromDate} onChange={setFromDate} maxDate={shiftDay(toDate, -1)} className="w-[160px]" />
                        </div>
                        <div className="flex flex-col gap-1">
                            <label className="text-sm text-muted-foreground">To</label>
                            <DatePicker value={toDate} onChange={setToDate} minDate={shiftDay(fromDate, 1)} className="w-[160px]" />
                        </div>
                        <Button size="sm" onClick={loadData} disabled={!targetCurrency || loading || fromDate >= toDate}>
                            {loading ? <Loader2 className="h-4 w-4 animate-spin mr-2" /> : null}
                            Generate
                        </Button>
                    </div>
                </CardContent>
            </Card>

            {loading && (
                <div className="flex justify-center p-8">
                    <Loader2 className="h-8 w-8 animate-spin text-muted-foreground" />
                </div>
            )}

            {error && (
                <div className="flex items-center gap-4 p-4 mb-4 text-destructive bg-destructive/10 rounded-md">
                    <span>{error}</span>
                    <Button variant="outline" size="sm" onClick={loadData}>Retry</Button>
                </div>
            )}

            {!loading && !error && chart && (
                <Card className="mb-6">
                    <CardContent className="pt-6">
                        <MultiSeriesChart data={chart.data} lines={chart.lines} currency={targetCurrency} />
                    </CardContent>
                </Card>
            )}

            <div className="flex flex-col gap-3">
                {queries.map((query, index) => (
                    <MetricQueryEditor
                        key={query.id}
                        query={query}
                        label={queryLetter(index)}
                        color={QUERY_HUES[index % QUERY_HUES.length]}
                        metricLabel={metricLabel}
                        metrics={metrics}
                        groupByOptions={groupByOptions}
                        fundOptions={fundMultiSelectOptions}
                        unitGroups={unitGroups}
                        removable={queries.length > 1}
                        onChange={updateQuery}
                        onDuplicate={() => duplicateQuery(query.id)}
                        onRemove={() => removeQuery(query.id)}
                    />
                ))}
                <div>
                    <Button variant="outline" size="sm" onClick={addQuery}>
                        <Plus className="h-4 w-4 mr-2" />
                        Add query
                    </Button>
                </div>
            </div>
        </div>
    );
}

export default AnalyticsPage;
