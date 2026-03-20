import { useState } from 'react';
import {
    XAxis,
    YAxis,
    CartesianGrid,
    Tooltip,
    Legend,
    ResponsiveContainer,
    ComposedChart,
    Line,
} from 'recharts';

export interface ValueChartDataPoint {
    label: string;
    netChange: number;
    balance: number;
}

type SeriesKey = 'netChange' | 'balance';

const seriesConfig: Record<SeriesKey, { name: string; color: string }> = {
    balance: { name: 'Balance', color: '#2563eb' },
    netChange: { name: 'Net Change', color: '#16a34a' },
};

interface ValueChartProps {
    title: string;
    data: ValueChartDataPoint[];
}

function ValueChart({ title, data }: ValueChartProps) {
    const [hiddenSeries, setHiddenSeries] = useState<Set<SeriesKey>>(new Set());

    const handleLegendClick = (dataKey: string) => {
        const key = dataKey as SeriesKey;
        setHiddenSeries(prev => {
            const next = new Set(prev);
            if (next.has(key)) {
                next.delete(key);
            } else {
                next.add(key);
            }
            return next;
        });
    };

    const isHidden = (key: SeriesKey) => hiddenSeries.has(key);

    return (
        <div className="w-full">
            <h3 className="text-lg font-semibold mb-4 text-center">{title}</h3>
            <div style={{ height: '400px', width: '100%' }}>
                <ResponsiveContainer width="100%" height="100%">
                    <ComposedChart data={data} margin={{ top: 5, right: 30, left: 20, bottom: 5 }}>
                        <CartesianGrid strokeDasharray="3 3" className="stroke-muted" />
                        <XAxis
                            dataKey="label"
                            className="text-muted-foreground"
                            tick={{ fill: 'currentColor' }}
                        />
                        <YAxis
                            className="text-muted-foreground"
                            tick={{ fill: 'currentColor' }}
                        />
                        <Tooltip
                            contentStyle={{
                                backgroundColor: 'hsl(var(--card))',
                                border: '1px solid hsl(var(--border))',
                                borderRadius: 'var(--radius)',
                                color: 'hsl(var(--card-foreground))'
                            }}
                        />
                        <Legend
                            onClick={(e) => handleLegendClick(e.dataKey as string)}
                            wrapperStyle={{ cursor: 'pointer' }}
                            formatter={(value, entry) => (
                                <span style={{
                                    color: isHidden(entry.dataKey as SeriesKey)
                                        ? 'hsl(var(--muted-foreground))'
                                        : entry.color,
                                    textDecoration: isHidden(entry.dataKey as SeriesKey)
                                        ? 'line-through'
                                        : 'none'
                                }}>
                                    {value}
                                </span>
                            )}
                        />
                        <Line
                            type="monotone"
                            dataKey="balance"
                            name="Balance"
                            stroke={seriesConfig.balance.color}
                            strokeWidth={2}
                            dot={{ fill: seriesConfig.balance.color }}
                            hide={isHidden('balance')}
                        />
                        <Line
                            type="monotone"
                            dataKey="netChange"
                            name="Net Change"
                            stroke={seriesConfig.netChange.color}
                            strokeWidth={2}
                            dot={{ fill: seriesConfig.netChange.color }}
                            hide={isHidden('netChange')}
                        />
                    </ComposedChart>
                </ResponsiveContainer>
            </div>
        </div>
    );
}

export default ValueChart;
