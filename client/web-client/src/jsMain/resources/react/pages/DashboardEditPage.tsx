import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { TimeGranularity } from '../api/analyticsApi';
import {
    DashboardChart,
    DashboardLookback,
    LookbackUnit,
    appendDashboardChart,
    deleteDashboardChart,
    getDashboard,
    notifyDashboardsChanged,
    reorderDashboardCharts,
    updateDashboard,
} from '../api/dashboardApi';
import { listAccounts } from '../api/accountApi';
import { granularityOptions, lookbackUnitOptions, metricLabel } from '../lib/chartAssembly';
import { buildUnitOptions } from '../lib/unitOptions';
import { Button } from '../components/ui/button';
import { Card, CardContent } from '../components/ui/card';
import { Input } from '../components/ui/input';
import { Label } from '../components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '../components/ui/select';
import { ArrowDown, ArrowUp, Loader2, Pencil, Plus, Trash2 } from 'lucide-react';

interface DashboardEditPageProps {
    userId: string;
}

function generateQueryId(): string {
    return `q-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 8)}`;
}

function DashboardEditPage({ userId }: DashboardEditPageProps) {
    const { dashboardId } = useParams<{ dashboardId: string }>();
    const navigate = useNavigate();

    const [loaded, setLoaded] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [saving, setSaving] = useState(false);

    const [name, setName] = useState('');
    const [granularity, setGranularity] = useState<TimeGranularity>('MONTHLY');
    const [lookback, setLookback] = useState<DashboardLookback>({ amount: 12, unit: 'MONTH' });
    const [targetCurrency, setTargetCurrency] = useState('');
    const [charts, setCharts] = useState<DashboardChart[]>([]);

    const [currencies, setCurrencies] = useState<string[]>([]);

    useEffect(() => {
        if (!dashboardId) return;
        getDashboard(userId, dashboardId)
            .then(dashboard => {
                setName(dashboard.name);
                setGranularity(dashboard.defaultGranularity);
                setLookback(dashboard.defaultLookback);
                setTargetCurrency(dashboard.defaultTargetCurrency);
                setCharts(dashboard.charts);
                setLoaded(true);
            })
            .catch(err => setError(err instanceof Error ? err.message : 'Failed to load dashboard'));
    }, [userId, dashboardId]);

    useEffect(() => {
        listAccounts(userId)
            .then(result => setCurrencies(buildUnitOptions(result.items).currencies))
            .catch(() => {});
    }, [userId]);

    const moveChart = async (index: number, offset: number) => {
        if (!dashboardId) return;
        const target = index + offset;
        if (target < 0 || target >= charts.length) return;
        const ids = charts.map(c => c.id);
        [ids[index], ids[target]] = [ids[target], ids[index]];
        try {
            setCharts(await reorderDashboardCharts(userId, dashboardId, ids));
        } catch (err) {
            setError(err instanceof Error ? err.message : 'Failed to reorder charts');
        }
    };

    const removeChart = async (chart: DashboardChart) => {
        if (!dashboardId) return;
        if (!window.confirm(`Delete chart "${chart.name}"?`)) return;
        try {
            await deleteDashboardChart(userId, dashboardId, chart.id);
            setCharts(prev => prev.filter(c => c.id !== chart.id));
        } catch (err) {
            setError(err instanceof Error ? err.message : 'Failed to delete chart');
        }
    };

    const addChart = async () => {
        if (!dashboardId) return;
        try {
            const created = await appendDashboardChart(userId, dashboardId, {
                name: `Chart ${charts.length + 1}`,
                queries: [{ id: generateQueryId(), label: metricLabel('BALANCE'), metric: 'BALANCE' }],
            });
            navigate(`/dashboards/${dashboardId}/charts/${created.id}/edit`);
        } catch (err) {
            setError(err instanceof Error ? err.message : 'Failed to add chart');
        }
    };

    const valid = name.trim().length > 0 && lookback.amount > 0;

    const save = async () => {
        if (!dashboardId || !valid) return;
        setSaving(true);
        setError(null);
        try {
            await updateDashboard(userId, dashboardId, {
                name: name.trim(),
                defaultGranularity: granularity,
                defaultLookback: lookback,
                defaultTargetCurrency: targetCurrency,
            });
            notifyDashboardsChanged();
            navigate(`/dashboards/${dashboardId}`);
        } catch (err) {
            setError(err instanceof Error ? err.message : 'Failed to save dashboard');
        } finally {
            setSaving(false);
        }
    };

    const currencyOptions = (currencies.includes(targetCurrency) || !targetCurrency
        ? currencies
        : [targetCurrency, ...currencies]);

    if (error && !loaded) {
        return <div className="p-4 text-destructive bg-destructive/10 rounded-md">{error}</div>;
    }
    if (!loaded) {
        return (
            <div className="flex justify-center p-8">
                <Loader2 className="h-8 w-8 animate-spin text-muted-foreground" />
            </div>
        );
    }

    return (
        <div>
            <div className="flex items-center justify-between mb-6">
                <h1 className="text-2xl font-bold">Edit dashboard</h1>
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

            {error && (
                <div className="p-4 mb-4 text-destructive bg-destructive/10 rounded-md">{error}</div>
            )}

            <Card className="mb-6">
                <CardContent className="pt-6">
                    <div className="flex flex-wrap items-end gap-4">
                        <div className="flex flex-col gap-1">
                            <Label className="text-sm text-muted-foreground">Name</Label>
                            <Input value={name} onChange={(e) => setName(e.target.value)} className="w-[220px] h-9" />
                        </div>
                        <div className="flex flex-col gap-1">
                            <Label className="text-sm text-muted-foreground">Default granularity</Label>
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
                            <Label className="text-sm text-muted-foreground">Default lookback</Label>
                            <div className="flex gap-2">
                                <Input
                                    type="number"
                                    min={1}
                                    value={lookback.amount}
                                    onChange={(e) => setLookback({ ...lookback, amount: parseInt(e.target.value, 10) || 0 })}
                                    className="w-[80px] h-9"
                                />
                                <Select
                                    value={lookback.unit}
                                    onValueChange={(v) => setLookback({ ...lookback, unit: v as LookbackUnit })}
                                >
                                    <SelectTrigger className="w-[110px] h-9">
                                        <SelectValue />
                                    </SelectTrigger>
                                    <SelectContent>
                                        {lookbackUnitOptions.map(u => (
                                            <SelectItem key={u.value} value={u.value}>{u.label}</SelectItem>
                                        ))}
                                    </SelectContent>
                                </Select>
                            </div>
                        </div>
                        <div className="flex flex-col gap-1">
                            <Label className="text-sm text-muted-foreground">Default currency</Label>
                            <Select value={targetCurrency} onValueChange={setTargetCurrency}>
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

            <div className="flex flex-col gap-3">
                {charts.length === 0 && (
                    <p className="text-muted-foreground">This dashboard has no charts yet.</p>
                )}
                {charts.map((chart, index) => (
                    <Card key={chart.id}>
                        <CardContent className="pt-6 flex items-center justify-between">
                            <div>
                                <p className="font-medium">{chart.name}</p>
                                <p className="text-sm text-muted-foreground">
                                    {chart.queries.length} quer{chart.queries.length === 1 ? 'y' : 'ies'}:{' '}
                                    {chart.queries.map(q => q.label).join(' · ')}
                                </p>
                            </div>
                            <div className="flex gap-2">
                                <Button variant="ghost" size="sm" title="Move up" disabled={index === 0}
                                    onClick={() => moveChart(index, -1)}>
                                    <ArrowUp className="h-4 w-4" />
                                </Button>
                                <Button variant="ghost" size="sm" title="Move down" disabled={index === charts.length - 1}
                                    onClick={() => moveChart(index, 1)}>
                                    <ArrowDown className="h-4 w-4" />
                                </Button>
                                <Button variant="outline" size="sm" title="Edit chart"
                                    onClick={() => navigate(`/dashboards/${dashboardId}/charts/${chart.id}/edit`)}>
                                    <Pencil className="h-4 w-4" />
                                </Button>
                                <Button variant="outline" size="sm" title="Delete chart" onClick={() => removeChart(chart)}>
                                    <Trash2 className="h-4 w-4" />
                                </Button>
                            </div>
                        </CardContent>
                    </Card>
                ))}
                <div>
                    <Button variant="outline" size="sm" onClick={addChart}>
                        <Plus className="h-4 w-4 mr-2" />
                        Add chart
                    </Button>
                </div>
            </div>
        </div>
    );
}

export default DashboardEditPage;
