import { afterEach, beforeAll, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import type { PublicBusiness } from '@/types/api';
import { LIGHT_COLORS, completeTheme } from '../theme';
import type { Section, SiteDocument } from '../types';
import { SitePage, headerOverHero } from './SitePage';
import type { SiteData } from './SiteData';

// The contact module fails to draw, to show one broken section does not take the page down.
vi.mock('../sections/contact', () => ({
  ContactMapInfo: () => {
    throw new Error('map exploded');
  },
}));

const business: PublicBusiness = {
  name: 'Harbor Bistro',
  slug: 'harbor-bistro',
  tagline: 'Fish, fresh off the boat',
  description: 'A small place by the water.',
  accentColor: '#2f5bea',
  showMap: true,
  darkTheme: false,
  timezone: 'Europe/Skopje',
  currency: 'EUR',
  openingHours: [],
  specialHours: [],
  gallery: [],
};

const data: SiteData = {
  business,
  services: [],
  servicesLoading: false,
  servicesError: null,
  events: [],
  team: [],
  gallery: [],
};

const shown = { desktop: true, mobile: true };
const tone = { tone: 'DEFAULT' as const };

const hero: Section = { type: 'HERO', id: 'hero', layout: 'CENTERED', visibility: shown, style: tone, content: {} };
const about: Section = { type: 'ABOUT', id: 'about', layout: 'CENTERED_TEXT', visibility: shown, style: tone, content: {} };
const contact: Section = { type: 'CONTACT', id: 'contact', layout: 'MAP_AND_INFO', visibility: shown, style: tone, content: { showHours: true } };

function page(sections: unknown[]): SiteDocument {
  return {
    schemaVersion: 1,
    theme: completeTheme({ colors: LIGHT_COLORS }),
    header: { layout: 'LOGO_LEFT_NAV_RIGHT', sticky: true, transparentOverHero: true },
    footer: { layout: 'CENTERED' },
    sections: sections as Section[],
  };
}

function renderPage(document: SiteDocument) {
  return render(
    <MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
      <SitePage site={document} data={data} />
    </MemoryRouter>,
  );
}

const drawnTypes = (container: HTMLElement) =>
  [...container.querySelectorAll('section[data-section-type]')].map((node) => node.getAttribute('data-section-type'));

describe('SitePage', () => {
  // Sections load lazily; compiling their modules the first time can outlast a findBy under a busy test run.
  beforeAll(async () => {
    await Promise.all([import('../sections/hero'), import('../sections/about'), import('../sections/contact')]);
  }, 30_000);
  afterEach(() => vi.restoreAllMocks());

  it('draws the sections in the order of the document, each with its layout', async () => {
    const { container } = renderPage(page([about, hero]));

    expect(await screen.findByRole('heading', { level: 1, name: 'Harbor Bistro' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { level: 2, name: 'Welcome to Harbor Bistro' })).toBeInTheDocument();
    expect(drawnTypes(container)).toEqual(['ABOUT', 'HERO']);
  });

  it('fills texts left empty from the business, and shows texts the owner wrote instead', async () => {
    renderPage(page([hero, { ...about, content: { title: 'Our story', text: 'Since 1998.' } }]));

    expect(await screen.findByText('Fish, fresh off the boat')).toBeInTheDocument();
    expect(await screen.findByRole('heading', { name: 'Our story' })).toBeInTheDocument();
    expect(screen.getByText('Since 1998.')).toBeInTheDocument();
    expect(screen.queryByText('A small place by the water.')).toBeNull();
  });

  it('skips a section type or layout it does not know, with a warning, and draws the rest', async () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {});
    const { container } = renderPage(
      page([
        { ...hero, type: 'SPACESHIP' },
        { ...about, layout: 'UPSIDE_DOWN' },
        { ...about, id: 'about-2', layout: 'toString' },
        hero,
      ]),
    );

    expect(await screen.findByRole('heading', { level: 1, name: 'Harbor Bistro' })).toBeInTheDocument();
    expect(drawnTypes(container)).toEqual(['HERO']);
    expect(warn).toHaveBeenCalledTimes(3);
    expect(warn.mock.calls[0][0]).toContain('SPACESHIP');
  });

  it('leaves out a hidden section, and keeps one shown on a single screen size', async () => {
    const { container } = renderPage(
      page([
        { ...about, visibility: { desktop: false, mobile: false } },
        { ...hero, visibility: { desktop: true, mobile: false } },
      ]),
    );

    expect(await screen.findByRole('heading', { level: 1, name: 'Harbor Bistro' })).toBeInTheDocument();
    expect(drawnTypes(container)).toEqual(['HERO']);
  });

  it('keeps the page standing when one section fails to draw', async () => {
    const error = vi.spyOn(console, 'error').mockImplementation(() => {});
    renderPage(page([hero, contact, about]));

    expect(await screen.findByRole('heading', { level: 1, name: 'Harbor Bistro' })).toBeInTheDocument();
    expect(await screen.findByRole('heading', { name: 'Welcome to Harbor Bistro' })).toBeInTheDocument();
    expect(error.mock.calls.some((call) => String(call[0]).includes('contact'))).toBe(true);
  });

  it('gives each section an anchor and names it by its heading', async () => {
    const { container } = renderPage(page([about]));

    await screen.findByRole('heading', { name: 'Welcome to Harbor Bistro' });
    const section = container.querySelector('#section-about');
    expect(section).toHaveAttribute('aria-labelledby', 'section-about-title');
    expect(screen.getByRole('region', { name: 'Welcome to Harbor Bistro' })).toBe(section);
  });
});

describe('headerOverHero', () => {
  it('runs the header over a dark photo hero at the top', () => {
    expect(headerOverHero(page([{ ...hero, layout: 'FULL_WIDTH_IMAGE' }, about]))).toBe(true);
  });

  it('keeps it solid over a light hero, a page that starts otherwise, or when the owner turned it off', () => {
    expect(headerOverHero(page([hero]))).toBe(false);
    expect(headerOverHero(page([about, { ...hero, layout: 'FULL_WIDTH_IMAGE' }]))).toBe(false);
    const off = page([{ ...hero, layout: 'FULL_WIDTH_IMAGE' }]);
    expect(headerOverHero({ ...off, header: { ...off.header, transparentOverHero: false } })).toBe(false);
  });

  it('looks past hidden sections to the first one shown', () => {
    expect(headerOverHero(page([{ ...about, visibility: { desktop: false, mobile: false } }, { ...hero, layout: 'SLIDESHOW' }]))).toBe(true);
  });
});
