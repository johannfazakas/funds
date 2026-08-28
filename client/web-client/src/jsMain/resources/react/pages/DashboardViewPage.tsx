import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { MetricInfo, MetricsReport, TimeGranularity, listMetrics, streamMetricsReport } from '../api/analyticsApi';
import { Dashboard, DashboardChart, DashboardLookback, DashboardQuery, getDashboard, toMetricQuery } from '../api/dashboardApi';
import { Account, listAccounts } from '../api/accountApi';
import { Fund, listFunds } from '../api/fundApi';
import MultiSeriesChart from '../components/MultiSeriesChart';
import {
    ChartQueryView,
    buildChartModel,
    emptyStreamReport,
    granularityOptions,
    mergeStreamValue,
    useIsDarkTheme,
} from '../lib/chartAssembly';
import { buildUnitOptions } from '../lib/unitOptions';
import { Button } from '../components/ui/button';
import { Card, CardContent } from '../components/ui/card';
import { DatePicker } from '../components/ui/date-picker';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '../components/ui/select';
import { Loader2, Pencil } from 'lucide-react';

interface DashboardViewPageProps {
    userId: string;
}

function formatLocalDate(date: Date): string {
    return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
}

export function resolveLookbackDates(lookback: DashboardLookback): { fromDate: string; toDate: string } {
    const to = new Date();
    to.setDate(to.getDate() + 1);
    const from = new Date(to);
    switch (lookback.unit) {
        case 'DAY':
            from.setDate(from.getDate() - lookback.amount);
            break;
        case 'WEEK':
            from.setDate(from.getDate() - 7 * lookback.amount);
            break;
        case 'MONTH':
            from.setMonth(from.getMonth() - lookback.amount);
            break;
        case 'YEAR':
            from.setFullYear(from.getFullYear() - lookback.amount);
            break;
    }
    return { fromDate: formatLocalDate(from), toDate: formatLocalDate(to) };
}

function shiftDay(dateStr: string, days: number): string {
    const date = new Date(`${dateStr}T00:00:00`);
    date.setDate(date.getDate() + days);
    return formatLocalDate(date);
}

export function lookbackLabel(lookback: DashboardLookback): string {
    const unit = lookback.unit.toLowerCase() + (lookback.amount > 1 ? 's' : '');
    return `last ${lookback.amount} ${unit}`;
}

function toQueryView(query: DashboardQuery): ChartQueryView {
    return {
        id: query.id,
        label: query.label,
        metric: query.metric,
        grouping: query.grouping ?? null,
        fundIds: query.filter?.fundIds ?? [],
        unitValues: query.filter?.units?.map(u => u.value) ?? [],
    };
}

interface ViewSettings {
    granularity: TimeGranularity;
    fromDate: string;
    toDate: string;
    targetCurrency: string;
}

interface DashboardChartCardProps {
    userId: string;
    dashboardId: string;
    chart: DashboardChart;
    settings: ViewSettings;
    funds: Fund[];
    accounts: Account[];
    metrics: MetricInfo[];
}

function DashboardChartCard({ userId, dashboardId, chart, settings, funds, accounts, metrics }: DashboardChartCardProps) {
    const darkTheme = useIsDarkTheme();
    const [report, setReport] = useState<MetricsReport | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    const metricUnit = (name: string) => metrics.find(m => m.metric === name)?.unit ?? 'CURRENCY';

    useEffect(() => {
        if (settings.fromDate >= settings.toDate || !settings.targetCurrency) return;
        const controller = new AbortController();
        let current: MetricsReport | null = null;
        setReport(null);
        setLoading(true);
        setError(null);
        streamMetricsReport(userId, {
            interval: {
                granularity: settings.granularity,
                from: `${settings.fromDate}T00:00:00`,
                to: `${settings.toDate}T00:00:00`,
            },
            targetCurrency: settings.targetCurrency,
            queries: chart.queries.map(toMetricQuery),
        }, {
            onBuckets: (buckets) => {
                current = emptyStreamReport(buckets, chart.queries.map(toQueryView), metricUnit, settings.targetCurrency);
                setReport(current);
                setLoading(false);
            },
            onValue: (value) => {
                if (!current) return;
                current = mergeStreamValue(current, value);
                setReport(current);
            },
            onComplete: () => {},
            onError: (message) => {
                current = null;
                setReport(null);
                setError(message);
            },
        }, controller.signal).catch(err => {
            if (controller.signal.aborted) return;
            setReport(null);
            setError(err instanceof Error ? err.message : 'Failed to load chart');
        }).finally(() => setLoading(false));
        return () => controller.abort();
    }, [
        userId,
        chart.id,
        settings.granularity,
        settings.fromDate,
        settings.toDate,
        settings.targetCurrency,
        metrics.length,
    ]);

    const chartModel = report ? buildChartModel(report, chart.queries.map(toQueryView), funds, accounts, darkTheme) : null;

    return (
        <Card className="mb-6">
            <CardContent className="pt-6">
                <div className="flex items-center justify-between mb-4">
                    <h3 className="text-lg font-semibold">{chart.name}</h3>
                    <Button variant="ghost" size="sm" title="Edit chart" asChild>
                        <Link to={`/dashboards/${dashboardId}/charts/${chart.id}/edit`}>
                            <Pencil className="h-4 w-4" />
                        </Link>
                    </Button>
                </div>
                {loading && (
                    <div className="flex justify-center p-8">
                        <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
                    </div>
                )}
                {error && (
                    <div className="p-4 text-destructive bg-destructive/10 rounded-md">
                        Failed to load chart: {error}
                    </div>
                )}
                {!loading && !error && chartModel && (
                    <MultiSeriesChart data={chartModel.data} lines={chartModel.lines} currency={settings.targetCurrency} />
                )}
            </CardContent>
        </Card>
    );
}

