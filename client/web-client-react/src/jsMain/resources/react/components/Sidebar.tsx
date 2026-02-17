import { NavLink } from 'react-router-dom';
import { Button } from './ui/button';
import { cn } from '../lib/utils';
import ThemeToggle from './ThemeToggle';

interface SidebarProps {
    onLogout: () => void;
}

function Sidebar({ onLogout }: SidebarProps) {
    return (
        <aside className="fixed left-0 top-0 w-56 h-screen bg-card border-r flex flex-col">
            <div className="flex-1 p-4">
                <h1 className="text-xl font-bold mb-6 px-2">Funds</h1>

                <nav className="space-y-1">
                    <p className="px-2 py-1.5 text-xs font-semibold text-muted-foreground uppercase tracking-wider">
                        Data
                    </p>
                    <NavLink
                        to="/funds"
                        className={({ isActive }) =>
                            cn(
                                "flex items-center px-2 py-1.5 text-sm rounded-md transition-colors",
                                isActive
                                    ? "bg-accent text-accent-foreground font-medium"
                                    : "text-muted-foreground hover:bg-accent hover:text-accent-foreground"
                            )
                        }
                    >
                        Funds
                    </NavLink>
                    <NavLink
                        to="/accounts"
                        className={({ isActive }) =>
                            cn(
                                "flex items-center px-2 py-1.5 text-sm rounded-md transition-colors",
                                isActive
                                    ? "bg-accent text-accent-foreground font-medium"
                                    : "text-muted-foreground hover:bg-accent hover:text-accent-foreground"
                            )
                        }
                    >
                        Accounts
                    </NavLink>
                    <NavLink
                        to="/records"
                        className={({ isActive }) =>
                            cn(
                                "flex items-center px-2 py-1.5 text-sm rounded-md transition-colors",
                                isActive
                                    ? "bg-accent text-accent-foreground font-medium"
                                    : "text-muted-foreground hover:bg-accent hover:text-accent-foreground"
                            )
                        }
                    >
                        Records
                    </NavLink>
                    <NavLink
                        to="/labels"
                        className={({ isActive }) =>
                            cn(
                                "flex items-center px-2 py-1.5 text-sm rounded-md transition-colors",
                                isActive
                                    ? "bg-accent text-accent-foreground font-medium"
                                    : "text-muted-foreground hover:bg-accent hover:text-accent-foreground"
                            )
                        }
                    >
                        Labels
                    </NavLink>

                    <p className="px-2 py-1.5 mt-4 text-xs font-semibold text-muted-foreground uppercase tracking-wider">
                        Reports
                    </p>
                    <NavLink
                        to="/expenses"
                        className={({ isActive }) =>
                            cn(
                                "flex items-center px-2 py-1.5 text-sm rounded-md transition-colors",
                                isActive
                                    ? "bg-accent text-accent-foreground font-medium"
                                    : "text-muted-foreground hover:bg-accent hover:text-accent-foreground"
                            )
                        }
                    >
                        Expenses
                    </NavLink>
                </nav>
            </div>

            <div className="p-4 border-t flex items-center justify-between gap-2">
                <Button variant="ghost" size="sm" className="flex-1" onClick={onLogout}>
                    Logout
                </Button>
                <ThemeToggle />
            </div>
        </aside>
    );
}

export default Sidebar;
