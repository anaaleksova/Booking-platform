/**
 * A sign-up waiting for its emailed code, remembered for this browser tab so
 * a reload (often while fetching the email, especially on a phone) returns to
 * the code step instead of an empty form. The server keeps the details; the
 * password is never stored here.
 */
export interface PendingSignup {
  email: string;
  /** Business sign-ups only. */
  businessName?: string;
  /** The email already had an account the business is being added to. */
  existingAccount?: boolean;
}

export type SignupKind = 'owner' | 'customer';

/** The server forgets an unconfirmed sign-up after 15 minutes; so does the page. */
const LIFETIME_MS = 15 * 60 * 1000;

const key = (kind: SignupKind) => `pendingSignup:${kind}`;

export function loadPendingSignup(kind: SignupKind): PendingSignup | null {
  try {
    const raw = sessionStorage.getItem(key(kind));
    if (!raw) {
      return null;
    }
    const { savedAt, ...pending } = JSON.parse(raw) as PendingSignup & { savedAt: number };
    return Date.now() - savedAt < LIFETIME_MS && pending.email ? pending : null;
  } catch {
    return null;
  }
}

export function savePendingSignup(kind: SignupKind, pending: PendingSignup): void {
  try {
    sessionStorage.setItem(key(kind), JSON.stringify({ ...pending, savedAt: Date.now() }));
  } catch {
    // Storage unavailable (private mode): the code step just won't survive a reload.
  }
}

export function clearPendingSignup(kind: SignupKind): void {
  try {
    sessionStorage.removeItem(key(kind));
  } catch {
    // Nothing to clear.
  }
}
