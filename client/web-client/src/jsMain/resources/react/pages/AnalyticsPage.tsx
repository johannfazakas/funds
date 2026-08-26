import { useEffect, useRef, useState } from 'react';
import { listMetrics, getMetricsReport, TimeGranularity, GroupBy, MetricInfo, MetricSeries, MetricsReport } from '../api/analyticsApi';
import { listFunds, Fund } from '../api/fundApi';
import { listAccounts, Account } from '../api/accountApi';
import ValueChart, { ValueChartDataPoint } from '../components/ValueChart';
import GroupedValueChart, { GroupedValueChartDataPoint } from '../components/GroupedValueChart';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '../components/ui/select';
import { MultiSelect, MultiSelectOption, MultiSelectGroup } from '../components/ui/multi-select';
import { Button } from '../components/ui/button';
import { Card, CardContent } from '../components/ui/card';
import { DatePicker } from '../components/ui/date-picker';
import { Loader2 } from 'lucide-react';

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

const unitColors: Record<string, string> = {
    CURRENCY: '#2563eb',
    PERCENTAGE: '#ea580c',
};

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

function toSingleSeriesChartData(report: MetricsReport, series: MetricSeries): ValueChartDataPoint[] {
    const group = series.groups[0];
    return report.buckets.map((bucket, index) => ({
        label: formatBucketLabel(bucket, report.granularity),
        value: formatValue(group?.values[index], series.unit),
    }));
}

function toGroupedChartData(
    report: MetricsReport,
    series: MetricSeries,
    resolveGroupName: (key: string) => string,
): { data: GroupedValueChartDataPoint[]; groups: string[] } {
    const groupKeys = series.groups.map(g => g.groupKey).sort();
    const groups = groupKeys.map(resolveGroupName);

    const data: GroupedValueChartDataPoint[] = report.buckets.map((bucket, index) => {
        const point: GroupedValueChartDataPoint = {
            label: formatBucketLabel(bucket, report.granularity),
        };
        for (const key of groupKeys) {
            const seriesGroup = series.groups.find(g => g.groupKey === key);
            point[resolveGroupName(key)] = formatValue(seriesGroup?.values[index], series.unit);
        }
        return point;
    });

    return { data, groups };
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
    const [report, setReport] = useState<MetricsReport | null>(null);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [metrics, setMetrics] = useState<MetricInfo[]>([]);
    const [selectedMetric, setSelectedMetric] = useState<string>('BALANCE');
    const [granularity, setGranularity] = useState<TimeGranularity>('MONTHLY');
    const [fromDate, setFromDate] = useState(defaultFromDate);
    const [toDate, setToDate] = useState(defaultToDate);
    const [selectedFundIds, setSelectedFundIds] = useState<string[]>([]);
    const [selectedUnits, setSelectedUnits] = useState<string[]>([]);
    const [targetCurrency, setTargetCurrency] = useState<string>('');
    const [groupBy, setGroupBy] = useState<string>('NONE');

    const [funds, setFunds] = useState<Fund[]>([]);
    const [accounts, setAccounts] = useState<Account[]>([]);
    const [unitGroups, setUnitGroups] = useState<MultiSelectGroup[]>([]);

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

    const resolveGroupName = (key: string): string => {
        if (key === 'UNGROUPED') return 'None';
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
            const units = selectedUnits.map(key => {
                const [type, value] = key.split(':');
                return { type, value };
            });
            const data = await getMetricsReport(userId, {
                metrics: [selectedMetric],
                interval: {
                    granularity,
                    from: toLocalDateTime(fromDate),
                    to: toLocalDateTime(toDate),
                },
                filter: {
                    fundIds: selectedFundIds.length > 0 ? selectedFundIds : undefined,
                    units: units.length > 0 ? units : undefined,
                },
                targetCurrency,
                grouping: groupBy !== 'NONE' ? groupBy as GroupBy : undefined,
            });
            setReport(data);
        } catch (err) {
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

    const activeSeries = report?.series.find(s => s.metric === selectedMetric) ?? report?.series[0] ?? null;
    const activeLabel = activeSeries ? metricLabel(activeSeries.metric) : metricLabel(selectedMetric);
    const activeColor = activeSeries ? unitColors[activeSeries.unit] ?? unitColors.CURRENCY : unitColors.CURRENCY;
    const chartCurrency = activeSeries?.unit === 'CURRENCY' ? targetCurrency : undefined;

    const isGrouped = activeSeries != null && groupBy !== 'NONE' &&
        activeSeries.groups.some(g => g.groupKey !== 'UNGROUPED');

    const singleSeriesData = report && activeSeries && !isGrouped
        ? toSingleSeriesChartData(report, activeSeries) : [];
    const groupedData = report && activeSeries && isGrouped
        ? toGroupedChartData(report, activeSeries, resolveGroupName) : null;

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
                    <div className="flex flex-col gap-4">
                        <div className="flex flex-wrap items-end gap-4">
                            <div className="flex flex-col gap-1">
                                <label className="text-sm text-muted-foreground">Metric</label>
                                <Select value={selectedMetric} onValueChange={setSelectedMetric}>
                                    <SelectTrigger className="w-[200px] h-9">
                                        <SelectValue />
                                    </SelectTrigger>
                                    <SelectContent>
                                        {metrics.map(m => (
                                            <SelectItem key={m.metric} value={m.metric}>{metricLabel(m.metric)}</SelectItem>
                                        ))}
                                    </SelectContent>
                                </Select>
                            </div>
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
                                <label className="text-sm text-muted-foreground">Group by</label>
                                <Select value={groupBy} onValueChange={setGroupBy}>
                                    <SelectTrigger className="w-[140px] h-9">
                                        <SelectValue />
                                    </SelectTrigger>
                                    <SelectContent>
                                        {groupByOptions.map(g => (
                                            <SelectItem key={g.value} value={g.value}>{g.label}</SelectItem>
                                        ))}
                                    </SelectContent>
                                </Select>
                            </div>
                        </div>
                        <div className="flex flex-wrap items-end gap-4">
                            <div className="flex flex-col gap-1">
                                <label className="text-sm text-muted-foreground">Funds</label>
                                <MultiSelect
                                    values={selectedFundIds}
                                    onValuesChange={setSelectedFundIds}
                                    options={fundMultiSelectOptions}
                                    placeholder="All funds"
                                    className="w-[180px]"
                                />
                            </div>
                            <div className="flex flex-col gap-1">
                                <label className="text-sm text-muted-foreground">Financial units</label>
                                <MultiSelect
                                    values={selectedUnits}
                                    onValuesChange={setSelectedUnits}
                                    groups={unitGroups}
                                    placeholder="All units"
                                    className="w-[180px]"
                                />
                            </div>
                        </div>
                        <div className="flex flex-wrap items-end gap-4">
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

            {!loading && !error && report && activeSeries && !isGrouped && (
                <Card>
                    <CardContent className="pt-6">
                        <ValueChart
                            title={activeLabel}
                            data={singleSeriesData}
                            seriesName={activeLabel}
                            seriesColor={activeColor}
                            currency={chartCurrency}
                        />
                    </CardContent>
                </Card>
            )}

            {!loading && !error && report && activeSeries && isGrouped && groupedData && (
                <Card>
                    <CardContent className="pt-6">
                        <GroupedValueChart
                            title={activeLabel}
                            data={groupedData.data}
                            groups={groupedData.groups}
                            currency={chartCurrency}
                        />
                    </CardContent>
                </Card>
            )}
        </div>
    );
}

export default AnalyticsPage;
