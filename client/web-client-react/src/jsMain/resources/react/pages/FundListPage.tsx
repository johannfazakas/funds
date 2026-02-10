import { useEffect, useState, useCallback } from 'react';
import { Fund, listFunds, createFund, deleteFund } from '../api/fundApi';
import { FundSortField, SortOrder } from '../api/types';
import { Button } from '../components/ui/button';
import { Input } from '../components/ui/input';
import { Label } from '../components/ui/label';
import { Card } from '../components/ui/card';
import {
    Table,
    TableBody,
    TableCell,
    TableHead,
    TableHeader,
    TableRow,
} from '../components/ui/table';
import {
    Dialog,
    DialogContent,
    DialogDescription,
    DialogFooter,
    DialogHeader,
    DialogTitle,
} from '../components/ui/dialog';
import { Loader2 } from 'lucide-react';
import { Pagination } from '../components/Pagination';
import { SortableTableHead } from '../components/SortableTableHead';

interface FundListPageProps {
    userId: string;
}

const DEFAULT_PAGE_SIZE = 10;

function FundListPage({ userId }: FundListPageProps) {
    const [funds, setFunds] = useState<Fund[]>([]);
    const [total, setTotal] = useState(0);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [showCreateModal, setShowCreateModal] = useState(false);
    const [newFundName, setNewFundName] = useState('');
    const [creating, setCreating] = useState(false);
    const [createError, setCreateError] = useState<string | null>(null);
    const [fundToDelete, setFundToDelete] = useState<Fund | null>(null);
    const [deleting, setDeleting] = useState(false);
    const [deleteError, setDeleteError] = useState<string | null>(null);

    const [offset, setOffset] = useState(0);
    const [limit, setLimit] = useState(DEFAULT_PAGE_SIZE);
    const [sortField, setSortField] = useState<FundSortField | null>(null);
    const [sortOrder, setSortOrder] = useState<SortOrder>('asc');

    const loadFunds = useCallback(async () => {
        setLoading(true);
        setError(null);

        try {
            const result = await listFunds(userId, {
                pagination: { offset, limit },
                sort: sortField ? { field: sortField, order: sortOrder } : undefined,
            });
            setFunds(result.items);
            setTotal(result.total);
        } catch (err) {
            setError('Failed to load funds: ' + (err instanceof Error ? err.message : 'Unknown error'));
        } finally {
            setLoading(false);
        }
    }, [userId, offset, limit, sortField, sortOrder]);

    useEffect(() => {
        loadFunds();
    }, [loadFunds]);

    const handleSort = (field: FundSortField) => {
        if (sortField === field) {
            setSortOrder(sortOrder === 'asc' ? 'desc' : 'asc');
        } else {
            setSortField(field);
            setSortOrder('asc');
        }
        setOffset(0);
    };

    const handlePageChange = (newOffset: number) => {
        setOffset(newOffset);
    };

    const handlePageSizeChange = (newLimit: number) => {
        setLimit(newLimit);
        setOffset(0);
    };

    const handleCreate = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!newFundName.trim()) {
            setCreateError('Fund name cannot be empty');
            return;
        }

        setCreating(true);
        setCreateError(null);

        try {
            await createFund(userId, newFundName.trim());
            setShowCreateModal(false);
            setNewFundName('');
            setOffset(0);
            await loadFunds();
        } catch (err) {
            setCreateError('Failed to create fund: ' + (err instanceof Error ? err.message : 'Unknown error'));
        } finally {
            setCreating(false);
        }
    };

    const handleDelete = async () => {
        if (!fundToDelete) return;

        setDeleting(true);
        setDeleteError(null);

        try {
            await deleteFund(userId, fundToDelete.id);
            setFundToDelete(null);
            await loadFunds();
        } catch (err) {
            setDeleteError('Failed to delete fund: ' + (err instanceof Error ? err.message : 'Unknown error'));
        } finally {
            setDeleting(false);
        }
    };

    const openCreateModal = () => {
        setNewFundName('');
        setCreateError(null);
        setShowCreateModal(true);
    };

    return (
        <div>
            <div className="flex justify-between items-center mb-6">
                <h1 className="text-2xl font-bold">Funds</h1>
                <Button onClick={openCreateModal}>Create Fund</Button>
            </div>

            {loading && (
                <div className="flex justify-center p-8">
                    <Loader2 className="h-8 w-8 animate-spin text-muted-foreground" />
                </div>
            )}

            {error && (
                <div className="flex items-center gap-4 p-4 mb-4 text-destructive bg-destructive/10 rounded-md">
                    <span>{error}</span>
                    <Button variant="outline" size="sm" onClick={loadFunds}>Retry</Button>
                </div>
            )}

            {!loading && !error && funds.length === 0 && (
                <div className="text-center text-muted-foreground py-8">
                    No funds yet — create one to get started.
                </div>
            )}

            {!loading && !error && funds.length > 0 && (
                <Card>
                    <Table>
                        <TableHeader>
                            <TableRow>
                                <SortableTableHead
                                    field="name"
                                    currentField={sortField}
                                    currentOrder={sortOrder}
                                    onSort={handleSort}
                                >
                                    Name
                                </SortableTableHead>
                                <TableHead className="w-24"></TableHead>
                            </TableRow>
                        </TableHeader>
                        <TableBody>
                            {funds.map((fund) => (
                                <TableRow key={fund.id}>
                                    <TableCell>{fund.name}</TableCell>
                                    <TableCell>
                                        <Button
                                            variant="ghost"
                                            size="sm"
                                            className="text-destructive hover:text-destructive"
                                            onClick={() => { setDeleteError(null); setFundToDelete(fund); }}
                                        >
                                            Delete
                                        </Button>
                                    </TableCell>
                                </TableRow>
                            ))}
                        </TableBody>
                    </Table>
                    <Pagination
                        offset={offset}
                        limit={limit}
                        total={total}
                        onPageChange={handlePageChange}
                        onPageSizeChange={handlePageSizeChange}
                    />
                </Card>
            )}

            <Dialog open={showCreateModal} onOpenChange={setShowCreateModal}>
                <DialogContent>
                    <DialogHeader>
                        <DialogTitle>Create Fund</DialogTitle>
                    </DialogHeader>
                    <form onSubmit={handleCreate}>
                        <div className="space-y-4 py-4">
                            <div className="space-y-2">
                                <Label htmlFor="fundName">Fund name</Label>
                                <Input
                                    id="fundName"
                                    value={newFundName}
                                    onChange={(e) => setNewFundName(e.target.value)}
                                    disabled={creating}
                                    placeholder="Enter fund name"
                                    autoFocus
                                />
                            </div>
                            {createError && (
                                <div className="p-3 text-sm text-destructive bg-destructive/10 rounded-md">
                                    {createError}
                                </div>
                            )}
                        </div>
                        <DialogFooter>
                            <Button
                                type="button"
                                variant="outline"
                                onClick={() => setShowCreateModal(false)}
                                disabled={creating}
                            >
                                Cancel
                            </Button>
                            <Button type="submit" disabled={creating}>
                                {creating ? (
                                    <>
                                        <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                                        Creating...
                                    </>
                                ) : (
                                    'Create'
                                )}
                            </Button>
                        </DialogFooter>
                    </form>
                </DialogContent>
            </Dialog>

            <Dialog open={!!fundToDelete} onOpenChange={(open) => !open && !deleting && setFundToDelete(null)}>
                <DialogContent>
                    <DialogHeader>
                        <DialogTitle>Delete Fund</DialogTitle>
                        <DialogDescription>
                            Are you sure you want to delete "<strong>{fundToDelete?.name}</strong>"?
                        </DialogDescription>
                    </DialogHeader>
                    {deleteError && (
                        <div className="p-3 text-sm text-destructive bg-destructive/10 rounded-md">
                            {deleteError}
                        </div>
                    )}
                    <DialogFooter>
                        <Button
                            type="button"
                            variant="outline"
                            onClick={() => setFundToDelete(null)}
                            disabled={deleting}
                        >
                            Cancel
                        </Button>
                        <Button
                            variant="destructive"
                            onClick={handleDelete}
                            disabled={deleting}
                        >
                            {deleting ? (
                                <>
                                    <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                                    Deleting...
                                </>
                            ) : (
                                'Delete'
                            )}
                        </Button>
                    </DialogFooter>
                </DialogContent>
            </Dialog>
        </div>
    );
}

export default FundListPage;
