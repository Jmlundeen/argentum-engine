/**
 * Standalone account/auth store (separate from the game store — accounts are orthogonal to a live
 * game). Holds the signed-in user and drives the magic-link login flow. The auth token itself lives
 * in localStorage (see api/account); this store mirrors the derived user for the UI.
 */
import { create } from 'zustand'
import {
  type AccountUser,
  type LoginResponse,
  clearAuthToken,
  fetchMe,
  setAuthToken,
} from '@/api/account'

export type AuthStatus = 'idle' | 'loading' | 'authenticated' | 'anonymous'

interface AuthState {
  user: AccountUser | null
  status: AuthStatus
  /** Resolve the current session from a stored token (call once on app start). */
  init: () => Promise<void>
  /** Apply a fresh login (stores the token and the user). */
  setSession: (login: LoginResponse) => void
  /** Sign out: drop the token and the user. */
  logout: () => void
}

export const useAuthStore = create<AuthState>((set) => ({
  user: null,
  status: 'idle',

  init: async () => {
    set({ status: 'loading' })
    const user = await fetchMe()
    set(user ? { user, status: 'authenticated' } : { user: null, status: 'anonymous' })
  },

  setSession: (login) => {
    setAuthToken(login.authToken)
    set({ user: login.user, status: 'authenticated' })
  },

  logout: () => {
    clearAuthToken()
    set({ user: null, status: 'anonymous' })
  },
}))
