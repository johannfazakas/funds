import { handleApiError } from './apiUtils';

export interface TransactionRecord {
    id: string;
    accountId: string;
    fundId: string;
    amount: string;
    unit: string;
    recordType: 'CURRENCY' | 'INSTRUMENT';
    labels: string[];
}

export interface Transaction {
    id: string;
    userId: string;
    externalId: string;
    dateTime: string;
    type: string;
    records: TransactionRecord[];
}

declare const window: Window & {
    FUNDS_CONFIG?: { fundServiceUrl?: string };
};

function getBaseUrl(): string {
    const url = window.FUNDS_CONFIG?.fundServiceUrl;
    if (!url) {
        throw new Error('FUNDS_CONFIG.fundServiceUrl is not configured');
    }
    return url;
}

const BASE_PATH = '/funds-api/fund/v1';

interface RawRecord {
    type: 'CURRENCY' | 'INSTRUMENT';
    id: string;
    accountId: string;
    fundId: string;
    amount: string;
    unit: string;
    labels: string[];
}

function mapRecord(raw: RawRecord): TransactionRecord {
    return {
        id: raw.id,
        accountId: raw.accountId,
        fundId: raw.fundId,
        amount: raw.amount,
        unit: raw.unit,
        recordType: raw.type,
        labels: raw.labels,
    };
}

function mapTransaction(raw: any): Transaction {
    const records: TransactionRecord[] = [];
    switch (raw.type) {
        case 'SINGLE_RECORD':
            records.push(mapRecord(raw.record));
            break;
        case 'TRANSFER':
            records.push(mapRecord(raw.sourceRecord));
            records.push(mapRecord(raw.destinationRecord));
            break;
        case 'EXCHANGE':
            records.push(mapRecord(raw.sourceRecord));
            records.push(mapRecord(raw.destinationRecord));
            if (raw.feeRecord) records.push(mapRecord(raw.feeRecord));
            break;
        case 'OPEN_POSITION':
        case 'CLOSE_POSITION':
            records.push(mapRecord(raw.currencyRecord));
            records.push(mapRecord(raw.instrumentRecord));
            break;
    }
    return {
        id: raw.id,
        userId: raw.userId,
        externalId: raw.externalId,
        dateTime: raw.dateTime,
        type: raw.type,
        records,
    };
}

export async function getTransaction(userId: string, transactionId: string): Promise<Transaction> {
    const url = `${getBaseUrl()}${BASE_PATH}/transactions/${transactionId}`;
    const response = await fetch(url, {
        headers: { 'FUNDS_USER_ID': userId },
    });
    if (!response.ok) await handleApiError(response, 'Failed to load transaction');
    const raw = await response.json();
    return mapTransaction(raw);
}
