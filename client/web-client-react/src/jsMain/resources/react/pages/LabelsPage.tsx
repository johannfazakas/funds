import { useEffect, useState, useCallback } from 'react';
import { Label, listLabels, createLabel, deleteLabel } from '../api/labelApi';
import { Button } from '../components/ui/button';
import { Input } from '../components/ui/input';
import { Card } from '../components/ui/card';
import {
    Table,
    TableBody,
    TableCell,
    TableHead,
    TableHeader,
    TableRow,
} from '../components/ui/table';
import { Loader2, Trash2 } from 'lucide-react';

interface LabelsPageProps {
    userId: string;
}

function LabelsPage({ userId }: LabelsPageProps) {
    const [labels, setLabels] = useState<Label[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [newLabelName, setNewLabelName] = useState('');
    const [creating, setCreating] = useState(false);
    const [deleteError, setDeleteError] = useState<string | null>(null);

    const loadLabels = useCallback(async () => {
        setLoading(true);
        setError(null);
        try {
            const result = await listLabels(userId);
            setLabels(result);
        } catch (err) {
            setError(err instanceof Error ? err.message : 'Failed to load labels');
        } finally {
            setLoading(false);
        }
    }, [userId]);

    useEffect(() => {
        loadLabels();
    }, [loadLabels]);

    const handleCreate = async () => {
        if (!newLabelName.trim()) return;
        setCreating(true);
        setError(null);
        try {
            await createLabel(userId, newLabelName.trim());
            setNewLabelName('');
            await loadLabels();
        } catch (err) {
            setError(err instanceof Error ? err.message : 'Failed to create label');
        } finally {
            setCreating(false);
        }
    };

    const handleDelete = async (label: Label) => {
        setDeleteError(null);
        try {
            await deleteLabel(userId, label.id);
            await loadLabels();
        } catch (err) {
            setDeleteError(err instanceof Error ? err.message : 'Failed to delete label');
        }
    };

    return (
        <div>
            <div className="flex justify-between items-center mb-6">
                <h1 className="text-2xl font-bold">Labels</h1>
            </div>

            <div className="mb-4 flex items-center gap-2">
                <Input
                    value={newLabelName}
                    onChange={(e) => setNewLabelName(e.target.value)}
                    onKeyDown={(e) => e.key === 'Enter' && handleCreate()}
                    placeholder="New label name"
                    className="w-48"
                />
                <Button onClick={handleCreate} disabled={creating || !newLabelName.trim()}>
                    {creating ? <Loader2 className="h-4 w-4 animate-spin" /> : 'Add'}
                </Button>
            </div>

            {error && (
                <div className="flex items-center gap-4 p-4 mb-4 text-destructive bg-destructive/10 rounded-md">
                    <span>{error}</span>
                    <Button variant="outline" size="sm" onClick={loadLabels}>Retry</Button>
                </div>
            )}

            {deleteError && (
                <div className="flex items-center gap-4 p-4 mb-4 text-destructive bg-destructive/10 rounded-md">
                    <span>{deleteError}</span>
                    <Button variant="outline" size="sm" onClick={() => setDeleteError(null)}>Dismiss</Button>
                </div>
            )}

            {loading && (
                <div className="flex justify-center p-8">
                    <Loader2 className="h-8 w-8 animate-spin text-muted-foreground" />
                </div>
            )}

            {!loading && !error && labels.length === 0 && (
                <div className="text-center text-muted-foreground py-8">
                    No labels yet.
                </div>
            )}

            {!loading && !error && labels.length > 0 && (
                <Card>
                    <Table>
                        <TableHeader>
                            <TableRow>
                                <TableHead>Name</TableHead>
                                <TableHead className="w-16" />
                            </TableRow>
                        </TableHeader>
                        <TableBody>
                            {labels.map((label) => (
                                <TableRow key={label.id}>
                                    <TableCell>{label.name}</TableCell>
                                    <TableCell>
                                        <Button
                                            variant="ghost"
                                            size="sm"
                                            onClick={() => handleDelete(label)}
                                        >
                                            <Trash2 className="h-4 w-4 text-muted-foreground" />
                                        </Button>
                                    </TableCell>
                                </TableRow>
                            ))}
                        </TableBody>
                    </Table>
                </Card>
            )}
        </div>
    );
}

export default LabelsPage;
