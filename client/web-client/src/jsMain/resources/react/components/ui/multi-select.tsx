import * as React from "react"
import * as Popover from "@radix-ui/react-popover"
import { Check, ChevronDown } from "lucide-react"
import { cn } from "../../lib/utils"

export interface MultiSelectOption {
    value: string;
    label: string;
}

export interface MultiSelectGroup {
    label: string;
    options: MultiSelectOption[];
}

interface MultiSelectProps {
    values: string[];
    onValuesChange: (values: string[]) => void;
    options?: MultiSelectOption[];
    groups?: MultiSelectGroup[];
    placeholder?: string;
    className?: string;
}

export function MultiSelect({
    values,
    onValuesChange,
    options,
    groups,
    placeholder = "All",
    className,
}: MultiSelectProps) {
    const [open, setOpen] = React.useState(false);

    const allOptions = groups
        ? groups.flatMap(g => g.options)
        : (options || []);

    const toggle = (value: string) => {
        onValuesChange(
            values.includes(value)
                ? values.filter(v => v !== value)
                : [...values, value]
        );
    };

    const selectedLabels = allOptions
        .filter(o => values.includes(o.value))
        .map(o => o.label);

    const displayText = selectedLabels.length === 0
        ? placeholder
        : selectedLabels.join(", ");

    const renderOption = (option: MultiSelectOption) => (
        <button
            key={option.value}
            type="button"
            onClick={() => toggle(option.value)}
            className="relative flex w-full cursor-default select-none items-center rounded-sm py-1.5 pl-8 pr-2 text-sm outline-none hover:bg-accent hover:text-accent-foreground"
        >
            <span className="absolute left-2 flex h-3.5 w-3.5 items-center justify-center">
                <Check className={cn("h-4 w-4", values.includes(option.value) ? "opacity-100" : "opacity-0")} />
            </span>
            {option.label}
        </button>
    );

    return (
        <Popover.Root open={open} onOpenChange={setOpen}>
            <Popover.Trigger asChild>
                <button
                    type="button"
                    className={cn(
                        "flex h-9 items-center justify-between rounded-md border border-input bg-background px-3 py-1 text-sm ring-offset-background focus:outline-none focus:ring-2 focus:ring-ring focus:ring-offset-2",
                        className
                    )}
                >
                    <span className={cn("truncate", values.length === 0 && "text-muted-foreground")}>
                        {displayText}
                    </span>
                    <ChevronDown className="ml-2 h-4 w-4 shrink-0 opacity-50" />
                </button>
            </Popover.Trigger>
            <Popover.Portal>
                <Popover.Content
                    className="z-50 min-w-[var(--radix-popover-trigger-width)] rounded-md border bg-popover text-popover-foreground shadow-md"
                    align="start"
                    sideOffset={4}
                    onOpenAutoFocus={(e) => e.preventDefault()}
                >
                    <div className="max-h-48 overflow-y-auto p-1">
                        {groups ? (
                            groups.map((group, idx) => (
                                <React.Fragment key={group.label}>
                                    {idx > 0 && <div className="my-1 h-px bg-border" />}
                                    <div className="px-2 py-1.5 text-xs font-medium text-muted-foreground">
                                        {group.label}
                                    </div>
                                    {group.options.map(renderOption)}
                                </React.Fragment>
                            ))
                        ) : (
                            (options || []).map(renderOption)
                        )}
                        {allOptions.length === 0 && (
                            <div className="py-6 text-center text-sm text-muted-foreground">No options available.</div>
                        )}
                    </div>
                </Popover.Content>
            </Popover.Portal>
        </Popover.Root>
    );
}
