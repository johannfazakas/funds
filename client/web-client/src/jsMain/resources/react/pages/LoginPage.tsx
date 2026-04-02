import { useState } from 'react';
import { Card, CardContent, CardHeader, CardTitle } from '../components/ui/card';
import { Button } from '../components/ui/button';
import { Input } from '../components/ui/input';
import { Label } from '../components/ui/label';
import { Loader2 } from 'lucide-react';

declare const ro: {
    jf: {
        funds: {
            client: {
                web: {
                    UserApi: {
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
            const user = await ro.jf.funds.client.web.UserApi.loginWithUsername(username);
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
        <div className="min-h-screen flex items-center justify-center bg-muted/40">
            <Card className="w-96">
                <CardHeader>
                    <CardTitle className="text-2xl text-center">Funds Login</CardTitle>
                </CardHeader>
                <CardContent>
                    <form onSubmit={handleSubmit} className="space-y-4">
                        <div className="space-y-2">
                            <Label htmlFor="username">Username</Label>
                            <Input
                                id="username"
                                type="text"
                                value={username}
                                onChange={(e) => setUsername(e.target.value)}
                                disabled={loading}
                                placeholder="Enter your username"
                            />
                        </div>
                        {error && (
                            <div className="p-3 text-sm text-destructive bg-destructive/10 rounded-md">
                                {error}
                            </div>
                        )}
                        <Button type="submit" className="w-full" disabled={loading}>
                            {loading ? (
                                <>
                                    <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                                    Logging in...
                                </>
                            ) : (
                                'Login'
                            )}
                        </Button>
                    </form>
                </CardContent>
            </Card>
        </div>
    );
}

export default LoginPage;
