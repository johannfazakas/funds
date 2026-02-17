import { handleApiError } from './apiUtils';

export interface Label {
    id: string;
    name: string;
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

export async function listLabels(userId: string): Promise<Label[]> {
    const url = `${getBaseUrl()}${BASE_PATH}/labels`;
    const response = await fetch(url, {
        headers: { 'FUNDS_USER_ID': userId }
    });
    if (!response.ok) await handleApiError(response, 'Failed to load labels');
    return response.json();
}

export async function createLabel(userId: string, name: string): Promise<Label> {
    const response = await fetch(`${getBaseUrl()}${BASE_PATH}/labels`, {
        method: 'POST',
        headers: {
            'FUNDS_USER_ID': userId,
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({ name })
    });
    if (!response.ok) await handleApiError(response, 'Failed to create label');
    return response.json();
}

export async function deleteLabel(userId: string, labelId: string): Promise<void> {
    const response = await fetch(`${getBaseUrl()}${BASE_PATH}/labels/${labelId}`, {
        method: 'DELETE',
        headers: { 'FUNDS_USER_ID': userId }
    });
    if (!response.ok) await handleApiError(response, 'Failed to delete label');
}
