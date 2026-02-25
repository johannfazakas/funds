import * as React from "react"
import * as Popover from "@radix-ui/react-popover"
import { Command } from "cmdk"
import { Check, ChevronsUpDown } from "lucide-react"
import { cn } from "../../lib/utils"

interface SearchableSelectProps {
    value: string;
    onValueChange: (value: string) => void;
    options: string[];
    placeholder?: string;
    disabled?: boolean;
    className?: string;
}

export function SearchableSelect({
    value,
    onValueChange,
    options,
    placeholder = "Select...",
    disabled,
    className,
}: SearchableSelectProps) {
    const [open, setOpen] = React.useState(false);
    const [search, setSearch] = React.useState("");

    return (
        <Popover.Root open={open} onOpenChange={setOpen}>
            <Popover.Trigger asChild disabled={disabled}>
                <button
                    type="button"
                    role="combobox"
                    aria-expanded={open}
                    disabled={disabled}
                    className={cn(
                        "flex h-10 w-full items-center justify-between rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-ring focus:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50",
                        className
                    )}
                >
                    <span className="truncate">{value || <span className="text-muted-foreground">{placeholder}</span>}</span>
                    <ChevronsUpDown className="ml-2 h-4 w-4 shrink-0 opacity-50" />
                </button>
            </Popover.Trigger>
            <Popover.Portal>
                <Popover.Content
                    className="z-50 w-[var(--radix-popover-trigger-width)] rounded-md border bg-popover text-popover-foreground shadow-md"
                    align="start"
                    sideOffset={4}
                    onOpenAutoFocus={(e) => e.preventDefault()}
                >
                    <Command shouldFilter={false}>
                        <div className="flex items-center border-b px-3">
                            <Command.Input
                                value={search}
                                onValueChange={setSearch}
                                placeholder="Search..."
                                className="flex h-9 w-full bg-transparent py-2 text-sm outline-none placeholder:text-muted-foreground"
                            />
                        </div>
                        <Command.List className="max-h-48 overflow-y-auto p-1">
                            {options
                                .filter(opt => opt.toLowerCase().includes(search.toLowerCase()))
                                .map(opt => (
                                    <Command.Item
                                        key={opt}
                                        value={opt}
                                        onSelect={() => {
                                            onValueChange(opt);
                                            setOpen(false);
                                            setSearch("");
                                        }}
                                        className="relative flex cursor-default select-none items-center rounded-sm px-2 py-1.5 text-sm outline-none aria-selected:bg-accent aria-selected:text-accent-foreground data-[disabled]:pointer-events-none data-[disabled]:opacity-50"
                                    >
                                        <Check className={cn("mr-2 h-4 w-4", value === opt ? "opacity-100" : "opacity-0")} />
                                        {opt}
                                    </Command.Item>
                                ))}
                            {options.filter(opt => opt.toLowerCase().includes(search.toLowerCase())).length === 0 && (
                                <div className="py-6 text-center text-sm text-muted-foreground">No results found.</div>
                            )}
                        </Command.List>
                    </Command>
                </Popover.Content>
            </Popover.Portal>
        </Popover.Root>
    );
}
