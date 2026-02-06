import { ListResponse } from './types';

export interface Account {
    id: string;
    name: string;
    unit: {
        type: string;
        value: string;
    };
}

interface CreateAccountRequest {
    name: string;
    unit: {
        type: string;
        value: string;
    };
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

export async function listAccounts(userId: string): Promise<Account[]> {
    const response = await fetch(`${getBaseUrl()}${BASE_PATH}/accounts`, {
        headers: { 'FUNDS_USER_ID': userId }
    });
    if (!response.ok) throw new Error(`Failed to list accounts: ${response.status}`);
    const data: ListResponse<Account> = await response.json();
    return data.items;
}

export async function createAccount(
    userId: string,
    name: string,
    unitType: string,
    unitValue: string
): Promise<Account> {
    const response = await fetch(`${getBaseUrl()}${BASE_PATH}/accounts`, {
        method: 'POST',
        headers: {
            'FUNDS_USER_ID': userId,
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({
            name,
            unit: { type: unitType, value: unitValue }
        } as CreateAccountRequest)
    });
    if (!response.ok) throw new Error(`Failed to create account: ${response.status}`);
    return response.json();
}

export async function deleteAccount(userId: string, accountId: string): Promise<void> {
    const response = await fetch(`${getBaseUrl()}${BASE_PATH}/accounts/${accountId}`, {
        method: 'DELETE',
        headers: { 'FUNDS_USER_ID': userId }
    });
    if (!response.ok) throw new Error(`Failed to delete account: ${response.status}`);
}
