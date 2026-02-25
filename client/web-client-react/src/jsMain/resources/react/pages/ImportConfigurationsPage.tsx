import { useEffect, useState, useCallback } from 'react';
import {
    ImportConfiguration,
    ImportConfigurationSortField,
    AccountMatcher,
    ExchangeMatcher,
    listImportConfigurations,
    createImportConfiguration,
    updateImportConfiguration,
    deleteImportConfiguration,
} from '../api/importConfigurationApi';
import { SortOrder } from '../api/types';
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
    DialogFooter,
    DialogHeader,
    DialogDescription,
    DialogTitle,
} from '../components/ui/dialog';
import { Loader2, Pencil, Trash2 } from 'lucide-react';
import { Pagination } from '../components/Pagination';
import { SortableTableHead } from '../components/SortableTableHead';
import {
    MatchersEditor,
    fundMatchersToRows,
    rowsToFundMatchers,
    labelMatchersToRows,
    rowsToLabelMatchers,
} from '../components/matchers/MatchersEditor';
import { FundMatcherRow } from '../components/matchers/FundMatcherEditor';
import { LabelMatcherRow } from '../components/matchers/LabelMatcherEditor';

interface ImportConfigurationsPageProps {
    userId: string;
}

const DEFAULT_PAGE_SIZE = 10;

