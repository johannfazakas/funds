export interface Fund {
    id: string;
    name: string;
}

interface ListResponse<T> {
    items: T[];
    count: number;
}

interface CreateFundRequest {
    name: string;
}

declare const window: Window & {
    FUNDS_CONFIG?: { fundServiceUrl?: string };
};

const getBaseUrl = () =>
    window.FUNDS_CONFIG?.fundServiceUrl ?? 'http://localhost:5253';

const BASE_PATH = '/funds-api/fund/v1';

export async function listFunds(userId: string): Promise<Fund[]> {
    const response = await fetch(`${getBaseUrl()}${BASE_PATH}/funds`, {
        headers: { 'FUNDS_USER_ID': userId }
    });
    if (!response.ok) throw new Error(`Failed to list funds: ${response.status}`);
    const data: ListResponse<Fund> = await response.json();
    return data.items;
}

export async function createFund(userId: string, name: string): Promise<Fund> {
    const response = await fetch(`${getBaseUrl()}${BASE_PATH}/funds`, {
        method: 'POST',
        headers: {
            'FUNDS_USER_ID': userId,
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({ name } as CreateFundRequest)
    });
    if (!response.ok) throw new Error(`Failed to create fund: ${response.status}`);
    return response.json();
}

export async function deleteFund(userId: string, fundId: string): Promise<void> {
    const response = await fetch(`${getBaseUrl()}${BASE_PATH}/funds/${fundId}`, {
        method: 'DELETE',
        headers: { 'FUNDS_USER_ID': userId }
    });
    if (!response.ok) throw new Error(`Failed to delete fund: ${response.status}`);
}
