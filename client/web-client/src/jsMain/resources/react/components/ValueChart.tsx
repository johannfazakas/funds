import {
    XAxis,
    YAxis,
    CartesianGrid,
    Tooltip,
    ResponsiveContainer,
    ComposedChart,
    Area,
} from 'recharts';

export interface ValueChartDataPoint {
    label: string;
    value: number;
}

interface ValueChartProps {
    title: string;
    data: ValueChartDataPoint[];
    seriesName: string;
    seriesColor: string;
    currency?: string;
}

function formatCompact(value: number): string {
    return Intl.NumberFormat(undefined, { notation: 'compact', maximumFractionDigits: 1 }).format(value);
}

function formatFull(value: number): string {
    return Intl.NumberFormat(undefined, { maximumFractionDigits: 0 }).format(value);
}

function ValueChart({ title, data, seriesName, seriesColor, currency }: ValueChartProps) {
    const prefix = currency ? `${currency} ` : '';

    return (
        <div className="w-full">
            <h3 className="text-lg font-semibold mb-4 text-center">{title}</h3>
            <div style={{ height: '400px', width: '100%' }}>
                <ResponsiveContainer width="100%" height="100%">
                    <ComposedChart data={data} margin={{ top: 5, right: 30, left: 20, bottom: 5 }}>
                        <CartesianGrid strokeDasharray="none" stroke="hsl(var(--border))" strokeWidth={0.5} />
                        <XAxis
                            dataKey="label"
                            className="text-muted-foreground"
                            tick={{ fill: 'currentColor', fontSize: 11 }}
                        />
                        <YAxis
                            className="text-muted-foreground"
                            tick={{ fill: 'currentColor', fontSize: 11 }}
                            domain={[0, 'auto']}
                            tickFormatter={(v) => `${prefix}${formatCompact(v)}`}
                        />
                        <Tooltip
                            contentStyle={{
                                backgroundColor: 'hsl(var(--card))',
                                border: '1px solid hsl(var(--border))',
                                borderRadius: 'var(--radius)',
                                color: 'hsl(var(--card-foreground))'
                            }}
                            formatter={(value: number) => [`${prefix}${formatFull(value)}`, seriesName]}
                        />
                        <Area
                            type="linear"
                            dataKey="value"
                            name={seriesName}
                            stroke={seriesColor}
                            fill={seriesColor + '30'}
                            strokeWidth={1}
                            dot={false}
                        />
                    </ComposedChart>
                </ResponsiveContainer>
            </div>
        </div>
    );
}

export default ValueChart;
