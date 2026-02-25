import { DndContext, closestCenter, KeyboardSensor, PointerSensor, useSensor, useSensors, DragEndEvent } from '@dnd-kit/core';
import { SortableContext, verticalListSortingStrategy, sortableKeyboardCoordinates } from '@dnd-kit/sortable';
import { Button } from '../ui/button';
import { Input } from '../ui/input';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '../ui/select';
import { Plus, X } from 'lucide-react';
import { SortableRow } from './SortableRow';

const ACCOUNT_MATCHER_TYPES = [
    { value: 'by_name', label: 'By Name' },
    { value: 'skipped', label: 'Skipped' },
];

export interface AccountMatcherRow {
    type: string;
    importAccountName: string;
    accountName?: string;
}

interface AccountMatcherEditorProps {
    matchers: AccountMatcherRow[];
    onChange: (matchers: AccountMatcherRow[]) => void;
    accountNames: string[];
    disabled?: boolean;
}

export function AccountMatcherEditor({ matchers, onChange, accountNames, disabled }: AccountMatcherEditorProps) {
    const sensors = useSensors(
        useSensor(PointerSensor),
        useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates }),
    );

    const ids = matchers.map((_, i) => `account-${i}`);

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

    const updateMatcher = (index: number, updated: AccountMatcherRow) => {
        const next = [...matchers];
        next[index] = updated;
        onChange(next);
    };

    const removeMatcher = (index: number) => {
        onChange(matchers.filter((_, i) => i !== index));
    };

    const addMatcher = () => {
        onChange([...matchers, { type: 'by_name', importAccountName: '', accountName: '' }]);
    };

    return (
        <div className="space-y-1.5">
            <DndContext sensors={sensors} collisionDetection={closestCenter} onDragEnd={handleDragEnd}>
                <SortableContext items={ids} strategy={verticalListSortingStrategy}>
                    {matchers.map((matcher, index) => (
                        <SortableRow key={ids[index]} id={ids[index]} disabled={disabled}>
                            <div className="flex items-center gap-1 w-32 shrink-0">
                                <span className="text-xs text-muted-foreground whitespace-nowrap">type</span>
                                <Select
                                    value={matcher.type}
                                    onValueChange={(type) => {
                                        const updated: AccountMatcherRow = type === 'skipped'
                                            ? { type, importAccountName: matcher.importAccountName }
                                            : { type, importAccountName: matcher.importAccountName, accountName: matcher.accountName || '' };
                                        updateMatcher(index, updated);
                                    }}
                                    disabled={disabled}
                                >
                                    <SelectTrigger className="h-8">
                                        <SelectValue />
                                    </SelectTrigger>
                                    <SelectContent>
                                        {ACCOUNT_MATCHER_TYPES.map(t => (
                                            <SelectItem key={t.value} value={t.value}>{t.label}</SelectItem>
                                        ))}
                                    </SelectContent>
                                </Select>
                            </div>
                            <div className="flex items-center gap-1 flex-1 min-w-0">
                                <span className="text-xs text-muted-foreground whitespace-nowrap">import</span>
                                <Input
                                    value={matcher.importAccountName}
                                    onChange={(e) => updateMatcher(index, { ...matcher, importAccountName: e.target.value })}
                                    placeholder="Import account name"
                                    disabled={disabled}
                                    className="h-8 text-sm"
                                />
                            </div>
                            {matcher.type === 'by_name' && (
                                <div className="flex items-center gap-1 flex-1 min-w-0">
                                    <span className="text-xs text-muted-foreground whitespace-nowrap">account</span>
                                    <Select
                                        value={matcher.accountName || ''}
                                        onValueChange={(name) => updateMatcher(index, { ...matcher, accountName: name })}
                                        disabled={disabled}
                                    >
                                        <SelectTrigger className="h-8">
                                            <SelectValue placeholder="Select account" />
                                        </SelectTrigger>
                                        <SelectContent>
                                            {accountNames.map(name => (
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
                <Plus className="h-3.5 w-3.5 mr-1" /> Add Account Matcher
            </Button>
        </div>
    );
}
