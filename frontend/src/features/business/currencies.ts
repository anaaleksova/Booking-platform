/** Currencies a business can price in. */
export const CURRENCIES = ['EUR', 'USD', 'GBP', 'CHF', 'MKD', 'RSD', 'BGN', 'TRY', 'SEK', 'NOK', 'DKK', 'PLN', 'CZK', 'HUF', 'RON'];

/** Where the timezone says a business most likely is, its currency; euros otherwise. */
const BY_TIMEZONE: Record<string, string> = {
  'Europe/Skopje': 'MKD',
  'Europe/Belgrade': 'RSD',
  'Europe/London': 'GBP',
  'Europe/Istanbul': 'TRY',
  'Europe/Sofia': 'BGN',
  'Europe/Zurich': 'CHF',
  'Europe/Warsaw': 'PLN',
  'Europe/Prague': 'CZK',
  'Europe/Budapest': 'HUF',
  'Europe/Bucharest': 'RON',
  'Europe/Stockholm': 'SEK',
  'Europe/Oslo': 'NOK',
  'Europe/Copenhagen': 'DKK',
};

export function currencyForTimezone(timezone: string): string {
  if (timezone.startsWith('America/')) {
    return 'USD';
  }
  return BY_TIMEZONE[timezone] ?? 'EUR';
}
