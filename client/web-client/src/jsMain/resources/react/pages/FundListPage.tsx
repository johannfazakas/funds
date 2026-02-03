import { useEffect, useState } from 'react';
import '../styles/FundListPage.css';

declare const ro: {
    jf: {
        funds: {
            client: {
                web: {
                    FundApi: {
                        listFunds(userId: string): Promise<Array<{ id: string; name: string }>>;
                        createFund(userId: string, name: string): Promise<{ id: string; name: string }>;
                        deleteFund(userId: string, fundId: string): Promise<void>;
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

interface FundListPageProps {
    userId: string;
}

function FundListPage({ userId }: FundListPageProps) {
    const [funds, setFunds] = useState<Fund[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [showCreateModal, setShowCreateModal] = useState(false);
    const [newFundName, setNewFundName] = useState('');
    const [creating, setCreating] = useState(false);
    const [createError, setCreateError] = useState<string | null>(null);
    const [fundToDelete, setFundToDelete] = useState<Fund | null>(null);
    const [deleting, setDeleting] = useState(false);
    const [deleteError, setDeleteError] = useState<string | null>(null);

    useEffect(() => {
        loadFunds();
    }, [userId]);

    const loadFunds = async () => {
        setLoading(true);
        setError(null);

        try {
            const fundList = await ro.jf.funds.client.web.FundApi.listFunds(userId);
            setFunds(fundList);
        } catch (err) {
            setError('Failed to load funds: ' + (err instanceof Error ? err.message : 'Unknown error'));
        } finally {
            setLoading(false);
        }
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
            await ro.jf.funds.client.web.FundApi.createFund(userId, newFundName.trim());
            setShowCreateModal(false);
            setNewFundName('');
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
            await ro.jf.funds.client.web.FundApi.deleteFund(userId, fundToDelete.id);
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
        <div className="fund-list-container">
            <div className="page-header">
                <h1>Funds</h1>
                <button className="create-button" onClick={openCreateModal}>Create Fund</button>
            </div>

            <div className="main-content">
                {loading && <div className="loading">Loading...</div>}

                {error && (
                    <div className="error-container">
                        <div className="error">{error}</div>
                        <button onClick={loadFunds}>Retry</button>
                    </div>
                )}

                {!loading && !error && funds.length === 0 && (
                    <div className="empty-state">No funds yet — create one to get started.</div>
                )}

                {!loading && !error && funds.length > 0 && (
                    <table className="fund-table">
                        <thead>
                            <tr>
                                <th>Name</th>
                                <th className="actions-column"></th>
                            </tr>
                        </thead>
                        <tbody>
                            {funds.map((fund) => (
                                <tr key={fund.id}>
                                    <td>{fund.name}</td>
                                    <td className="actions-column">
                                        <button
                                            className="delete-button"
                                            onClick={() => { setDeleteError(null); setFundToDelete(fund); }}
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
                        <h2>Create Fund</h2>
                        <form onSubmit={handleCreate}>
                            <div className="form-group">
                                <label htmlFor="fundName">Fund name</label>
                                <input
                                    id="fundName"
                                    type="text"
                                    value={newFundName}
                                    onChange={(e) => setNewFundName(e.target.value)}
                                    disabled={creating}
                                    placeholder="Enter fund name"
                                    autoFocus
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

            {fundToDelete && (
                <div className="modal-overlay" onClick={() => !deleting && setFundToDelete(null)}>
                    <div className="modal" onClick={(e) => e.stopPropagation()}>
                        <h2>Delete Fund</h2>
                        <p className="delete-message">Are you sure you want to delete "<strong>{fundToDelete.name}</strong>"?</p>
                        {deleteError && <div className="error">{deleteError}</div>}
                        <div className="modal-actions">
                            <button
                                type="button"
                                className="cancel-button"
                                onClick={() => setFundToDelete(null)}
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

export default FundListPage;
