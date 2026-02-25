import { DndContext, closestCenter, KeyboardSensor, PointerSensor, useSensor, useSensors, DragEndEvent } from '@dnd-kit/core';
import { SortableContext, verticalListSortingStrategy, sortableKeyboardCoordinates } from '@dnd-kit/sortable';
import { Button } from '../ui/button';
import { Input } from '../ui/input';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '../ui/select';
import { Plus, X } from 'lucide-react';
import { SortableRow } from './SortableRow';

const FUND_MATCHER_TYPES = [
    { value: 'by_account', label: 'By Account' },
    { value: 'by_label', label: 'By Label' },
    { value: 'by_account_label', label: 'By Account & Label' },
    { value: 'by_label_with_post_transfer', label: 'By Label (Post Transfer)' },
    { value: 'by_account_label_with_post_transfer', label: 'By Account & Label (Post Transfer)' },
    { value: 'by_account_label_with_pre_transfer', label: 'By Account & Label (Pre Transfer)' },
];

export interface FundMatcherRow {
    type: string;
    fundName: string;
    importAccountName?: string;
    importLabel?: string;
    initialFundName?: string;
}

interface FundMatcherEditorProps {
    matchers: FundMatcherRow[];
    onChange: (matchers: FundMatcherRow[]) => void;
    fundNames: string[];
    disabled?: boolean;
}

function hasAccountName(type: string): boolean {
    return type.includes('account');
}

function hasLabel(type: string): boolean {
    return type.includes('label');
}

function hasTransfer(type: string): boolean {
    return type.includes('transfer');
}

export function FundMatcherEditor({ matchers, onChange, fundNames, disabled }: FundMatcherEditorProps) {
    const sensors = useSensors(
        useSensor(PointerSensor),
        useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates }),
    );

    const ids = matchers.map((_, i) => `fund-${i}`);

    const handleDragEnd = (event: DragEndEvent) => {
        const { active, over } = event;
        if (!over || active.id === over.id) return;
        const oldIndex = ids.indexOf(active.id as string);
        const newIndex = ids.indexOf(over.id as string);
        const next = [...matchers];
        const [moved] = next.splice(oldIndex, 1);
        next.splice(newIndex, 0, moved);
        onChange(next);
    };

    const updateMatcher = (index: number, updated: FundMatcherRow) => {
        const next = [...matchers];
        next[index] = updated;
        onChange(next);
    };

    const removeMatcher = (index: number) => {
        onChange(matchers.filter((_, i) => i !== index));
    };

    const addMatcher = () => {
        onChange([...matchers, { type: 'by_account', fundName: '', importAccountName: '' }]);
    };

    const handleTypeChange = (index: number, type: string) => {
        const current = matchers[index];
        const updated: FundMatcherRow = { type, fundName: current.fundName };
        if (hasAccountName(type)) {
            updated.importAccountName = current.importAccountName || '';
        }
        if (hasLabel(type)) {
            updated.importLabel = current.importLabel || '';
        }
        if (hasTransfer(type)) {
            updated.initialFundName = current.initialFundName || '';
        }
        updateMatcher(index, updated);
    };

    return (
        <div className="space-y-1.5">
            <DndContext sensors={sensors} collisionDetection={closestCenter} onDragEnd={handleDragEnd}>
                <SortableContext items={ids} strategy={verticalListSortingStrategy}>
                    {matchers.map((matcher, index) => (
                        <SortableRow key={ids[index]} id={ids[index]} disabled={disabled}>
                            <div className="flex items-center gap-1 shrink-0" style={{ minWidth: '200px' }}>
                                <span className="text-xs text-muted-foreground whitespace-nowrap">type</span>
                                <Select
                                    value={matcher.type}
                                    onValueChange={(type) => handleTypeChange(index, type)}
                                    disabled={disabled}
                                >
                                    <SelectTrigger className="h-8">
                                        <SelectValue />
                                    </SelectTrigger>
                                    <SelectContent>
                                        {FUND_MATCHER_TYPES.map(t => (
                                            <SelectItem key={t.value} value={t.value}>{t.label}</SelectItem>
                                        ))}
                                    </SelectContent>
                                </Select>
                            </div>
                            {hasAccountName(matcher.type) && (
                                <div className="flex items-center gap-1 flex-1 min-w-0">
                                    <span className="text-xs text-muted-foreground whitespace-nowrap">import acct</span>
                                    <Input
                                        value={matcher.importAccountName || ''}
                                        onChange={(e) => updateMatcher(index, { ...matcher, importAccountName: e.target.value })}
                                        placeholder="Import account name"
                                        disabled={disabled}
                                        className="h-8 text-sm"
                                    />
                                </div>
                            )}
                            {hasLabel(matcher.type) && (
                                <div className="flex items-center gap-1 flex-1 min-w-0">
                                    <span className="text-xs text-muted-foreground whitespace-nowrap">import label</span>
                                    <Input
                                        value={matcher.importLabel || ''}
                                        onChange={(e) => updateMatcher(index, { ...matcher, importLabel: e.target.value })}
                                        placeholder="Import label"
                                        disabled={disabled}
                                        className="h-8 text-sm"
                                    />
                                </div>
                            )}
                            <div className="flex items-center gap-1 flex-1 min-w-0">
                                <span className="text-xs text-muted-foreground whitespace-nowrap">fund</span>
                                <Select
                                    value={matcher.fundName}
                                    onValueChange={(name) => updateMatcher(index, { ...matcher, fundName: name })}
                                    disabled={disabled}
                                >
                                    <SelectTrigger className="h-8">
                                        <SelectValue placeholder="Select fund" />
                                    </SelectTrigger>
                                    <SelectContent>
                                        {fundNames.map(name => (
                                            <SelectItem key={name} value={name}>{name}</SelectItem>
                                        ))}
                                    </SelectContent>
                                </Select>
                            </div>
                            {hasTransfer(matcher.type) && (
                                <div className="flex items-center gap-1 flex-1 min-w-0">
                                    <span className="text-xs text-muted-foreground whitespace-nowrap">initial</span>
                                    <Select
                                        value={matcher.initialFundName || ''}
                                        onValueChange={(name) => updateMatcher(index, { ...matcher, initialFundName: name })}
                                        disabled={disabled}
                                    >
                                        <SelectTrigger className="h-8">
                                            <SelectValue placeholder="Select fund" />
                                        </SelectTrigger>
                                        <SelectContent>
                                            {fundNames.map(name => (
                                                <SelectItem key={name} value={name}>{name}</SelectItem>
                                            ))}
                                        </SelectContent>
                                    </Select>
                                </div>
                            )}
                            <Button
                                type="button"
                                variant="ghost"
                                size="sm"
                                className="h-8 w-6 p-0 shrink-0 text-muted-foreground hover:text-destructive"
                                onClick={() => removeMatcher(index)}
                                disabled={disabled}
                            >
                                <X className="h-3.5 w-3.5" />
                            </Button>
                        </SortableRow>
                    ))}
                </SortableContext>
            </DndContext>
            <Button type="button" variant="outline" size="sm" onClick={addMatcher} disabled={disabled}>
                <Plus className="h-3.5 w-3.5 mr-1" /> Add Fund Matcher
            </Button>
        </div>
    );
}
