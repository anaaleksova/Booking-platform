/**
 * A business's homepage as data, as the API sends it (`SiteDocument` in the
 * backend). The backend validates every save; these types say what the
 * renderer and the editor may rely on. Checked against the API in
 * `types/api.contract.ts`.
 */

export interface SiteColors {
  /** Buttons, links and highlights. */
  primary: string;
  /** Dark bands such as the footer. */
  secondary: string;
  /** Small details: eyebrows, ratings, the highlighted price. */
  accent: string;
  /** The page. */
  background: string;
  /** Cards and the alternating section band. */
  surface: string;
  /** Body text and headings. */
  text: string;
}

/** The curated fonts; SYSTEM is the device's own, which every page used before fonts could be chosen. */
export type SiteFont =
  | 'SYSTEM'
  | 'INTER'
  | 'ROBOTO'
  | 'OPEN_SANS'
  | 'LATO'
  | 'MONTSERRAT'
  | 'POPPINS'
  | 'NUNITO'
  | 'WORK_SANS'
  | 'RALEWAY'
  | 'PLAYFAIR_DISPLAY'
  | 'LORA'
  | 'MERRIWEATHER'
  | 'CORMORANT_GARAMOND'
  | 'DM_SERIF_DISPLAY'
  | 'OSWALD'
  | 'BEBAS_NEUE';

export interface SiteTypography {
  headingFont: SiteFont;
  bodyFont: SiteFont;
  /** Body text size in px, 14–20; everything else scales with it. */
  baseSize: number;
  /** 300–900, in hundreds. */
  headingWeight: number;
  headingStyle: 'NORMAL' | 'UPPERCASE' | 'ITALIC';
  headingAlign: 'CENTER' | 'LEFT';
}

export interface SiteComponents {
  /** Corners of cards and photos. */
  corners: 'SHARP' | 'SOFT' | 'ROUNDED';
  buttonShape: 'SQUARE' | 'ROUNDED' | 'PILL';
  buttonStyle: 'FILLED' | 'OUTLINE' | 'GHOST';
  cardStyle: 'MINIMAL' | 'BORDERED' | 'SHADOW';
  imageShape: 'SQUARE' | 'ROUNDED' | 'CIRCLE';
  /** px, 1–3, for bordered cards. */
  borderWidth: number;
  shadow: 'SOFT' | 'STRONG';
}

export interface SiteTheme {
  colors: SiteColors;
  typography: SiteTypography;
  components: SiteComponents;
}

export interface SiteHeader {
  layout: 'LOGO_LEFT_NAV_RIGHT';
  sticky: boolean;
  transparentOverHero: boolean;
}

export interface SiteFooter {
  layout: 'CENTERED';
}

export interface SiteVisibility {
  desktop: boolean;
  mobile: boolean;
}

export type SectionTone = 'DEFAULT' | 'SURFACE' | 'PRIMARY' | 'SECONDARY';

export interface SectionStyle {
  tone: SectionTone;
}

export interface SiteImage {
  mediaId: string;
  /** For screen readers; empty when the image is only decoration. */
  alt?: string | null;
}

export type ButtonAction = 'BOOKING' | 'SECTION' | 'URL';

export interface SiteButton {
  label: string;
  action: ButtonAction;
  /** The section id for SECTION, the address for URL. */
  target?: string | null;
}

export type ServiceSource = 'ALL' | 'SELECTED';

interface SectionBase<T extends string, L extends string, C> {
  type: T;
  id: string;
  layout: L;
  visibility: SiteVisibility;
  style: SectionStyle;
  content: C;
}

/** Texts left null show the business's own (name, tagline) or a default heading. */
interface Heading {
  eyebrow?: string | null;
  title?: string | null;
}

export type HeroLayout = 'FULL_WIDTH_IMAGE' | 'SPLIT' | 'CENTERED' | 'VIDEO' | 'SLIDESHOW';
export type HeroSection = SectionBase<
  'HERO',
  HeroLayout,
  Heading & {
    subtitle?: string | null;
    /** None: the business's cover photo. */
    images?: SiteImage[] | null;
    videoUrl?: string | null;
    /** Null: the usual booking button. */
    buttons?: SiteButton[] | null;
  }
>;

export type EventsLayout = 'CARDS' | 'LIST';
export type EventsSection = SectionBase<'EVENTS', EventsLayout, Heading>;

export type ServicesLayout = 'GROUPED_LIST' | 'GRID' | 'LIST' | 'CAROUSEL' | 'ALTERNATING';
export type ServicesSection = SectionBase<
  'SERVICES',
  ServicesLayout,
  Heading & { source: ServiceSource; serviceIds?: number[] | null; showBookButton: boolean }
>;

export type AboutLayout = 'CENTERED_TEXT' | 'TEXT_IMAGE' | 'TEXT_STATS';
export interface AboutStat {
  value: string;
  label: string;
}
export type AboutSection = SectionBase<
  'ABOUT',
  AboutLayout,
  Heading & { text?: string | null; image?: SiteImage | null; stats?: AboutStat[] | null }
>;

export type GalleryLayout = 'GRID' | 'MASONRY' | 'CAROUSEL';
export type GallerySection = SectionBase<
  'GALLERY',
  GalleryLayout,
  Heading & { source: 'GALLERY' | 'CUSTOM'; images?: SiteImage[] | null }
>;

export type TestimonialsLayout = 'CARDS' | 'SLIDER' | 'FEATURED';
export interface Testimonial {
  quote: string;
  author: string;
  detail?: string | null;
  rating?: number | null;
  photo?: SiteImage | null;
}
export type TestimonialsSection = SectionBase<'TESTIMONIALS', TestimonialsLayout, Heading & { items?: Testimonial[] | null }>;

export type TeamLayout = 'CARDS' | 'LIST';
export interface TeamMemberRef {
  employeeId: number;
  role?: string | null;
  bio?: string | null;
}
export type TeamSection = SectionBase<'TEAM', TeamLayout, Heading & { members?: TeamMemberRef[] | null }>;

export type PricingLayout = 'CARDS' | 'TABLE';
export type PricingSection = SectionBase<
  'PRICING',
  PricingLayout,
  Heading & { source: ServiceSource; serviceIds?: number[] | null; highlightServiceId?: number | null }
>;

export type ContactLayout = 'MAP_AND_INFO' | 'INFO_ONLY' | 'WITH_HOURS';
export type ContactSection = SectionBase<'CONTACT', ContactLayout, Heading & { showHours: boolean }>;

export type Section =
  | HeroSection
  | EventsSection
  | ServicesSection
  | AboutSection
  | GallerySection
  | TestimonialsSection
  | TeamSection
  | PricingSection
  | ContactSection;

export type SectionType = Section['type'];

export interface SiteDocument {
  schemaVersion: number;
  theme: SiteTheme;
  header: SiteHeader;
  footer: SiteFooter;
  sections: Section[];
}

/** The owner's view of the homepage. */
export interface SiteState {
  draft: SiteDocument;
  published: SiteDocument;
  /** Sent back with the next save. */
  draftVersion: number;
  /** Null while the business has never published. */
  publishedAt?: string | null;
  hasUnpublishedChanges: boolean;
}

export interface PublicTeamMember {
  id: number;
  name: string;
  imageUrl?: string | null;
}

/** The published homepage, with the team and gallery it shows. */
export interface PublicSite {
  site: SiteDocument;
  team: PublicTeamMember[];
  gallery: Array<{ url: string; caption?: string | null }>;
}
