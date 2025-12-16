import { useState } from 'react';
import '../styles/LoginPage.css';

declare const ro: {
    jf: {
        funds: {
            client: {
                web: {
                    FundsApi: {
                        loginWithUsername(username: string): Promise<{ id: string; username: string } | null>;
                    };
                };
            };
        };
    };
};

interface LoginPageProps {
    onLogin: (userId: string) => void;
}

function LoginPage({ onLogin }: LoginPageProps) {
    const [username, setUsername] = useState('');
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();

        if (!username.trim()) {
            setError('Username cannot be empty');
            return;
        }

        setLoading(true);
        setError(null);

        try {
            const user = await ro.jf.funds.client.web.FundsApi.loginWithUsername(username);
            if (user) {
                onLogin(user.id);
            } else {
                setError('User not found');
            }
        } catch (err) {
            setError('Login failed: ' + (err instanceof Error ? err.message : 'Unknown error'));
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="login-container">
            <div className="login-card">
                <h1>Funds Login</h1>
                <form onSubmit={handleSubmit}>
                    <div className="form-group">
                        <label htmlFor="username">Username</label>
                        <input
                            id="username"
                            type="text"
                            value={username}
                            onChange={(e) => setUsername(e.target.value)}
                            disabled={loading}
                            placeholder="Enter your username"
                        />
                    </div>
                    {error && <div className="error">{error}</div>}
                    <button type="submit" disabled={loading}>
                        {loading ? 'Logging in...' : 'Login'}
                    </button>
                </form>
            </div>
        </div>
    );
}

export default LoginPage;
