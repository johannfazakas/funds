import { useSortable } from '@dnd-kit/sortable';
import { CSS } from '@dnd-kit/utilities';
import { GripVertical } from 'lucide-react';

interface SortableRowProps {
    id: string;
    disabled?: boolean;
    children: React.ReactNode;
}

export function SortableRow({ id, disabled, children }: SortableRowProps) {
    const {
        attributes,
        listeners,
        setNodeRef,
        transform,
        isDragging,
    } = useSortable({ id, disabled });

    const style = {
        transform: CSS.Transform.toString(transform),
        opacity: isDragging ? 0.5 : 1,
    };

    return (
        <div ref={setNodeRef} style={style} className="flex items-end gap-2">
            <button
                type="button"
                className="h-8 w-5 shrink-0 flex items-center justify-center text-muted-foreground cursor-grab active:cursor-grabbing disabled:cursor-not-allowed disabled:opacity-30"
                disabled={disabled}
                {...attributes}
                {...listeners}
            >
                <GripVertical className="h-3.5 w-3.5" />
            </button>
            {children}
        </div>
    );
}