function DashboardViewPage({ userId }: DashboardViewPageProps) {
    const { dashboardId } = useParams<{ dashboardId: string }>();
    const [dashboard, setDashboard] = useState<Dashboard | null>(null);
    const [error, setError] = useState<string | null>(null);
    const [metrics, setMetrics] = useState<MetricInfo[]>([]);
    const [funds, setFunds] = useState<Fund[]>([]);
    const [accounts, setAccounts] = useState<Account[]>([]);
    const [currencies, setCurrencies] = useState<string[]>([]);
    const [settings, setSettings] = useState<ViewSettings | null>(null);

    useEffect(() => {
        if (!dashboardId) return;
        setDashboard(null);
        setSettings(null);
        setError(null);
        getDashboard(userId, dashboardId)
            .then(loaded => {
                setDashboard(loaded);
                const { fromDate, toDate } = resolveLookbackDates(loaded.defaultLookback);
                setSettings({
                    granularity: loaded.defaultGranularity,
                    fromDate,
                    toDate,
                    targetCurrency: loaded.defaultTargetCurrency,
                });
            })
            .catch(err => setError(err instanceof Error ? err.message : 'Failed to load dashboard'));
    }, [userId, dashboardId]);

    useEffect(() => {
        listMetrics().then(setMetrics).catch(() => {});
        listFunds(userId).then(result => setFunds(result.items)).catch(() => {});
        listAccounts(userId).then(result => {
            setAccounts(result.items);
            setCurrencies(buildUnitOptions(result.items).currencies);
        }).catch(() => {});
    }, [userId]);

    if (error) {
        return <div className="p-4 text-destructive bg-destructive/10 rounded-md">{error}</div>;
    }
    if (!dashboard || !settings) {
        return (
            <div className="flex justify-center p-8">
                <Loader2 className="h-8 w-8 animate-spin text-muted-foreground" />
            </div>
        );
    }

    const currencyOptions = currencies.includes(settings.targetCurrency)
        ? currencies
        : [settings.targetCurrency, ...currencies];

    return (
        <div>
            <div className="flex items-center justify-between mb-6">
                <h1 className="text-2xl font-bold">{dashboard.name}</h1>
                <Button variant="outline" size="sm" asChild>
                    <Link to={`/dashboards/${dashboard.id}/edit`}>
                        <Pencil className="h-4 w-4 mr-2" />
                        Edit
                    </Link>
                </Button>
            </div>

            <Card className="mb-6">
                <CardContent className="pt-6">
                    <div className="flex flex-wrap items-end gap-4">
                        <div className="flex flex-col gap-1">
                            <label className="text-sm text-muted-foreground">Granularity</label>
                            <Select
                                value={settings.granularity}
                                onValueChange={(v) => setSettings({ ...settings, granularity: v as TimeGranularity })}
                            >
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
                            <label className="text-sm text-muted-foreground">From</label>
                            <DatePicker
                                value={settings.fromDate}
                                onChange={(v) => setSettings({ ...settings, fromDate: v })}
                                maxDate={shiftDay(settings.toDate, -1)}
                                className="w-[160px]"
                            />
                        </div>
                        <div className="flex flex-col gap-1">
                            <label className="text-sm text-muted-foreground">To</label>
                            <DatePicker
                                value={settings.toDate}
                                onChange={(v) => setSettings({ ...settings, toDate: v })}
                                minDate={shiftDay(settings.fromDate, 1)}
                                className="w-[160px]"
                            />
                        </div>
                        <div className="flex flex-col gap-1">
                            <label className="text-sm text-muted-foreground">Currency</label>
                            <Select
                                value={settings.targetCurrency}
                                onValueChange={(v) => setSettings({ ...settings, targetCurrency: v })}
                            >
                                <SelectTrigger className="w-[120px] h-9">
                                    <SelectValue />
                                </SelectTrigger>
                                <SelectContent>
                                    {currencyOptions.map(c => (
                                        <SelectItem key={c} value={c}>{c}</SelectItem>
                                    ))}
                                </SelectContent>
                            </Select>
                        </div>
                    </div>
                </CardContent>
            </Card>

            {dashboard.charts.length === 0 && (
                <p className="text-muted-foreground">
                    This dashboard has no charts yet — add some from the edit page or from Analytics.
                </p>
            )}
            {dashboard.charts.map(chart => (
                <DashboardChartCard
                    key={chart.id}
                    userId={userId}
                    dashboardId={dashboard.id}
                    chart={chart}
                    settings={settings}
                    funds={funds}
                    accounts={accounts}
                    metrics={metrics}
                />
            ))}
        </div>
    );
}

export default DashboardViewPage;
