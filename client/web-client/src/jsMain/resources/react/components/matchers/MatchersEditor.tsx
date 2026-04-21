import { useState, useEffect } from 'react';
import { AccountMatcher, FundMatcher, ExchangeMatcher, CategoryMatcher } from '../../api/importConfigurationApi';
import { listAccounts } from '../../api/accountApi';
import { listFunds } from '../../api/fundApi';
import { listCategories } from '../../api/categoryApi';
import { Badge } from '../ui/badge';
import { AccountMatcherEditor } from './AccountMatcherEditor';
import { FundMatcherEditor } from './FundMatcherEditor';
import { ExchangeMatcherEditor } from './ExchangeMatcherEditor';
import { CategoryMatcherEditor, CategoryMatcherRow } from './CategoryMatcherEditor';
import { ChevronDown, ChevronRight } from 'lucide-react';

export function categoryMatchersToRows(matchers: CategoryMatcher[]): CategoryMatcherRow[] {
    return matchers.flatMap(m =>
        m.importLabels.map(importLabel => ({
            importLabel,
            category: m.category,
        }))
    );
}

export function rowsToCategoryMatchers(rows: CategoryMatcherRow[]): CategoryMatcher[] {
    return rows.map(r => ({
        importLabels: [r.importLabel],
        category: r.category,
    }));
}

interface MatchersEditorProps {
    userId: string;
    accountMatchers: AccountMatcher[];
    fundMatchers: FundMatcher[];
    exchangeMatchers: ExchangeMatcher[];
    categoryMatcherRows: CategoryMatcherRow[];
    onAccountMatchersChange: (matchers: AccountMatcher[]) => void;
    onFundMatchersChange: (matchers: FundMatcher[]) => void;
    onExchangeMatchersChange: (matchers: ExchangeMatcher[]) => void;
    onCategoryMatcherRowsChange: (rows: CategoryMatcherRow[]) => void;
    disabled?: boolean;
}

interface SectionProps {
    title: string;
    count: number;
    defaultOpen?: boolean;
    children: React.ReactNode;
}

function CollapsibleSection({ title, count, defaultOpen = false, children }: SectionProps) {
    const [open, setOpen] = useState(defaultOpen);

    return (
        <div className="border rounded-md">
            <button
                type="button"
                className="flex items-center gap-2 w-full px-3 py-2 text-sm font-medium hover:bg-muted/50 rounded-t-md"
                onClick={() => setOpen(!open)}
            >
                {open ? <ChevronDown className="h-4 w-4" /> : <ChevronRight className="h-4 w-4" />}
                {title}
                {count > 0 && <Badge variant="secondary" className="ml-auto">{count}</Badge>}
            </button>
            {open && <div className="px-3 pb-3">{children}</div>}
        </div>
    );
}

export function MatchersEditor({
    userId,
    accountMatchers,
    fundMatchers,
    exchangeMatchers,
    categoryMatcherRows,
    onAccountMatchersChange,
    onFundMatchersChange,
    onExchangeMatchersChange,
    onCategoryMatcherRowsChange,
    disabled,
}: MatchersEditorProps) {
    const [accountNames, setAccountNames] = useState<string[]>([]);
    const [fundNames, setFundNames] = useState<string[]>([]);
    const [categoryNames, setCategoryNames] = useState<string[]>([]);

    useEffect(() => {
        listAccounts(userId, { pagination: { offset: 0, limit: 1000 }, sort: { field: 'name', order: 'asc' } })
            .then(result => setAccountNames(result.items.map(a => a.name)))
            .catch(() => {});
        listFunds(userId, { pagination: { offset: 0, limit: 1000 }, sort: { field: 'name', order: 'asc' } })
            .then(result => setFundNames(result.items.map(f => f.name)))
            .catch(() => {});
        listCategories(userId)
            .then(categories => setCategoryNames(categories.map(c => c.name).sort()))
            .catch(() => {});
    }, [userId]);

    return (
        <div className="space-y-2">
            <CollapsibleSection title="Account Matchers" count={accountMatchers.length}>
                <AccountMatcherEditor matchers={accountMatchers} onChange={onAccountMatchersChange} accountNames={accountNames} disabled={disabled} />
            </CollapsibleSection>
            <CollapsibleSection title="Fund Matchers" count={fundMatchers.length}>
                <FundMatcherEditor matchers={fundMatchers} onChange={onFundMatchersChange} fundNames={fundNames} disabled={disabled} />
            </CollapsibleSection>
            <CollapsibleSection title="Category Matchers" count={categoryMatcherRows.length}>
                <CategoryMatcherEditor matchers={categoryMatcherRows} onChange={onCategoryMatcherRowsChange} categoryNames={categoryNames} disabled={disabled} />
            </CollapsibleSection>
            <CollapsibleSection title="Exchange Matchers" count={exchangeMatchers.length}>
                <ExchangeMatcherEditor matchers={exchangeMatchers} onChange={onExchangeMatchersChange} disabled={disabled} />
            </CollapsibleSection>
        </div>
    );
}
