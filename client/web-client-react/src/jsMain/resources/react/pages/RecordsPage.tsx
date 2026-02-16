import { useEffect, useState, useCallback } from 'react';
import { Record, RecordFilter, listRecords } from '../api/recordApi';
import { listFunds, Fund } from '../api/fundApi';
import { listAccounts, Account } from '../api/accountApi';
import { RecordSortField, SortOrder } from '../api/types';
import { Button } from '../components/ui/button';
import { Input } from '../components/ui/input';
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
    Select,
    SelectContent,
    SelectItem,
    SelectTrigger,
    SelectValue,
} from '../components/ui/select';
import { Loader2 } from 'lucide-react';
import { Pagination } from '../components/Pagination';
import { SortableTableHead } from '../components/SortableTableHead';
import TransactionDetailModal from '../components/TransactionDetailModal';

interface RecordsPageProps {
    userId: string;
}

const DEFAULT_PAGE_SIZE = 10;

function RecordsPage({ userId }: RecordsPageProps) {
    const [records, setRecords] = useState<Record[]>([]);
    const [total, setTotal] = useState(0);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    const [funds, setFunds] = useState<Fund[]>([]);
    const [accounts, setAccounts] = useState<Account[]>([]);
    const [fundsMap, setFundsMap] = useState<Map<string, string>>(new Map());
    const [accountsMap, setAccountsMap] = useState<Map<string, string>>(new Map());

    const [offset, setOffset] = useState(0);
    const [limit, setLimit] = useState(DEFAULT_PAGE_SIZE);
    const [sortField, setSortField] = useState<RecordSortField | null>(null);
    const [sortOrder, setSortOrder] = useState<SortOrder>('desc');

    const [filterAccountId, setFilterAccountId] = useState<string>('');
    const [filterFundId, setFilterFundId] = useState<string>('');
    const [filterUnit, setFilterUnit] = useState<string>('');
    const [filterLabel, setFilterLabel] = useState<string>('');
    const [filterFromDate, setFilterFromDate] = useState<string>('');
    const [filterToDate, setFilterToDate] = useState<string>('');
    const [selectedTransactionId, setSelectedTransactionId] = useState<string | null>(null);

    useEffect(() => {
        const loadReferenceData = async () => {
            try {
                const [fundsResult, accountsResult] = await Promise.all([
                    listFunds(userId, { pagination: { offset: 0, limit: 1000 } }),
                    listAccounts(userId, { pagination: { offset: 0, limit: 1000 } }),
                ]);
                setFunds(fundsResult.items);
                setAccounts(accountsResult.items);
                setFundsMap(new Map(fundsResult.items.map(f => [f.id, f.name])));
                setAccountsMap(new Map(accountsResult.items.map(a => [a.id, a.name])));
            } catch {
                // Ignore errors loading reference data
            }
        };
        loadReferenceData();
    }, [userId]);

    const loadRecords = useCallback(async () => {
        setLoading(true);
        setError(null);

        const filter: RecordFilter = {};
        if (filterAccountId) filter.accountId = filterAccountId;
        if (filterFundId) filter.fundId = filterFundId;
        if (filterUnit.trim()) filter.unit = filterUnit.trim();
        if (filterLabel.trim()) filter.label = filterLabel.trim();
        if (filterFromDate) filter.fromDate = filterFromDate;
        if (filterToDate) filter.toDate = filterToDate;

        try {
            const result = await listRecords(userId, {
                pagination: { offset, limit },
                sort: sortField ? { field: sortField, order: sortOrder } : undefined,
                filter: Object.keys(filter).length > 0 ? filter : undefined,
            });
            setRecords(result.items);
            setTotal(result.total);
        } catch (err) {
            setError(err instanceof Error ? err.message : 'Failed to load records');
        } finally {
            setLoading(false);
        }
    }, [userId, offset, limit, sortField, sortOrder, filterAccountId, filterFundId, filterUnit, filterLabel, filterFromDate, filterToDate]);

    useEffect(() => {
        loadRecords();
    }, [loadRecords]);

    const handleSort = (field: RecordSortField) => {
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

    const handleFilterChange = () => {
        setOffset(0);
    };

    const clearFilters = () => {
        setFilterAccountId('');
        setFilterFundId('');
        setFilterUnit('');
        setFilterLabel('');
        setFilterFromDate('');
        setFilterToDate('');
        setOffset(0);
    };

    const hasActiveFilters = filterAccountId || filterFundId || filterUnit.trim() || filterLabel.trim() || filterFromDate || filterToDate;

    return (
        <div>
            <div className="flex justify-between items-center mb-6">
                <h1 className="text-2xl font-bold">Records</h1>
            </div>

            <div className="mb-4 flex flex-wrap items-center gap-2">
                <Input
                    type="date"
                    value={filterFromDate}
                    onChange={(e) => { setFilterFromDate(e.target.value); handleFilterChange(); }}
                    className="w-36"
                />
                <Input
                    type="date"
                    value={filterToDate}
                    onChange={(e) => { setFilterToDate(e.target.value); handleFilterChange(); }}
                    className="w-36"
                />
                <Select
                    value={filterAccountId}
                    onValueChange={(value) => { setFilterAccountId(value === 'all' ? '' : value); handleFilterChange(); }}
                >
                    <SelectTrigger className="w-40">
                        <SelectValue placeholder="Account" />
                    </SelectTrigger>
                    <SelectContent>
                        <SelectItem value="all">All accounts</SelectItem>
                        {accounts.map(account => (
                            <SelectItem key={account.id} value={account.id}>
                                {account.name}
                            </SelectItem>
                        ))}
                    </SelectContent>
                </Select>
                <Select
                    value={filterFundId}
                    onValueChange={(value) => { setFilterFundId(value === 'all' ? '' : value); handleFilterChange(); }}
                >
                    <SelectTrigger className="w-36">
                        <SelectValue placeholder="Fund" />
                    </SelectTrigger>
                    <SelectContent>
                        <SelectItem value="all">All funds</SelectItem>
                        {funds.map(fund => (
                            <SelectItem key={fund.id} value={fund.id}>
                                {fund.name}
                            </SelectItem>
                        ))}
                    </SelectContent>
                </Select>
                <Input
                    value={filterUnit}
                    onChange={(e) => setFilterUnit(e.target.value)}
                    onBlur={handleFilterChange}
                    onKeyDown={(e) => e.key === 'Enter' && handleFilterChange()}
                    placeholder="Unit"
                    className="w-28"
                />
                <Input
                    value={filterLabel}
                    onChange={(e) => setFilterLabel(e.target.value)}
                    onBlur={handleFilterChange}
                    onKeyDown={(e) => e.key === 'Enter' && handleFilterChange()}
                    placeholder="Label"
                    className="w-28"
                />
                {hasActiveFilters && (
                    <Button variant="outline" size="sm" onClick={clearFilters}>
                        Clear
                    </Button>
                )}
            </div>

            {loading && (
                <div className="flex justify-center p-8">
                    <Loader2 className="h-8 w-8 animate-spin text-muted-foreground" />
                </div>
            )}

            {error && (
                <div className="flex items-center gap-4 p-4 mb-4 text-destructive bg-destructive/10 rounded-md">
                    <span>{error}</span>
                    <Button variant="outline" size="sm" onClick={loadRecords}>Retry</Button>
                </div>
            )}

            {!loading && !error && records.length === 0 && (
                <div className="text-center text-muted-foreground py-8">
                    {hasActiveFilters ? 'No records match the current filters.' : 'No records yet.'}
                </div>
            )}

            {!loading && !error && records.length > 0 && (
                <Card>
                    <Table>
                        <TableHeader>
                            <TableRow>
                                <SortableTableHead
                                    field="date"
                                    currentField={sortField}
                                    currentOrder={sortOrder}
                                    onSort={handleSort}
                                >
                                    Date
                                </SortableTableHead>
                                <TableHead>Account</TableHead>
                                <TableHead>Fund</TableHead>
                                <SortableTableHead
                                    field="amount"
                                    currentField={sortField}
                                    currentOrder={sortOrder}
                                    onSort={handleSort}
                                >
                                    Amount
                                </SortableTableHead>
                                <TableHead>Unit</TableHead>
                                <TableHead>Labels</TableHead>
                                <TableHead>Note</TableHead>
                            </TableRow>
                        </TableHeader>
                        <TableBody>
                            {records.map((record) => (
                                <TableRow key={record.id} className="cursor-pointer" onClick={() => setSelectedTransactionId(record.transactionId)}>
                                    <TableCell className="text-muted-foreground">{record.dateTime.substring(0, 10)}</TableCell>
                                    <TableCell>{accountsMap.get(record.accountId) || record.accountId}</TableCell>
                                    <TableCell>{fundsMap.get(record.fundId) || record.fundId}</TableCell>
                                    <TableCell className={parseFloat(record.amount) < 0 ? 'text-destructive' : 'text-green-600'}>
                                        {parseFloat(record.amount).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 8 })}
                                    </TableCell>
                                    <TableCell>
                                        <Badge variant={record.type === 'CURRENCY' ? 'default' : 'secondary'}>
                                            {record.unit}
                                        </Badge>
                                    </TableCell>
                                    <TableCell>
                                        <div className="flex flex-wrap gap-1">
                                            {record.labels.map((label, idx) => (
                                                <Badge key={idx} variant="outline" className="text-xs">
                                                    {label}
                                                </Badge>
                                            ))}
                                        </div>
                                    </TableCell>
                                    <TableCell className="text-muted-foreground text-sm">{record.note}</TableCell>
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

            <TransactionDetailModal
                open={selectedTransactionId !== null}
                onClose={() => setSelectedTransactionId(null)}
                userId={userId}
                transactionId={selectedTransactionId}
                accountsMap={accountsMap}
                fundsMap={fundsMap}
            />
        </div>
    );
}

export default RecordsPage;
