export interface JsReportView {
    id: string;
    name: string;
}

export interface JsGroupedBudgetReport {
    viewId: string;
    timeBuckets: JsBucketData[];
}

export interface JsBucketData {
    label: string;
    bucketType: string;
    groups: JsGroupBudget[];
}

export interface JsGroupBudget {
    group: string;
    allocated: number;
    spent: number;
    left: number;
}

export interface ChartDataPoint {
    label: string;
    bucketType: string;
    allocated: number;
    spent: number;
    left: number;
}