function ImportConfigurationsPage({ userId }: ImportConfigurationsPageProps) {
    const [configurations, setConfigurations] = useState<ImportConfiguration[]>([]);
    const [total, setTotal] = useState(0);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    const [offset, setOffset] = useState(0);
    const [limit, setLimit] = useState(DEFAULT_PAGE_SIZE);
    const [sortField, setSortField] = useState<ImportConfigurationSortField | null>(null);
    const [sortOrder, setSortOrder] = useState<SortOrder>('desc');

    const [showCreateModal, setShowCreateModal] = useState(false);
    const [newName, setNewName] = useState('');
    const [newAccountMatcherRows, setNewAccountMatcherRows] = useState<AccountMatcher[]>([]);
    const [newFundMatcherRows, setNewFundMatcherRows] = useState<FundMatcherRow[]>([]);
    const [newExchangeMatchers, setNewExchangeMatchers] = useState<ExchangeMatcher[]>([]);
    const [newLabelMatcherRows, setNewLabelMatcherRows] = useState<LabelMatcherRow[]>([]);
    const [creating, setCreating] = useState(false);
    const [createError, setCreateError] = useState<string | null>(null);

    const [configToEdit, setConfigToEdit] = useState<ImportConfiguration | null>(null);
    const [editName, setEditName] = useState('');
    const [editAccountMatcherRows, setEditAccountMatcherRows] = useState<AccountMatcher[]>([]);
    const [editFundMatcherRows, setEditFundMatcherRows] = useState<FundMatcherRow[]>([]);
    const [editExchangeMatchers, setEditExchangeMatchers] = useState<ExchangeMatcher[]>([]);
    const [editLabelMatcherRows, setEditLabelMatcherRows] = useState<LabelMatcherRow[]>([]);
    const [editing, setEditing] = useState(false);
    const [editError, setEditError] = useState<string | null>(null);

    const [configToDelete, setConfigToDelete] = useState<ImportConfiguration | null>(null);
    const [deleting, setDeleting] = useState(false);
    const [deleteError, setDeleteError] = useState<string | null>(null);

    const loadConfigurations = useCallback(async () => {
        setLoading(true);
        setError(null);
        try {
            const result = await listImportConfigurations(userId, {
                pagination: { offset, limit },
                sort: sortField ? { field: sortField, order: sortOrder } : undefined,
            });
            setConfigurations(result.items);
            setTotal(result.total);
        } catch (err) {
            setError(err instanceof Error ? err.message : 'Failed to load configurations');
        } finally {
            setLoading(false);
        }
    }, [userId, offset, limit, sortField, sortOrder]);

    useEffect(() => {
        loadConfigurations();
    }, [loadConfigurations]);

    const handleSort = (field: ImportConfigurationSortField) => {
        if (sortField === field) {
            setSortOrder(sortOrder === 'asc' ? 'desc' : 'asc');
        } else {
            setSortField(field);
            setSortOrder('desc');
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

    const openCreateModal = () => {
        setNewName('');
        setNewAccountMatcherRows([]);
        setNewFundMatcherRows([]);
        setNewExchangeMatchers([]);
        setNewLabelMatcherRows([]);
        setCreateError(null);
        setShowCreateModal(true);
    };

    const handleCreate = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!newName.trim()) {
            setCreateError('Name cannot be empty');
            return;
        }

        setCreating(true);
        setCreateError(null);
        try {
            await createImportConfiguration(userId, {
                name: newName.trim(),
                accountMatchers: newAccountMatcherRows,
                fundMatchers: rowsToFundMatchers(newFundMatcherRows),
                exchangeMatchers: newExchangeMatchers,
                labelMatchers: rowsToLabelMatchers(newLabelMatcherRows),
            });
            setShowCreateModal(false);
            setOffset(0);
            await loadConfigurations();
        } catch (err) {
            setCreateError(err instanceof Error ? err.message : 'Failed to create configuration');
        } finally {
            setCreating(false);
        }
    };

    const openEditModal = (config: ImportConfiguration) => {
        setConfigToEdit(config);
        setEditName(config.name);
        setEditAccountMatcherRows([...config.accountMatchers]);
        setEditFundMatcherRows(fundMatchersToRows(config.fundMatchers));
        setEditExchangeMatchers([...config.exchangeMatchers]);
        setEditLabelMatcherRows(labelMatchersToRows(config.labelMatchers));
        setEditError(null);
    };

    const handleUpdate = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!configToEdit) return;
        if (!editName.trim()) {
            setEditError('Name cannot be empty');
            return;
        }

        setEditing(true);
        setEditError(null);
        try {
            await updateImportConfiguration(userId, configToEdit.importConfigurationId, {
                name: editName.trim(),
                accountMatchers: editAccountMatcherRows,
                fundMatchers: rowsToFundMatchers(editFundMatcherRows),
                exchangeMatchers: editExchangeMatchers,
                labelMatchers: rowsToLabelMatchers(editLabelMatcherRows),
            });
            setConfigToEdit(null);
            await loadConfigurations();
        } catch (err) {
            setEditError(err instanceof Error ? err.message : 'Failed to update configuration');
        } finally {
            setEditing(false);
        }
    };

    const handleDelete = async () => {
        if (!configToDelete) return;
        setDeleting(true);
        setDeleteError(null);
        try {
            await deleteImportConfiguration(userId, configToDelete.importConfigurationId);
            setConfigToDelete(null);
            await loadConfigurations();
        } catch (err) {
            setDeleteError(err instanceof Error ? err.message : 'Failed to delete configuration');
        } finally {
            setDeleting(false);
        }
    };

    const matchersSummary = (config: ImportConfiguration): string => {
        const parts: string[] = [];
        if (config.accountMatchers.length > 0) parts.push(`${config.accountMatchers.length} account`);
        if (config.fundMatchers.length > 0) parts.push(`${config.fundMatchers.length} fund`);
        if (config.exchangeMatchers.length > 0) parts.push(`${config.exchangeMatchers.length} exchange`);
        if (config.labelMatchers.length > 0) parts.push(`${config.labelMatchers.length} label`);
        return parts.length > 0 ? parts.join(', ') : 'No matchers';
    };

    const formatDateTime = (dateTime: string) => {
        const date = new Date(dateTime);
        if (isNaN(date.getTime())) return dateTime;
        return date.toLocaleString(undefined, {
            year: 'numeric',
            month: 'short',
            day: 'numeric',
            hour: '2-digit',
            minute: '2-digit',
        });
    };

    return (
        <div>
            <div className="flex justify-between items-center mb-6">
                <h1 className="text-2xl font-bold">Import Configurations</h1>
                <Button onClick={openCreateModal}>New Configuration</Button>
            </div>

            {loading && (
                <div className="flex justify-center p-8">
                    <Loader2 className="h-8 w-8 animate-spin text-muted-foreground" />
                </div>
            )}

            {error && (
                <div className="flex items-center gap-4 p-4 mb-4 text-destructive bg-destructive/10 rounded-md">
                    <span>{error}</span>
                    <Button variant="outline" size="sm" onClick={loadConfigurations}>Retry</Button>
                </div>
            )}

            {!loading && !error && configurations.length === 0 && (
                <div className="text-center text-muted-foreground py-8">
                    No import configurations yet — create one to get started.
                </div>
            )}

            {!loading && !error && configurations.length > 0 && (
                <Card>
                    <Table>
                        <TableHeader>
                            <TableRow>
                                <SortableTableHead
                                    field="NAME"
                                    currentField={sortField}
                                    currentOrder={sortOrder}
                                    onSort={handleSort}
                                >
                                    Name
                                </SortableTableHead>
                                <TableHead>Matchers</TableHead>
                                <SortableTableHead
                                    field="CREATED_AT"
                                    currentField={sortField}
                                    currentOrder={sortOrder}
                                    onSort={handleSort}
                                >
                                    Created At
                                </SortableTableHead>
                                <TableHead className="w-24"></TableHead>
                            </TableRow>
                        </TableHeader>
                        <TableBody>
                            {configurations.map((config) => (
                                <TableRow key={config.importConfigurationId}>
                                    <TableCell className="font-medium">{config.name}</TableCell>
                                    <TableCell className="text-muted-foreground text-sm">
                                        {matchersSummary(config)}
                                    </TableCell>
                                    <TableCell className="text-muted-foreground">
                                        {formatDateTime(config.createdAt)}
                                    </TableCell>
                                    <TableCell>
                                        <div className="flex justify-end gap-1">
                                            <Button
                                                variant="ghost"
                                                size="sm"
                                                onClick={() => openEditModal(config)}
                                            >
                                                <Pencil className="h-4 w-4" />
                                            </Button>
                                            <Button
                                                variant="ghost"
                                                size="sm"
                                                className="text-destructive hover:text-destructive"
                                                onClick={() => { setDeleteError(null); setConfigToDelete(config); }}
                                            >
                                                <Trash2 className="h-4 w-4" />
                                            </Button>
                                        </div>
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
                <DialogContent className="max-w-5xl max-h-[80vh] overflow-y-auto">
                    <DialogHeader>
                        <DialogTitle>New Import Configuration</DialogTitle>
                    </DialogHeader>
                    <form onSubmit={handleCreate}>
                        <div className="space-y-4 py-4">
                            <div className="space-y-2">
                                <Label htmlFor="name">Name</Label>
                                <Input
                                    id="name"
                                    value={newName}
                                    onChange={(e) => setNewName(e.target.value)}
                                    disabled={creating}
                                    placeholder="Configuration name"
                                />
                            </div>
                            <div className="space-y-2">
                                <Label>Matchers</Label>
                                <MatchersEditor
                                    userId={userId}
                                    accountMatchers={newAccountMatcherRows}
                                    fundMatcherRows={newFundMatcherRows}
                                    exchangeMatchers={newExchangeMatchers}
                                    labelMatcherRows={newLabelMatcherRows}
                                    onAccountMatchersChange={setNewAccountMatcherRows}
                                    onFundMatcherRowsChange={setNewFundMatcherRows}
                                    onExchangeMatchersChange={setNewExchangeMatchers}
                                    onLabelMatcherRowsChange={setNewLabelMatcherRows}
                                    disabled={creating}
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

            <Dialog open={!!configToEdit} onOpenChange={(open) => !open && !editing && setConfigToEdit(null)}>
                <DialogContent className="max-w-5xl max-h-[80vh] overflow-y-auto">
                    <DialogHeader>
                        <DialogTitle>Edit Import Configuration</DialogTitle>
                    </DialogHeader>
                    <form onSubmit={handleUpdate}>
                        <div className="space-y-4 py-4">
                            <div className="space-y-2">
                                <Label htmlFor="editName">Name</Label>
                                <Input
                                    id="editName"
                                    value={editName}
                                    onChange={(e) => setEditName(e.target.value)}
                                    disabled={editing}
                                />
                            </div>
                            <div className="space-y-2">
                                <Label>Matchers</Label>
                                <MatchersEditor
                                    userId={userId}
                                    accountMatchers={editAccountMatcherRows}
                                    fundMatcherRows={editFundMatcherRows}
                                    exchangeMatchers={editExchangeMatchers}
                                    labelMatcherRows={editLabelMatcherRows}
                                    onAccountMatchersChange={setEditAccountMatcherRows}
                                    onFundMatcherRowsChange={setEditFundMatcherRows}
                                    onExchangeMatchersChange={setEditExchangeMatchers}
                                    onLabelMatcherRowsChange={setEditLabelMatcherRows}
                                    disabled={editing}
                                />
                            </div>
                            {editError && (
                                <div className="p-3 text-sm text-destructive bg-destructive/10 rounded-md">
                                    {editError}
                                </div>
                            )}
                        </div>
                        <DialogFooter>
                            <Button
                                type="button"
                                variant="outline"
                                onClick={() => setConfigToEdit(null)}
                                disabled={editing}
                            >
                                Cancel
                            </Button>
                            <Button type="submit" disabled={editing}>
                                {editing ? (
                                    <>
                                        <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                                        Saving...
                                    </>
                                ) : (
                                    'Save'
                                )}
                            </Button>
                        </DialogFooter>
                    </form>
                </DialogContent>
            </Dialog>

            <Dialog open={!!configToDelete} onOpenChange={(open) => !open && !deleting && setConfigToDelete(null)}>
                <DialogContent>
                    <DialogHeader>
                        <DialogTitle>Delete Import Configuration</DialogTitle>
                        <DialogDescription>
                            Are you sure you want to delete "<strong>{configToDelete?.name}</strong>"?
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
                            onClick={() => setConfigToDelete(null)}
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

export default ImportConfigurationsPage;
