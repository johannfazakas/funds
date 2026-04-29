import { useState, useEffect } from 'react';
import { AccountMatcher, FundMatcher, ExchangeMatcher, CategoryMatcher } from '../../api/importConfigurationApi';
import { Account, listAccounts } from '../../api/accountApi';
import { Fund, listFunds } from '../../api/fundApi';
import { Category, listCategories } from '../../api/categoryApi';
import { Badge } from '../ui/badge';
import { AccountMatcherEditor } from './AccountMatcherEditor';
import { FundMatcherEditor } from './FundMatcherEditor';
import { ExchangeMatcherEditor } from './ExchangeMatcherEditor';
import { CategoryMatcherEditor } from './CategoryMatcherEditor';
import { ChevronDown, ChevronRight } from 'lucide-react';

interface MatchersEditorProps {
    userId: string;
    accountMatchers: AccountMatcher[];
    fundMatchers: FundMatcher[];
    exchangeMatchers: ExchangeMatcher[];
    categoryMatchers: CategoryMatcher[];
    onAccountMatchersChange: (matchers: AccountMatcher[]) => void;
    onFundMatchersChange: (matchers: FundMatcher[]) => void;
    onExchangeMatchersChange: (matchers: ExchangeMatcher[]) => void;
    onCategoryMatchersChange: (matchers: CategoryMatcher[]) => void;
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
    categoryMatchers,
    onAccountMatchersChange,
    onFundMatchersChange,
    onExchangeMatchersChange,
    onCategoryMatchersChange,
    disabled,
}: MatchersEditorProps) {
    const [accounts, setAccounts] = useState<Account[]>([]);
    const [funds, setFunds] = useState<Fund[]>([]);
    const [categories, setCategories] = useState<Category[]>([]);

    useEffect(() => {
        listAccounts(userId, { pagination: { offset: 0, limit: 1000 }, sort: { field: 'name', order: 'asc' } })
            .then(result => setAccounts(result.items))
            .catch(() => {});
        listFunds(userId, { pagination: { offset: 0, limit: 1000 }, sort: { field: 'name', order: 'asc' } })
            .then(result => setFunds(result.items))
            .catch(() => {});
        listCategories(userId)
            .then(cats => setCategories(cats.sort((a, b) => a.name.localeCompare(b.name))))
            .catch(() => {});
    }, [userId]);

    return (
        <div className="space-y-2">
            <CollapsibleSection title="Account Matchers" count={accountMatchers.length}>
                <AccountMatcherEditor matchers={accountMatchers} onChange={onAccountMatchersChange} accounts={accounts} disabled={disabled} />
            </CollapsibleSection>
            <CollapsibleSection title="Category Matchers" count={categoryMatchers.length}>
                <CategoryMatcherEditor matchers={categoryMatchers} onChange={onCategoryMatchersChange} categories={categories} disabled={disabled} />
            </CollapsibleSection>
            <CollapsibleSection title="Fund Matchers" count={fundMatchers.length}>
                <FundMatcherEditor matchers={fundMatchers} onChange={onFundMatchersChange} accounts={accounts} funds={funds} categories={categories} disabled={disabled} />
            </CollapsibleSection>
            <CollapsibleSection title="Exchange Matchers" count={exchangeMatchers.length}>
                <ExchangeMatcherEditor matchers={exchangeMatchers} onChange={onExchangeMatchersChange} disabled={disabled} />
            </CollapsibleSection>
        </div>
    );
}
