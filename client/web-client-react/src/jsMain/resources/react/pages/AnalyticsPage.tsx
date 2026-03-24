import { useCallback, useEffect, useState } from 'react';
import { getBalanceReport, getNetChangeReport, TimeGranularity, ReportResponse } from '../api/analyticsApi';
import ValueChart, { ValueChartDataPoint } from '../components/ValueChart';
import { Button } from '../components/ui/button';
import { Card, CardContent } from '../components/ui/card';
import { Loader2 } from 'lucide-react';

interface AnalyticsPageProps {
    userId: string;
}

type ReportType = 'balance' | 'netChange';

const reportTypes: { value: ReportType; label: string; seriesName: string; color: string }[] = [
    { value: 'balance', label: 'Balance', seriesName: 'Balance', color: '#2563eb' },
    { value: 'netChange', label: 'Net Change', seriesName: 'Net Change', color: '#16a34a' },
];

const granularities: { value: TimeGranularity; label: string }[] = [
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
        value: parseFloat(bucket.value),
    }));
}

function toLocalDateTime(dateStr: string): string {
    return `${dateStr}T00:00:00`;
}

function AnalyticsPage({ userId }: AnalyticsPageProps) {
    const [report, setReport] = useState<ReportResponse | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [reportType, setReportType] = useState<ReportType>('balance');
    const [granularity, setGranularity] = useState<TimeGranularity>('MONTHLY');
    const [fromDate, setFromDate] = useState(defaultFromDate);
    const [toDate, setToDate] = useState(defaultToDate);

    const loadData = useCallback(async () => {
        setLoading(true);
        setError(null);
        try {
            const request = {
                granularity,
                from: toLocalDateTime(fromDate),
                to: toLocalDateTime(toDate),
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
    }, [userId, reportType, granularity, fromDate, toDate]);

    useEffect(() => {
        loadData();
    }, [loadData]);

    const chartData = report ? toChartData(report) : [];
    const activeReportType = reportTypes.find(r => r.value === reportType)!;

    return (
        <div>
            <h1 className="text-2xl font-bold mb-6">Analytics</h1>

            <Card className="mb-6">
                <CardContent className="pt-6">
                    <div className="flex flex-wrap items-end gap-4">
                        <div className="flex gap-1">
                            {reportTypes.map(r => (
                                <Button
                                    key={r.value}
                                    variant={reportType === r.value ? 'default' : 'ghost'}
                                    size="sm"
                                    onClick={() => setReportType(r.value)}
                                >
                                    {r.label}
                                </Button>
                            ))}
                        </div>
                        <div className="flex gap-1">
                            {granularities.map(g => (
                                <Button
                                    key={g.value}
                                    variant={granularity === g.value ? 'default' : 'ghost'}
                                    size="sm"
                                    onClick={() => setGranularity(g.value)}
                                >
                                    {g.label}
                                </Button>
                            ))}
                        </div>
                        <div className="flex items-center gap-2">
                            <label className="text-sm text-muted-foreground">From</label>
                            <input
                                type="date"
                                value={fromDate}
                                onChange={e => setFromDate(e.target.value)}
                                className="h-9 rounded-md border border-input bg-background px-3 py-1 text-sm"
                            />
                        </div>
                        <div className="flex items-center gap-2">
                            <label className="text-sm text-muted-foreground">To</label>
                            <input
                                type="date"
                                value={toDate}
                                onChange={e => setToDate(e.target.value)}
                                className="h-9 rounded-md border border-input bg-background px-3 py-1 text-sm"
                            />
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
