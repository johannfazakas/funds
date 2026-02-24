import { useEffect, useState, useCallback, useRef } from 'react';
import {
    ImportFile,
    ImportFileType,
    ImportFileStatus,
    ImportFileSortField,
    listImportFiles,
    createImportFile,
    confirmUpload,
    getDownloadUrl,
    deleteImportFile,
} from '../api/importFileApi';
import { SortOrder } from '../api/types';
import { Button } from '../components/ui/button';
import { Input } from '../components/ui/input';
import { Label } from '../components/ui/label';
import { Card } from '../components/ui/card';
import {
    Table,
    TableBody,
    TableCell,
    TableHead,
    TableHeader,
    TableRow,
} from '../components/ui/table';
import {
    Dialog,
    DialogContent,
    DialogFooter,
    DialogHeader,
    DialogDescription,
    DialogTitle,
} from '../components/ui/dialog';
import {
    Select,
    SelectContent,
    SelectItem,
    SelectTrigger,
    SelectValue,
} from '../components/ui/select';
import { Badge } from '../components/ui/badge';
import { Loader2, Download, Trash2 } from 'lucide-react';
import { Pagination } from '../components/Pagination';
import { SortableTableHead } from '../components/SortableTableHead';

interface ImportsPageProps {
    userId: string;
}

const DEFAULT_PAGE_SIZE = 10;

