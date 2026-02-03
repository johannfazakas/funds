import '../styles/ContentPage.css';

interface AccountsPageProps {
    userId: string;
}

function AccountsPage({ userId }: AccountsPageProps) {
    return (
        <div className="content-page">
            <h1>Accounts</h1>
            <p className="placeholder-text">Accounts page coming soon.</p>
        </div>
    );
}

export default AccountsPage;
