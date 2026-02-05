import { NavLink } from 'react-router-dom';
import '../styles/Sidebar.css';

interface SidebarProps {
    onLogout: () => void;
}

function Sidebar({ onLogout }: SidebarProps) {
    return (
        <aside className="sidebar">
            <div className="sidebar-top">
                <h1 className="sidebar-title">Funds</h1>

                <nav className="sidebar-nav">
                    <div className="sidebar-section">
                        <h2 className="sidebar-section-title">Data</h2>
                        <NavLink to="/funds" className={({ isActive }) => `sidebar-link ${isActive ? 'active' : ''}`}>
                            Funds
                        </NavLink>
                        <NavLink to="/accounts" className={({ isActive }) => `sidebar-link ${isActive ? 'active' : ''}`}>
                            Accounts
                        </NavLink>
                        <NavLink to="/transactions" className={({ isActive }) => `sidebar-link ${isActive ? 'active' : ''}`}>
                            Transactions
                        </NavLink>
                    </div>

                    <div className="sidebar-section">
                        <h2 className="sidebar-section-title">Reports</h2>
                        <NavLink to="/expenses" className={({ isActive }) => `sidebar-link ${isActive ? 'active' : ''}`}>
                            Expenses
                        </NavLink>
                    </div>
                </nav>
            </div>

            <div className="sidebar-bottom">
                <button onClick={onLogout} className="sidebar-logout">
                    Logout
                </button>
            </div>
        </aside>
    );
}

export default Sidebar;
