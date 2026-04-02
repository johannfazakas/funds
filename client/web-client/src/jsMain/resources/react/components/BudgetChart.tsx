import { useState } from 'react';
import {
    XAxis,
    YAxis,
    CartesianGrid,
    Tooltip,
    Legend,
    ResponsiveContainer,
    Area,
    ComposedChart,
    Line,
} from 'recharts';
import { ChartDataPoint } from '../types/reporting';

interface BudgetChartProps {
    title: string;
    data: ChartDataPoint[];
}

type SeriesKey = 'spent' | 'allocated' | 'left';

const seriesConfig: Record<SeriesKey, { name: string; color: string }> = {
    spent: { name: 'Spent', color: '#dc3545' },
    allocated: { name: 'Allocated', color: '#28a745' },
    left: { name: 'Left', color: '#ffa500' },
};

function BudgetChart({ title, data }: BudgetChartProps) {
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
                        <Area
                            type="monotone"
                            dataKey="left"
                            name="Left"
                            stroke={seriesConfig.left.color}
                            fill="rgba(255, 165, 0, 0.2)"
                            strokeWidth={2}
                            hide={isHidden('left')}
                        />
                        <Line
                            type="monotone"
                            dataKey="spent"
                            name="Spent"
                            stroke={seriesConfig.spent.color}
                            strokeWidth={2}
                            dot={{ fill: seriesConfig.spent.color }}
                            hide={isHidden('spent')}
                        />
                        <Line
                            type="monotone"
                            dataKey="allocated"
                            name="Allocated"
                            stroke={seriesConfig.allocated.color}
                            strokeWidth={2}
                            dot={{ fill: seriesConfig.allocated.color }}
                            hide={isHidden('allocated')}
                        />
                    </ComposedChart>
                </ResponsiveContainer>
            </div>
        </div>
    );
}

export default BudgetChart;
