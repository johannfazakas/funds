import { useEffect, useState } from 'react';
import '../styles/AccountsPage.css';

declare const ro: {
    jf: {
        funds: {
            client: {
                web: {
                    AccountApi: {
                        listAccounts(userId: string): Promise<Array<{ id: string; name: string; unitType: string; unitValue: string }>>;
                        createAccount(userId: string, name: string, unitType: string, unitValue: string): Promise<{ id: string; name: string; unitType: string; unitValue: string }>;
                        deleteAccount(userId: string, accountId: string): Promise<void>;
                    };
                };
            };
        };
    };
};

interface Account {
    id: string;
    name: string;
    unitType: string;
    unitValue: string;
}

interface AccountsPageProps {
    userId: string;
}

function AccountsPage({ userId }: AccountsPageProps) {
    const [accounts, setAccounts] = useState<Account[]>([]);
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

    useEffect(() => {
        loadAccounts();
    }, [userId]);

    const loadAccounts = async () => {
        setLoading(true);
        setError(null);

        try {
            const accountList = await ro.jf.funds.client.web.AccountApi.listAccounts(userId);
            setAccounts(accountList);
        } catch (err) {
            setError('Failed to load accounts: ' + (err instanceof Error ? err.message : 'Unknown error'));
        } finally {
            setLoading(false);
        }
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
            await ro.jf.funds.client.web.AccountApi.createAccount(
                userId,
                newAccountName.trim(),
                newUnitType,
                newUnitValue.trim().toUpperCase(),
            );
            setShowCreateModal(false);
            setNewAccountName('');
            setNewUnitType('currency');
            setNewUnitValue('');
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
            await ro.jf.funds.client.web.AccountApi.deleteAccount(userId, accountToDelete.id);
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
        <div className="account-list-container">
            <div className="page-header">
                <h1>Accounts</h1>
                <button className="create-button" onClick={openCreateModal}>Create Account</button>
            </div>

            <div className="main-content">
                {loading && <div className="loading">Loading...</div>}

                {error && (
                    <div className="error-container">
                        <div className="error">{error}</div>
                        <button onClick={loadAccounts}>Retry</button>
                    </div>
                )}

                {!loading && !error && accounts.length === 0 && (
                    <div className="empty-state">No accounts yet — create one to get started.</div>
                )}

                {!loading && !error && accounts.length > 0 && (
                    <table className="account-table">
                        <thead>
                            <tr>
                                <th>Name</th>
                                <th>Unit</th>
                                <th className="actions-column"></th>
                            </tr>
                        </thead>
                        <tbody>
                            {accounts.map((account) => (
                                <tr key={account.id}>
                                    <td>{account.name}</td>
                                    <td>
                                        <span className={`unit-badge ${account.unitType}`}>
                                            {account.unitValue}
                                        </span>
                                    </td>
                                    <td className="actions-column">
                                        <button
                                            className="delete-button"
                                            onClick={() => { setDeleteError(null); setAccountToDelete(account); }}
                                        >
                                            Delete
                                        </button>
                                    </td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                )}
            </div>

            {showCreateModal && (
                <div className="modal-overlay" onClick={() => setShowCreateModal(false)}>
                    <div className="modal" onClick={(e) => e.stopPropagation()}>
                        <h2>Create Account</h2>
                        <form onSubmit={handleCreate}>
                            <div className="form-group">
                                <label htmlFor="accountName">Account name</label>
                                <input
                                    id="accountName"
                                    type="text"
                                    value={newAccountName}
                                    onChange={(e) => setNewAccountName(e.target.value)}
                                    disabled={creating}
                                    placeholder="Enter account name"
                                    autoFocus
                                />
                            </div>
                            <div className="form-group">
                                <label htmlFor="unitType">Unit type</label>
                                <select
                                    id="unitType"
                                    value={newUnitType}
                                    onChange={(e) => setNewUnitType(e.target.value)}
                                    disabled={creating}
                                >
                                    <option value="currency">Currency</option>
                                    <option value="instrument">Instrument</option>
                                </select>
                            </div>
                            <div className="form-group">
                                <label htmlFor="unitValue">Unit value</label>
                                <input
                                    id="unitValue"
                                    type="text"
                                    value={newUnitValue}
                                    onChange={(e) => setNewUnitValue(e.target.value)}
                                    disabled={creating}
                                    placeholder={newUnitType === 'currency' ? 'e.g. RON, EUR, USD' : 'e.g. BTC, AAPL'}
                                />
                            </div>
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

            {accountToDelete && (
                <div className="modal-overlay" onClick={() => !deleting && setAccountToDelete(null)}>
                    <div className="modal" onClick={(e) => e.stopPropagation()}>
                        <h2>Delete Account</h2>
                        <p className="delete-message">Are you sure you want to delete "<strong>{accountToDelete.name}</strong>"?</p>
                        {deleteError && <div className="error">{deleteError}</div>}
                        <div className="modal-actions">
                            <button
                                type="button"
                                className="cancel-button"
                                onClick={() => setAccountToDelete(null)}
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

export default AccountsPage;
