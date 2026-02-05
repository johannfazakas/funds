import { JsGroupedBudgetReport, ChartDataPoint } from '../types/reporting';

export function transformToTotalChartData(report: JsGroupedBudgetReport): ChartDataPoint[] {
    return report.timeBuckets.map(bucket => {
        const totals = bucket.groups.reduce(
            (acc, group) => ({
                allocated: acc.allocated + group.allocated,
                spent: acc.spent + group.spent,
                left: acc.left + group.left,
            }),
            { allocated: 0, spent: 0, left: 0 }
        );
        return {
            label: bucket.label,
            bucketType: bucket.bucketType,
            allocated: totals.allocated,
            spent: totals.spent,
            left: totals.left,
        };
    });
}

export function transformToGroupChartData(report: JsGroupedBudgetReport, groupName: string): ChartDataPoint[] {
    return report.timeBuckets.map(bucket => {
        const group = bucket.groups.find(g => g.group === groupName);
        return {
            label: bucket.label,
            bucketType: bucket.bucketType,
            allocated: group?.allocated ?? 0,
            spent: group?.spent ?? 0,
            left: group?.left ?? 0,
        };
    });
}

export function getAvailableGroups(report: JsGroupedBudgetReport): string[] {
    if (report.timeBuckets.length === 0) {
        return [];
    }
    return report.timeBuckets[0].groups.map(g => g.group);
}
