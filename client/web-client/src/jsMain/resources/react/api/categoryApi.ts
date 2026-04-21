import { handleApiError } from './apiUtils';

export interface Category {
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

export async function listCategories(userId: string): Promise<Category[]> {
    const url = `${getBaseUrl()}${BASE_PATH}/categories`;
    const response = await fetch(url, {
        headers: { 'FUNDS_USER_ID': userId }
    });
    if (!response.ok) await handleApiError(response, 'Failed to load categories');
    return response.json();
}

export async function createCategory(userId: string, name: string): Promise<Category> {
    const response = await fetch(`${getBaseUrl()}${BASE_PATH}/categories`, {
        method: 'POST',
        headers: {
            'FUNDS_USER_ID': userId,
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({ name })
    });
    if (!response.ok) await handleApiError(response, 'Failed to create category');
    return response.json();
}

export async function deleteCategory(userId: string, categoryId: string): Promise<void> {
    const response = await fetch(`${getBaseUrl()}${BASE_PATH}/categories/${categoryId}`, {
        method: 'DELETE',
        headers: { 'FUNDS_USER_ID': userId }
    });
    if (!response.ok) await handleApiError(response, 'Failed to delete category');
}
