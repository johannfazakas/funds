import { useEffect, useState } from 'react';
import '../styles/TransactionsPage.css';

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
        <div className="transactions-container">
            <div className="page-header">
                <h1>Transactions</h1>
                <button className="create-button" onClick={openCreateModal}>Create Transaction</button>
            </div>

            <div className="filter-bar">
                <div className="filter-group">
                    <label>From</label>
                    <input type="date" value={filterFromDate} onChange={e => setFilterFromDate(e.target.value)} />
                </div>
                <div className="filter-group">
                    <label>To</label>
                    <input type="date" value={filterToDate} onChange={e => setFilterToDate(e.target.value)} />
                </div>
                <div className="filter-group">
                    <label>Fund</label>
                    <select value={filterFundId} onChange={e => setFilterFundId(e.target.value)}>
                        <option value="">All funds</option>
                        {funds.map(f => <option key={f.id} value={f.id}>{f.name}</option>)}
                    </select>
                </div>
                <div className="filter-group">
                    <label>Account</label>
                    <select value={filterAccountId} onChange={e => setFilterAccountId(e.target.value)}>
                        <option value="">All accounts</option>
                        {accounts.map(a => <option key={a.id} value={a.id}>{a.name}</option>)}
                    </select>
                </div>
                <button className="apply-button" onClick={handleApplyFilter}>Apply</button>
                <button className="clear-button" onClick={handleClearFilter}>Clear</button>
            </div>

            <div className="main-content">
                {loading && <div className="loading">Loading...</div>}

                {error && (
                    <div className="error-container">
                        <div className="error">{error}</div>
                        <button onClick={loadTransactions}>Retry</button>
                    </div>
                )}

                {!loading && !error && transactions.length === 0 && (
                    <div className="empty-state">No transactions found.</div>
                )}

                {!loading && !error && transactions.length > 0 && (
                    <table className="transaction-table">
                        <thead>
                            <tr>
                                <th style={{ width: 32 }}></th>
                                <th>Date</th>
                                <th>Type</th>
                                <th>Summary</th>
                                <th className="actions-column"></th>
                            </tr>
                        </thead>
                        <tbody>
                            {transactions.map(tx => {
                                const isExpanded = expandedRows.has(tx.id);
                                return (
                                    <>
                                        <tr key={tx.id} className="transaction-row">
                                            <td className="expand-toggle" onClick={() => toggleExpand(tx.id)}>
                                                {isExpanded ? '▼' : '▶'}
                                            </td>
                                            <td>{tx.dateTime.substring(0, 10)}</td>
                                            <td>
                                                <span className={`type-badge ${tx.type}`}>
                                                    {formatType(tx.type)}
                                                </span>
                                            </td>
                                            <td className="transaction-summary">
                                                {transactionSummary(tx, accountMap)}
                                            </td>
                                            <td className="actions-column">
                                                <button
                                                    className="delete-button"
                                                    onClick={() => { setDeleteError(null); setTransactionToDelete(tx); }}
                                                >
                                                    Delete
                                                </button>
                                            </td>
                                        </tr>
                                        {isExpanded && (
                                            <tr key={tx.id + '-details'} className="record-details-row">
                                                <td colSpan={5}>
                                                    <table className="record-details-table">
                                                        <thead>
                                                            <tr>
                                                                <th>Account</th>
                                                                <th>Fund</th>
                                                                <th>Amount</th>
                                                                <th>Unit</th>
                                                                <th>Labels</th>
                                                            </tr>
                                                        </thead>
                                                        <tbody>
                                                            {tx.records.map(rec => (
                                                                <tr key={rec.id}>
                                                                    <td>{accountMap[rec.accountId]?.name ?? rec.accountId}</td>
                                                                    <td>{fundMap[rec.fundId]?.name ?? rec.fundId}</td>
                                                                    <td>{rec.amount}</td>
                                                                    <td>
                                                                        <span className={`type-badge ${rec.unitType}`}>
                                                                            {rec.unitValue}
                                                                        </span>
                                                                    </td>
                                                                    <td>
                                                                        {rec.labels.map((l, i) => (
                                                                            <span key={i} className="label-badge">{l}</span>
                                                                        ))}
                                                                    </td>
                                                                </tr>
                                                            ))}
                                                        </tbody>
                                                    </table>
                                                </td>
                                            </tr>
                                        )}
                                    </>
                                );
                            })}
                        </tbody>
                    </table>
                )}
            </div>

            {showCreateModal && (
                <div className="modal-overlay" onClick={() => setShowCreateModal(false)}>
                    <div className="modal" onClick={e => e.stopPropagation()}>
                        <h2>Create Transaction</h2>
                        <form onSubmit={handleCreate}>
                            <div className="form-group">
                                <label htmlFor="txType">Type</label>
                                <select
                                    id="txType"
                                    value={createType}
                                    onChange={e => handleTypeChange(e.target.value)}
                                    disabled={creating}
                                >
                                    {Object.keys(RECORD_CONFIGS).map(t => (
                                        <option key={t} value={t}>{formatType(t)}</option>
                                    ))}
                                </select>
                            </div>
                            <div className="form-group">
                                <label htmlFor="txDateTime">Date / Time</label>
                                <input
                                    id="txDateTime"
                                    type="datetime-local"
                                    value={createDateTime}
                                    onChange={e => setCreateDateTime(e.target.value)}
                                    disabled={creating}
                                />
                            </div>
                            <div className="form-group">
                                <label htmlFor="txExternalId">External ID (optional)</label>
                                <input
                                    id="txExternalId"
                                    type="text"
                                    value={createExternalId}
                                    onChange={e => setCreateExternalId(e.target.value)}
                                    disabled={creating}
                                    placeholder="Leave blank for auto-generated"
                                />
                            </div>

                            {(RECORD_CONFIGS[createType] || []).map(config => (
                                <div key={config.role} className="record-section">
                                    <div className="record-section-title">
                                        {config.label} {!config.required && '(optional)'}
                                    </div>
                                    <div className="form-group">
                                        <label>Account</label>
                                        <select
                                            value={createRecords[config.role]?.accountId ?? ''}
                                            onChange={e => updateRecordField(config.role, 'accountId', e.target.value)}
                                            disabled={creating}
                                        >
                                            <option value="">Select account</option>
                                            {filteredAccounts(config.accountFilter).map(a => (
                                                <option key={a.id} value={a.id}>
                                                    {a.name} ({a.unitValue})
                                                </option>
                                            ))}
                                        </select>
                                    </div>
                                    <div className="form-group">
                                        <label>Fund</label>
                                        <select
                                            value={createRecords[config.role]?.fundId ?? ''}
                                            onChange={e => updateRecordField(config.role, 'fundId', e.target.value)}
                                            disabled={creating}
                                        >
                                            <option value="">Select fund</option>
                                            {funds.map(f => (
                                                <option key={f.id} value={f.id}>{f.name}</option>
                                            ))}
                                        </select>
                                    </div>
                                    <div className="form-group">
                                        <label>Amount</label>
                                        <input
                                            type="text"
                                            value={createRecords[config.role]?.amount ?? ''}
                                            onChange={e => updateRecordField(config.role, 'amount', e.target.value)}
                                            disabled={creating}
                                            placeholder="0.00"
                                        />
                                    </div>
                                </div>
                            ))}

                            {createError && <div className="error">{createError}</div>}
                            <div className="modal-actions">
                                <button
                                    type="button"
                                    className="cancel-button"
                                    onClick={() => setShowCreateModal(false)}
                                    disabled={creating}
                                >
                                    Cancel
                                </button>
                                <button type="submit" className="submit-button" disabled={creating}>
                                    {creating ? 'Creating...' : 'Create'}
                                </button>
                            </div>
                        </form>
                    </div>
                </div>
            )}

            {transactionToDelete && (
                <div className="modal-overlay" onClick={() => !deleting && setTransactionToDelete(null)}>
                    <div className="modal" onClick={e => e.stopPropagation()}>
                        <h2>Delete Transaction</h2>
                        <p className="delete-message">
                            Are you sure you want to delete this <strong>{formatType(transactionToDelete.type)}</strong> transaction
                            from <strong>{transactionToDelete.dateTime.substring(0, 10)}</strong>?
                        </p>
                        {deleteError && <div className="error">{deleteError}</div>}
                        <div className="modal-actions">
                            <button
                                type="button"
                                className="cancel-button"
                                onClick={() => setTransactionToDelete(null)}
                                disabled={deleting}
                            >
                                Cancel
                            </button>
                            <button
                                type="button"
                                className="danger-button"
                                onClick={handleDelete}
                                disabled={deleting}
                            >
                                {deleting ? 'Deleting...' : 'Delete'}
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
}

export default TransactionsPage;
