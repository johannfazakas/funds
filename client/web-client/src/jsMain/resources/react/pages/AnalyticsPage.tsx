import { useEffect, useRef, useState } from 'react';
import { listMetrics, streamMetricsReport, TimeGranularity, GroupBy, MetricInfo, MetricsReport } from '../api/analyticsApi';
import { Dashboard, DashboardQuery, appendDashboardChart, listDashboards, notifyDashboardsChanged, toMetricQuery } from '../api/dashboardApi';
import { listFunds, Fund } from '../api/fundApi';
import { listAccounts, Account } from '../api/accountApi';
import MultiSeriesChart from '../components/MultiSeriesChart';
import MetricQueryEditor, { QueryState } from '../components/MetricQueryEditor';
import { ChartQueryView, autoQueryLabel, buildChartModel, emptyStreamReport, granularityOptions, groupByOptions, mergeStreamValue, metricLabel, queryHue, useIsDarkTheme } from '../lib/chartAssembly';
import { buildUnitOptions } from '../lib/unitOptions';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '../components/ui/select';
import { MultiSelectOption, MultiSelectGroup } from '../components/ui/multi-select';
import { Button } from '../components/ui/button';
import { Card, CardContent } from '../components/ui/card';
import { DatePicker } from '../components/ui/date-picker';
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '../components/ui/dialog';
import { Input } from '../components/ui/input';
import { Label } from '../components/ui/label';
import { Loader2, Plus } from 'lucide-react';

interface AnalyticsPageProps {
    userId: string;
}

function generateQueryId(): string {
    return `q-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 8)}`;
}

function defaultQuery(): QueryState {
    return {
        id: generateQueryId(),
        label: metricLabel('BALANCE'),
        labelTouched: false,
        metric: 'BALANCE',
        groupBy: 'NONE',
        fundIds: [],
        units: [],
        visible: true,
        collapsed: false,
    };
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
        state.queries = state.queries.map(q => (q.label
            ? q
            : { ...q, label: metricLabel(q.metric), labelTouched: false }));
        return state;
    } catch {
        return null;
    }
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

function toQueryView(query: QueryState): ChartQueryView {
    return {
        id: query.id,
        label: query.label,
        metric: query.metric,
        grouping: query.groupBy !== 'NONE' ? query.groupBy as GroupBy : null,
        fundIds: query.fundIds,
        unitValues: query.units.map(key => key.split(':')[1]),
        visible: query.visible,
    };
}

function toDashboardQuery(query: QueryState): DashboardQuery {
    const units = query.units.map(key => {
        const [type, value] = key.split(':');
        return { type, value };
    });
    return {
        id: query.id,
        label: query.label,
        metric: query.metric,
        grouping: query.groupBy !== 'NONE' ? query.groupBy as GroupBy : undefined,
        filter: {
            fundIds: query.fundIds.length > 0 ? query.fundIds : undefined,
            units: units.length > 0 ? units : undefined,
        },
    };
}

