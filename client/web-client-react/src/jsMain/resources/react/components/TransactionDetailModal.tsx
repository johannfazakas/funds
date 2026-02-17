import { useEffect, useState } from 'react';
import { Transaction, TransactionRecord, getTransaction } from '../api/transactionApi';
import { Badge } from './ui/badge';
import { Card } from './ui/card';
import { Button } from './ui/button';
import {
    Dialog,
    DialogContent,
    DialogHeader,
    DialogTitle,
} from './ui/dialog';
import { Loader2, ArrowRight } from 'lucide-react';

interface TransactionDetailModalProps {
    open: boolean;
    onClose: () => void;
    userId: string;
    transactionId: string | null;
    accountsMap: Map<string, string>;
    fundsMap: Map<string, string>;
}

function formatType(type: string): string {
    return type.replace(/_/g, ' ');
}

function formatAmount(amount: string): string {
    return parseFloat(amount).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 8 });
}

function RecordCard({ record, accountsMap, fundsMap, label }: {
    record: TransactionRecord;
    accountsMap: Map<string, string>;
    fundsMap: Map<string, string>;
    label?: string;
}) {
    const isNegative = parseFloat(record.amount) < 0;
    return (
        <Card className="p-4 flex-1">
            {label && <div className="text-xs font-semibold text-muted-foreground uppercase mb-2">{label}</div>}
            <div className="space-y-1.5">
                <div className="flex justify-between items-center">
                    <span className="text-sm text-muted-foreground">Account</span>
                    <span className="text-sm font-medium">{accountsMap.get(record.accountId) || record.accountId}</span>
                </div>
                <div className="flex justify-between items-center">
                    <span className="text-sm text-muted-foreground">Fund</span>
                    <span className="text-sm font-medium">{fundsMap.get(record.fundId) || record.fundId}</span>
                </div>
                <div className="flex justify-between items-center">
                    <span className="text-sm text-muted-foreground">Amount</span>
                    <span className={`text-sm font-medium ${isNegative ? 'text-destructive' : 'text-green-600'}`}>
                        {formatAmount(record.amount)}
                    </span>
                </div>
                <div className="flex justify-between items-center">
                    <span className="text-sm text-muted-foreground">Unit</span>
                    <Badge variant={record.recordType === 'CURRENCY' ? 'default' : 'secondary'}>
                        {record.unit}
                    </Badge>
                </div>
                {record.note && (
                    <div className="flex justify-between items-start gap-4">
                        <span className="text-sm text-muted-foreground shrink-0">Note</span>
                        <span className="text-sm text-right">{record.note}</span>
                    </div>
                )}
                {record.labels.length > 0 && (
                    <div className="flex justify-between items-start">
                        <span className="text-sm text-muted-foreground">Labels</span>
                        <div className="flex flex-wrap gap-1 justify-end">
                            {record.labels.map((label, idx) => (
                                <Badge key={idx} variant="outline" className="text-xs">{label}</Badge>
                            ))}
                        </div>
                    </div>
                )}
            </div>
        </Card>
    );
}

function FlowLayout({ left, right, leftLabel, rightLabel, accountsMap, fundsMap }: {
    left: TransactionRecord;
    right: TransactionRecord;
    leftLabel: string;
    rightLabel: string;
    accountsMap: Map<string, string>;
    fundsMap: Map<string, string>;
}) {
    return (
        <div className="flex items-stretch gap-3">
            <RecordCard record={left} accountsMap={accountsMap} fundsMap={fundsMap} label={leftLabel} />
            <div className="flex items-center">
                <ArrowRight className="h-5 w-5 text-muted-foreground" />
            </div>
            <RecordCard record={right} accountsMap={accountsMap} fundsMap={fundsMap} label={rightLabel} />
        </div>
    );
}

function SingleRecordDetail({ tx, accountsMap, fundsMap }: {
    tx: Transaction;
    accountsMap: Map<string, string>;
    fundsMap: Map<string, string>;
}) {
    return <RecordCard record={tx.records[0]} accountsMap={accountsMap} fundsMap={fundsMap} />;
}

function TransferDetail({ tx, accountsMap, fundsMap }: {
    tx: Transaction;
    accountsMap: Map<string, string>;
    fundsMap: Map<string, string>;
}) {
    return (
        <FlowLayout
            left={tx.records[0]} right={tx.records[1]}
            leftLabel="Source" rightLabel="Destination"
            accountsMap={accountsMap} fundsMap={fundsMap}
        />
    );
}

