import { useEffect, useState, useCallback } from 'react';
import { Button } from '../components/ui/button';
import { Input } from '../components/ui/input';
import { Label } from '../components/ui/label';
import { Card } from '../components/ui/card';
import { Badge } from '../components/ui/badge';
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
import {
    Select,
    SelectContent,
    SelectItem,
    SelectTrigger,
    SelectValue,
} from '../components/ui/select';
import { Loader2 } from 'lucide-react';
import { Account, listAccounts, createAccount, deleteAccount } from '../api/accountApi';
import { AccountSortField, SortOrder } from '../api/types';
import { Pagination } from '../components/Pagination';
import { SortableTableHead } from '../components/SortableTableHead';

interface AccountsPageProps {
    userId: string;
}

const DEFAULT_PAGE_SIZE = 10;

function AccountsPage({ userId }: AccountsPageProps) {
    const [accounts, setAccounts] = useState<Account[]>([]);
    const [total, setTotal] = useState(0);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [showCreateModal, setShowCreateModal] = useState(false);
    const [newAccountName, setNewAccountName] = useState('');
    const [newUnitType, setNewUnitType] = useState('currency');
    const [newUnitValue, setNewUnitValue] = useState('');
    const [creating, setCreating] = useState(false);
    const [createError, setCreateError] = useState<string | null>(null);
    const [accountToDelete, setAccountToDelete] = useState<Account | null>(null);
    const [deleting, setDeleting] = useState(false);
    const [deleteError, setDeleteError] = useState<string | null>(null);

    const [offset, setOffset] = useState(0);
    const [limit, setLimit] = useState(DEFAULT_PAGE_SIZE);
    const [sortField, setSortField] = useState<AccountSortField | null>(null);
    const [sortOrder, setSortOrder] = useState<SortOrder>('asc');

    const loadAccounts = useCallback(async () => {
        setLoading(true);
        setError(null);

        try {
            const result = await listAccounts(userId, {
                pagination: { offset, limit },
                sort: sortField ? { field: sortField, order: sortOrder } : undefined,
            });
            setAccounts(result.items);
            setTotal(result.total);
        } catch (err) {
            setError('Failed to load accounts: ' + (err instanceof Error ? err.message : 'Unknown error'));
        } finally {
            setLoading(false);
        }
    }, [userId, offset, limit, sortField, sortOrder]);

    useEffect(() => {
        loadAccounts();
    }, [loadAccounts]);

    const handleSort = (field: AccountSortField) => {
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
        if (!newAccountName.trim()) {
            setCreateError('Account name cannot be empty');
            return;
        }
        if (!newUnitValue.trim()) {
            setCreateError('Unit value cannot be empty');
            return;
        }

        setCreating(true);
        setCreateError(null);

        try {
            await createAccount(
                userId,
                newAccountName.trim(),
                newUnitType,
                newUnitValue.trim().toUpperCase(),
            );
            setShowCreateModal(false);
            setNewAccountName('');
            setNewUnitType('currency');
            setNewUnitValue('');
            setOffset(0);
            await loadAccounts();
        } catch (err) {
            setCreateError('Failed to create account: ' + (err instanceof Error ? err.message : 'Unknown error'));
        } finally {
            setCreating(false);
        }
    };

    const handleDelete = async () => {
        if (!accountToDelete) return;

        setDeleting(true);
        setDeleteError(null);

        try {
            await deleteAccount(userId, accountToDelete.id);
            setAccountToDelete(null);
            await loadAccounts();
        } catch (err) {
            setDeleteError('Failed to delete account: ' + (err instanceof Error ? err.message : 'Unknown error'));
        } finally {
            setDeleting(false);
        }
    };

    const openCreateModal = () => {
        setNewAccountName('');
        setNewUnitType('currency');
        setNewUnitValue('');
        setCreateError(null);
        setShowCreateModal(true);
    };

    return (
        <div>
            <div className="flex justify-between items-center mb-6">
                <h1 className="text-2xl font-bold">Accounts</h1>
                <Button onClick={openCreateModal}>Create Account</Button>
            </div>

            {loading && (
                <div className="flex justify-center p-8">
                    <Loader2 className="h-8 w-8 animate-spin text-muted-foreground" />
                </div>
            )}

            {error && (
                <div className="flex items-center gap-4 p-4 mb-4 text-destructive bg-destructive/10 rounded-md">
                    <span>{error}</span>
                    <Button variant="outline" size="sm" onClick={loadAccounts}>Retry</Button>
                </div>
            )}

            {!loading && !error && accounts.length === 0 && (
                <div className="text-center text-muted-foreground py-8">
                    No accounts yet — create one to get started.
                </div>
            )}

            {!loading && !error && accounts.length > 0 && (
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
                                <SortableTableHead
                                    field="unit"
                                    currentField={sortField}
                                    currentOrder={sortOrder}
                                    onSort={handleSort}
                                >
                                    Unit
                                </SortableTableHead>
                                <TableHead className="w-24"></TableHead>
                            </TableRow>
                        </TableHeader>
                        <TableBody>
                            {accounts.map((account) => (
                                <TableRow key={account.id}>
                                    <TableCell>{account.name}</TableCell>
                                    <TableCell>
                                        <Badge variant={account.unit.type === 'currency' ? 'default' : 'secondary'}>
                                            {account.unit.value}
                                        </Badge>
                                    </TableCell>
                                    <TableCell>
                                        <Button
                                            variant="ghost"
                                            size="sm"
                                            className="text-destructive hover:text-destructive"
                                            onClick={() => { setDeleteError(null); setAccountToDelete(account); }}
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
                        <DialogTitle>Create Account</DialogTitle>
                    </DialogHeader>
                    <form onSubmit={handleCreate}>
                        <div className="space-y-4 py-4">
                            <div className="space-y-2">
                                <Label htmlFor="accountName">Account name</Label>
                                <Input
                                    id="accountName"
                                    value={newAccountName}
                                    onChange={(e) => setNewAccountName(e.target.value)}
                                    disabled={creating}
                                    placeholder="Enter account name"
                                    autoFocus
                                />
                            </div>
                            <div className="space-y-2">
                                <Label htmlFor="unitType">Unit type</Label>
                                <Select value={newUnitType} onValueChange={setNewUnitType} disabled={creating}>
                                    <SelectTrigger>
                                        <SelectValue />
                                    </SelectTrigger>
                                    <SelectContent>
                                        <SelectItem value="currency">Currency</SelectItem>
                                        <SelectItem value="instrument">Instrument</SelectItem>
                                    </SelectContent>
                                </Select>
                            </div>
                            <div className="space-y-2">
                                <Label htmlFor="unitValue">Unit value</Label>
                                <Input
                                    id="unitValue"
                                    value={newUnitValue}
                                    onChange={(e) => setNewUnitValue(e.target.value)}
                                    disabled={creating}
                                    placeholder={newUnitType === 'currency' ? 'e.g. RON, EUR, USD' : 'e.g. BTC, AAPL'}
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

            <Dialog open={!!accountToDelete} onOpenChange={(open) => !open && !deleting && setAccountToDelete(null)}>
                <DialogContent>
                    <DialogHeader>
                        <DialogTitle>Delete Account</DialogTitle>
                        <DialogDescription>
                            Are you sure you want to delete "<strong>{accountToDelete?.name}</strong>"?
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
                            onClick={() => setAccountToDelete(null)}
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

export default AccountsPage;
