import type { PublicService } from '@/types/api';

/**
 * A service with no price is a reservation, not a purchase: a table at a
 * restaurant or bar, where the bill comes at the venue. Customers see no price,
 * no total and no discount code for it.
 */
export function isFree(service: Pick<PublicService, 'price'>): boolean {
  return service.price === 0;
}

/** When nothing a business offers has a price, its pages speak of reservations rather than a price list. */
export function onlyFree(services: PublicService[] | undefined): boolean {
  return Boolean(services?.length) && services!.every(isFree);
}

/** The label of the main "book" button. */
export function bookLabel(services: PublicService[] | undefined): string {
  return onlyFree(services) ? 'Make a reservation' : 'Book an appointment';
}
