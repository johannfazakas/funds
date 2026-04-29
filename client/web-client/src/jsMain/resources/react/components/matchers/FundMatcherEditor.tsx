import { FundMatcher, FundMatcherCategoryRule } from '../../api/importConfigurationApi';
import { Account } from '../../api/accountApi';
import { Fund } from '../../api/fundApi';
import { Category } from '../../api/categoryApi';
import { Button } from '../ui/button';
import { SearchableSelect } from '../ui/searchable-select';
import { MultiSelect, MultiSelectOption } from '../ui/multi-select';
import { Plus, X } from 'lucide-react';

interface FundMatcherEditorProps {
    matchers: FundMatcher[];
    onChange: (matchers: FundMatcher[]) => void;
    accounts: Account[];
    funds: Fund[];
    categories: Category[];
    disabled?: boolean;
}

export function FundMatcherEditor({ matchers, onChange, accounts, funds, categories, disabled }: FundMatcherEditorProps) {
    const updateMatcher = (index: number, updated: FundMatcher) => {
        const next = [...matchers];
        next[index] = updated;
        onChange(next);
    };

    const removeMatcher = (index: number) => {
        onChange(matchers.filter((_, i) => i !== index));
    };

    const addMatcher = () => {
        onChange([...matchers, { accountIds: [], defaultFundId: undefined, categoryRules: [] }]);
    };

    const accountOptions: MultiSelectOption[] = accounts.map(a => ({ value: a.id, label: a.name }));
    const fundName = (id?: string) => funds.find(f => f.id === id)?.name ?? '';
    const categoryName = (id: string) => categories.find(c => c.id === id)?.name ?? '';

    return (
        <div className="space-y-3">
            {matchers.map((matcher, index) => (
                <div key={index} className="border rounded-md bg-background">
                    <div className="flex items-center gap-2 px-3 py-2 border-b bg-muted/30 rounded-t-md">
                        <span className="text-sm font-medium">Rule {index + 1}</span>
                        <div className="ml-auto">
                            <Button
                                type="button"
                                variant="ghost"
                                size="sm"
                                className="h-6 w-6 p-0 text-muted-foreground hover:text-destructive"
                                onClick={() => removeMatcher(index)}
                                disabled={disabled}
                            >
                                <X className="h-3.5 w-3.5" />
                            </Button>
                        </div>
                    </div>
                    <div className="p-3 space-y-3">
                        <div>
                            <div className="text-xs font-semibold text-muted-foreground uppercase tracking-wide mb-1">Accounts</div>
                            <MultiSelect
                                values={matcher.accountIds}
                                onValuesChange={(ids) => updateMatcher(index, { ...matcher, accountIds: ids })}
                                options={accountOptions}
                                placeholder="Select accounts..."
                                className="h-auto min-h-[2rem] text-sm"
                            />
                        </div>
                        <div>
                            <div className="text-xs font-semibold text-muted-foreground uppercase tracking-wide mb-1">Default Fund</div>
                            <div className="flex items-center gap-1">
                                <SearchableSelect
                                    value={fundName(matcher.defaultFundId)}
                                    onValueChange={(name) => {
                                        const f = funds.find(f => f.name === name);
                                        updateMatcher(index, { ...matcher, defaultFundId: f?.id });
                                    }}
                                    options={['(none)', ...funds.map(f => f.name)]}
                                    placeholder="No default"
                                    disabled={disabled}
                                    className="h-8 text-sm"
                                />
                            </div>
                        </div>
                        <div>
                            <div className="text-xs font-semibold text-muted-foreground uppercase tracking-wide mb-1">Category Rules</div>
                            <div className="space-y-1">
                                {matcher.categoryRules.map((rule, ruleIdx) => (
                                    <div key={ruleIdx} className="flex items-center gap-1 p-1.5 border rounded-md bg-card">
                                        <div className="flex items-center gap-1 flex-1 min-w-0">
                                            <span className="text-xs text-muted-foreground whitespace-nowrap">category</span>
                                            <SearchableSelect
                                                value={categoryName(rule.categoryId)}
                                                onValueChange={(name) => {
                                                    const cat = categories.find(c => c.name === name);
                                                    if (cat) {
                                                        const rules = [...matcher.categoryRules];
                                                        rules[ruleIdx] = { ...rule, categoryId: cat.id };
                                                        updateMatcher(index, { ...matcher, categoryRules: rules });
                                                    }
                                                }}
                                                options={categories.map(c => c.name)}
                                                placeholder="Select"
                                                disabled={disabled}
                                                className="h-8 text-sm"
                                            />
                                        </div>
                                        <div className="flex items-center gap-1 flex-1 min-w-0">
                                            <span className="text-xs text-muted-foreground whitespace-nowrap">fund</span>
                                            <SearchableSelect
                                                value={fundName(rule.fundId)}
                                                onValueChange={(name) => {
                                                    const f = funds.find(f => f.name === name);
                                                    if (f) {
                                                        const rules = [...matcher.categoryRules];
                                                        rules[ruleIdx] = { ...rule, fundId: f.id };
                                                        updateMatcher(index, { ...matcher, categoryRules: rules });
                                                    }
                                                }}
                                                options={funds.map(f => f.name)}
                                                placeholder="Select"
                                                disabled={disabled}
                                                className="h-8 text-sm"
                                            />
                                        </div>
                                        {rule.intermediaryFundId != null ? (
                                            <div className="flex items-center gap-1 flex-1 min-w-0">
                                                <span className="text-xs text-muted-foreground whitespace-nowrap">intermediary</span>
                                                <SearchableSelect
                                                    value={fundName(rule.intermediaryFundId)}
                                                    onValueChange={(name) => {
                                                        const f = funds.find(f => f.name === name);
                                                        if (f) {
                                                            const rules = [...matcher.categoryRules];
                                                            rules[ruleIdx] = { ...rule, intermediaryFundId: f.id };
                                                            updateMatcher(index, { ...matcher, categoryRules: rules });
                                                        }
                                                    }}
                                                    options={funds.map(f => f.name)}
                                                    placeholder="Select"
                                                    disabled={disabled}
                                                    className="h-8 text-sm"
                                                />
                                                <button
                                                    type="button"
                                                    onClick={() => {
                                                        const rules = [...matcher.categoryRules];
                                                        rules[ruleIdx] = { ...rule, intermediaryFundId: undefined };
                                                        updateMatcher(index, { ...matcher, categoryRules: rules });
                                                    }}
                                                    disabled={disabled}
                                                    className="text-muted-foreground hover:text-destructive shrink-0 p-0.5"
                                                >
                                                    <X className="h-2.5 w-2.5" />
                                                </button>
                                            </div>
                                        ) : (
                                            <button
                                                type="button"
                                                onClick={() => {
                                                    const rules = [...matcher.categoryRules];
                                                    rules[ruleIdx] = { ...rule, intermediaryFundId: '' };
                                                    updateMatcher(index, { ...matcher, categoryRules: rules });
                                                }}
                                                disabled={disabled}
                                                className="inline-flex items-center gap-0.5 px-1.5 py-0.5 text-xs text-muted-foreground border border-dashed rounded-full hover:bg-muted/50 hover:text-foreground disabled:opacity-50 shrink-0"
                                            >
                                                <Plus className="h-2.5 w-2.5" />intermediary
                                            </button>
                                        )}
                                        <Button
                                            type="button"
                                            variant="ghost"
                                            size="sm"
                                            className="h-8 w-6 p-0 shrink-0 text-muted-foreground hover:text-destructive"
                                            onClick={() => {
                                                const rules = matcher.categoryRules.filter((_, i) => i !== ruleIdx);
                                                updateMatcher(index, { ...matcher, categoryRules: rules });
                                            }}
                                            disabled={disabled}
                                        >
                                            <X className="h-3.5 w-3.5" />
                                        </Button>
                                    </div>
                                ))}
                            </div>
                            <div className="mt-1.5">
                                <button
                                    type="button"
                                    onClick={() => {
                                        const newRule: FundMatcherCategoryRule = { categoryId: '', fundId: '' };
                                        updateMatcher(index, { ...matcher, categoryRules: [...matcher.categoryRules, newRule] });
                                    }}
                                    disabled={disabled}
                                    className="inline-flex items-center gap-0.5 px-1.5 py-0.5 text-xs text-muted-foreground border border-dashed rounded-full hover:bg-muted/50 hover:text-foreground disabled:opacity-50"
                                >
                                    <Plus className="h-2.5 w-2.5" />add rule
                                </button>
                            </div>
                        </div>
                    </div>
                </div>
            ))}
            <Button type="button" variant="outline" size="sm" onClick={addMatcher} disabled={disabled}>
                <Plus className="h-3.5 w-3.5 mr-1" /> Add Fund Matcher
            </Button>
        </div>
    );
}
