import { useState } from 'react';
import { DndContext, closestCenter, KeyboardSensor, PointerSensor, useSensor, useSensors, DragEndEvent } from '@dnd-kit/core';
import { SortableContext, verticalListSortingStrategy, sortableKeyboardCoordinates } from '@dnd-kit/sortable';
import { CategoryMatcher } from '../../api/importConfigurationApi';
import { Category } from '../../api/categoryApi';
import { Button } from '../ui/button';
import { Input } from '../ui/input';
import { SearchableSelect } from '../ui/searchable-select';
import { Plus, X } from 'lucide-react';
import { SortableRow } from './SortableRow';

interface CategoryMatcherEditorProps {
    matchers: CategoryMatcher[];
    onChange: (matchers: CategoryMatcher[]) => void;
    categories: Category[];
    disabled?: boolean;
}

export function CategoryMatcherEditor({ matchers, onChange, categories, disabled }: CategoryMatcherEditorProps) {
    const sensors = useSensors(
        useSensor(PointerSensor),
        useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates }),
    );

    const ids = matchers.map((_, i) => `category-${i}`);

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

    const updateMatcher = (index: number, updated: CategoryMatcher) => {
        const next = [...matchers];
        next[index] = updated;
        onChange(next);
    };

    const removeMatcher = (index: number) => {
        onChange(matchers.filter((_, i) => i !== index));
    };

    const addMatcher = () => {
        onChange([...matchers, { importLabels: [''], categoryId: '' }]);
    };

    const categoryName = (id: string) => categories.find(c => c.id === id)?.name ?? '';

    return (
        <div className="space-y-1.5">
            <DndContext sensors={sensors} collisionDetection={closestCenter} onDragEnd={handleDragEnd}>
                <SortableContext items={ids} strategy={verticalListSortingStrategy}>
                    {matchers.map((matcher, index) => (
                        <SortableRow key={ids[index]} id={ids[index]} disabled={disabled}>
                            <div className="flex flex-wrap items-center gap-1 flex-1 min-w-0">
                                <div className="flex items-center gap-1 shrink-0">
                                    <span className="text-xs text-muted-foreground whitespace-nowrap">category</span>
                                    <SearchableSelect
                                        value={categoryName(matcher.categoryId)}
                                        onValueChange={(name) => {
                                            const cat = categories.find(c => c.name === name);
                                            if (cat) updateMatcher(index, { ...matcher, categoryId: cat.id });
                                        }}
                                        options={categories.map(c => c.name)}
                                        placeholder="Select category"
                                        disabled={disabled}
                                        className="h-8 text-sm"
                                    />
                                </div>
                                <div className="flex items-center gap-1 flex-1 min-w-0">
                                    <span className="text-xs text-muted-foreground whitespace-nowrap">import labels</span>
                                    <div className="flex flex-wrap items-center gap-1 flex-1">
                                        {matcher.importLabels.map((label, labelIdx) => (
                                            <ImportLabelChip
                                                key={labelIdx}
                                                value={label}
                                                onChange={(val) => {
                                                    const labels = [...matcher.importLabels];
                                                    labels[labelIdx] = val;
                                                    updateMatcher(index, { ...matcher, importLabels: labels });
                                                }}
                                                onRemove={() => {
                                                    const labels = matcher.importLabels.filter((_, i) => i !== labelIdx);
                                                    updateMatcher(index, { ...matcher, importLabels: labels.length ? labels : [''] });
                                                }}
                                                disabled={disabled}
                                            />
                                        ))}
                                        <button
                                            type="button"
                                            onClick={() => updateMatcher(index, { ...matcher, importLabels: [...matcher.importLabels, ''] })}
                                            disabled={disabled}
                                            className="inline-flex items-center gap-0.5 px-1.5 py-0.5 text-xs text-muted-foreground border border-dashed rounded-full hover:bg-muted/50 hover:text-foreground disabled:opacity-50 shrink-0"
                                        >
                                            <Plus className="h-2.5 w-2.5" />add
                                        </button>
                                    </div>
                                </div>
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
                <Plus className="h-3.5 w-3.5 mr-1" /> Add Category Matcher
            </Button>
        </div>
    );
}

function ImportLabelChip({ value, onChange, onRemove, disabled }: {
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
                placeholder="Import label"
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
