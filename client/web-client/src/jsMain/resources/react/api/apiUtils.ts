export interface ApiError {
    title: string;
    detail?: string;
}

export async function handleApiError(
    response: Response,
    fallbackMessage: string
): Promise<never> {
    let message = fallbackMessage;
    try {
        const body = await response.json();
        if (body && typeof body.title === 'string') {
            message = `${fallbackMessage}: ${body.title}`;
        }
    } catch {
        // JSON parsing failed, use fallback
    }
    throw new Error(message);
}
