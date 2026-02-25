import { useState, useEffect } from 'react';
import { AccountMatcher, FundMatcher, ExchangeMatcher, LabelMatcher } from '../../api/importConfigurationApi';
import { listAccounts } from '../../api/accountApi';
import { listFunds } from '../../api/fundApi';
import { listLabels } from '../../api/labelApi';
import { Badge } from '../ui/badge';
import { AccountMatcherEditor } from './AccountMatcherEditor';
import { FundMatcherEditor } from './FundMatcherEditor';
import { ExchangeMatcherEditor } from './ExchangeMatcherEditor';
import { LabelMatcherEditor, LabelMatcherRow } from './LabelMatcherEditor';
import { ChevronDown, ChevronRight } from 'lucide-react';

export function labelMatchersToRows(matchers: LabelMatcher[]): LabelMatcherRow[] {
    return matchers.flatMap(m =>
        m.importLabels.map(label => ({
            importLabel: label,
            label: m.label,
        }))
    );
}

export function rowsToLabelMatchers(rows: LabelMatcherRow[]): LabelMatcher[] {
    return rows.map(r => ({
        importLabels: [r.importLabel],
        label: r.label,
    }));
}

interface MatchersEditorProps {
    userId: string;
    accountMatchers: AccountMatcher[];
    fundMatchers: FundMatcher[];
    exchangeMatchers: ExchangeMatcher[];
    labelMatcherRows: LabelMatcherRow[];
    onAccountMatchersChange: (matchers: AccountMatcher[]) => void;
    onFundMatchersChange: (matchers: FundMatcher[]) => void;
    onExchangeMatchersChange: (matchers: ExchangeMatcher[]) => void;
    onLabelMatcherRowsChange: (rows: LabelMatcherRow[]) => void;
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
    labelMatcherRows,
    onAccountMatchersChange,
    onFundMatchersChange,
    onExchangeMatchersChange,
    onLabelMatcherRowsChange,
    disabled,
}: MatchersEditorProps) {
    const [accountNames, setAccountNames] = useState<string[]>([]);
    const [fundNames, setFundNames] = useState<string[]>([]);
    const [labelNames, setLabelNames] = useState<string[]>([]);

    useEffect(() => {
        listAccounts(userId, { pagination: { offset: 0, limit: 1000 }, sort: { field: 'name', order: 'asc' } })
            .then(result => setAccountNames(result.items.map(a => a.name)))
            .catch(() => {});
        listFunds(userId, { pagination: { offset: 0, limit: 1000 }, sort: { field: 'name', order: 'asc' } })
            .then(result => setFundNames(result.items.map(f => f.name)))
            .catch(() => {});
        listLabels(userId)
            .then(labels => setLabelNames(labels.map(l => l.name).sort()))
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
            <CollapsibleSection title="Label Matchers" count={labelMatcherRows.length}>
                <LabelMatcherEditor matchers={labelMatcherRows} onChange={onLabelMatcherRowsChange} labelNames={labelNames} disabled={disabled} />
            </CollapsibleSection>
            <CollapsibleSection title="Exchange Matchers" count={exchangeMatchers.length}>
                <ExchangeMatcherEditor matchers={exchangeMatchers} onChange={onExchangeMatchersChange} disabled={disabled} />
            </CollapsibleSection>
        </div>
    );
}
