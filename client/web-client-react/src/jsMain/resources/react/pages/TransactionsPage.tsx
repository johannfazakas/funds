import { useEffect, useState } from 'react';
import { Button } from '../components/ui/button';
import { Input } from '../components/ui/input';
import { Label } from '../components/ui/label';
import { Card, CardContent } from '../components/ui/card';
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
import { Loader2, ChevronRight, ChevronDown } from 'lucide-react';

declare const ro: {
    jf: {
        funds: {
            client: {
                web: {
                    TransactionApi: {
                        listTransactions(userId: string, fromDate: string | null, toDate: string | null, fundId: string | null, accountId: string | null): Promise<Array<Transaction>>;
                        createTransaction(userId: string, type: string, dateTime: string, externalId: string, records: Array<CreateRecord>): Promise<Transaction>;
                        deleteTransaction(userId: string, transactionId: string): Promise<void>;
                    };
                    FundApi: {
                        listFunds(userId: string): Promise<Array<Fund>>;
                    };
                    AccountApi: {
                        listAccounts(userId: string): Promise<Array<Account>>;
                    };
                };
            };
        };
    };
};

interface Fund {
    id: string;
    name: string;
}

interface Account {
    id: string;
    name: string;
    unitType: string;
    unitValue: string;
}

interface CreateRecord {
    role: string;
    accountId: string;
    fundId: string;
    amount: string;
    unitType: string;
    unitValue: string;
    labels?: string[];
}

interface TransactionRecord {
    id: string;
    accountId: string;
    fundId: string;
    amount: string;
    unitType: string;
    unitValue: string;
    recordType: string;
    labels: string[];
}

interface Transaction {
    id: string;
    userId: string;
    externalId: string;
    dateTime: string;
    type: string;
    records: TransactionRecord[];
}

interface RecordConfig {
    role: string;
    label: string;
    accountFilter: 'currency' | 'instrument';
    required: boolean;
}

const RECORD_CONFIGS: Record<string, RecordConfig[]> = {
    SINGLE_RECORD: [
        { role: 'record', label: 'Record', accountFilter: 'currency', required: true },
    ],
    TRANSFER: [
        { role: 'sourceRecord', label: 'Source', accountFilter: 'currency', required: true },
        { role: 'destinationRecord', label: 'Destination', accountFilter: 'currency', required: true },
    ],
    EXCHANGE: [
        { role: 'sourceRecord', label: 'Source', accountFilter: 'currency', required: true },
        { role: 'destinationRecord', label: 'Destination', accountFilter: 'currency', required: true },
        { role: 'feeRecord', label: 'Fee', accountFilter: 'currency', required: false },
    ],
    OPEN_POSITION: [
        { role: 'currencyRecord', label: 'Currency', accountFilter: 'currency', required: true },
        { role: 'instrumentRecord', label: 'Instrument', accountFilter: 'instrument', required: true },
    ],
    CLOSE_POSITION: [
        { role: 'currencyRecord', label: 'Currency', accountFilter: 'currency', required: true },
        { role: 'instrumentRecord', label: 'Instrument', accountFilter: 'instrument', required: true },
    ],
};

interface RecordFormData {
    accountId: string;
    fundId: string;
    amount: string;
}

interface TransactionsPageProps {
    userId: string;
}

function formatType(type: string): string {
    return type.replace(/_/g, ' ');
}

function transactionSummary(tx: Transaction, accountMap: Record<string, Account>): string {
    const fmtRecord = (r: TransactionRecord) => {
        const acc = accountMap[r.accountId];
        const accName = acc?.name ?? '?';
        return `${r.amount} ${r.unitValue} (${accName})`;
    };

    switch (tx.type) {
        case 'SINGLE_RECORD':
            return tx.records[0] ? fmtRecord(tx.records[0]) : '';
        case 'TRANSFER': {
            const src = tx.records[0];
            const dst = tx.records[1];
            const srcName = src ? (accountMap[src.accountId]?.name ?? '?') : '?';
            const dstName = dst ? (accountMap[dst.accountId]?.name ?? '?') : '?';
            return `${srcName} → ${dstName}: ${src?.amount ?? ''} ${src?.unitValue ?? ''}`;
        }
        case 'EXCHANGE': {
            const src = tx.records[0];
            const dst = tx.records[1];
            return `${src ? fmtRecord(src) : '?'} → ${dst ? fmtRecord(dst) : '?'}`;
        }
        case 'OPEN_POSITION':
        case 'CLOSE_POSITION': {
            const cur = tx.records.find(r => r.recordType === 'CURRENCY');
            const ins = tx.records.find(r => r.recordType === 'INSTRUMENT');
            return `${cur ? fmtRecord(cur) : '?'} / ${ins ? fmtRecord(ins) : '?'}`;
        }
        default:
            return tx.records.map(fmtRecord).join(', ');
    }
}

