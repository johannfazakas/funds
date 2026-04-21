import { useEffect, useState, useCallback } from 'react';
import { Category, listCategories, createCategory, deleteCategory } from '../api/categoryApi';
import { Button } from '../components/ui/button';
import { ActionButton } from '../components/ui/action-button';
import { Input } from '../components/ui/input';
import { Card } from '../components/ui/card';
import {
    Table,
    TableBody,
    TableCell,
    TableHead,
    TableHeader,
    TableRow,
} from '../components/ui/table';
import { Loader2, Trash2 } from 'lucide-react';

interface CategoriesPageProps {
    userId: string;
}

function CategoriesPage({ userId }: CategoriesPageProps) {
    const [categories, setCategories] = useState<Category[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [newCategoryName, setNewCategoryName] = useState('');
    const [creating, setCreating] = useState(false);
    const [deleteError, setDeleteError] = useState<string | null>(null);

    const loadCategories = useCallback(async () => {
        setLoading(true);
        setError(null);
        try {
            const result = await listCategories(userId);
            setCategories(result);
        } catch (err) {
            setError(err instanceof Error ? err.message : 'Failed to load categories');
        } finally {
            setLoading(false);
        }
    }, [userId]);

    useEffect(() => {
        loadCategories();
    }, [loadCategories]);

    const handleCreate = async () => {
        if (!newCategoryName.trim()) return;
        setCreating(true);
        setError(null);
        try {
            await createCategory(userId, newCategoryName.trim());
            setNewCategoryName('');
            await loadCategories();
        } catch (err) {
            setError(err instanceof Error ? err.message : 'Failed to create category');
        } finally {
            setCreating(false);
        }
    };

    const handleDelete = async (category: Category) => {
        setDeleteError(null);
        try {
            await deleteCategory(userId, category.id);
            await loadCategories();
        } catch (err) {
            setDeleteError(err instanceof Error ? err.message : 'Failed to delete category');
        }
    };

    return (
        <div>
            <div className="flex justify-between items-center mb-6">
                <h1 className="text-2xl font-bold">Categories</h1>
            </div>

            <div className="mb-4 flex items-center gap-2">
                <Input
                    value={newCategoryName}
                    onChange={(e) => setNewCategoryName(e.target.value)}
                    onKeyDown={(e) => e.key === 'Enter' && handleCreate()}
                    placeholder="New category name"
                    className="w-48"
                />
                <Button onClick={handleCreate} disabled={creating || !newCategoryName.trim()}>
                    {creating ? <Loader2 className="h-4 w-4 animate-spin" /> : 'Add'}
                </Button>
            </div>

            {error && (
                <div className="flex items-center gap-4 p-4 mb-4 text-destructive bg-destructive/10 rounded-md">
                    <span>{error}</span>
                    <Button variant="outline" size="sm" onClick={loadCategories}>Retry</Button>
                </div>
            )}

            {deleteError && (
                <div className="flex items-center gap-4 p-4 mb-4 text-destructive bg-destructive/10 rounded-md">
                    <span>{deleteError}</span>
                    <Button variant="outline" size="sm" onClick={() => setDeleteError(null)}>Dismiss</Button>
                </div>
            )}

            {loading && (
                <div className="flex justify-center p-8">
                    <Loader2 className="h-8 w-8 animate-spin text-muted-foreground" />
                </div>
            )}

            {!loading && !error && categories.length === 0 && (
                <div className="text-center text-muted-foreground py-8">
                    No categories yet.
                </div>
            )}

            {!loading && !error && categories.length > 0 && (
                <Card>
                    <Table>
                        <TableHeader>
                            <TableRow>
                                <TableHead>Name</TableHead>
                                <TableHead className="w-16" />
                            </TableRow>
                        </TableHeader>
                        <TableBody>
                            {categories.map((category) => (
                                <TableRow key={category.id}>
                                    <TableCell>{category.name}</TableCell>
                                    <TableCell>
                                        <ActionButton icon={Trash2} tooltip="Delete" destructive
                                            onClick={() => handleDelete(category)} />
                                    </TableCell>
                                </TableRow>
                            ))}
                        </TableBody>
                    </Table>
                </Card>
            )}
        </div>
    );
}

export default CategoriesPage;
