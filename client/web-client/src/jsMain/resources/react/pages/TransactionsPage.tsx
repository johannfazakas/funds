import '../styles/ContentPage.css';

interface TransactionsPageProps {
    userId: string;
}

function TransactionsPage({ userId }: TransactionsPageProps) {
    return (
        <div className="content-page">
            <h1>Transactions</h1>
            <p className="placeholder-text">Transactions page coming soon.</p>
        </div>
    );
}

export default TransactionsPage;
