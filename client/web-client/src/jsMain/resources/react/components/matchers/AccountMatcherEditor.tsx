import { useState } from 'react';
import { DndContext, closestCenter, KeyboardSensor, PointerSensor, useSensor, useSensors, DragEndEvent } from '@dnd-kit/core';
import { SortableContext, verticalListSortingStrategy, sortableKeyboardCoordinates } from '@dnd-kit/sortable';
import { AccountMatcher } from '../../api/importConfigurationApi';
import { Account } from '../../api/accountApi';
import { Button } from '../ui/button';
import { Input } from '../ui/input';
import { SearchableSelect } from '../ui/searchable-select';
import { Plus, X } from 'lucide-react';
import { SortableRow } from './SortableRow';

interface AccountMatcherEditorProps {
    matchers: AccountMatcher[];
    onChange: (matchers: AccountMatcher[]) => void;
    accounts: Account[];
    disabled?: boolean;
}

export function AccountMatcherEditor({ matchers, onChange, accounts, disabled }: AccountMatcherEditorProps) {
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
        onChange([...matchers, { importAccountNames: [''], accountId: undefined, skipped: false }]);
    };

    const accountName = (id?: string) => accounts.find(a => a.id === id)?.name ?? '';

    return (
        <div className="space-y-1.5">
            <DndContext sensors={sensors} collisionDetection={closestCenter} onDragEnd={handleDragEnd}>
                <SortableContext items={ids} strategy={verticalListSortingStrategy}>
                    {matchers.map((matcher, index) => (
                        <SortableRow key={ids[index]} id={ids[index]} disabled={disabled}>
                            <div className="flex flex-wrap items-center gap-1 flex-1 min-w-0">
                                {!matcher.skipped && (
                                    <div className="flex items-center gap-1 shrink-0">
                                        <span className="text-xs text-muted-foreground whitespace-nowrap">account</span>
                                        <SearchableSelect
                                            value={accountName(matcher.accountId)}
                                            onValueChange={(name) => {
                                                const acc = accounts.find(a => a.name === name);
                                                if (acc) updateMatcher(index, { ...matcher, accountId: acc.id });
                                            }}
                                            options={accounts.map(a => a.name)}
                                            placeholder="Select account"
                                            disabled={disabled}
                                            className="h-8 text-sm"
                                        />
                                    </div>
                                )}
                                <div className="flex items-center gap-1 flex-1 min-w-0">
                                    <span className="text-xs text-muted-foreground whitespace-nowrap">import names</span>
                                    <div className="flex flex-wrap items-center gap-1 flex-1">
                                        {matcher.importAccountNames.map((name, nameIdx) => (
                                            <ImportNameChip
                                                key={nameIdx}
                                                value={name}
                                                onChange={(val) => {
                                                    const names = [...matcher.importAccountNames];
                                                    names[nameIdx] = val;
                                                    updateMatcher(index, { ...matcher, importAccountNames: names });
                                                }}
                                                onRemove={() => {
                                                    const names = matcher.importAccountNames.filter((_, i) => i !== nameIdx);
                                                    updateMatcher(index, { ...matcher, importAccountNames: names.length ? names : [''] });
                                                }}
                                                disabled={disabled}
                                            />
                                        ))}
                                        <button
                                            type="button"
                                            onClick={() => updateMatcher(index, { ...matcher, importAccountNames: [...matcher.importAccountNames, ''] })}
                                            disabled={disabled}
                                            className="inline-flex items-center gap-0.5 px-1.5 py-0.5 text-xs text-muted-foreground border border-dashed rounded-full hover:bg-muted/50 hover:text-foreground disabled:opacity-50 shrink-0"
                                        >
                                            <Plus className="h-2.5 w-2.5" />add
                                        </button>
                                    </div>
                                </div>
                                <label className="flex items-center gap-1.5 shrink-0 cursor-pointer">
                                    <input
                                        type="checkbox"
                                        checked={matcher.skipped || false}
                                        onChange={(e) => updateMatcher(index, {
                                            importAccountNames: matcher.importAccountNames,
                                            skipped: e.target.checked,
                                            ...(e.target.checked ? {} : { accountId: matcher.accountId }),
                                        })}
                                        disabled={disabled}
                                        className="h-3.5 w-3.5"
                                    />
                                    <span className="text-xs text-muted-foreground">skip</span>
                                </label>
                            </div>
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

function ImportNameChip({ value, onChange, onRemove, disabled }: {
    value: string;
    onChange: (val: string) => void;
    onRemove: () => void;
    disabled?: boolean;
}) {
    const [editing, setEditing] = useState(!value);

    if (editing) {
        return (
            <Input
                value={value}
                onChange={(e) => onChange(e.target.value)}
                onBlur={() => { if (value) setEditing(false); }}
                onKeyDown={(e) => { if (e.key === 'Enter' && value) setEditing(false); }}
                autoFocus
                disabled={disabled}
                className="h-6 text-xs w-32"
                placeholder="Import name"
            />
        );
    }

    return (
        <span className="inline-flex items-center gap-1 px-2 py-0.5 bg-secondary text-secondary-foreground rounded-full text-xs">
            <span className="cursor-pointer" onClick={() => setEditing(true)}>{value}</span>
            <button type="button" onClick={onRemove} disabled={disabled} className="text-muted-foreground hover:text-destructive">
                <X className="h-2.5 w-2.5" />
            </button>
        </span>
    );
}
