import { DndContext, closestCenter, KeyboardSensor, PointerSensor, useSensor, useSensors, DragEndEvent } from '@dnd-kit/core';
import { SortableContext, verticalListSortingStrategy, sortableKeyboardCoordinates } from '@dnd-kit/sortable';
import { Button } from '../ui/button';
import { Input } from '../ui/input';
import { SearchableSelect } from '../ui/searchable-select';
import { Plus, X } from 'lucide-react';
import { SortableRow } from './SortableRow';

export interface LabelMatcherRow {
    importLabel: string;
    label: string;
}

interface LabelMatcherEditorProps {
    matchers: LabelMatcherRow[];
    onChange: (matchers: LabelMatcherRow[]) => void;
    labelNames: string[];
    disabled?: boolean;
}

export function LabelMatcherEditor({ matchers, onChange, labelNames, disabled }: LabelMatcherEditorProps) {
    const sensors = useSensors(
        useSensor(PointerSensor),
        useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates }),
    );

    const ids = matchers.map((_, i) => `label-${i}`);

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

    const updateMatcher = (index: number, updated: LabelMatcherRow) => {
        const next = [...matchers];
        next[index] = updated;
        onChange(next);
    };

    const removeMatcher = (index: number) => {
        onChange(matchers.filter((_, i) => i !== index));
    };

    const addMatcher = () => {
        onChange([...matchers, { importLabel: '', label: '' }]);
    };

    return (
        <div className="space-y-1.5">
            <DndContext sensors={sensors} collisionDetection={closestCenter} onDragEnd={handleDragEnd}>
                <SortableContext items={ids} strategy={verticalListSortingStrategy}>
                    {matchers.map((matcher, index) => (
                        <SortableRow key={ids[index]} id={ids[index]} disabled={disabled}>
                            <div className="flex items-center gap-1 flex-1 min-w-0">
                                <span className="text-xs text-muted-foreground whitespace-nowrap">import</span>
                                <Input
                                    value={matcher.importLabel}
                                    onChange={(e) => updateMatcher(index, { ...matcher, importLabel: e.target.value })}
                                    placeholder="Import label"
                                    disabled={disabled}
                                    className="h-8 text-sm"
                                />
                            </div>
                            <div className="flex items-center gap-1 flex-1 min-w-0">
                                <span className="text-xs text-muted-foreground whitespace-nowrap">label</span>
                                <SearchableSelect
                                    value={matcher.label}
                                    onValueChange={(name) => updateMatcher(index, { ...matcher, label: name })}
                                    options={labelNames}
                                    placeholder="Select label"
                                    disabled={disabled}
                                    className="h-8 text-sm"
                                />
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
                <Plus className="h-3.5 w-3.5 mr-1" /> Add Label Matcher
            </Button>
        </div>
    );
}
