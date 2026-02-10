import { TableHead } from './ui/table';
import { ArrowUp, ArrowDown, ArrowUpDown } from 'lucide-react';
import { SortOrder } from '../api/types';

interface SortableTableHeadProps<T extends string> {
    field: T;
    currentField: T | null;
    currentOrder: SortOrder;
    onSort: (field: T) => void;
    children: React.ReactNode;
    className?: string;
}

export function SortableTableHead<T extends string>({
    field,
    currentField,
    currentOrder,
    onSort,
    children,
    className,
}: SortableTableHeadProps<T>) {
    const isActive = currentField === field;

    const handleClick = () => {
        onSort(field);
    };

    return (
        <TableHead
            className={`cursor-pointer select-none hover:bg-muted/50 ${className || ''}`}
            onClick={handleClick}
        >
            <div className="flex items-center space-x-1">
                <span>{children}</span>
                {isActive ? (
                    currentOrder === 'asc' ? (
                        <ArrowUp className="h-4 w-4" />
                    ) : (
                        <ArrowDown className="h-4 w-4" />
                    )
                ) : (
                    <ArrowUpDown className="h-4 w-4 text-muted-foreground" />
                )}
            </div>
        </TableHead>
    );
}
