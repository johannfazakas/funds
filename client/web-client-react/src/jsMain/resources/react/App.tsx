import { BrowserRouter, Routes, Route, Navigate, Outlet } from 'react-router-dom';
import { useState } from 'react';
import LoginPage from './pages/LoginPage';
import FundListPage from './pages/FundListPage';
import AccountsPage from './pages/AccountsPage';
import TransactionsPage from './pages/TransactionsPage';
import ExpensesPage from './pages/ExpensesPage';
import Sidebar from './components/Sidebar';

function App() {
    const [userId, setUserId] = useState<string | null>(
        localStorage.getItem('userId')
    );

    const handleLogin = (id: string) => {
        localStorage.setItem('userId', id);
        setUserId(id);
    };

    const handleLogout = () => {
        localStorage.removeItem('userId');
        setUserId(null);
    };

    return (
        <BrowserRouter>
            <Routes>
                <Route
                    path="/login"
                    element={
                        userId ? <Navigate to="/funds" /> : <LoginPage onLogin={handleLogin} />
                    }
                />
                <Route
                    element={
                        userId ? (
                            <div className="flex min-h-screen">
                                <Sidebar onLogout={handleLogout} />
                                <main className="flex-1 ml-56 min-h-screen bg-muted/40 p-6">
                                    <Outlet />
                                </main>
                            </div>
                        ) : (
                            <Navigate to="/login" />
                        )
                    }
                >
                    <Route path="/funds" element={<FundListPage userId={userId!} />} />
                    <Route path="/accounts" element={<AccountsPage userId={userId!} />} />
                    <Route path="/transactions" element={<TransactionsPage userId={userId!} />} />
                    <Route path="/expenses" element={<ExpensesPage userId={userId!} />} />
                </Route>
                <Route path="*" element={<Navigate to="/login" />} />
            </Routes>
        </BrowserRouter>
    );
}

export default App;
