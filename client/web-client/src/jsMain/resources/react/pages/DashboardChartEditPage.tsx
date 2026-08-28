import { useEffect, useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { GroupBy, MetricInfo, MetricsReport, listMetrics, streamMetricsReport } from '../api/analyticsApi';
import { Dashboard, DashboardQuery, getDashboard, toMetricQuery, updateDashboardChart } from '../api/dashboardApi';
import { Account, listAccounts } from '../api/accountApi';
import { Fund, listFunds } from '../api/fundApi';
import MetricQueryEditor, { QueryState } from '../components/MetricQueryEditor';
import MultiSeriesChart from '../components/MultiSeriesChart';
import {
    ChartQueryView,
    autoQueryLabel,
    buildChartModel,
    emptyStreamReport,
    mergeStreamValue,
    metricLabel,
    groupByOptions,
    queryHue,
    useIsDarkTheme,
} from '../lib/chartAssembly';
import { buildUnitOptions } from '../lib/unitOptions';
import { resolveLookbackDates } from './DashboardViewPage';
import { Button } from '../components/ui/button';
import { Card, CardContent } from '../components/ui/card';
import { Input } from '../components/ui/input';
import { Label } from '../components/ui/label';
import { MultiSelectGroup, MultiSelectOption } from '../components/ui/multi-select';
import { Loader2, Plus, RefreshCw } from 'lucide-react';

interface DashboardChartEditPageProps {
    userId: string;
}

function generateQueryId(): string {
    return `q-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 8)}`;
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

function DashboardChartEditPage({ userId }: DashboardChartEditPageProps) {
    const { dashboardId, chartId } = useParams<{ dashboardId: string; chartId: string }>();
    const navigate = useNavigate();
    const darkTheme = useIsDarkTheme();

    const [dashboard, setDashboard] = useState<Dashboard | null>(null);
    const [loadError, setLoadError] = useState<string | null>(null);
    const [saving, setSaving] = useState(false);
    const [saveError, setSaveError] = useState<string | null>(null);

    const [name, setName] = useState('');
    const [queries, setQueries] = useState<QueryState[] | null>(null);

    const [metrics, setMetrics] = useState<MetricInfo[]>([]);
    const [funds, setFunds] = useState<Fund[]>([]);
    const [accounts, setAccounts] = useState<Account[]>([]);
    const [unitGroups, setUnitGroups] = useState<MultiSelectGroup[]>([]);

    const [report, setReport] = useState<MetricsReport | null>(null);
    const [previewLoading, setPreviewLoading] = useState(false);
    const [previewError, setPreviewError] = useState<string | null>(null);
    const abortRef = useRef<AbortController | null>(null);

    useEffect(() => {
        if (!dashboardId || !chartId) return;
        Promise.all([getDashboard(userId, dashboardId), listFunds(userId), listAccounts(userId)])
            .then(([loadedDashboard, fundsResult, accountsResult]) => {
                const chart = loadedDashboard.charts.find(c => c.id === chartId);
                if (!chart) {
                    setLoadError('Chart not found on this dashboard');
                    return;
                }
                setDashboard(loadedDashboard);
                setFunds(fundsResult.items);
                setAccounts(accountsResult.items);
                setUnitGroups(buildUnitOptions(accountsResult.items).unitGroups);
                setName(chart.name);
                const fundName = (id: string) => fundsResult.items.find(f => f.id === id)?.name ?? id;
                setQueries(chart.queries.map(query => {
                    const state: QueryState = {
                        id: query.id,
                        label: query.label,
                        labelTouched: true,
                        metric: query.metric,
                        groupBy: query.grouping ?? 'NONE',
                        fundIds: query.filter?.fundIds ?? [],
                        units: query.filter?.units?.map(u => `${u.type}:${u.value}`) ?? [],
                        visible: true,
                        collapsed: true,
                    };
                    return { ...state, labelTouched: query.label !== autoQueryLabel(toQueryView(state), fundName) };
                }));
            })
            .catch(err => setLoadError(err instanceof Error ? err.message : 'Failed to load chart'));
        listMetrics().then(setMetrics).catch(() => {});
    }, [userId, dashboardId, chartId]);

    const metricUnit = (metricName: string) => metrics.find(m => m.metric === metricName)?.unit ?? 'CURRENCY';

    const refreshPreview = (previewQueries: QueryState[] | null = queries) => {
        if (!dashboard || !previewQueries || previewQueries.length === 0) return;
        abortRef.current?.abort();
        const controller = new AbortController();
        abortRef.current = controller;
        setPreviewLoading(true);
        setPreviewError(null);
        setReport(null);
        const { fromDate, toDate } = resolveLookbackDates(dashboard.defaultLookback);
        let current: MetricsReport | null = null;
        streamMetricsReport(userId, {
            interval: {
                granularity: dashboard.defaultGranularity,
                from: `${fromDate}T00:00:00`,
                to: `${toDate}T00:00:00`,
            },
            targetCurrency: dashboard.defaultTargetCurrency,
            queries: previewQueries.map(q => toMetricQuery(toDashboardQuery(q))),
        }, {
            onBuckets: (buckets) => {
                current = emptyStreamReport(
                    buckets,
                    previewQueries.map(toQueryView),
                    metricUnit,
                    dashboard.defaultTargetCurrency,
                );
                setReport(current);
                setPreviewLoading(false);
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
                setPreviewError(message);
            },
        }, controller.signal).catch(err => {
            if (controller.signal.aborted) return;
            setReport(null);
            setPreviewError(err instanceof Error ? err.message : 'Failed to load preview');
        }).finally(() => {
            if (abortRef.current === controller) setPreviewLoading(false);
        });
    };

    const previewInitialized = useRef(false);
    useEffect(() => {
        if (!previewInitialized.current && dashboard && queries && metrics.length > 0) {
            previewInitialized.current = true;
            refreshPreview(queries);
        }
    }, [dashboard, queries, metrics]);

    const fundName = (id: string) => funds.find(f => f.id === id)?.name ?? id;

    const withAutoLabel = (query: QueryState): QueryState => (query.labelTouched
        ? query
        : { ...query, label: autoQueryLabel(toQueryView(query), fundName) });

    const updateQuery = (updated: QueryState) => {
        const next = withAutoLabel(updated);
        setQueries(prev => prev?.map(q => (q.id === next.id ? next : q)) ?? null);
    };

    const addQuery = () => {
        setQueries(prev => [...(prev ?? []), {
            id: generateQueryId(),
            label: metricLabel('BALANCE'),
            labelTouched: false,
            metric: 'BALANCE',
            groupBy: 'NONE',
            fundIds: [],
            units: [],
            visible: true,
            collapsed: false,
        }]);
    };

    const duplicateQuery = (id: string) => {
        setQueries(prev => {
            if (!prev) return prev;
            const index = prev.findIndex(q => q.id === id);
            if (index < 0) return prev;
            const copy = { ...prev[index], id: generateQueryId() };
            return [...prev.slice(0, index + 1), copy, ...prev.slice(index + 1)];
        });
    };

    const removeQuery = (id: string) => {
        setQueries(prev => (prev && prev.length > 1 ? prev.filter(q => q.id !== id) : prev));
    };

    const moveQuery = (id: string, offset: number) => {
        setQueries(prev => {
            if (!prev) return prev;
            const index = prev.findIndex(q => q.id === id);
            const target = index + offset;
            if (index < 0 || target < 0 || target >= prev.length) return prev;
            const next = [...prev];
            [next[index], next[target]] = [next[target], next[index]];
            return next;
        });
    };

    const valid = name.trim().length > 0
        && (queries?.length ?? 0) > 0
        && (queries ?? []).every(q => q.label.trim().length > 0);

    const save = async () => {
        if (!dashboardId || !chartId || !queries || !valid) return;
        setSaving(true);
        setSaveError(null);
        try {
            await updateDashboardChart(userId, dashboardId, chartId, {
                name: name.trim(),
                queries: queries.map(toDashboardQuery),
            });
            navigate(`/dashboards/${dashboardId}`);
        } catch (err) {
            setSaveError(err instanceof Error ? err.message : 'Failed to save chart');
        } finally {
            setSaving(false);
        }
    };

    if (loadError) {
        return <div className="p-4 text-destructive bg-destructive/10 rounded-md">{loadError}</div>;
    }
    if (!dashboard || !queries) {
        return (
            <div className="flex justify-center p-8">
                <Loader2 className="h-8 w-8 animate-spin text-muted-foreground" />
            </div>
        );
    }

    const chartModel = report ? buildChartModel(report, queries.map(toQueryView), funds, accounts, darkTheme) : null;
    const fundOptions: MultiSelectOption[] = funds.map(f => ({ value: f.id, label: f.name }));

    return (
        <div>
            <div className="flex items-center justify-between mb-6">
                <h1 className="text-2xl font-bold">Edit chart — {dashboard.name}</h1>
                <div className="flex gap-2">
                    <Button variant="outline" size="sm" onClick={() => navigate(`/dashboards/${dashboardId}`)}>
                        Cancel
                    </Button>
                    <Button size="sm" onClick={save} disabled={saving || !valid}>
                        {saving ? <Loader2 className="h-4 w-4 animate-spin mr-2" /> : null}
                        Save
                    </Button>
                </div>
            </div>

            {saveError && (
                <div className="p-4 mb-4 text-destructive bg-destructive/10 rounded-md">{saveError}</div>
            )}

            <Card className="mb-6">
                <CardContent className="pt-6">
                    <div className="flex flex-wrap items-end gap-4">
                        <div className="flex flex-col gap-1">
                            <Label className="text-sm text-muted-foreground">Chart name</Label>
                            <Input value={name} onChange={(e) => setName(e.target.value)} className="w-[280px] h-9" />
                        </div>
                        <Button variant="outline" size="sm" onClick={() => refreshPreview()} disabled={previewLoading}>
                            <RefreshCw className="h-4 w-4 mr-2" />
                            Refresh preview
                        </Button>
                    </div>
                </CardContent>
            </Card>

            <Card className="mb-6">
                <CardContent className="pt-6">
                    {previewLoading && (
                        <div className="flex justify-center p-8">
                            <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
                        </div>
                    )}
                    {previewError && (
                        <div className="p-4 text-destructive bg-destructive/10 rounded-md">
                            Failed to load preview: {previewError}
                        </div>
                    )}
                    {!previewLoading && !previewError && chartModel && (
                        <MultiSeriesChart
                            data={chartModel.data}
                            lines={chartModel.lines}
                            currency={dashboard.defaultTargetCurrency}
                        />
                    )}
                </CardContent>
            </Card>

            <div className="flex flex-col gap-3">
                {queries.map((query, index) => (
                    <MetricQueryEditor
                        key={query.id}
                        query={query}
                        color={queryHue(index, darkTheme)}
                        metricLabel={metricLabel}
                        metrics={metrics}
                        groupByOptions={groupByOptions}
                        fundOptions={fundOptions}
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
        </div>
    );
}

export default DashboardChartEditPage;