function ImportsPage({ userId }: ImportsPageProps) {
    const [importFiles, setImportFiles] = useState<ImportFile[]>([]);
    const [total, setTotal] = useState(0);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    const [offset, setOffset] = useState(0);
    const [limit, setLimit] = useState(DEFAULT_PAGE_SIZE);
    const [sortField, setSortField] = useState<ImportFileSortField | null>(null);
    const [sortOrder, setSortOrder] = useState<SortOrder>('desc');

    const [filterType, setFilterType] = useState<string>('');
    const [filterStatus, setFilterStatus] = useState<string>('');

    const [showUploadModal, setShowUploadModal] = useState(false);
    const [fileType, setFileType] = useState<ImportFileType>('WALLET_CSV');
    const [selectedFiles, setSelectedFiles] = useState<File[]>([]);
    const [uploading, setUploading] = useState(false);
    const [uploadError, setUploadError] = useState<string | null>(null);
    const fileInputRef = useRef<HTMLInputElement>(null);

    const [fileToDelete, setFileToDelete] = useState<ImportFile | null>(null);
    const [deleting, setDeleting] = useState(false);
    const [deleteError, setDeleteError] = useState<string | null>(null);

    const loadImportFiles = useCallback(async () => {
        setLoading(true);
        setError(null);
        try {
            const filter: { type?: ImportFileType; status?: ImportFileStatus } = {};
            if (filterType) filter.type = filterType as ImportFileType;
            if (filterStatus) filter.status = filterStatus as ImportFileStatus;

            const result = await listImportFiles(userId, {
                pagination: { offset, limit },
                sort: sortField ? { field: sortField, order: sortOrder } : undefined,
                filter: Object.keys(filter).length > 0 ? filter : undefined,
            });
            setImportFiles(result.items);
            setTotal(result.total);
        } catch (err) {
            setError(err instanceof Error ? err.message : 'Failed to load import files');
        } finally {
            setLoading(false);
        }
    }, [userId, offset, limit, sortField, sortOrder, filterType, filterStatus]);

    useEffect(() => {
        loadImportFiles();
    }, [loadImportFiles]);

    const handleSort = (field: ImportFileSortField) => {
        if (sortField === field) {
            setSortOrder(sortOrder === 'asc' ? 'desc' : 'asc');
        } else {
            setSortField(field);
            setSortOrder('desc');
        }
        setOffset(0);
    };

    const handlePageChange = (newOffset: number) => {
        setOffset(newOffset);
    };

    const handlePageSizeChange = (newLimit: number) => {
        setLimit(newLimit);
        setOffset(0);
    };

    const handleFilterChange = () => {
        setOffset(0);
    };

    const clearFilters = () => {
        setFilterType('');
        setFilterStatus('');
        setOffset(0);
    };

    const hasActiveFilters = filterType || filterStatus;

    const handleFileSelect = (e: React.ChangeEvent<HTMLInputElement>) => {
        if (e.target.files) {
            setSelectedFiles(Array.from(e.target.files));
        }
    };

    const openUploadModal = () => {
        setFileType('WALLET_CSV');
        setSelectedFiles([]);
        setUploadError(null);
        if (fileInputRef.current) {
            fileInputRef.current.value = '';
        }
        setShowUploadModal(true);
    };

    const handleUpload = async (e: React.FormEvent) => {
        e.preventDefault();
        if (selectedFiles.length === 0) {
            setUploadError('Please select at least one file');
            return;
        }

        setUploading(true);
        setUploadError(null);

        try {
            for (const file of selectedFiles) {
                const createResponse = await createImportFile(userId, file.name, fileType);
                await fetch(createResponse.uploadUrl, {
                    method: 'PUT',
                    body: file,
                });
                await confirmUpload(userId, createResponse.importFileId);
            }
            setShowUploadModal(false);
            await loadImportFiles();
        } catch (err) {
            setUploadError(err instanceof Error ? err.message : 'Failed to upload files');
        } finally {
            setUploading(false);
        }
    };

    const handleDelete = async () => {
        if (!fileToDelete) return;
        setDeleting(true);
        setDeleteError(null);
        try {
            await deleteImportFile(userId, fileToDelete.importFileId);
            setFileToDelete(null);
            await loadImportFiles();
        } catch (err) {
            setDeleteError(err instanceof Error ? err.message : 'Failed to delete file');
        } finally {
            setDeleting(false);
        }
    };

    const handleDownload = async (importFile: ImportFile) => {
        try {
            const url = await getDownloadUrl(userId, importFile.importFileId);
            window.open(url, '_blank');
        } catch (err) {
            setError(err instanceof Error ? err.message : 'Failed to get download URL');
        }
    };

    const formatDateTime = (dateTime: string) => {
        const date = new Date(dateTime);
        if (isNaN(date.getTime())) return dateTime;
        return date.toLocaleString(undefined, {
            year: 'numeric',
            month: 'short',
            day: 'numeric',
            hour: '2-digit',
            minute: '2-digit',
        });
    };

    return (
        <div>
            <div className="flex justify-between items-center mb-6">
                <h1 className="text-2xl font-bold">Imports</h1>
                <Button onClick={openUploadModal}>Upload Files</Button>
            </div>

            <div className="mb-4 flex flex-wrap items-center gap-2">
                <Select
                    value={filterType}
                    onValueChange={(value) => { setFilterType(value === 'all' ? '' : value); handleFilterChange(); }}
                >
                    <SelectTrigger className="w-44">
                        <SelectValue placeholder="Type" />
                    </SelectTrigger>
                    <SelectContent>
                        <SelectItem value="all">All types</SelectItem>
                        <SelectItem value="WALLET_CSV">Wallet CSV</SelectItem>
                        <SelectItem value="FUNDS_FORMAT_CSV">Funds Format CSV</SelectItem>
                    </SelectContent>
                </Select>
                <Select
                    value={filterStatus}
                    onValueChange={(value) => { setFilterStatus(value === 'all' ? '' : value); handleFilterChange(); }}
                >
                    <SelectTrigger className="w-36">
                        <SelectValue placeholder="Status" />
                    </SelectTrigger>
                    <SelectContent>
                        <SelectItem value="all">All statuses</SelectItem>
                        <SelectItem value="PENDING">Pending</SelectItem>
                        <SelectItem value="UPLOADED">Uploaded</SelectItem>
                    </SelectContent>
                </Select>
                {hasActiveFilters && (
                    <Button variant="outline" size="sm" onClick={clearFilters}>
                        Clear
                    </Button>
                )}
            </div>

            {loading && (
                <div className="flex justify-center p-8">
                    <Loader2 className="h-8 w-8 animate-spin text-muted-foreground" />
                </div>
            )}

            {error && (
                <div className="flex items-center gap-4 p-4 mb-4 text-destructive bg-destructive/10 rounded-md">
                    <span>{error}</span>
                    <Button variant="outline" size="sm" onClick={loadImportFiles}>Retry</Button>
                </div>
            )}

            {!loading && !error && importFiles.length === 0 && (
                <div className="text-center text-muted-foreground py-8">
                    {hasActiveFilters ? 'No import files match the current filters.' : 'No import files yet — upload one to get started.'}
                </div>
            )}

            {!loading && !error && importFiles.length > 0 && (
                <Card>
                    <Table>
                        <TableHeader>
                            <TableRow>
                                <SortableTableHead
                                    field="FILE_NAME"
                                    currentField={sortField}
                                    currentOrder={sortOrder}
                                    onSort={handleSort}
                                >
                                    File Name
                                </SortableTableHead>
                                <TableHead>Type</TableHead>
                                <TableHead>Status</TableHead>
                                <SortableTableHead
                                    field="CREATED_AT"
                                    currentField={sortField}
                                    currentOrder={sortOrder}
                                    onSort={handleSort}
                                >
                                    Created At
                                </SortableTableHead>
                                <TableHead className="w-24"></TableHead>
                            </TableRow>
                        </TableHeader>
                        <TableBody>
                            {importFiles.map((file) => (
                                <TableRow key={file.importFileId}>
                                    <TableCell>{file.fileName}</TableCell>
                                    <TableCell>
                                        <Badge className={
                                            file.type === 'WALLET_CSV'
                                                ? 'bg-blue-100 text-blue-800 border-blue-200 dark:bg-blue-900 dark:text-blue-200 dark:border-blue-800'
                                                : 'bg-violet-100 text-violet-800 border-violet-200 dark:bg-violet-900 dark:text-violet-200 dark:border-violet-800'
                                        }>
                                            {file.type === 'WALLET_CSV' ? 'Wallet CSV' : 'Funds Format CSV'}
                                        </Badge>
                                    </TableCell>
                                    <TableCell>
                                        <Badge className={
                                            file.status === 'UPLOADED'
                                                ? 'bg-green-100 text-green-800 border-green-200 dark:bg-green-900 dark:text-green-200 dark:border-green-800'
                                                : 'bg-amber-100 text-amber-800 border-amber-200 dark:bg-amber-900 dark:text-amber-200 dark:border-amber-800'
                                        }>
                                            {file.status === 'UPLOADED' ? 'Uploaded' : 'Pending'}
                                        </Badge>
                                    </TableCell>
                                    <TableCell className="text-muted-foreground">
                                        {formatDateTime(file.createdAt)}
                                    </TableCell>
                                    <TableCell>
                                        <div className="flex justify-end gap-1">
                                            {file.status === 'UPLOADED' && (
                                                <Button
                                                    variant="ghost"
                                                    size="sm"
                                                    onClick={() => handleDownload(file)}
                                                >
                                                    <Download className="h-4 w-4" />
                                                </Button>
                                            )}
                                            <Button
                                                variant="ghost"
                                                size="sm"
                                                className="text-destructive hover:text-destructive"
                                                onClick={() => { setDeleteError(null); setFileToDelete(file); }}
                                            >
                                                <Trash2 className="h-4 w-4" />
                                            </Button>
                                        </div>
                                    </TableCell>
                                </TableRow>
                            ))}
                        </TableBody>
                    </Table>
                    <Pagination
                        offset={offset}
                        limit={limit}
                        total={total}
                        onPageChange={handlePageChange}
                        onPageSizeChange={handlePageSizeChange}
                    />
                </Card>
            )}

            <Dialog open={showUploadModal} onOpenChange={setShowUploadModal}>
                <DialogContent>
                    <DialogHeader>
                        <DialogTitle>Upload Import File</DialogTitle>
                    </DialogHeader>
                    <form onSubmit={handleUpload}>
                        <div className="space-y-4 py-4">
                            <div className="space-y-2">
                                <Label htmlFor="file">Files</Label>
                                <Input
                                    id="file"
                                    type="file"
                                    multiple
                                    ref={fileInputRef}
                                    onChange={handleFileSelect}
                                    disabled={uploading}
                                    accept=".csv"
                                />
                            </div>
                            <div className="space-y-2">
                                <Label htmlFor="fileType">Type</Label>
                                <Select
                                    value={fileType}
                                    onValueChange={(value) => setFileType(value as ImportFileType)}
                                    disabled={uploading}
                                >
                                    <SelectTrigger>
                                        <SelectValue />
                                    </SelectTrigger>
                                    <SelectContent>
                                        <SelectItem value="WALLET_CSV">Wallet CSV</SelectItem>
                                        <SelectItem value="FUNDS_FORMAT_CSV">Funds Format CSV</SelectItem>
                                    </SelectContent>
                                </Select>
                            </div>
                            {uploadError && (
                                <div className="p-3 text-sm text-destructive bg-destructive/10 rounded-md">
                                    {uploadError}
                                </div>
                            )}
                        </div>
                        <DialogFooter>
                            <Button
                                type="button"
                                variant="outline"
                                onClick={() => setShowUploadModal(false)}
                                disabled={uploading}
                            >
                                Cancel
                            </Button>
                            <Button type="submit" disabled={uploading || selectedFiles.length === 0}>
                                {uploading ? (
                                    <>
                                        <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                                        Uploading...
                                    </>
                                ) : (
                                    'Upload'
                                )}
                            </Button>
                        </DialogFooter>
                    </form>
                </DialogContent>
            </Dialog>

            <Dialog open={!!fileToDelete} onOpenChange={(open) => !open && !deleting && setFileToDelete(null)}>
                <DialogContent>
                    <DialogHeader>
                        <DialogTitle>Delete Import File</DialogTitle>
                        <DialogDescription>
                            Are you sure you want to delete "<strong>{fileToDelete?.fileName}</strong>"?
                        </DialogDescription>
                    </DialogHeader>
                    {deleteError && (
                        <div className="p-3 text-sm text-destructive bg-destructive/10 rounded-md">
                            {deleteError}
                        </div>
                    )}
                    <DialogFooter>
                        <Button
                            type="button"
                            variant="outline"
                            onClick={() => setFileToDelete(null)}
                            disabled={deleting}
                        >
                            Cancel
                        </Button>
                        <Button
                            variant="destructive"
                            onClick={handleDelete}
                            disabled={deleting}
                        >
                            {deleting ? (
                                <>
                                    <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                                    Deleting...
                                </>
                            ) : (
                                'Delete'
                            )}
                        </Button>
                    </DialogFooter>
                </DialogContent>
            </Dialog>
        </div>
    );
}

export default ImportsPage;
