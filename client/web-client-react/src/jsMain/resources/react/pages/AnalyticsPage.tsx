import { useCallback, useEffect, useState } from 'react';
import { getValueReport, TimeGranularity, ValueReportResponse } from '../api/analyticsApi';
import ValueChart, { ValueChartDataPoint } from '../components/ValueChart';
import { Button } from '../components/ui/button';
import { Card, CardContent } from '../components/ui/card';
import { Loader2 } from 'lucide-react';

interface AnalyticsPageProps {
    userId: string;
}

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

function toChartData(report: ValueReportResponse): ValueChartDataPoint[] {
    return report.buckets.map(bucket => ({
        label: formatBucketLabel(bucket.dateTime, report.granularity),
        netChange: parseFloat(bucket.netChange),
        balance: parseFloat(bucket.balance),
    }));
}

function toLocalDateTime(dateStr: string): string {
    return `${dateStr}T00:00:00`;
}

function AnalyticsPage({ userId }: AnalyticsPageProps) {
    const [report, setReport] = useState<ValueReportResponse | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [granularity, setGranularity] = useState<TimeGranularity>('MONTHLY');
    const [fromDate, setFromDate] = useState(defaultFromDate);
    const [toDate, setToDate] = useState(defaultToDate);

    const loadData = useCallback(async () => {
        setLoading(true);
        setError(null);
        try {
            const data = await getValueReport(userId, {
                granularity,
                from: toLocalDateTime(fromDate),
                to: toLocalDateTime(toDate),
            });
            setReport(data);
        } catch (err) {
            setError('Failed to load analytics data: ' + (err instanceof Error ? err.message : 'Unknown error'));
        } finally {
            setLoading(false);
        }
    }, [userId, granularity, fromDate, toDate]);

    useEffect(() => {
        loadData();
    }, [loadData]);

    const chartData = report ? toChartData(report) : [];

    return (
        <div>
            <h1 className="text-2xl font-bold mb-6">Analytics</h1>

            <Card className="mb-6">
                <CardContent className="pt-6">
                    <div className="flex flex-wrap items-end gap-4">
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
                        <ValueChart title="Value Report" data={chartData} />
                    </CardContent>
                </Card>
            )}
        </div>
    );
}

export default AnalyticsPage;
