import * as React from "react"
import { format, parseISO } from "date-fns"
import * as Popover from "@radix-ui/react-popover"
import { Calendar as CalendarIcon } from "lucide-react"
import { cn } from "../../lib/utils"
import { Button } from "./button"
import { Calendar } from "./calendar"

interface DatePickerProps {
    value: string
    onChange: (value: string) => void
    className?: string
}

function DatePicker({ value, onChange, className }: DatePickerProps) {
    const [open, setOpen] = React.useState(false)
    const date = value ? parseISO(value) : undefined

    const handleSelect = (selected: Date | undefined) => {
        if (selected) {
            const year = selected.getFullYear()
            const month = String(selected.getMonth() + 1).padStart(2, '0')
            const day = String(selected.getDate()).padStart(2, '0')
            onChange(`${year}-${month}-${day}`)
            setOpen(false)
        }
    }

    return (
        <Popover.Root open={open} onOpenChange={setOpen}>
            <Popover.Trigger asChild>
                <Button
                    variant="outline"
                    size="sm"
                    className={cn(
                        "justify-start text-left font-normal",
                        !date && "text-muted-foreground",
                        className
                    )}
                >
                    <CalendarIcon className="mr-2 h-4 w-4" />
                    {date ? format(date, "MMM d, yyyy") : "Pick a date"}
                </Button>
            </Popover.Trigger>
            <Popover.Portal>
                <Popover.Content
                    className="z-50 rounded-md border bg-popover p-0 text-popover-foreground shadow-md outline-none animate-in fade-in-0 zoom-in-95"
                    align="start"
                    sideOffset={4}
                >
                    <Calendar
                        mode="single"
                        selected={date}
                        onSelect={handleSelect}
                        defaultMonth={date}
                        initialFocus
                    />
                </Popover.Content>
            </Popover.Portal>
        </Popover.Root>
    )
}

export { DatePicker }
