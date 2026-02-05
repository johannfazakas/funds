import { useEffect, useState } from 'react';
import BudgetChart from '../components/BudgetChart';
import { JsReportView, JsGroupedBudgetReport, ChartDataPoint } from '../types/reporting';
import { transformToTotalChartData, transformToGroupChartData, getAvailableGroups } from '../utils/chartUtils';
import { Button } from '../components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '../components/ui/card';
import { Loader2 } from 'lucide-react';

declare const ro: {
    jf: {
        funds: {
            client: {
                web: {
                    ReportingApi: {
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
}

function ExpensesPage({ userId }: ExpensesPageProps) {
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
            const views = await ro.jf.funds.client.web.ReportingApi.listReportViews(userId);
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
            const interval = ro.jf.funds.client.web.ReportingApi.yearlyInterval(fromYear, toYear, forecastUntilYear);
            const data = await ro.jf.funds.client.web.ReportingApi.getGroupedBudgetData(
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
        <div>
            <h1 className="text-2xl font-bold mb-6">Expenses</h1>

            {loading && (
                <div className="flex justify-center p-8">
                    <Loader2 className="h-8 w-8 animate-spin text-muted-foreground" />
                </div>
            )}

            {error && (
                <div className="flex items-center gap-4 p-4 mb-4 text-destructive bg-destructive/10 rounded-md">
                    <span>{error}</span>
                    {reportViewId && <Button variant="outline" size="sm" onClick={loadData}>Retry</Button>}
                </div>
            )}

            {!loading && !error && report && (
                <>
                    <Card className="mb-6">
                        <CardContent className="pt-6">
                            <BudgetChart title="Total Expenses" data={totalChartData} />
                        </CardContent>
                    </Card>

                    <Card className="mb-6">
                        <CardHeader>
                            <CardTitle className="text-lg">Select Group</CardTitle>
                        </CardHeader>
                        <CardContent>
                            <div className="flex flex-wrap gap-2">
                                {groups.map(group => (
                                    <Button
                                        key={group}
                                        variant={selectedGroup === group ? 'default' : 'ghost'}
                                        size="sm"
                                        onClick={() => setSelectedGroup(group)}
                                    >
                                        {group}
                                    </Button>
                                ))}
                            </div>
                        </CardContent>
                    </Card>

                    {selectedGroup && (
                        <Card>
                            <CardContent className="pt-6">
                                <BudgetChart title={`${selectedGroup} Expenses`} data={groupChartData} />
                            </CardContent>
                        </Card>
                    )}
                </>
            )}
        </div>
    );
}

export default ExpensesPage;
