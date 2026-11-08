import { lazy, type ComponentType, type LazyExoticComponent } from 'react';
import type { Section, SectionType } from '../types';
import type { SectionProps } from '../sections/shared';

type LayoutComponent<S extends Section> = LazyExoticComponent<ComponentType<SectionProps<S>>>;

type Registry = {
  [T in SectionType]: Record<Extract<Section, { type: T }>['layout'], LayoutComponent<Extract<Section, { type: T }>>>;
};

/**
 * A layout from a section type's module, loaded the first time a page uses
 * it: a visitor downloads only the section types their page has.
 */
function layout<S extends Section>(
  load: () => Promise<Record<string, unknown>>,
  name: string,
): LayoutComponent<S> {
  return lazy(() => load().then((module) => ({ default: module[name] as ComponentType<SectionProps<S>> })));
}

const hero = () => import('../sections/hero');
const events = () => import('../sections/events');
const services = () => import('../sections/services');
const pricing = () => import('../sections/pricing');
const about = () => import('../sections/about');
const gallery = () => import('../sections/gallery');
const testimonials = () => import('../sections/testimonials');
const team = () => import('../sections/team');
const contact = () => import('../sections/contact');

/**
 * Which component draws each section type in each layout. A new layout is
 * one component in its type's module and one line here (and the value in
 * the backend's layout enum). TypeScript fails the build when a layout of
 * the catalogue has no component.
 */
export const SECTION_REGISTRY: Registry = {
  HERO: {
    FULL_WIDTH_IMAGE: layout(hero, 'HeroFullWidth'),
    SLIDESHOW: layout(hero, 'HeroSlideshow'),
    VIDEO: layout(hero, 'HeroVideo'),
    SPLIT: layout(hero, 'HeroSplit'),
    CENTERED: layout(hero, 'HeroCentered'),
  },
  EVENTS: {
    CARDS: layout(events, 'EventsCardsLayout'),
    LIST: layout(events, 'EventsListLayout'),
  },
  SERVICES: {
    GROUPED_LIST: layout(services, 'ServicesGroupedList'),
    GRID: layout(services, 'ServicesGrid'),
    CAROUSEL: layout(services, 'ServicesCarousel'),
    LIST: layout(services, 'ServicesList'),
    ALTERNATING: layout(services, 'ServicesAlternating'),
  },
  PRICING: {
    CARDS: layout(pricing, 'PricingCards'),
    TABLE: layout(pricing, 'PricingTable'),
  },
  ABOUT: {
    CENTERED_TEXT: layout(about, 'AboutCentered'),
    TEXT_IMAGE: layout(about, 'AboutTextImage'),
    TEXT_STATS: layout(about, 'AboutStats'),
  },
  GALLERY: {
    GRID: layout(gallery, 'GalleryGrid'),
    MASONRY: layout(gallery, 'GalleryMasonry'),
    CAROUSEL: layout(gallery, 'GalleryCarousel'),
  },
  TESTIMONIALS: {
    CARDS: layout(testimonials, 'TestimonialsCards'),
    SLIDER: layout(testimonials, 'TestimonialsSlider'),
    FEATURED: layout(testimonials, 'TestimonialsFeatured'),
  },
  TEAM: {
    CARDS: layout(team, 'TeamCards'),
    LIST: layout(team, 'TeamList'),
  },
  CONTACT: {
    MAP_AND_INFO: layout(contact, 'ContactMapInfo'),
    INFO_ONLY: layout(contact, 'ContactInfoOnly'),
    WITH_HOURS: layout(contact, 'ContactWithHours'),
  },
};

/**
 * The component for a section, or null when this version of the app does
 * not know its type or layout (a newer document, or bad data).
 */
export function layoutComponent(section: { type: string; layout: string }): ComponentType<{ section: never }> | null {
  const registry = SECTION_REGISTRY as Record<string, Record<string, ComponentType<{ section: never }>>>;
  // Own keys only: a type or layout of "toString" must not find Object's.
  if (!Object.hasOwn(registry, section.type) || !Object.hasOwn(registry[section.type], section.layout)) {
    return null;
  }
  return registry[section.type][section.layout];
}
