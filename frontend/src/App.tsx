import { useState } from 'react'
import { fetchJoke } from './api'
import './App.css'

const ERROR_TEXT = "Couldn't come up with a joke right now. Please try again."

function App() {
  const [joke, setJoke] = useState<string | null>(null)
  const [error, setError] = useState(false)
  const [loading, setLoading] = useState(false)

  async function handleClick() {
    setLoading(true)
    setJoke(null)
    setError(false)
    try {
      setJoke(await fetchJoke())
    } catch {
      setError(true)
    } finally {
      setLoading(false)
    }
  }

  return (
    <main className="card">
      <h1>Jokester</h1>
      <button type="button" onClick={handleClick} disabled={loading}>
        {loading ? 'Thinking…' : 'Tell me a joke'}
      </button>
      {joke && <blockquote className="joke">{joke}</blockquote>}
      {error && (
        <p className="error" role="alert">
          {ERROR_TEXT}
        </p>
      )}
    </main>
  )
}

export default App
