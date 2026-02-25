import { DndContext, closestCenter, KeyboardSensor, PointerSensor, useSensor, useSensors, DragEndEvent } from '@dnd-kit/core';
import { SortableContext, verticalListSortingStrategy, sortableKeyboardCoordinates } from '@dnd-kit/sortable';
import { Button } from '../ui/button';
import { Input } from '../ui/input';
import { SearchableSelect } from '../ui/searchable-select';
import { Plus, X } from 'lucide-react';
import { SortableRow } from './SortableRow';
import { FundMatcher } from '../../api/importConfigurationApi';

interface FundMatcherEditorProps {
    matchers: FundMatcher[];
    onChange: (matchers: FundMatcher[]) => void;
    fundNames: string[];
    disabled?: boolean;
}

function AddFieldButton({ label, onClick, disabled }: { label: string; onClick: () => void; disabled?: boolean }) {
    return (
        <button
            type="button"
            onClick={onClick}
            disabled={disabled}
            className="inline-flex items-center gap-0.5 px-1.5 py-0.5 text-xs text-muted-foreground border border-dashed rounded-full hover:bg-muted/50 hover:text-foreground disabled:opacity-50 shrink-0"
        >
            <Plus className="h-2.5 w-2.5" />{label}
        </button>
    );
}

function RemovableField({ label, children, onRemove, disabled }: { label: string; children: React.ReactNode; onRemove: () => void; disabled?: boolean }) {
    return (
        <div className="flex items-center gap-1 flex-1 min-w-0">
            <span className="text-xs text-muted-foreground whitespace-nowrap">{label}</span>
            {children}
            <button
                type="button"
                onClick={onRemove}
                disabled={disabled}
                className="text-muted-foreground hover:text-destructive shrink-0 p-0.5"
            >
                <X className="h-2.5 w-2.5" />
            </button>
        </div>
    );
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

    const updateMatcher = (index: number, updated: FundMatcher) => {
        const next = [...matchers];
        next[index] = updated;
        onChange(next);
    };

    const removeMatcher = (index: number) => {
        onChange(matchers.filter((_, i) => i !== index));
    };

    const addMatcher = () => {
        onChange([...matchers, { fundName: '', importAccountName: '' }]);
    };

    return (
        <div className="space-y-1.5">
            <DndContext sensors={sensors} collisionDetection={closestCenter} onDragEnd={handleDragEnd}>
                <SortableContext items={ids} strategy={verticalListSortingStrategy}>
                    {matchers.map((matcher, index) => {
                        const hasAccount = matcher.importAccountName !== undefined;
                        const hasLabel = matcher.importLabel !== undefined;
                        const hasIntermediary = matcher.intermediaryFundName !== undefined;

                        return (
                            <SortableRow key={ids[index]} id={ids[index]} disabled={disabled}>
                                <div className="flex flex-wrap items-center gap-1 flex-1 min-w-0">
                                    <div className="flex items-center gap-1 flex-1 min-w-0">
                                        <span className="text-xs text-muted-foreground whitespace-nowrap">fund</span>
                                        <SearchableSelect
                                            value={matcher.fundName}
                                            onValueChange={(name) => updateMatcher(index, { ...matcher, fundName: name })}
                                            options={fundNames}
                                            placeholder="Select fund"
                                            disabled={disabled}
                                            className="h-8 text-sm"
                                        />
                                    </div>
                                    {hasAccount && (
                                        <RemovableField
                                            label="account"
                                            onRemove={() => updateMatcher(index, { ...matcher, importAccountName: undefined })}
                                            disabled={disabled}
                                        >
                                            <Input
                                                value={matcher.importAccountName || ''}
                                                onChange={(e) => updateMatcher(index, { ...matcher, importAccountName: e.target.value })}
                                                placeholder="Import account"
                                                disabled={disabled}
                                                className="h-8 text-sm"
                                            />
                                        </RemovableField>
                                    )}
                                    {hasLabel && (
                                        <RemovableField
                                            label="label"
                                            onRemove={() => updateMatcher(index, { ...matcher, importLabel: undefined })}
                                            disabled={disabled}
                                        >
                                            <Input
                                                value={matcher.importLabel || ''}
                                                onChange={(e) => updateMatcher(index, { ...matcher, importLabel: e.target.value })}
                                                placeholder="Import label"
                                                disabled={disabled}
                                                className="h-8 text-sm"
                                            />
                                        </RemovableField>
                                    )}
                                    {hasIntermediary && (
                                        <RemovableField
                                            label="intermediary"
                                            onRemove={() => updateMatcher(index, { ...matcher, intermediaryFundName: undefined })}
                                            disabled={disabled}
                                        >
                                            <SearchableSelect
                                                value={matcher.intermediaryFundName || ''}
                                                onValueChange={(name) => updateMatcher(index, { ...matcher, intermediaryFundName: name })}
                                                options={fundNames}
                                                placeholder="Select fund"
                                                disabled={disabled}
                                                className="h-8 text-sm"
                                            />
                                        </RemovableField>
                                    )}
                                    {(!hasAccount || !hasLabel || !hasIntermediary) && (
                                        <div className="flex items-center gap-1 shrink-0">
                                            {!hasAccount && (
                                                <AddFieldButton label="account" onClick={() => updateMatcher(index, { ...matcher, importAccountName: '' })} disabled={disabled} />
                                            )}
                                            {!hasLabel && (
                                                <AddFieldButton label="label" onClick={() => updateMatcher(index, { ...matcher, importLabel: '' })} disabled={disabled} />
                                            )}
                                            {!hasIntermediary && (
                                                <AddFieldButton label="transfer" onClick={() => updateMatcher(index, { ...matcher, intermediaryFundName: '' })} disabled={disabled} />
                                            )}
                                        </div>
                                    )}
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
                        );
                    })}
                </SortableContext>
            </DndContext>
            <Button type="button" variant="outline" size="sm" onClick={addMatcher} disabled={disabled}>
                <Plus className="h-3.5 w-3.5 mr-1" /> Add Fund Matcher
            </Button>
        </div>
    );
}
