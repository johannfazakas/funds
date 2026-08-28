import { MetricInfo } from '../api/analyticsApi';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from './ui/select';
import { MultiSelect, MultiSelectOption, MultiSelectGroup } from './ui/multi-select';
import { Button } from './ui/button';
import { Input } from './ui/input';
import { ArrowDown, ArrowUp, ChevronDown, ChevronRight, Copy, Eye, EyeOff, Trash2 } from 'lucide-react';

export interface QueryState {
    id: string;
    label: string;
    labelTouched: boolean;
    metric: string;
    groupBy: string;
    fundIds: string[];
    units: string[];
    visible: boolean;
    collapsed: boolean;
}

interface MetricQueryEditorProps {
    query: QueryState;
    color: string;
    metricLabel: (name: string) => string;
    metrics: MetricInfo[];
    groupByOptions: { value: string; label: string }[];
    fundOptions: MultiSelectOption[];
    unitGroups: MultiSelectGroup[];
    removable: boolean;
    moveUpDisabled: boolean;
    moveDownDisabled: boolean;
    onChange: (query: QueryState) => void;
    onDuplicate: () => void;
    onRemove: () => void;
    onMove: (offset: number) => void;
}

function MetricQueryEditor({
    query,
    color,
    metricLabel,
    metrics,
    groupByOptions,
    fundOptions,
    unitGroups,
    removable,
    moveUpDisabled,
    moveDownDisabled,
    onChange,
    onDuplicate,
    onRemove,
    onMove,
}: MetricQueryEditorProps) {
    const summaryParts = [metricLabel(query.metric)];
    if (query.groupBy !== 'NONE') {
        const groupLabel = groupByOptions.find(g => g.value === query.groupBy)?.label ?? query.groupBy;
        summaryParts.push(`by ${groupLabel}`);
    }
    if (query.fundIds.length > 0) summaryParts.push(`${query.fundIds.length} fund(s)`);
    if (query.units.length > 0) summaryParts.push(`${query.units.length} unit(s)`);

    return (
        <div className={`border rounded-md ${query.visible ? '' : 'opacity-60'}`}>
            <div className="flex items-center gap-2 px-3 py-2">
                <button
                    type="button"
                    className="flex items-center gap-2 flex-1 text-left"
                    onClick={() => onChange({ ...query, collapsed: !query.collapsed })}
                >
                    {query.collapsed
                        ? <ChevronRight className="h-4 w-4 text-muted-foreground" />
                        : <ChevronDown className="h-4 w-4 text-muted-foreground" />}
                    <span className="inline-block w-3 h-3 rounded-sm" style={{ backgroundColor: color }} />
                    <span className="font-medium text-sm">{query.label}</span>
                    <span className="text-sm text-muted-foreground">{summaryParts.join(' · ')}</span>
                </button>
                <Button variant="ghost" size="sm" title="Move up" disabled={moveUpDisabled} onClick={() => onMove(-1)}>
                    <ArrowUp className="h-4 w-4" />
                </Button>
                <Button variant="ghost" size="sm" title="Move down" disabled={moveDownDisabled} onClick={() => onMove(1)}>
                    <ArrowDown className="h-4 w-4" />
                </Button>
                <Button
                    variant="ghost"
                    size="sm"
                    title={query.visible ? 'Hide query lines' : 'Show query lines'}
                    onClick={() => onChange({ ...query, visible: !query.visible })}
                >
                    {query.visible ? <Eye className="h-4 w-4" /> : <EyeOff className="h-4 w-4" />}
                </Button>
                <Button variant="ghost" size="sm" title="Duplicate query" onClick={onDuplicate}>
                    <Copy className="h-4 w-4" />
                </Button>
                <Button variant="ghost" size="sm" title="Remove query" onClick={onRemove} disabled={!removable}>
                    <Trash2 className="h-4 w-4" />
                </Button>
            </div>
            {!query.collapsed && (
                <div className="flex flex-wrap items-end gap-4 px-3 pb-3">
                    <div className="flex flex-col gap-1">
                        <label className="text-sm text-muted-foreground">Label</label>
                        <Input
                            value={query.label}
                            onChange={(e) => onChange({ ...query, label: e.target.value, labelTouched: true })}
                            className="w-[220px] h-9"
                        />
                    </div>
                    <div className="flex flex-col gap-1">
                        <label className="text-sm text-muted-foreground">Metric</label>
                        <Select value={query.metric} onValueChange={(v) => onChange({ ...query, metric: v })}>
                            <SelectTrigger className="w-[200px] h-9">
                                <SelectValue />
                            </SelectTrigger>
                            <SelectContent>
                                {metrics.map(m => (
                                    <SelectItem key={m.metric} value={m.metric}>{metricLabel(m.metric)}</SelectItem>
                                ))}
                            </SelectContent>
                        </Select>
                    </div>
                    <div className="flex flex-col gap-1">
                        <label className="text-sm text-muted-foreground">Group by</label>
                        <Select value={query.groupBy} onValueChange={(v) => onChange({ ...query, groupBy: v })}>
                            <SelectTrigger className="w-[140px] h-9">
                                <SelectValue />
                            </SelectTrigger>
                            <SelectContent>
                                {groupByOptions.map(g => (
                                    <SelectItem key={g.value} value={g.value}>{g.label}</SelectItem>
                                ))}
                            </SelectContent>
                        </Select>
                    </div>
                    <div className="flex flex-col gap-1">
                        <label className="text-sm text-muted-foreground">Funds</label>
                        <MultiSelect
                            values={query.fundIds}
                            onValuesChange={(v) => onChange({ ...query, fundIds: v })}
                            options={fundOptions}
                            placeholder="All funds"
                            className="w-[180px]"
                        />
                    </div>
                    <div className="flex flex-col gap-1">
                        <label className="text-sm text-muted-foreground">Financial units</label>
                        <MultiSelect
                            values={query.units}
                            onValuesChange={(v) => onChange({ ...query, units: v })}
                            groups={unitGroups}
                            placeholder="All units"
                            className="w-[180px]"
                        />
                    </div>
                </div>
            )}
        </div>
    );
}

export default MetricQueryEditor;