function ExchangeDetail({ tx, accountsMap, fundsMap }: {
    tx: Transaction;
    accountsMap: Map<string, string>;
    fundsMap: Map<string, string>;
}) {
    const fee = tx.records[2];
    return (
        <div className="space-y-3">
            <FlowLayout
                left={tx.records[0]} right={tx.records[1]}
                leftLabel="Source" rightLabel="Destination"
                accountsMap={accountsMap} fundsMap={fundsMap}
            />
            {fee && <RecordCard record={fee} accountsMap={accountsMap} fundsMap={fundsMap} label="Fee" />}
        </div>
    );
}

function PositionDetail({ tx, accountsMap, fundsMap, direction }: {
    tx: Transaction;
    accountsMap: Map<string, string>;
    fundsMap: Map<string, string>;
    direction: 'open' | 'close';
}) {
    const currency = tx.records.find(r => r.recordType === 'CURRENCY');
    const instrument = tx.records.find(r => r.recordType === 'INSTRUMENT');
    if (!currency || !instrument) return null;

    const left = direction === 'open' ? currency : instrument;
    const right = direction === 'open' ? instrument : currency;
    const leftLabel = direction === 'open' ? 'Currency' : 'Instrument';
    const rightLabel = direction === 'open' ? 'Instrument' : 'Currency';

    return (
        <FlowLayout
            left={left} right={right}
            leftLabel={leftLabel} rightLabel={rightLabel}
            accountsMap={accountsMap} fundsMap={fundsMap}
        />
    );
}

function TransactionDetailModal({ open, onClose, userId, transactionId, accountsMap, fundsMap }: TransactionDetailModalProps) {
    const [transaction, setTransaction] = useState<Transaction | null>(null);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        if (!transactionId || !open) {
            setTransaction(null);
            return;
        }
        const load = async () => {
            setLoading(true);
            setError(null);
            try {
                const tx = await getTransaction(userId, transactionId);
                setTransaction(tx);
            } catch (err) {
                setError(err instanceof Error ? err.message : 'Failed to load transaction');
            } finally {
                setLoading(false);
            }
        };
        load();
    }, [userId, transactionId, open]);

    const renderDetail = () => {
        if (!transaction) return null;
        switch (transaction.type) {
            case 'SINGLE_RECORD':
                return <SingleRecordDetail tx={transaction} accountsMap={accountsMap} fundsMap={fundsMap} />;
            case 'TRANSFER':
                return <TransferDetail tx={transaction} accountsMap={accountsMap} fundsMap={fundsMap} />;
            case 'EXCHANGE':
                return <ExchangeDetail tx={transaction} accountsMap={accountsMap} fundsMap={fundsMap} />;
            case 'OPEN_POSITION':
                return <PositionDetail tx={transaction} accountsMap={accountsMap} fundsMap={fundsMap} direction="open" />;
            case 'CLOSE_POSITION':
                return <PositionDetail tx={transaction} accountsMap={accountsMap} fundsMap={fundsMap} direction="close" />;
            default:
                return <div className="text-muted-foreground">Unknown transaction type: {transaction.type}</div>;
        }
    };

    return (
        <Dialog open={open} onOpenChange={(isOpen) => !isOpen && onClose()}>
            <DialogContent className="max-w-2xl">
                <DialogHeader>
                    <DialogTitle>
                        {transaction ? (
                            <div className="flex items-center gap-3">
                                <span>Transaction</span>
                                <Badge variant="outline">{formatType(transaction.type)}</Badge>
                            </div>
                        ) : 'Transaction Detail'}
                    </DialogTitle>
                </DialogHeader>

                {loading && (
                    <div className="flex justify-center p-8">
                        <Loader2 className="h-8 w-8 animate-spin text-muted-foreground" />
                    </div>
                )}

                {error && (
                    <div className="flex items-center gap-4 p-4 text-destructive bg-destructive/10 rounded-md">
                        <span>{error}</span>
                        <Button variant="outline" size="sm" onClick={() => {
                            if (transactionId) {
                                setLoading(true);
                                setError(null);
                                getTransaction(userId, transactionId)
                                    .then(setTransaction)
                                    .catch(err => setError(err instanceof Error ? err.message : 'Failed to load transaction'))
                                    .finally(() => setLoading(false));
                            }
                        }}>Retry</Button>
                    </div>
                )}

                {!loading && !error && transaction && (
                    <div className="space-y-4">
                        <div className="flex gap-4 text-sm text-muted-foreground">
                            <span>{transaction.dateTime.substring(0, 10)}</span>
                            <span>{transaction.dateTime.substring(11, 19)}</span>
                        </div>
                        {renderDetail()}
                    </div>
                )}
            </DialogContent>
        </Dialog>
    );
}

export default TransactionDetailModal;
