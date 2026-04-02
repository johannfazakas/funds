import { LucideIcon, Loader2 } from 'lucide-react';
import { Button } from './button';
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from './tooltip';
import { cn } from '../../lib/utils';

interface ActionButtonProps {
    icon: LucideIcon;
    tooltip: string;
    onClick: () => void;
    destructive?: boolean;
    disabled?: boolean;
    loading?: boolean;
}

function ActionButton({ icon: Icon, tooltip, onClick, destructive, disabled, loading }: ActionButtonProps) {
    return (
        <TooltipProvider>
            <Tooltip>
                <TooltipTrigger asChild>
                    <Button
                        variant="ghost"
                        size="sm"
                        className={cn(destructive && "text-destructive hover:text-destructive")}
                        onClick={onClick}
                        disabled={disabled || loading}
                    >
                        {loading
                            ? <Loader2 className="h-4 w-4 animate-spin" />
                            : <Icon className="h-4 w-4" />
                        }
                    </Button>
                </TooltipTrigger>
                <TooltipContent>{tooltip}</TooltipContent>
            </Tooltip>
        </TooltipProvider>
    );
}

export { ActionButton };
