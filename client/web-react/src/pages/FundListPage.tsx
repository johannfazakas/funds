import { useEffect, useState } from 'react'
import './FundListPage.css'

interface Fund {
  id: string
  name: string
}

interface FundListPageProps {
  userId: string
  onLogout: () => void
}

function FundListPage({ userId, onLogout }: FundListPageProps) {
  const [funds, setFunds] = useState<Fund[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    loadFunds()
  }, [userId])

  const loadFunds = async () => {
    setLoading(true)
    setError(null)

    try {
      const response = await fetch('http://localhost:5253/funds-api/fund/v1/funds', {
        headers: {
          'FUNDS_USER_ID': userId
        }
      })

      if (response.ok) {
        const data = await response.json()
        setFunds(data.items || [])
      } else {
        setError('Failed to load funds')
      }
    } catch (err) {
      setError('Failed to load funds: ' + (err instanceof Error ? err.message : 'Unknown error'))
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="fund-list-container">
      <header className="header">
        <h1>My Funds</h1>
        <button onClick={onLogout} className="logout-button">
          Logout
        </button>
      </header>

      <main className="main-content">
        {loading && <div className="loading">Loading...</div>}

        {error && (
          <div className="error-container">
            <div className="error">{error}</div>
            <button onClick={loadFunds}>Retry</button>
          </div>
        )}

        {!loading && !error && funds.length === 0 && (
          <div className="empty-state">No funds available</div>
        )}

        {!loading && !error && funds.length > 0 && (
          <div className="fund-grid">
            {funds.map((fund) => (
              <div key={fund.id} className="fund-card">
                <h3>{fund.name}</h3>
                <p className="fund-id">ID: {fund.id}</p>
              </div>
            ))}
          </div>
        )}
      </main>
    </div>
  )
}

export default FundListPage
