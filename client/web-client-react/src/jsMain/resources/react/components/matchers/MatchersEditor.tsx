import { useState, useEffect } from 'react';
import { AccountMatcher, FundMatcher, ExchangeMatcher, LabelMatcher } from '../../api/importConfigurationApi';
import { listAccounts } from '../../api/accountApi';
import { listFunds } from '../../api/fundApi';
import { listLabels } from '../../api/labelApi';
import { Badge } from '../ui/badge';
import { AccountMatcherEditor, AccountMatcherRow } from './AccountMatcherEditor';
import { FundMatcherEditor, FundMatcherRow } from './FundMatcherEditor';
import { ExchangeMatcherEditor } from './ExchangeMatcherEditor';
import { LabelMatcherEditor, LabelMatcherRow } from './LabelMatcherEditor';
import { ChevronDown, ChevronRight } from 'lucide-react';

export function accountMatchersToRows(matchers: AccountMatcher[]): AccountMatcherRow[] {
    return matchers.flatMap(m =>
        m.importAccountNames.map(name => ({
            type: m.type,
            importAccountName: name,
            accountName: m.accountName,
        }))
    );
}

export function rowsToAccountMatchers(rows: AccountMatcherRow[]): AccountMatcher[] {
    return rows.map(r => ({
        type: r.type,
        importAccountNames: [r.importAccountName],
        ...(r.type === 'by_name' ? { accountName: r.accountName } : {}),
    }));
}

export function fundMatchersToRows(matchers: FundMatcher[]): FundMatcherRow[] {
    const rows: FundMatcherRow[] = [];
    for (const m of matchers) {
        const names = m.importAccountNames || [];
        const labels = m.importLabels || [];
        const hasAccount = m.type.includes('account');
        const hasLabel = m.type.includes('label');
        const items = hasAccount ? names : hasLabel ? labels : [''];
        for (const item of items.length > 0 ? items : ['']) {
            rows.push({
                type: m.type,
                fundName: m.fundName,
                importAccountName: hasAccount ? item : undefined,
                importLabel: hasLabel && !hasAccount ? item : (hasLabel ? (labels[0] || '') : undefined),
                initialFundName: m.initialFundName,
            });
        }
    }
    return rows;
}

export function rowsToFundMatchers(rows: FundMatcherRow[]): FundMatcher[] {
    return rows.map(r => {
        const m: FundMatcher = { type: r.type, fundName: r.fundName };
        if (r.type.includes('account') && r.importAccountName !== undefined) {
            m.importAccountNames = [r.importAccountName];
        }
        if (r.type.includes('label') && r.importLabel !== undefined) {
            m.importLabels = [r.importLabel];
        }
        if (r.type.includes('transfer') && r.initialFundName !== undefined) {
            m.initialFundName = r.initialFundName;
        }
        return m;
    });
}

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
    accountMatcherRows: AccountMatcherRow[];
    fundMatcherRows: FundMatcherRow[];
    exchangeMatchers: ExchangeMatcher[];
    labelMatcherRows: LabelMatcherRow[];
    onAccountMatcherRowsChange: (rows: AccountMatcherRow[]) => void;
    onFundMatcherRowsChange: (rows: FundMatcherRow[]) => void;
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
    accountMatcherRows,
    fundMatcherRows,
    exchangeMatchers,
    labelMatcherRows,
    onAccountMatcherRowsChange,
    onFundMatcherRowsChange,
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
            <CollapsibleSection title="Account Matchers" count={accountMatcherRows.length}>
                <AccountMatcherEditor matchers={accountMatcherRows} onChange={onAccountMatcherRowsChange} accountNames={accountNames} disabled={disabled} />
            </CollapsibleSection>
            <CollapsibleSection title="Fund Matchers" count={fundMatcherRows.length}>
                <FundMatcherEditor matchers={fundMatcherRows} onChange={onFundMatcherRowsChange} fundNames={fundNames} disabled={disabled} />
            </CollapsibleSection>
            <CollapsibleSection title="Exchange Matchers" count={exchangeMatchers.length}>
                <ExchangeMatcherEditor matchers={exchangeMatchers} onChange={onExchangeMatchersChange} disabled={disabled} />
            </CollapsibleSection>
            <CollapsibleSection title="Label Matchers" count={labelMatcherRows.length}>
                <LabelMatcherEditor matchers={labelMatcherRows} onChange={onLabelMatcherRowsChange} labelNames={labelNames} disabled={disabled} />
            </CollapsibleSection>
        </div>
    );
}
