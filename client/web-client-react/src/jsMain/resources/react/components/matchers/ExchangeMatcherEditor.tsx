import { DndContext, closestCenter, KeyboardSensor, PointerSensor, useSensor, useSensors, DragEndEvent } from '@dnd-kit/core';
import { SortableContext, verticalListSortingStrategy, sortableKeyboardCoordinates } from '@dnd-kit/sortable';
import { ExchangeMatcher } from '../../api/importConfigurationApi';
import { Button } from '../ui/button';
import { Input } from '../ui/input';
import { Plus, X } from 'lucide-react';
import { SortableRow } from './SortableRow';

interface ExchangeMatcherEditorProps {
    matchers: ExchangeMatcher[];
    onChange: (matchers: ExchangeMatcher[]) => void;
    disabled?: boolean;
}

export function ExchangeMatcherEditor({ matchers, onChange, disabled }: ExchangeMatcherEditorProps) {
    const sensors = useSensors(
        useSensor(PointerSensor),
        useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates }),
    );

    const ids = matchers.map((_, i) => `exchange-${i}`);

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

    const updateMatcher = (index: number, updated: ExchangeMatcher) => {
        const next = [...matchers];
        next[index] = updated;
        onChange(next);
    };

    const removeMatcher = (index: number) => {
        onChange(matchers.filter((_, i) => i !== index));
    };

    const addMatcher = () => {
        onChange([...matchers, { type: 'by_label', label: '' }]);
    };

    return (
        <div className="space-y-1.5">
            <DndContext sensors={sensors} collisionDetection={closestCenter} onDragEnd={handleDragEnd}>
                <SortableContext items={ids} strategy={verticalListSortingStrategy}>
                    {matchers.map((matcher, index) => (
                        <SortableRow key={ids[index]} id={ids[index]} disabled={disabled}>
                            <div className="flex items-center gap-1 flex-1 min-w-0">
                                <span className="text-xs text-muted-foreground whitespace-nowrap">label</span>
                                <Input
                                    value={matcher.label}
                                    onChange={(e) => updateMatcher(index, { ...matcher, label: e.target.value })}
                                    placeholder="Exchange label"
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
                <Plus className="h-3.5 w-3.5 mr-1" /> Add Exchange Matcher
            </Button>
        </div>
    );
}
