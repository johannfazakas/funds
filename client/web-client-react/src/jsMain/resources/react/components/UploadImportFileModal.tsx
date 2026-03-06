import { useEffect, useRef, useState } from 'react';
import { ImportFileType, createImportFile, confirmUpload, importFile } from '../api/importFileApi';
import { ImportConfiguration, listImportConfigurations } from '../api/importConfigurationApi';
import { Button } from './ui/button';
import { Input } from './ui/input';
import { Label } from './ui/label';
import {
    Dialog,
    DialogContent,
    DialogFooter,
    DialogHeader,
    DialogTitle,
} from './ui/dialog';
import {
    Select,
    SelectContent,
    SelectItem,
    SelectTrigger,
    SelectValue,
} from './ui/select';
import { Loader2 } from 'lucide-react';

interface UploadImportFileModalProps {
    userId: string;
    open: boolean;
    onOpenChange: (open: boolean) => void;
    onUploaded: (importingFileIds?: string[]) => void;
}

export function UploadImportFileModal({ userId, open, onOpenChange, onUploaded }: UploadImportFileModalProps) {
    const [fileType, setFileType] = useState<ImportFileType>('WALLET_CSV');
    const [selectedFiles, setSelectedFiles] = useState<File[]>([]);
    const [fileNames, setFileNames] = useState<string[]>([]);
    const [uploading, setUploading] = useState(false);
    const [uploadError, setUploadError] = useState<string | null>(null);
    const fileInputRef = useRef<HTMLInputElement>(null);

    const [configurations, setConfigurations] = useState<ImportConfiguration[]>([]);
    const [selectedConfigurationId, setSelectedConfigurationId] = useState<string>('');

    useEffect(() => {
        if (!open) return;
        setFileType('WALLET_CSV');
        setSelectedFiles([]);
        setFileNames([]);
        setUploadError(null);
        setSelectedConfigurationId('');
        if (fileInputRef.current) {
            fileInputRef.current.value = '';
        }
        listImportConfigurations(userId)
            .then((result) => setConfigurations(result.items))
            .catch(() => setConfigurations([]));
    }, [open, userId]);

    const handleFileSelect = (e: React.ChangeEvent<HTMLInputElement>) => {
        if (e.target.files) {
            const files = Array.from(e.target.files);
            setSelectedFiles(files);
            setFileNames(files.map(f => f.name));
        }
    };

    const handleUpload = async (): Promise<string[]> => {
        if (selectedFiles.length === 0) {
            setUploadError('Please select at least one file');
            return [];
        }
        if (!selectedConfigurationId) {
            setUploadError('Please select an import configuration');
            return [];
        }

        setUploading(true);
        setUploadError(null);

        const uploadedFileIds: string[] = [];
        for (let i = 0; i < selectedFiles.length; i++) {
            const file = selectedFiles[i];
            const fileName = fileNames[i];
            const createResponse = await createImportFile(userId, fileName, fileType, selectedConfigurationId);
            await fetch(createResponse.uploadUrl, {
                method: 'PUT',
                body: file,
            });
            await confirmUpload(userId, createResponse.importFileId);
            uploadedFileIds.push(createResponse.importFileId);
        }
        return uploadedFileIds;
    };

    const handleUploadOnly = async () => {
        try {
            await handleUpload();
            onOpenChange(false);
            onUploaded();
        } catch (err) {
            setUploadError(err instanceof Error ? err.message : 'Failed to upload files');
        } finally {
            setUploading(false);
        }
    };

    const handleUploadAndImport = async () => {
        try {
            const uploadedFileIds = await handleUpload();
            for (const fileId of uploadedFileIds) {
                importFile(userId, fileId);
            }
            onOpenChange(false);
            onUploaded(uploadedFileIds);
        } catch (err) {
            setUploadError(err instanceof Error ? err.message : 'Failed to upload files');
        } finally {
            setUploading(false);
        }
    };

    return (
        <Dialog open={open} onOpenChange={onOpenChange}>
            <DialogContent>
                <DialogHeader>
                    <DialogTitle>Upload Import File</DialogTitle>
                </DialogHeader>
                <form onSubmit={(e) => e.preventDefault()}>
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
                        {selectedFiles.length > 0 && (
                            <div className="space-y-2">
                                <Label>File Names</Label>
                                {selectedFiles.map((file, index) => (
                                    <div key={index} className="flex items-center gap-2">
                                        <span className="text-sm text-muted-foreground truncate min-w-0 flex-shrink-0 max-w-[40%]">
                                            {file.name}
                                        </span>
                                        <Input
                                            value={fileNames[index] || ''}
                                            onChange={(e) => {
                                                const updated = [...fileNames];
                                                updated[index] = e.target.value;
                                                setFileNames(updated);
                                            }}
                                            disabled={uploading}
                                            className="flex-1"
                                        />
                                    </div>
                                ))}
                            </div>
                        )}
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
                        <div className="space-y-2">
                            <Label htmlFor="configuration">Configuration</Label>
                            <Select
                                value={selectedConfigurationId}
                                onValueChange={setSelectedConfigurationId}
                                disabled={uploading}
                            >
                                <SelectTrigger>
                                    <SelectValue placeholder="Select configuration" />
                                </SelectTrigger>
                                <SelectContent>
                                    {configurations.map((config) => (
                                        <SelectItem key={config.importConfigurationId} value={config.importConfigurationId}>
                                            {config.name}
                                        </SelectItem>
                                    ))}
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
                            onClick={() => onOpenChange(false)}
                            disabled={uploading}
                        >
                            Cancel
                        </Button>
                        <Button
                            type="button"
                            variant="secondary"
                            disabled={uploading || selectedFiles.length === 0 || !selectedConfigurationId}
                            onClick={handleUploadOnly}
                        >
                            {uploading ? (
                                <>
                                    <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                                    Uploading...
                                </>
                            ) : (
                                'Upload'
                            )}
                        </Button>
                        <Button
                            type="button"
                            disabled={uploading || selectedFiles.length === 0 || !selectedConfigurationId}
                            onClick={handleUploadAndImport}
                        >
                            {uploading ? (
                                <>
                                    <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                                    Importing...
                                </>
                            ) : (
                                'Upload and Import'
                            )}
                        </Button>
                    </DialogFooter>
                </form>
            </DialogContent>
        </Dialog>
    );
}