function TransactionsPage({ userId }: TransactionsPageProps) {
    const [transactions, setTransactions] = useState<Transaction[]>([]);
    const [funds, setFunds] = useState<Fund[]>([]);
    const [accounts, setAccounts] = useState<Account[]>([]);
    const [accountMap, setAccountMap] = useState<Record<string, Account>>({});
    const [fundMap, setFundMap] = useState<Record<string, Fund>>({});
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    const [filterFromDate, setFilterFromDate] = useState('');
    const [filterToDate, setFilterToDate] = useState('');
    const [filterFundId, setFilterFundId] = useState('');
    const [filterAccountId, setFilterAccountId] = useState('');

    const [expandedRows, setExpandedRows] = useState<Set<string>>(new Set());

    const [showCreateModal, setShowCreateModal] = useState(false);
    const [createType, setCreateType] = useState('SINGLE_RECORD');
    const [createDateTime, setCreateDateTime] = useState('');
    const [createExternalId, setCreateExternalId] = useState('');
    const [createRecords, setCreateRecords] = useState<Record<string, RecordFormData>>({});
    const [creating, setCreating] = useState(false);
    const [createError, setCreateError] = useState<string | null>(null);

    const [transactionToDelete, setTransactionToDelete] = useState<Transaction | null>(null);
    const [deleting, setDeleting] = useState(false);
    const [deleteError, setDeleteError] = useState<string | null>(null);

    useEffect(() => {
        loadReferenceData();
    }, [userId]);

    const loadReferenceData = async () => {
        setLoading(true);
        setError(null);
        try {
            const [fundList, accountList] = await Promise.all([
                ro.jf.funds.client.web.FundApi.listFunds(userId),
                ro.jf.funds.client.web.AccountApi.listAccounts(userId),
            ]);
            setFunds(fundList);
            setAccounts(accountList);
            const aMap: Record<string, Account> = {};
            accountList.forEach(a => { aMap[a.id] = a; });
            setAccountMap(aMap);
            const fMap: Record<string, Fund> = {};
            fundList.forEach(f => { fMap[f.id] = f; });
            setFundMap(fMap);
            await loadTransactions();
        } catch (err) {
            setError('Failed to load data: ' + (err instanceof Error ? err.message : 'Unknown error'));
            setLoading(false);
        }
    };

    const loadTransactions = async () => {
        setLoading(true);
        setError(null);
        try {
            const txList = await ro.jf.funds.client.web.TransactionApi.listTransactions(
                userId,
                filterFromDate || null,
                filterToDate || null,
                filterFundId || null,
                filterAccountId || null,
            );
            setTransactions(txList);
        } catch (err) {
            setError('Failed to load transactions: ' + (err instanceof Error ? err.message : 'Unknown error'));
        } finally {
            setLoading(false);
        }
    };

    const handleApplyFilter = () => {
        loadTransactions();
    };

    const handleClearFilter = () => {
        setFilterFromDate('');
        setFilterToDate('');
        setFilterFundId('');
        setFilterAccountId('');
    };

    const toggleExpand = (id: string) => {
        setExpandedRows(prev => {
            const next = new Set(prev);
            if (next.has(id)) next.delete(id);
            else next.add(id);
            return next;
        });
    };

    const initCreateRecords = (type: string) => {
        const configs = RECORD_CONFIGS[type] || [];
        const initial: Record<string, RecordFormData> = {};
        configs.forEach(c => {
            initial[c.role] = { accountId: '', fundId: '', amount: '' };
        });
        return initial;
    };

    const openCreateModal = () => {
        const type = 'SINGLE_RECORD';
        setCreateType(type);
        setCreateDateTime('');
        setCreateExternalId('');
        setCreateRecords(initCreateRecords(type));
        setCreateError(null);
        setShowCreateModal(true);
    };

    const handleTypeChange = (type: string) => {
        setCreateType(type);
        setCreateRecords(initCreateRecords(type));
    };

    const updateRecordField = (role: string, field: keyof RecordFormData, value: string) => {
        setCreateRecords(prev => ({
            ...prev,
            [role]: { ...prev[role], [field]: value },
        }));
    };

    const handleCreate = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!createDateTime.trim()) {
            setCreateError('Date/time is required');
            return;
        }

        const configs = RECORD_CONFIGS[createType] || [];
        for (const config of configs) {
            if (!config.required) continue;
            const rec = createRecords[config.role];
            if (!rec?.accountId || !rec?.fundId || !rec?.amount) {
                setCreateError(`${config.label}: account, fund, and amount are required`);
                return;
            }
        }

        setCreating(true);
        setCreateError(null);

        try {
            const recordsArray = configs
                .filter(c => {
                    const rec = createRecords[c.role];
                    return rec && rec.accountId && rec.fundId && rec.amount;
                })
                .map(c => {
                    const rec = createRecords[c.role];
                    const account = accountMap[rec.accountId];
                    return {
                        role: c.role,
                        accountId: rec.accountId,
                        fundId: rec.fundId,
                        amount: rec.amount,
                        unitType: account?.unitType ?? 'currency',
                        unitValue: account?.unitValue ?? '',
                        labels: [],
                    };
                });

            const dateTime = createDateTime.includes('T') ? createDateTime : createDateTime + 'T00:00:00';
            const externalId = createExternalId.trim() || `manual-${Date.now()}`;

            await ro.jf.funds.client.web.TransactionApi.createTransaction(
                userId,
                createType,
                dateTime,
                externalId,
                recordsArray,
            );
            setShowCreateModal(false);
            await loadTransactions();
        } catch (err) {
            setCreateError('Failed to create transaction: ' + (err instanceof Error ? err.message : 'Unknown error'));
        } finally {
            setCreating(false);
        }
    };

    const handleDelete = async () => {
        if (!transactionToDelete) return;
        setDeleting(true);
        setDeleteError(null);
        try {
            await ro.jf.funds.client.web.TransactionApi.deleteTransaction(userId, transactionToDelete.id);
            setTransactionToDelete(null);
            await loadTransactions();
        } catch (err) {
            setDeleteError('Failed to delete transaction: ' + (err instanceof Error ? err.message : 'Unknown error'));
        } finally {
            setDeleting(false);
        }
    };

    const filteredAccounts = (filter: 'currency' | 'instrument') =>
        accounts.filter(a => a.unitType === filter);

    return (
        <div>
            <div className="flex justify-between items-center mb-6">
                <h1 className="text-2xl font-bold">Transactions</h1>
                <Button onClick={openCreateModal}>Create Transaction</Button>
            </div>

            <Card className="mb-6">
                <CardContent className="pt-6">
                    <div className="flex flex-wrap gap-4 items-end">
                        <div className="space-y-2">
                            <Label>From</Label>
                            <Input type="date" className="w-40" value={filterFromDate} onChange={e => setFilterFromDate(e.target.value)} />
                        </div>
                        <div className="space-y-2">
                            <Label>To</Label>
                            <Input type="date" className="w-40" value={filterToDate} onChange={e => setFilterToDate(e.target.value)} />
                        </div>
                        <div className="space-y-2">
                            <Label>Fund</Label>
                            <Select value={filterFundId} onValueChange={setFilterFundId}>
                                <SelectTrigger className="w-40">
                                    <SelectValue placeholder="All funds" />
                                </SelectTrigger>
                                <SelectContent>
                                    <SelectItem value="">All funds</SelectItem>
                                    {funds.map(f => <SelectItem key={f.id} value={f.id}>{f.name}</SelectItem>)}
                                </SelectContent>
                            </Select>
                        </div>
                        <div className="space-y-2">
                            <Label>Account</Label>
                            <Select value={filterAccountId} onValueChange={setFilterAccountId}>
                                <SelectTrigger className="w-40">
                                    <SelectValue placeholder="All accounts" />
                                </SelectTrigger>
                                <SelectContent>
                                    <SelectItem value="">All accounts</SelectItem>
                                    {accounts.map(a => <SelectItem key={a.id} value={a.id}>{a.name}</SelectItem>)}
                                </SelectContent>
                            </Select>
                        </div>
                        <Button size="sm" onClick={handleApplyFilter}>Apply</Button>
                        <Button variant="ghost" size="sm" onClick={handleClearFilter}>Clear</Button>
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
                    <Button variant="outline" size="sm" onClick={loadTransactions}>Retry</Button>
                </div>
            )}

            {!loading && !error && transactions.length === 0 && (
                <div className="text-center text-muted-foreground py-8">
                    No transactions found.
                </div>
            )}

            {!loading && !error && transactions.length > 0 && (
                <Card>
                    <Table>
                        <TableHeader>
                            <TableRow>
                                <TableHead className="w-8"></TableHead>
                                <TableHead>Date</TableHead>
                                <TableHead>Type</TableHead>
                                <TableHead>Summary</TableHead>
                                <TableHead className="w-24"></TableHead>
                            </TableRow>
                        </TableHeader>
                        <TableBody>
                            {transactions.map(tx => {
                                const isExpanded = expandedRows.has(tx.id);
                                return (
                                    <>
                                        <TableRow key={tx.id}>
                                            <TableCell className="cursor-pointer" onClick={() => toggleExpand(tx.id)}>
                                                {isExpanded ? <ChevronDown className="h-4 w-4" /> : <ChevronRight className="h-4 w-4" />}
                                            </TableCell>
                                            <TableCell>{tx.dateTime.substring(0, 10)}</TableCell>
                                            <TableCell>
                                                <Badge variant="outline">{formatType(tx.type)}</Badge>
                                            </TableCell>
                                            <TableCell className="max-w-md truncate">
                                                {transactionSummary(tx, accountMap)}
                                            </TableCell>
                                            <TableCell>
                                                <Button
                                                    variant="ghost"
                                                    size="sm"
                                                    className="text-destructive hover:text-destructive"
                                                    onClick={() => { setDeleteError(null); setTransactionToDelete(tx); }}
                                                >
                                                    Delete
                                                </Button>
                                            </TableCell>
                                        </TableRow>
                                        {isExpanded && (
                                            <TableRow key={tx.id + '-details'}>
                                                <TableCell colSpan={5} className="bg-muted/50">
                                                    <div className="p-4">
                                                        <Table>
                                                            <TableHeader>
                                                                <TableRow>
                                                                    <TableHead>Account</TableHead>
                                                                    <TableHead>Fund</TableHead>
                                                                    <TableHead>Amount</TableHead>
                                                                    <TableHead>Unit</TableHead>
                                                                    <TableHead>Labels</TableHead>
                                                                </TableRow>
                                                            </TableHeader>
                                                            <TableBody>
                                                                {tx.records.map(rec => (
                                                                    <TableRow key={rec.id}>
                                                                        <TableCell>{accountMap[rec.accountId]?.name ?? rec.accountId}</TableCell>
                                                                        <TableCell>{fundMap[rec.fundId]?.name ?? rec.fundId}</TableCell>
                                                                        <TableCell>{rec.amount}</TableCell>
                                                                        <TableCell>
                                                                            <Badge variant={rec.unitType === 'currency' ? 'default' : 'secondary'}>
                                                                                {rec.unitValue}
                                                                            </Badge>
                                                                        </TableCell>
                                                                        <TableCell>
                                                                            {rec.labels.map((l, i) => (
                                                                                <Badge key={i} variant="outline" className="mr-1">{l}</Badge>
                                                                            ))}
                                                                        </TableCell>
                                                                    </TableRow>
                                                                ))}
                                                            </TableBody>
                                                        </Table>
                                                    </div>
                                                </TableCell>
                                            </TableRow>
                                        )}
                                    </>
                                );
                            })}
                        </TableBody>
                    </Table>
                </Card>
            )}

            <Dialog open={showCreateModal} onOpenChange={setShowCreateModal}>
                <DialogContent className="max-w-2xl">
                    <DialogHeader>
                        <DialogTitle>Create Transaction</DialogTitle>
                    </DialogHeader>
                    <form onSubmit={handleCreate}>
                        <div className="space-y-4 py-4">
                            <div className="grid grid-cols-2 gap-4">
                                <div className="space-y-2">
                                    <Label>Type</Label>
                                    <Select value={createType} onValueChange={handleTypeChange} disabled={creating}>
                                        <SelectTrigger>
                                            <SelectValue />
                                        </SelectTrigger>
                                        <SelectContent>
                                            {Object.keys(RECORD_CONFIGS).map(t => (
                                                <SelectItem key={t} value={t}>{formatType(t)}</SelectItem>
                                            ))}
                                        </SelectContent>
                                    </Select>
                                </div>
                                <div className="space-y-2">
                                    <Label>Date / Time</Label>
                                    <Input
                                        type="datetime-local"
                                        value={createDateTime}
                                        onChange={e => setCreateDateTime(e.target.value)}
                                        disabled={creating}
                                    />
                                </div>
                            </div>
                            <div className="space-y-2">
                                <Label>External ID (optional)</Label>
                                <Input
                                    value={createExternalId}
                                    onChange={e => setCreateExternalId(e.target.value)}
                                    disabled={creating}
                                    placeholder="Leave blank for auto-generated"
                                />
                            </div>

                            {(RECORD_CONFIGS[createType] || []).map(config => (
                                <div key={config.role} className="p-4 bg-muted rounded-lg space-y-3">
                                    <div className="font-semibold">
                                        {config.label} {!config.required && <span className="text-muted-foreground font-normal">(optional)</span>}
                                    </div>
                                    <div className="grid grid-cols-3 gap-3">
                                        <div className="space-y-2">
                                            <Label>Account</Label>
                                            <Select
                                                value={createRecords[config.role]?.accountId ?? ''}
                                                onValueChange={v => updateRecordField(config.role, 'accountId', v)}
                                                disabled={creating}
                                            >
                                                <SelectTrigger>
                                                    <SelectValue placeholder="Select account" />
                                                </SelectTrigger>
                                                <SelectContent>
                                                    {filteredAccounts(config.accountFilter).map(a => (
                                                        <SelectItem key={a.id} value={a.id}>
                                                            {a.name} ({a.unitValue})
                                                        </SelectItem>
                                                    ))}
                                                </SelectContent>
                                            </Select>
                                        </div>
                                        <div className="space-y-2">
                                            <Label>Fund</Label>
                                            <Select
                                                value={createRecords[config.role]?.fundId ?? ''}
                                                onValueChange={v => updateRecordField(config.role, 'fundId', v)}
                                                disabled={creating}
                                            >
                                                <SelectTrigger>
                                                    <SelectValue placeholder="Select fund" />
                                                </SelectTrigger>
                                                <SelectContent>
                                                    {funds.map(f => (
                                                        <SelectItem key={f.id} value={f.id}>{f.name}</SelectItem>
                                                    ))}
                                                </SelectContent>
                                            </Select>
                                        </div>
                                        <div className="space-y-2">
                                            <Label>Amount</Label>
                                            <Input
                                                value={createRecords[config.role]?.amount ?? ''}
                                                onChange={e => updateRecordField(config.role, 'amount', e.target.value)}
                                                disabled={creating}
                                                placeholder="0.00"
                                            />
                                        </div>
                                    </div>
                                </div>
                            ))}

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

            <Dialog open={!!transactionToDelete} onOpenChange={(open) => !open && !deleting && setTransactionToDelete(null)}>
                <DialogContent>
                    <DialogHeader>
                        <DialogTitle>Delete Transaction</DialogTitle>
                        <DialogDescription>
                            Are you sure you want to delete this <strong>{transactionToDelete && formatType(transactionToDelete.type)}</strong> transaction
                            from <strong>{transactionToDelete?.dateTime.substring(0, 10)}</strong>?
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
                            onClick={() => setTransactionToDelete(null)}
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

export default TransactionsPage;
