import { useState } from 'react';
import {
    XAxis,
    YAxis,
    CartesianGrid,
    Tooltip,
    Legend,
    ResponsiveContainer,
    ComposedChart,
    Area,
} from 'recharts';

export interface MultiSeriesChartDataPoint {
    label: string;
    [lineKey: string]: string | number | undefined;
}

export interface ChartLine {
    key: string;
    name: string;
    color: string;
    unit: 'CURRENCY' | 'PERCENTAGE';
}

interface MultiSeriesChartProps {
    data: MultiSeriesChartDataPoint[];
    lines: ChartLine[];
    currency?: string;
}

function formatCompact(value: number): string {
    return Intl.NumberFormat(undefined, { notation: 'compact', maximumFractionDigits: 1 }).format(value);
}

function formatFull(value: number, unit: 'CURRENCY' | 'PERCENTAGE'): string {
    return unit === 'PERCENTAGE'
        ? `${Intl.NumberFormat(undefined, { maximumFractionDigits: 2 }).format(value)}%`
        : Intl.NumberFormat(undefined, { maximumFractionDigits: 0 }).format(value);
}

function MultiSeriesChart({ data, lines, currency }: MultiSeriesChartProps) {
    const [hiddenLines, setHiddenLines] = useState<Set<string>>(new Set());
    const currencyPrefix = currency ? `${currency} ` : '';
    const yDomain: [(dataMin: number) => number, 'auto'] = [(dataMin: number) => Math.min(0, dataMin), 'auto'];
    const hasCurrency = lines.some(l => l.unit === 'CURRENCY');
    const hasPercentage = lines.some(l => l.unit === 'PERCENTAGE');
    const unitByLineKey = new Map(lines.map(l => [l.key, l.unit]));

    const handleLegendClick = (dataKey: string) => {
        setHiddenLines(prev => {
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
            <div style={{ height: '440px', width: '100%' }}>
                <ResponsiveContainer width="100%" height="100%">
                    <ComposedChart data={data} margin={{ top: 5, right: 30, left: 20, bottom: 5 }}>
                        <CartesianGrid strokeDasharray="none" stroke="hsl(var(--border))" strokeWidth={0.5} />
                        <XAxis
                            dataKey="label"
                            className="text-muted-foreground"
                            tick={{ fill: 'currentColor', fontSize: 11 }}
                        />
                        {hasCurrency && (
                            <YAxis
                                yAxisId="currency"
                                className="text-muted-foreground"
                                tick={{ fill: 'currentColor', fontSize: 11 }}
                                domain={yDomain}
                                tickFormatter={(v) => `${currencyPrefix}${formatCompact(v)}`}
                            />
                        )}
                        {hasPercentage && (
                            <YAxis
                                yAxisId="percentage"
                                orientation={hasCurrency ? 'right' : 'left'}
                                className="text-muted-foreground"
                                tick={{ fill: 'currentColor', fontSize: 11 }}
                                domain={yDomain}
                                tickFormatter={(v) => `${formatCompact(v)}%`}
                            />
                        )}
                        <Tooltip
                            contentStyle={{
                                backgroundColor: 'hsl(var(--card))',
                                border: '1px solid hsl(var(--border))',
                                borderRadius: 'var(--radius)',
                                color: 'hsl(var(--card-foreground))'
                            }}
                            formatter={(value: number, name: string, item: { dataKey?: string | number }) => {
                                const unit = unitByLineKey.get(String(item.dataKey)) ?? 'CURRENCY';
                                const prefix = unit === 'CURRENCY' ? currencyPrefix : '';
                                return [`${prefix}${formatFull(value, unit)}`, name];
                            }}
                        />
                        <Legend
                            onClick={(e) => handleLegendClick(e.dataKey as string)}
                            wrapperStyle={{ cursor: 'pointer' }}
                            formatter={(value, entry) => (
                                <span style={{
                                    color: hiddenLines.has(entry.dataKey as string)
                                        ? 'hsl(var(--muted-foreground))'
                                        : entry.color,
                                    textDecoration: hiddenLines.has(entry.dataKey as string)
                                        ? 'line-through'
                                        : 'none'
                                }}>
                                    {value}
                                </span>
                            )}
                        />
                        {lines.map(line => (
                            <Area
                                key={line.key}
                                yAxisId={line.unit === 'PERCENTAGE' ? 'percentage' : 'currency'}
                                type="linear"
                                dataKey={line.key}
                                name={line.name}
                                stroke={line.color}
                                fill={line.color + '30'}
                                strokeWidth={1}
                                dot={false}
                                hide={hiddenLines.has(line.key)}
                            />
                        ))}
                    </ComposedChart>
                </ResponsiveContainer>
            </div>
        </div>
    );
}

export default MultiSeriesChart;
