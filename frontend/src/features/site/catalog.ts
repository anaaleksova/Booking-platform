import type { HeroLayout, Section, SectionType } from './types';

export interface LayoutInfo {
  label: string;
  description: string;
}

export interface SectionTypeInfo<S extends Section = Section> {
  label: string;
  description: string;
  layouts: Record<S['layout'], LayoutInfo>;
  /** What a section of this type starts with when the owner adds one. */
  create: (id: string) => S;
}

type Catalog = { [T in SectionType]: SectionTypeInfo<Extract<Section, { type: T }>> };

const base = { visibility: { desktop: true, mobile: true }, style: { tone: 'DEFAULT' as const } };

/**
 * Every section type the builder offers, with its layouts, in the order the
 * "Add section" menu lists them. Must match the backend's `Section` types.
 */
export const SECTION_CATALOG: Catalog = {
  HERO: {
    label: 'Hero',
    description: 'The big opening banner with your name and a booking button.',
    layouts: {
      FULL_WIDTH_IMAGE: { label: 'Full-width photo', description: 'Your name over a photo that fills the screen.' },
      SLIDESHOW: { label: 'Slideshow', description: 'Several photos fading into each other, slowly zooming.' },
      VIDEO: { label: 'Background video', description: 'A muted YouTube or Vimeo video playing behind your name.' },
      SPLIT: { label: 'Photo beside text', description: 'Text on one side, a photo on the other.' },
      CENTERED: { label: 'Just text', description: 'Large centred words, no photo.' },
    },
    create: (id) => ({ ...base, id, type: 'HERO', layout: 'CENTERED', content: { images: [], buttons: null } }),
  },
  EVENTS: {
    label: 'Events',
    description: "What's on: your upcoming events, kept up to date.",
    layouts: {
      CARDS: { label: 'Cards', description: 'A card with a picture for each event.' },
      LIST: { label: 'Programme', description: 'A compact list: date, title, time.' },
    },
    create: (id) => ({ ...base, id, type: 'EVENTS', layout: 'CARDS', content: {} }),
  },
  SERVICES: {
    label: 'Services',
    description: 'Your services with prices, each one a link to book it.',
    layouts: {
      GROUPED_LIST: { label: 'Price list by category', description: 'The classic menu, grouped by category.' },
      GRID: { label: 'Grid of cards', description: 'A card with a photo for each service.' },
      CAROUSEL: { label: 'Carousel', description: 'The cards in a row that scrolls sideways.' },
      LIST: { label: 'Simple list', description: 'One row per service.' },
      ALTERNATING: { label: 'Photo and text', description: 'Large photos alternating left and right.' },
    },
    create: (id) => ({ ...base, id, type: 'SERVICES', layout: 'GRID', content: { source: 'ALL', serviceIds: [], showBookButton: true } }),
  },
  PRICING: {
    label: 'Pricing',
    description: 'Prices side by side; highlight your most popular.',
    layouts: {
      CARDS: { label: 'Price cards', description: 'A card per service, one highlighted.' },
      TABLE: { label: 'Table', description: 'Service, duration and price in a table.' },
    },
    create: (id) => ({ ...base, id, type: 'PRICING', layout: 'CARDS', content: { source: 'ALL', serviceIds: [], highlightServiceId: null } }),
  },
  ABOUT: {
    label: 'About us',
    description: 'Who you are, with a photo or a few numbers.',
    layouts: {
      CENTERED_TEXT: { label: 'Centred text', description: 'Your introduction in the middle of the page.' },
      TEXT_IMAGE: { label: 'Text and photo', description: 'Your introduction beside a photo.' },
      TEXT_STATS: { label: 'Text and numbers', description: 'Your introduction with highlights like "15 years".' },
    },
    create: (id) => ({ ...base, id, type: 'ABOUT', layout: 'TEXT_IMAGE', content: { stats: [] } }),
  },
  GALLERY: {
    label: 'Gallery',
    description: 'Photos of your place, from your gallery or picked for this section.',
    layouts: {
      GRID: { label: 'Row', description: 'Up to three side by side; more become a carousel.' },
      MASONRY: { label: 'Mosaic', description: 'Photos of any shape in columns.' },
      CAROUSEL: { label: 'Large carousel', description: 'Big photos you swipe through.' },
    },
    create: (id) => ({ ...base, id, type: 'GALLERY', layout: 'MASONRY', content: { source: 'GALLERY', images: [] } }),
  },
  TESTIMONIALS: {
    label: 'Reviews',
    description: 'What your customers say about you.',
    layouts: {
      CARDS: { label: 'Cards', description: 'Three reviews side by side.' },
      SLIDER: { label: 'Slider', description: 'One review at a time, large.' },
      FEATURED: { label: 'Featured review', description: 'Your best review, big, in your colour.' },
    },
    create: (id) => ({ ...base, id, type: 'TESTIMONIALS', layout: 'CARDS', content: { items: [] } }),
  },
  TEAM: {
    label: 'Team',
    description: 'The people customers will meet, with photo and role.',
    layouts: {
      CARDS: { label: 'Portraits', description: 'A portrait card per person.' },
      LIST: { label: 'List', description: 'One row per person with a short bio.' },
    },
    create: (id) => ({ ...base, id, type: 'TEAM', layout: 'CARDS', content: { members: [] } }),
  },
  CONTACT: {
    label: 'Contact',
    description: 'Address, phone, opening hours and a map.',
    layouts: {
      MAP_AND_INFO: { label: 'Map and details', description: 'A map beside your address and hours.' },
      INFO_ONLY: { label: 'Details only', description: 'Your address and hours, no map.' },
      WITH_HOURS: { label: 'Hours first', description: 'Your opening hours large, contact beside them.' },
    },
    create: (id) => ({ ...base, id, type: 'CONTACT', layout: 'MAP_AND_INFO', content: { showHours: true } }),
  },
};

export const SECTION_TYPES = Object.keys(SECTION_CATALOG) as SectionType[];

/** The hero layouts drawn as white text over a dark image: the header can run over them transparently. */
export const DARK_HERO_LAYOUTS: ReadonlyArray<HeroLayout> = ['FULL_WIDTH_IMAGE', 'SLIDESHOW', 'VIDEO'];

export function sectionLabel(section: Section): string {
  return SECTION_CATALOG[section.type]?.label ?? section.type;
}

export function layoutLabel(section: Section): string {
  const layouts = SECTION_CATALOG[section.type]?.layouts as Record<string, LayoutInfo> | undefined;
  return layouts?.[section.layout]?.label ?? section.layout;
}

/** Shown on neither screen size: kept with its settings, but not on the page. */
export function isHidden(section: Section): boolean {
  return !section.visibility.desktop && !section.visibility.mobile;
}
