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

export interface GroupedValueChartDataPoint {
    label: string;
    [groupName: string]: string | number;
}

interface GroupedValueChartProps {
    title: string;
    data: GroupedValueChartDataPoint[];
    groups: string[];
}

const COLORS = [
    '#2563eb', '#dc2626', '#16a34a', '#d97706', '#7c3aed',
    '#0891b2', '#db2777', '#65a30d', '#ea580c', '#6366f1',
    '#0d9488', '#ca8a04',
];

function GroupedValueChart({ title, data, groups }: GroupedValueChartProps) {
    const [hiddenGroups, setHiddenGroups] = useState<Set<string>>(new Set());

    const handleLegendClick = (dataKey: string) => {
        setHiddenGroups(prev => {
            const next = new Set(prev);
            if (next.has(dataKey)) {
                next.delete(dataKey);
            } else {
                next.add(dataKey);
            }
            return next;
        });
    };

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
                                    color: hiddenGroups.has(entry.dataKey as string)
                                        ? 'hsl(var(--muted-foreground))'
                                        : entry.color,
                                    textDecoration: hiddenGroups.has(entry.dataKey as string)
                                        ? 'line-through'
                                        : 'none'
                                }}>
                                    {value}
                                </span>
                            )}
                        />
                        {groups.map((group, index) => (
                            <Line
                                key={group}
                                type="monotone"
                                dataKey={group}
                                name={group}
                                stroke={COLORS[index % COLORS.length]}
                                strokeWidth={2}
                                dot={false}
                                hide={hiddenGroups.has(group)}
                            />
                        ))}
                    </ComposedChart>
                </ResponsiveContainer>
            </div>
        </div>
    );
}

export default GroupedValueChart;
