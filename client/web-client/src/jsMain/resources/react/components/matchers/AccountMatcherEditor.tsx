import { DndContext, closestCenter, KeyboardSensor, PointerSensor, useSensor, useSensors, DragEndEvent } from '@dnd-kit/core';
import { SortableContext, verticalListSortingStrategy, sortableKeyboardCoordinates } from '@dnd-kit/sortable';
import { AccountMatcher } from '../../api/importConfigurationApi';
import { Button } from '../ui/button';
import { Input } from '../ui/input';
import { SearchableSelect } from '../ui/searchable-select';
import { Plus, X } from 'lucide-react';
import { SortableRow } from './SortableRow';

interface AccountMatcherEditorProps {
    matchers: AccountMatcher[];
    onChange: (matchers: AccountMatcher[]) => void;
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

    const updateMatcher = (index: number, updated: AccountMatcher) => {
        const next = [...matchers];
        next[index] = updated;
        onChange(next);
    };

    const removeMatcher = (index: number) => {
        onChange(matchers.filter((_, i) => i !== index));
    };

    const addMatcher = () => {
        onChange([...matchers, { importAccountName: '', accountName: '' }]);
    };

    return (
        <div className="space-y-1.5">
            <DndContext sensors={sensors} collisionDetection={closestCenter} onDragEnd={handleDragEnd}>
                <SortableContext items={ids} strategy={verticalListSortingStrategy}>
                    {matchers.map((matcher, index) => (
                        <SortableRow key={ids[index]} id={ids[index]} disabled={disabled}>
                            {!matcher.skipped && (
                                <div className="flex items-center gap-1 flex-1 min-w-0">
                                    <span className="text-xs text-muted-foreground whitespace-nowrap">account</span>
                                    <SearchableSelect
                                        value={matcher.accountName || ''}
                                        onValueChange={(name) => updateMatcher(index, { ...matcher, accountName: name })}
                                        options={accountNames}
                                        placeholder="Select account"
                                        disabled={disabled}
                                        className="h-8 text-sm"
                                    />
                                </div>
                            )}
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
                            <label className="flex items-center gap-1.5 shrink-0 cursor-pointer">
                                <input
                                    type="checkbox"
                                    checked={matcher.skipped || false}
                                    onChange={(e) => updateMatcher(index, {
                                        importAccountName: matcher.importAccountName,
                                        skipped: e.target.checked,
                                        ...(e.target.checked ? {} : { accountName: matcher.accountName || '' }),
                                    })}
                                    disabled={disabled}
                                    className="h-3.5 w-3.5"
                                />
                                <span className="text-xs text-muted-foreground">skip</span>
                            </label>
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
