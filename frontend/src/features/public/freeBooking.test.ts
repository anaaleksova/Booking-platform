import { describe, expect, it } from 'vitest';
import type { PublicService } from '@/types/api';
import { bookLabel, onlyFree } from '@/features/public/freeBooking';

function service(price: number): PublicService {
  return {
    id: price,
    name: 'Table',
    durationMinutes: 120,
    price,
    pricePerParticipant: false,
    maxParticipants: 8,
    currency: 'EUR',
    choosableRequirements: [],
  };
}

describe('free bookings', () => {
  it('a business offering only free bookings speaks of reservations', () => {
    expect(onlyFree([service(0)])).toBe(true);
    expect(bookLabel([service(0)])).toBe('Make a reservation');
  });

  it('one priced service keeps the price list and appointments', () => {
    expect(onlyFree([service(0), service(35)])).toBe(false);
    expect(bookLabel([service(0), service(35)])).toBe('Book an appointment');
  });

  it('no services yet is not "only free"', () => {
    expect(onlyFree([])).toBe(false);
    expect(onlyFree(undefined)).toBe(false);
  });
});
