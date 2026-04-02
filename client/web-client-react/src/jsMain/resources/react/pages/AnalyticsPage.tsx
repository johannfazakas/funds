import { useEffect, useRef, useState } from 'react';
import { getBalanceReport, getNetChangeReport, TimeGranularity, ReportResponse } from '../api/analyticsApi';
import { listFunds, Fund } from '../api/fundApi';
import { listAccounts } from '../api/accountApi';
import ValueChart, { ValueChartDataPoint } from '../components/ValueChart';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '../components/ui/select';
import { MultiSelect, MultiSelectOption } from '../components/ui/multi-select';
import { Button } from '../components/ui/button';
import { Card, CardContent } from '../components/ui/card';
import { DatePicker } from '../components/ui/date-picker';
import { Loader2 } from 'lucide-react';

interface AnalyticsPageProps {
    userId: string;
}

type ReportType = 'balance' | 'netChange';

const reportTypeOptions: { value: ReportType; label: string; seriesName: string; color: string }[] = [
    { value: 'balance', label: 'Balance', seriesName: 'Balance', color: '#2563eb' },
    { value: 'netChange', label: 'Net Change', seriesName: 'Net Change', color: '#16a34a' },
];

const granularityOptions: { value: TimeGranularity; label: string }[] = [
    { value: 'DAILY', label: 'Daily' },
    { value: 'WEEKLY', label: 'Weekly' },
    { value: 'MONTHLY', label: 'Monthly' },
    { value: 'YEARLY', label: 'Yearly' },
];

function defaultFromDate(): string {
    const date = new Date();
    date.setFullYear(date.getFullYear() - 1);
    return date.toISOString().slice(0, 10);
}

function defaultToDate(): string {
    return new Date().toISOString().slice(0, 10);
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

function toChartData(report: ReportResponse): ValueChartDataPoint[] {
    return report.buckets.map(bucket => ({
        label: formatBucketLabel(bucket.dateTime, report.granularity),
        value: Math.round(parseFloat(bucket.value)),
    }));
}

function toLocalDateTime(dateStr: string): string {
    return `${dateStr}T00:00:00`;
}

function AnalyticsPage({ userId }: AnalyticsPageProps) {
    const [report, setReport] = useState<ReportResponse | null>(null);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [reportType, setReportType] = useState<ReportType>('balance');
    const [granularity, setGranularity] = useState<TimeGranularity>('MONTHLY');
    const [fromDate, setFromDate] = useState(defaultFromDate);
    const [toDate, setToDate] = useState(defaultToDate);
    const [selectedFundIds, setSelectedFundIds] = useState<string[]>([]);
    const [selectedUnits, setSelectedUnits] = useState<string[]>([]);
    const [targetCurrency, setTargetCurrency] = useState<string>('');

    const [funds, setFunds] = useState<Fund[]>([]);
    const [unitOptions, setUnitOptions] = useState<MultiSelectOption[]>([]);

    useEffect(() => {
        async function loadFilterOptions() {
            try {
                const [fundsResult, accountsResult] = await Promise.all([
                    listFunds(userId),
                    listAccounts(userId),
                ]);
                setFunds(fundsResult.items);
                const seen = new Set<string>();
                const units: MultiSelectOption[] = [];
                const currencies: string[] = [];
                for (const account of accountsResult.items) {
                    const key = `${account.unit.type}:${account.unit.value}`;
                    if (!seen.has(key)) {
                        seen.add(key);
                        units.push({ value: key, label: account.unit.value });
                        if (account.unit.type === 'currency') {
                            currencies.push(account.unit.value);
                        }
                    }
                }
                units.sort((a, b) => a.label.localeCompare(b.label));
                currencies.sort();
                setUnitOptions(units);
                if (currencies.length > 0 && !targetCurrency) {
                    setTargetCurrency(currencies[0]);
                }
            } catch {
                // filter options are best-effort
            }
        }
        loadFilterOptions();
    }, [userId]);

    const loadData = async () => {
        if (!targetCurrency) return;
        setLoading(true);
        setError(null);
        try {
            const units = selectedUnits.map(key => {
                const [type, value] = key.split(':');
                return { type, value };
            });
            const request = {
                granularity,
                from: toLocalDateTime(fromDate),
                to: toLocalDateTime(toDate),
                fundIds: selectedFundIds.length > 0 ? selectedFundIds : undefined,
                units: units.length > 0 ? units : undefined,
                targetCurrency,
            };
            const data = reportType === 'balance'
                ? await getBalanceReport(userId, request)
                : await getNetChangeReport(userId, request);
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

    const chartData = report ? toChartData(report) : [];
    const activeReportType = reportTypeOptions.find(r => r.value === reportType)!;

    const fundMultiSelectOptions: MultiSelectOption[] = funds.map(f => ({ value: f.id, label: f.name }));
    const currencyOptions = unitOptions
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
                                <label className="text-sm text-muted-foreground">Report</label>
                                <Select value={reportType} onValueChange={(v) => setReportType(v as ReportType)}>
                                    <SelectTrigger className="w-[140px] h-9">
                                        <SelectValue />
                                    </SelectTrigger>
                                    <SelectContent>
                                        {reportTypeOptions.map(r => (
                                            <SelectItem key={r.value} value={r.value}>{r.label}</SelectItem>
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
                                <label className="text-sm text-muted-foreground">Currencies</label>
                                <MultiSelect
                                    values={selectedUnits}
                                    onValuesChange={setSelectedUnits}
                                    options={unitOptions}
                                    placeholder="All currencies"
                                    className="w-[180px]"
                                />
                            </div>
                        </div>
                        <div className="flex flex-wrap items-end gap-4">
                            <div className="flex flex-col gap-1">
                                <label className="text-sm text-muted-foreground">From</label>
                                <DatePicker value={fromDate} onChange={setFromDate} className="w-[160px]" />
                            </div>
                            <div className="flex flex-col gap-1">
                                <label className="text-sm text-muted-foreground">To</label>
                                <DatePicker value={toDate} onChange={setToDate} className="w-[160px]" />
                            </div>
                            <Button size="sm" onClick={loadData} disabled={!targetCurrency || loading}>
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

            {!loading && !error && report && (
                <Card>
                    <CardContent className="pt-6">
                        <ValueChart
                            title={activeReportType.seriesName}
                            data={chartData}
                            seriesName={activeReportType.seriesName}
                            seriesColor={activeReportType.color}
                        />
                    </CardContent>
                </Card>
            )}
        </div>
    );
}

export default AnalyticsPage;
