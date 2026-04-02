import { Button } from './ui/button';
import {
    Select,
    SelectContent,
    SelectItem,
    SelectTrigger,
    SelectValue,
} from './ui/select';
import { ChevronLeft, ChevronRight } from 'lucide-react';

interface PaginationProps {
    offset: number;
    limit: number;
    total: number;
    onPageChange: (offset: number) => void;
    onPageSizeChange: (limit: number) => void;
    pageSizeOptions?: number[];
}

const DEFAULT_PAGE_SIZES = [10, 25, 50];

export function Pagination({
    offset,
    limit,
    total,
    onPageChange,
    onPageSizeChange,
    pageSizeOptions = DEFAULT_PAGE_SIZES,
}: PaginationProps) {
    const currentPage = Math.floor(offset / limit) + 1;
    const totalPages = Math.ceil(total / limit);
    const startItem = total === 0 ? 0 : offset + 1;
    const endItem = Math.min(offset + limit, total);

    const handlePrevious = () => {
        if (offset > 0) {
            onPageChange(Math.max(0, offset - limit));
        }
    };

    const handleNext = () => {
        if (offset + limit < total) {
            onPageChange(offset + limit);
        }
    };

    const handlePageSizeChange = (value: string) => {
        const newLimit = parseInt(value, 10);
        onPageSizeChange(newLimit);
    };

    return (
        <div className="flex items-center justify-between px-4 py-4">
            <div className="text-sm text-muted-foreground">
                Showing {startItem} to {endItem} of {total} items
            </div>
            <div className="flex items-center space-x-6">
                <div className="flex items-center space-x-2">
                    <span className="text-sm text-muted-foreground">Rows per page</span>
                    <Select value={limit.toString()} onValueChange={handlePageSizeChange}>
                        <SelectTrigger className="w-[70px]">
                            <SelectValue />
                        </SelectTrigger>
                        <SelectContent>
                            {pageSizeOptions.map((size) => (
                                <SelectItem key={size} value={size.toString()}>
                                    {size}
                                </SelectItem>
                            ))}
                        </SelectContent>
                    </Select>
                </div>
                <div className="flex items-center space-x-2">
                    <span className="text-sm text-muted-foreground">
                        Page {currentPage} of {totalPages || 1}
                    </span>
                    <Button
                        variant="outline"
                        size="icon"
                        onClick={handlePrevious}
                        disabled={offset === 0}
                    >
                        <ChevronLeft className="h-4 w-4" />
                    </Button>
                    <Button
                        variant="outline"
                        size="icon"
                        onClick={handleNext}
                        disabled={offset + limit >= total}
                    >
                        <ChevronRight className="h-4 w-4" />
                    </Button>
                </div>
            </div>
        </div>
    );
}