function AnalyticsPage({ userId }: AnalyticsPageProps) {
    const persisted = useRef(loadPersistedState()).current;
    const darkTheme = useIsDarkTheme();

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

    const [showAddToDashboard, setShowAddToDashboard] = useState(false);
    const [dashboards, setDashboards] = useState<Dashboard[]>([]);
    const [addTargetDashboardId, setAddTargetDashboardId] = useState<string>('');
    const [addChartName, setAddChartName] = useState('');
    const [addingChart, setAddingChart] = useState(false);
    const [addChartError, setAddChartError] = useState<string | null>(null);

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
                const options = buildUnitOptions(accountsResult.items);
                setUnitGroups(options.unitGroups);
                if (options.currencies.length > 0 && !targetCurrency) {
                    setTargetCurrency(options.currencies[0]);
                }
            } catch {
                // filter options are best-effort
            }
        }
        loadFilterOptions();
    }, [userId]);

    const abortRef = useRef<AbortController | null>(null);

    const metricUnit = (name: string) => metrics.find(m => m.metric === name)?.unit ?? 'CURRENCY';

    const loadData = async () => {
        if (!targetCurrency) return;
        abortRef.current?.abort();
        const controller = new AbortController();
        abortRef.current = controller;
        setLoading(true);
        setError(null);
        setReport(null);
        const requestQueries = queries;
        let current: MetricsReport | null = null;
        try {
            await streamMetricsReport(userId, {
                interval: {
                    granularity,
                    from: toLocalDateTime(fromDate),
                    to: toLocalDateTime(toDate),
                },
                targetCurrency,
                queries: requestQueries.map(q => toMetricQuery(toDashboardQuery(q))),
            }, {
                onBuckets: (buckets) => {
                    current = emptyStreamReport(buckets, requestQueries.map(toQueryView), metricUnit, targetCurrency);
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
                    setError('Failed to load analytics data: ' + message);
                },
            }, controller.signal);
        } catch (err) {
            if (controller.signal.aborted) return;
            setReport(null);
            setError('Failed to load analytics data: ' + (err instanceof Error ? err.message : 'Unknown error'));
        } finally {
            if (abortRef.current === controller) setLoading(false);
        }
    };

    const initialLoadDone = useRef(false);
    useEffect(() => {
        if (targetCurrency && !initialLoadDone.current) {
            initialLoadDone.current = true;
            loadData();
        }
    }, [targetCurrency]);

    const fundName = (id: string) => funds.find(f => f.id === id)?.name ?? id;

    const withAutoLabel = (query: QueryState): QueryState => (query.labelTouched
        ? query
        : { ...query, label: autoQueryLabel(toQueryView(query), fundName) });

    const updateQuery = (updated: QueryState) => {
        const next = withAutoLabel(updated);
        setQueries(prev => prev.map(q => (q.id === next.id ? next : q)));
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

    const moveQuery = (id: string, offset: number) => {
        setQueries(prev => {
            const index = prev.findIndex(q => q.id === id);
            const target = index + offset;
            if (index < 0 || target < 0 || target >= prev.length) return prev;
            const next = [...prev];
            [next[index], next[target]] = [next[target], next[index]];
            return next;
        });
    };

    const openAddToDashboard = async () => {
        setAddChartError(null);
        setAddChartName('');
        setShowAddToDashboard(true);
        try {
            const loaded = await listDashboards(userId);
            setDashboards(loaded);
            setAddTargetDashboardId(loaded[0]?.id ?? '');
        } catch (err) {
            setAddChartError(err instanceof Error ? err.message : 'Failed to load dashboards');
        }
    };

    const addChartToDashboard = async () => {
        if (!addTargetDashboardId || !addChartName.trim()) return;
        setAddingChart(true);
        setAddChartError(null);
        try {
            await appendDashboardChart(userId, addTargetDashboardId, {
                name: addChartName.trim(),
                queries: queries.map(toDashboardQuery),
            });
            notifyDashboardsChanged();
            setShowAddToDashboard(false);
        } catch (err) {
            setAddChartError(err instanceof Error ? err.message : 'Failed to add chart');
        } finally {
            setAddingChart(false);
        }
    };

    const chart = report ? buildChartModel(report, queries.map(toQueryView), funds, accounts, darkTheme) : null;

    const fundMultiSelectOptions: MultiSelectOption[] = funds.map(f => ({ value: f.id, label: f.name }));
    const currencyOptions = unitGroups
        .flatMap(g => g.options)
        .filter(u => u.value.startsWith('currency:'))
        .map(u => ({ value: u.value.split(':')[1], label: u.value.split(':')[1] }));

    return (
        <div>
            <h1 className="text-2xl font-bold mb-6">Chart</h1>

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
                        <Button variant="outline" size="sm" onClick={openAddToDashboard}>
                            Add to dashboard
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
                        color={queryHue(index, darkTheme)}
                        metricLabel={metricLabel}
                        metrics={metrics}
                        groupByOptions={groupByOptions}
                        fundOptions={fundMultiSelectOptions}
                        unitGroups={unitGroups}
                        removable={queries.length > 1}
                        moveUpDisabled={index === 0}
                        moveDownDisabled={index === queries.length - 1}
                        onChange={updateQuery}
                        onDuplicate={() => duplicateQuery(query.id)}
                        onRemove={() => removeQuery(query.id)}
                        onMove={(offset) => moveQuery(query.id, offset)}
                    />
                ))}
                <div>
                    <Button variant="outline" size="sm" onClick={addQuery}>
                        <Plus className="h-4 w-4 mr-2" />
                        Add query
                    </Button>
                </div>
            </div>

            <Dialog open={showAddToDashboard} onOpenChange={setShowAddToDashboard}>
                <DialogContent>
                    <DialogHeader>
                        <DialogTitle>Add chart to dashboard</DialogTitle>
                    </DialogHeader>
                    <div className="space-y-4 py-4">
                        {dashboards.length === 0 && !addChartError && (
                            <p className="text-sm text-muted-foreground">
                                No dashboards yet — create one from the Dashboards page first.
                            </p>
                        )}
                        {dashboards.length > 0 && (
                            <>
                                <div className="space-y-2">
                                    <Label>Dashboard</Label>
                                    <Select value={addTargetDashboardId} onValueChange={setAddTargetDashboardId}>
                                        <SelectTrigger className="h-9">
                                            <SelectValue />
                                        </SelectTrigger>
                                        <SelectContent>
                                            {dashboards.map(d => (
                                                <SelectItem key={d.id} value={d.id}>{d.name}</SelectItem>
                                            ))}
                                        </SelectContent>
                                    </Select>
                                </div>
                                <div className="space-y-2">
                                    <Label htmlFor="chartName">Chart name</Label>
                                    <Input
                                        id="chartName"
                                        value={addChartName}
                                        onChange={(e) => setAddChartName(e.target.value)}
                                        placeholder="Enter chart name"
                                        autoFocus
                                    />
                                </div>
                                <p className="text-sm text-muted-foreground">
                                    The chart keeps the current queries and uses the dashboard's default period, granularity, and currency.
                                </p>
                            </>
                        )}
                        {addChartError && <p className="text-sm text-destructive">{addChartError}</p>}
                    </div>
                    <DialogFooter>
                        <Button variant="outline" onClick={() => setShowAddToDashboard(false)}>Cancel</Button>
                        <Button
                            onClick={addChartToDashboard}
                            disabled={addingChart || !addTargetDashboardId || !addChartName.trim()}
                        >
                            {addingChart ? <Loader2 className="h-4 w-4 animate-spin mr-2" /> : null}
                            Add chart
                        </Button>
                    </DialogFooter>
                </DialogContent>
            </Dialog>
        </div>
    );
}

export default AnalyticsPage;
