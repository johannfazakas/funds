import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Dashboard, createDashboard, deleteDashboard, listDashboards, notifyDashboardsChanged, reorderDashboards } from '../api/dashboardApi';
import { listAccounts } from '../api/accountApi';
import { buildUnitOptions } from '../lib/unitOptions';
import { lookbackLabel } from './DashboardViewPage';
import { Button } from '../components/ui/button';
import { Card, CardContent } from '../components/ui/card';
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '../components/ui/dialog';
import { Input } from '../components/ui/input';
import { Label } from '../components/ui/label';
import { ArrowDown, ArrowUp, Loader2, Pencil, Plus, Trash2 } from 'lucide-react';

interface DashboardsListPageProps {
    userId: string;
}

function DashboardsListPage({ userId }: DashboardsListPageProps) {
    const navigate = useNavigate();
    const [dashboards, setDashboards] = useState<Dashboard[] | null>(null);
    const [error, setError] = useState<string | null>(null);
    const [currencies, setCurrencies] = useState<string[]>([]);
    const [showCreate, setShowCreate] = useState(false);
    const [newName, setNewName] = useState('');
    const [creating, setCreating] = useState(false);

    const load = () => {
        listDashboards(userId)
            .then(setDashboards)
            .catch(err => setError(err instanceof Error ? err.message : 'Failed to load dashboards'));
    };

    useEffect(load, [userId]);

    useEffect(() => {
        listAccounts(userId)
            .then(result => setCurrencies(buildUnitOptions(result.items).currencies))
            .catch(() => {});
    }, [userId]);

    const create = async () => {
        if (!newName.trim()) return;
        setCreating(true);
        try {
            const created = await createDashboard(userId, {
                name: newName.trim(),
                defaultGranularity: 'MONTHLY',
                defaultLookback: { amount: 12, unit: 'MONTH' },
                defaultTargetCurrency: currencies[0] ?? 'EUR',
            });
            notifyDashboardsChanged();
            setShowCreate(false);
            setNewName('');
            navigate(`/dashboards/${created.id}/edit`);
        } catch (err) {
            setError(err instanceof Error ? err.message : 'Failed to create dashboard');
        } finally {
            setCreating(false);
        }
    };

    const move = async (index: number, offset: number) => {
        if (!dashboards) return;
        const target = index + offset;
        if (target < 0 || target >= dashboards.length) return;
        const ids = dashboards.map(d => d.id);
        [ids[index], ids[target]] = [ids[target], ids[index]];
        try {
            const reordered = await reorderDashboards(userId, ids);
            setDashboards(reordered);
            notifyDashboardsChanged();
        } catch (err) {
            setError(err instanceof Error ? err.message : 'Failed to reorder dashboards');
        }
    };

    const remove = async (dashboard: Dashboard) => {
        if (!window.confirm(`Delete dashboard "${dashboard.name}"?`)) return;
        try {
            await deleteDashboard(userId, dashboard.id);
            notifyDashboardsChanged();
            load();
        } catch (err) {
            setError(err instanceof Error ? err.message : 'Failed to delete dashboard');
        }
    };

    return (
        <div>
            <div className="flex items-center justify-between mb-6">
                <h1 className="text-2xl font-bold">Dashboards</h1>
                <Button size="sm" onClick={() => setShowCreate(true)}>
                    <Plus className="h-4 w-4 mr-2" />
                    New dashboard
                </Button>
            </div>

            {error && (
                <div className="p-4 mb-4 text-destructive bg-destructive/10 rounded-md">{error}</div>
            )}

            {!dashboards && !error && (
                <div className="flex justify-center p-8">
                    <Loader2 className="h-8 w-8 animate-spin text-muted-foreground" />
                </div>
            )}

            {dashboards && dashboards.length === 0 && (
                <p className="text-muted-foreground">No dashboards yet — create one to get started.</p>
            )}

            <div className="flex flex-col gap-3">
                {dashboards?.map((dashboard, index) => (
                    <Card key={dashboard.id}>
                        <CardContent className="pt-6 flex items-center justify-between">
                            <div>
                                <Link to={`/dashboards/${dashboard.id}`} className="font-medium hover:underline">
                                    {dashboard.name}
                                </Link>
                                <p className="text-sm text-muted-foreground">
                                    {dashboard.charts.length} chart{dashboard.charts.length === 1 ? '' : 's'} ·{' '}
                                    {dashboard.defaultGranularity.toLowerCase()} · {lookbackLabel(dashboard.defaultLookback)} ·{' '}
                                    {dashboard.defaultTargetCurrency}
                                </p>
                            </div>
                            <div className="flex gap-2">
                                <Button variant="ghost" size="sm" title="Move up" disabled={index === 0}
                                    onClick={() => move(index, -1)}>
                                    <ArrowUp className="h-4 w-4" />
                                </Button>
                                <Button variant="ghost" size="sm" title="Move down" disabled={index === dashboards.length - 1}
                                    onClick={() => move(index, 1)}>
                                    <ArrowDown className="h-4 w-4" />
                                </Button>
                                <Button variant="outline" size="sm" asChild>
                                    <Link to={`/dashboards/${dashboard.id}/edit`}>
                                        <Pencil className="h-4 w-4" />
                                    </Link>
                                </Button>
                                <Button variant="outline" size="sm" onClick={() => remove(dashboard)}>
                                    <Trash2 className="h-4 w-4" />
                                </Button>
                            </div>
                        </CardContent>
                    </Card>
                ))}
            </div>

            <Dialog open={showCreate} onOpenChange={setShowCreate}>
                <DialogContent>
                    <DialogHeader>
                        <DialogTitle>Create dashboard</DialogTitle>
                    </DialogHeader>
                    <div className="space-y-2 py-4">
                        <Label htmlFor="dashboardName">Dashboard name</Label>
                        <Input
                            id="dashboardName"
                            value={newName}
                            onChange={(e) => setNewName(e.target.value)}
                            placeholder="Enter dashboard name"
                            autoFocus
                        />
                    </div>
                    <DialogFooter>
                        <Button variant="outline" onClick={() => setShowCreate(false)}>Cancel</Button>
                        <Button onClick={create} disabled={creating || !newName.trim()}>
                            {creating ? <Loader2 className="h-4 w-4 animate-spin mr-2" /> : null}
                            Create
                        </Button>
                    </DialogFooter>
                </DialogContent>
            </Dialog>
        </div>
    );
}

export default DashboardsListPage;
