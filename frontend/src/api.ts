/** Fetches a freshly generated joke. Throws on any failure or unusable response. */
export async function fetchJoke(): Promise<string> {
  const response = await fetch('/api/joke')
  if (!response.ok) {
    throw new Error(`Joke request failed with status ${response.status}`)
  }
  const body: unknown = await response.json()
  const joke = (body as { joke?: unknown } | null)?.joke
  if (typeof joke !== 'string' || joke.trim() === '') {
    throw new Error('Joke response had no joke')
  }
  return joke
}
