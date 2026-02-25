import { PageResponse, PaginationParams, SortParams } from './types';
import { handleApiError } from './apiUtils';

export type ImportFileType = 'WALLET_CSV' | 'FUNDS_FORMAT_CSV';
export type ImportFileStatus = 'PENDING' | 'UPLOADED';
export type ImportFileSortField = 'FILE_NAME' | 'CREATED_AT';

export interface ImportFileConfiguration {
    id: string;
    name: string;
}

export interface ImportFile {
    importFileId: string;
    fileName: string;
    type: ImportFileType;
    status: ImportFileStatus;
    importConfiguration?: ImportFileConfiguration;
    createdAt: string;
}

export interface ImportFileFilter {
    type?: ImportFileType;
    status?: ImportFileStatus;
}

export interface ListImportFilesParams {
    pagination?: PaginationParams;
    sort?: SortParams<ImportFileSortField>;
    filter?: ImportFileFilter;
}

export interface ListImportFilesResult {
    items: ImportFile[];
    total: number;
}

export interface CreateImportFileResponse {
    importFileId: string;
    fileName: string;
    type: ImportFileType;
    status: ImportFileStatus;
    uploadUrl: string;
}

interface DownloadUrlResponse {
    downloadUrl: string;
}

declare const window: Window & {
    FUNDS_CONFIG?: { importServiceUrl?: string };
};

function getBaseUrl(): string {
    const url = window.FUNDS_CONFIG?.importServiceUrl;
    if (!url) {
        throw new Error('FUNDS_CONFIG.importServiceUrl is not configured');
    }
    return url;
}

const BASE_PATH = '/funds-api/import/v1';

export async function listImportFiles(
    userId: string,
    params?: ListImportFilesParams
): Promise<ListImportFilesResult> {
    const queryParams = new URLSearchParams();

    if (params?.pagination) {
        queryParams.set('offset', params.pagination.offset.toString());
        queryParams.set('limit', params.pagination.limit.toString());
    }

    if (params?.sort) {
        queryParams.set('sort', params.sort.field);
        queryParams.set('order', params.sort.order);
    }

    if (params?.filter?.type) {
        queryParams.set('type', params.filter.type);
    }
    if (params?.filter?.status) {
        queryParams.set('status', params.filter.status);
    }

    const queryString = queryParams.toString();
    const url = `${getBaseUrl()}${BASE_PATH}/import-files${queryString ? `?${queryString}` : ''}`;

    const response = await fetch(url, {
        headers: { 'FUNDS_USER_ID': userId }
    });
    if (!response.ok) await handleApiError(response, 'Failed to load import files');
    const data: PageResponse<ImportFile> = await response.json();
    return { items: data.items, total: data.total };
}

export async function createImportFile(
    userId: string,
    fileName: string,
    type: ImportFileType
): Promise<CreateImportFileResponse> {
    const response = await fetch(`${getBaseUrl()}${BASE_PATH}/import-files`, {
        method: 'POST',
        headers: {
            'FUNDS_USER_ID': userId,
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({ fileName, type })
    });
    if (!response.ok) await handleApiError(response, 'Failed to create import file');
    return response.json();
}

export async function confirmUpload(
    userId: string,
    importFileId: string
): Promise<ImportFile> {
    const response = await fetch(
        `${getBaseUrl()}${BASE_PATH}/import-files/${importFileId}/confirm-upload`,
        {
            method: 'POST',
            headers: { 'FUNDS_USER_ID': userId }
        }
    );
    if (!response.ok) await handleApiError(response, 'Failed to confirm upload');
    return response.json();
}

export async function deleteImportFile(
    userId: string,
    importFileId: string
): Promise<void> {
    const response = await fetch(
        `${getBaseUrl()}${BASE_PATH}/import-files/${importFileId}`,
        {
            method: 'DELETE',
            headers: { 'FUNDS_USER_ID': userId }
        }
    );
    if (!response.ok) await handleApiError(response, 'Failed to delete import file');
}

export async function getDownloadUrl(
    userId: string,
    importFileId: string
): Promise<string> {
    const response = await fetch(
        `${getBaseUrl()}${BASE_PATH}/import-files/${importFileId}/download`,
        {
            headers: { 'FUNDS_USER_ID': userId }
        }
    );
    if (!response.ok) await handleApiError(response, 'Failed to get download URL');
    const data: DownloadUrlResponse = await response.json();
    return data.downloadUrl;
}
