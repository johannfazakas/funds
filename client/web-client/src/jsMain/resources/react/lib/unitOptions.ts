import { Account } from '../api/accountApi';
import { MultiSelectGroup, MultiSelectOption } from '../components/ui/multi-select';

export interface UnitOptions {
    unitGroups: MultiSelectGroup[];
    currencies: string[];
}

export function buildUnitOptions(accounts: Account[]): UnitOptions {
    const seen = new Set<string>();
    const currencyUnits: MultiSelectOption[] = [];
    const instrumentUnits: MultiSelectOption[] = [];
    const currencies: string[] = [];
    for (const account of accounts) {
        const key = `${account.unit.type}:${account.unit.value}`;
        if (!seen.has(key)) {
            seen.add(key);
            const option = { value: key, label: account.unit.value };
            if (account.unit.type === 'currency') {
                currencyUnits.push(option);
                currencies.push(account.unit.value);
            } else {
                instrumentUnits.push(option);
            }
        }
    }
    currencyUnits.sort((a, b) => a.label.localeCompare(b.label));
    instrumentUnits.sort((a, b) => a.label.localeCompare(b.label));
    currencies.sort();
    const unitGroups: MultiSelectGroup[] = [];
    if (currencyUnits.length > 0) unitGroups.push({ label: 'Currencies', options: currencyUnits });
    if (instrumentUnits.length > 0) unitGroups.push({ label: 'Instruments', options: instrumentUnits });
    return { unitGroups, currencies };
}
