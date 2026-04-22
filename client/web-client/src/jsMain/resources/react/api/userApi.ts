import { handleApiError } from './apiUtils';

export interface User {
    id: string;
    username: string;
}

declare const window: Window & {
    FUNDS_CONFIG?: { userServiceUrl?: string };
};

function getBaseUrl(): string {
    const url = window.FUNDS_CONFIG?.userServiceUrl;
    if (!url) {
        throw new Error('FUNDS_CONFIG.userServiceUrl is not configured');
    }
    return url;
}

const BASE_PATH = '/funds-api/user/v1';

export async function loginWithUsername(username: string): Promise<User | null> {
    const url = `${getBaseUrl()}${BASE_PATH}/users/username/${encodeURIComponent(username)}`;
    const response = await fetch(url);
    if (!response.ok) {
        if (response.status === 404) return null;
        await handleApiError(response, 'Login failed');
    }
    return await response.json();
}
