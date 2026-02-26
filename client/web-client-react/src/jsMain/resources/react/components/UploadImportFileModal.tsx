import { useEffect, useRef, useState } from 'react';
import { ImportFileType, createImportFile, confirmUpload } from '../api/importFileApi';
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
    onUploaded: () => void;
}

export function UploadImportFileModal({ userId, open, onOpenChange, onUploaded }: UploadImportFileModalProps) {
    const [fileType, setFileType] = useState<ImportFileType>('WALLET_CSV');
    const [selectedFiles, setSelectedFiles] = useState<File[]>([]);
    const [uploading, setUploading] = useState(false);
    const [uploadError, setUploadError] = useState<string | null>(null);
    const fileInputRef = useRef<HTMLInputElement>(null);

    const [configurations, setConfigurations] = useState<ImportConfiguration[]>([]);
    const [selectedConfigurationId, setSelectedConfigurationId] = useState<string>('');

    useEffect(() => {
        if (!open) return;
        setFileType('WALLET_CSV');
        setSelectedFiles([]);
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
            setSelectedFiles(Array.from(e.target.files));
        }
    };

    const handleUpload = async (e: React.FormEvent) => {
        e.preventDefault();
        if (selectedFiles.length === 0) {
            setUploadError('Please select at least one file');
            return;
        }
        if (!selectedConfigurationId) {
            setUploadError('Please select an import configuration');
            return;
        }

        setUploading(true);
        setUploadError(null);

        try {
            for (const file of selectedFiles) {
                const createResponse = await createImportFile(userId, file.name, fileType, selectedConfigurationId);
                await fetch(createResponse.uploadUrl, {
                    method: 'PUT',
                    body: file,
                });
                await confirmUpload(userId, createResponse.importFileId);
            }
            onOpenChange(false);
            onUploaded();
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
                        <Button type="submit" disabled={uploading || selectedFiles.length === 0 || !selectedConfigurationId}>
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
    );
}
