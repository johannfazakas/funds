import { useEffect, useState } from 'react';
import '../styles/FundListPage.css';

declare const ro: {
    jf: {
        funds: {
            client: {
                web: {
                    FundsApi: {
                        listFunds(userId: string): Promise<Array<{ id: string; name: string }>>;
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
    onLogout: () => void;
}

function FundListPage({ userId, onLogout }: FundListPageProps) {
    const [funds, setFunds] = useState<Fund[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        loadFunds();
    }, [userId]);

    const loadFunds = async () => {
        setLoading(true);
        setError(null);

        try {
            const fundList = await ro.jf.funds.client.web.FundsApi.listFunds(userId);
            setFunds(fundList);
        } catch (err) {
            setError('Failed to load funds: ' + (err instanceof Error ? err.message : 'Unknown error'));
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="fund-list-container">
            <header className="header">
                <h1>My Funds</h1>
                <button onClick={onLogout} className="logout-button">
                    Logout
                </button>
            </header>

            <main className="main-content">
                {loading && <div className="loading">Loading...</div>}

                {error && (
                    <div className="error-container">
                        <div className="error">{error}</div>
                        <button onClick={loadFunds}>Retry</button>
                    </div>
                )}

                {!loading && !error && funds.length === 0 && (
                    <div className="empty-state">No funds available</div>
                )}

                {!loading && !error && funds.length > 0 && (
                    <div className="fund-grid">
                        {funds.map((fund) => (
                            <div key={fund.id} className="fund-card">
                                <h3>{fund.name}</h3>
                                <p className="fund-id">ID: {fund.id}</p>
                            </div>
                        ))}
                    </div>
                )}
            </main>
        </div>
    );
}

export default FundListPage;
