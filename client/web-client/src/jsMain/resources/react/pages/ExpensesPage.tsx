import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import BudgetChart from '../components/BudgetChart';
import { JsReportView, JsGroupedBudgetReport, ChartDataPoint } from '../types/reporting';
import { transformToTotalChartData, transformToGroupChartData, getAvailableGroups } from '../utils/chartUtils';
import '../styles/ExpensesPage.css';

declare const ro: {
    jf: {
        funds: {
            client: {
                web: {
                    FundsApi: {
                        listReportViews(userId: string): Promise<JsReportView[]>;
                        yearlyInterval(from: number, to: number, forecastUntil: number | null): any;
                        getGroupedBudgetData(
                            userId: string,
                            reportViewId: string,
                            interval: any
                        ): Promise<JsGroupedBudgetReport>;
                    };
                };
            };
        };
    };
};

const EXPENSE_REPORT_VIEW_NAME = 'Expenses report';

interface ExpensesPageProps {
    userId: string;
    onLogout: () => void;
}

function ExpensesPage({ userId, onLogout }: ExpensesPageProps) {
    const [report, setReport] = useState<JsGroupedBudgetReport | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [selectedGroup, setSelectedGroup] = useState<string | null>(null);
    const [groups, setGroups] = useState<string[]>([]);
    const [reportViewId, setReportViewId] = useState<string | null>(null);

    const fromYear = 2020;
    const toYear = 2024;
    const forecastUntilYear = 2025;

    useEffect(() => {
        loadReportView();
    }, [userId]);

    useEffect(() => {
        if (reportViewId) {
            loadData();
        }
    }, [reportViewId]);

    const loadReportView = async () => {
        try {
            const views = await ro.jf.funds.client.web.FundsApi.listReportViews(userId);
            const expenseView = views.find(v => v.name === EXPENSE_REPORT_VIEW_NAME);
            if (expenseView) {
                setReportViewId(expenseView.id);
            } else {
                setError(`Report view "${EXPENSE_REPORT_VIEW_NAME}" not found`);
                setLoading(false);
            }
        } catch (err) {
            setError('Failed to load report views: ' + (err instanceof Error ? err.message : 'Unknown error'));
            setLoading(false);
        }
    };

    const loadData = async () => {
        if (!reportViewId) return;

        setLoading(true);
        setError(null);

        try {
            const interval = ro.jf.funds.client.web.FundsApi.yearlyInterval(fromYear, toYear, forecastUntilYear);
            const data = await ro.jf.funds.client.web.FundsApi.getGroupedBudgetData(
                userId,
                reportViewId,
                interval
            );
            setReport(data);
            const availableGroups = getAvailableGroups(data);
            setGroups(availableGroups);
            if (availableGroups.length > 0 && !selectedGroup) {
                setSelectedGroup(availableGroups[0]);
            }
        } catch (err) {
            setError('Failed to load expense data: ' + (err instanceof Error ? err.message : 'Unknown error'));
        } finally {
            setLoading(false);
        }
    };

    const totalChartData: ChartDataPoint[] = report ? transformToTotalChartData(report) : [];
    const groupChartData: ChartDataPoint[] = report && selectedGroup
        ? transformToGroupChartData(report, selectedGroup)
        : [];

    return (
        <div className="expenses-container">
            <header className="header">
                <h1>Expenses</h1>
                <nav className="nav-links">
                    <Link to="/funds" className="nav-link">Funds</Link>
                    <button onClick={onLogout} className="logout-button">
                        Logout
                    </button>
                </nav>
            </header>

            <main className="main-content">
                {loading && <div className="loading">Loading expense data...</div>}

                {error && (
                    <div className="error-container">
                        <div className="error">{error}</div>
                        {reportViewId && <button onClick={loadData}>Retry</button>}
                    </div>
                )}

                {!loading && !error && report && (
                    <>
                        <section className="chart-section">
                            <BudgetChart title="Total Expenses" data={totalChartData} />
                        </section>

                        <section className="group-selector">
                            <h3>Select Group</h3>
                            <div className="group-buttons">
                                {groups.map(group => (
                                    <button
                                        key={group}
                                        className={`group-button ${selectedGroup === group ? 'active' : ''}`}
                                        onClick={() => setSelectedGroup(group)}
                                    >
                                        {group}
                                    </button>
                                ))}
                            </div>
                        </section>

                        {selectedGroup && (
                            <section className="chart-section">
                                <BudgetChart title={`${selectedGroup} Expenses`} data={groupChartData} />
                            </section>
                        )}
                    </>
                )}
            </main>
        </div>
    );
}

export default ExpensesPage;
