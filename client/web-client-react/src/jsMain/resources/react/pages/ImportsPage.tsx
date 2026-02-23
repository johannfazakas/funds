import { useEffect, useState, useCallback, useRef } from 'react';
import {
    ImportFile,
    ImportFileType,
    listImportFiles,
    createImportFile,
    confirmUpload,
    getDownloadUrl,
} from '../api/importFileApi';
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
    DialogTitle,
} from '../components/ui/dialog';
import {
    Select,
    SelectContent,
    SelectItem,
    SelectTrigger,
    SelectValue,
} from '../components/ui/select';
import { Loader2, Download } from 'lucide-react';

interface ImportsPageProps {
    userId: string;
}

function ImportsPage({ userId }: ImportsPageProps) {
    const [importFiles, setImportFiles] = useState<ImportFile[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    const [showUploadModal, setShowUploadModal] = useState(false);
    const [fileType, setFileType] = useState<ImportFileType>('WALLET_CSV');
    const [selectedFiles, setSelectedFiles] = useState<File[]>([]);
    const [uploading, setUploading] = useState(false);
    const [uploadError, setUploadError] = useState<string | null>(null);
    const fileInputRef = useRef<HTMLInputElement>(null);

    const loadImportFiles = useCallback(async () => {
        setLoading(true);
        setError(null);
        try {
            const files = await listImportFiles(userId);
            setImportFiles(files);
        } catch (err) {
            setError(err instanceof Error ? err.message : 'Failed to load import files');
        } finally {
            setLoading(false);
        }
    }, [userId]);

    useEffect(() => {
        loadImportFiles();
    }, [loadImportFiles]);

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

    const handleDownload = async (importFile: ImportFile) => {
        try {
            const url = await getDownloadUrl(userId, importFile.importFileId);
            window.open(url, '_blank');
        } catch (err) {
            setError(err instanceof Error ? err.message : 'Failed to get download URL');
        }
    };

    return (
        <div>
            <div className="flex justify-between items-center mb-6">
                <h1 className="text-2xl font-bold">Imports</h1>
                <Button onClick={openUploadModal}>Upload Files</Button>
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
                    No import files yet — upload one to get started.
                </div>
            )}

            {!loading && !error && importFiles.length > 0 && (
                <Card>
                    <Table>
                        <TableHeader>
                            <TableRow>
                                <TableHead>File Name</TableHead>
                                <TableHead>Type</TableHead>
                                <TableHead>Status</TableHead>
                                <TableHead className="w-24"></TableHead>
                            </TableRow>
                        </TableHeader>
                        <TableBody>
                            {importFiles.map((file) => (
                                <TableRow key={file.importFileId}>
                                    <TableCell>{file.fileName}</TableCell>
                                    <TableCell>{file.type}</TableCell>
                                    <TableCell>{file.status}</TableCell>
                                    <TableCell>
                                        {file.status === 'UPLOADED' && (
                                            <Button
                                                variant="ghost"
                                                size="sm"
                                                onClick={() => handleDownload(file)}
                                            >
                                                <Download className="h-4 w-4" />
                                            </Button>
                                        )}
                                    </TableCell>
                                </TableRow>
                            ))}
                        </TableBody>
                    </Table>
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
        </div>
    );
}

export default ImportsPage;
