import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import App from './App'

const ERROR_TEXT = "Couldn't come up with a joke right now. Please try again."

const fetchMock = vi.fn<typeof fetch>()

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

beforeEach(() => {
  fetchMock.mockReset()
  vi.stubGlobal('fetch', fetchMock)
})

afterEach(() => {
  vi.unstubAllGlobals()
})

function jokeButton() {
  return screen.getByRole('button', { name: /tell me a joke|thinking/i })
}

test('shows a joke after clicking', async () => {
  fetchMock.mockResolvedValueOnce(jsonResponse(200, { joke: 'A joke.' }))
  render(<App />)

  await userEvent.click(screen.getByRole('button', { name: 'Tell me a joke' }))

  expect(await screen.findByText('A joke.')).toBeInTheDocument()
  expect(fetchMock).toHaveBeenCalledWith('/api/joke')
})

test('disables button while loading', async () => {
  fetchMock.mockReturnValueOnce(new Promise<Response>(() => {}))
  render(<App />)

  await userEvent.click(screen.getByRole('button', { name: 'Tell me a joke' }))

  const button = jokeButton()
  expect(button).toHaveTextContent('Thinking…')
  expect(button).toBeDisabled()
  await userEvent.click(button)
  expect(fetchMock).toHaveBeenCalledTimes(1)
})

test('shows error on 502 JSON', async () => {
  fetchMock.mockResolvedValueOnce(jsonResponse(502, { error: 'upstream' }))
  render(<App />)

  await userEvent.click(screen.getByRole('button', { name: 'Tell me a joke' }))

  expect(await screen.findByRole('alert')).toHaveTextContent(ERROR_TEXT)
})

test('shows error on non-JSON 500', async () => {
  fetchMock.mockResolvedValueOnce(new Response('<html>Proxy error</html>', { status: 500 }))
  render(<App />)

  await userEvent.click(screen.getByRole('button', { name: 'Tell me a joke' }))

  expect(await screen.findByRole('alert')).toHaveTextContent(ERROR_TEXT)
})

test('shows error on 200 with missing joke', async () => {
  fetchMock.mockResolvedValueOnce(jsonResponse(200, { joke: '' }))
  render(<App />)

  await userEvent.click(screen.getByRole('button', { name: 'Tell me a joke' }))

  expect(await screen.findByRole('alert')).toHaveTextContent(ERROR_TEXT)
})

test('shows error when fetch rejects', async () => {
  fetchMock.mockRejectedValueOnce(new TypeError('Failed to fetch'))
  render(<App />)

  await userEvent.click(screen.getByRole('button', { name: 'Tell me a joke' }))

  expect(await screen.findByRole('alert')).toHaveTextContent(ERROR_TEXT)
})

test('retry after error clears the error', async () => {
  fetchMock
    .mockResolvedValueOnce(jsonResponse(502, { error: 'upstream' }))
    .mockResolvedValueOnce(jsonResponse(200, { joke: 'A joke.' }))
  render(<App />)

  await userEvent.click(screen.getByRole('button', { name: 'Tell me a joke' }))
  await screen.findByRole('alert')
  await userEvent.click(screen.getByRole('button', { name: 'Tell me a joke' }))

  expect(await screen.findByText('A joke.')).toBeInTheDocument()
  expect(screen.queryByRole('alert')).not.toBeInTheDocument()
})

test('failure after a joke replaces it', async () => {
  fetchMock
    .mockResolvedValueOnce(jsonResponse(200, { joke: 'A joke.' }))
    .mockResolvedValueOnce(jsonResponse(502, { error: 'upstream' }))
  render(<App />)

  await userEvent.click(screen.getByRole('button', { name: 'Tell me a joke' }))
  await screen.findByText('A joke.')
  await userEvent.click(screen.getByRole('button', { name: 'Tell me a joke' }))

  expect(await screen.findByRole('alert')).toHaveTextContent(ERROR_TEXT)
  expect(screen.queryByText('A joke.')).not.toBeInTheDocument()
})
