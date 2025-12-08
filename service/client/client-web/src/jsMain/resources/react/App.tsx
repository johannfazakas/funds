import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { useState } from 'react';
import LoginPage from './pages/LoginPage';
import FundListPage from './pages/FundListPage';

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
                    path="/funds"
                    element={
                        userId ? (
                            <FundListPage userId={userId} onLogout={handleLogout} />
                        ) : (
                            <Navigate to="/login" />
                        )
                    }
                />
                <Route path="*" element={<Navigate to="/login" />} />
            </Routes>
        </BrowserRouter>
    );
}

export default App;
