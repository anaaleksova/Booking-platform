import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { EventsSection } from '@/features/public/EventsSection';
import type { PublicEvent } from '@/types/api';

function event(overrides: Partial<PublicEvent> = {}): PublicEvent {
  return {
    id: 1,
    title: 'Friday live music',
    performer: 'The Quartet',
    description: 'Jazz standards all night.',
    date: '2030-05-10',
    startTime: '21:00:00',
    endTime: '23:30:00',
    serviceId: 7,
    booking: 'OPEN',
    ...overrides,
  };
}

function renderEvents(events: PublicEvent[]) {
  render(
    <MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
      <EventsSection slug="corner-bar" events={events} title={(_, heading) => <h2>{heading}</h2>} />
    </MemoryRouter>,
  );
}

describe('EventsSection', () => {
  it('shows the band, the hours and a way to reserve on the day of the event', () => {
    renderEvents([event()]);

    expect(screen.getByText('The Quartet')).toBeInTheDocument();
    expect(screen.getByText('21:00 – 23:30')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Reserve your spot' })).toHaveAttribute(
      'href',
      '/b/corner-bar/book?service=7&date=2030-05-10',
    );
  });

  it('a fully booked event says so and offers no booking', () => {
    renderEvents([event({ booking: 'FULL' })]);

    expect(screen.getAllByText(/fully booked/i).length).toBeGreaterThan(0);
    expect(screen.queryByRole('link', { name: 'Reserve your spot' })).not.toBeInTheDocument();
  });

  it('an announcement has nothing to book, and an open end reads "from"', () => {
    renderEvents([event({ booking: 'NONE', serviceId: null, endTime: null })]);

    expect(screen.getByText('from 21:00')).toBeInTheDocument();
    expect(screen.queryByRole('link')).not.toBeInTheDocument();
    expect(screen.queryByText(/fully booked/i)).not.toBeInTheDocument();
  });

  it('renders nothing without events', () => {
    renderEvents([]);
    expect(screen.queryByRole('heading')).not.toBeInTheDocument();
  });
});
