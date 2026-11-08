import { SECTION_CATALOG } from '../catalog';
import type { Section, SectionType, SiteColors, SiteComponents, SiteDocument, SiteTypography } from '../types';

/**
 * The homepage editor's state and every change it can make, as pure
 * functions. `saved` is the draft as the server last returned it, so the
 * editor knows when there is something to save.
 */
export interface EditorState {
  document: SiteDocument;
  saved: SiteDocument;
}

export type EditorAction =
  /** A draft from the server: after loading, saving or discarding. */
  | { type: 'load'; document: SiteDocument }
  | { type: 'move'; id: string; by: -1 | 1 }
  /** `id` is the new section's: passed in, so the reducer stays pure. */
  | { type: 'add'; sectionType: SectionType; id: string }
  | { type: 'duplicate'; id: string; newId: string }
  | { type: 'remove'; id: string }
  | { type: 'setShown'; id: string; shown: boolean }
  | { type: 'updateSection'; id: string; update: (section: Section) => Section }
  | { type: 'setColors'; colors: Partial<SiteColors> }
  | { type: 'setTypography'; typography: Partial<SiteTypography> }
  | { type: 'setComponents'; components: Partial<SiteComponents> }
  | { type: 'setHeader'; header: Partial<SiteDocument['header']> };

export const MAX_SECTIONS = 30;

export function initialEditorState(document: SiteDocument): EditorState {
  return { document, saved: document };
}

function withSections(document: SiteDocument, sections: Section[]): SiteDocument {
  return { ...document, sections };
}

function mapSection(document: SiteDocument, id: string, update: (section: Section) => Section): SiteDocument {
  return withSections(document, document.sections.map((section) => (section.id === id ? update(section) : section)));
}

/** The document after one change. Changes that cannot apply (no such section, at the edge, at the limit) leave it as it is. */
function apply(document: SiteDocument, action: Exclude<EditorAction, { type: 'load' }>): SiteDocument {
  switch (action.type) {
    case 'move': {
      const from = document.sections.findIndex((section) => section.id === action.id);
      const to = from + action.by;
      if (from < 0 || to < 0 || to >= document.sections.length) {
        return document;
      }
      const sections = [...document.sections];
      [sections[from], sections[to]] = [sections[to], sections[from]];
      return withSections(document, sections);
    }
    case 'add':
      if (document.sections.length >= MAX_SECTIONS) {
        return document;
      }
      return withSections(document, [...document.sections, SECTION_CATALOG[action.sectionType].create(action.id) as Section]);
    case 'duplicate': {
      const index = document.sections.findIndex((section) => section.id === action.id);
      if (index < 0 || document.sections.length >= MAX_SECTIONS) {
        return document;
      }
      const copy = { ...structuredClone(document.sections[index]), id: action.newId } as Section;
      const sections = [...document.sections];
      sections.splice(index + 1, 0, copy);
      return withSections(document, sections);
    }
    case 'remove':
      return withSections(document, document.sections.filter((section) => section.id !== action.id));
    case 'setShown':
      return mapSection(document, action.id, (section) => ({
        ...section,
        visibility: { desktop: action.shown, mobile: action.shown },
      }));
    case 'updateSection':
      return mapSection(document, action.id, action.update);
    case 'setColors':
      return { ...document, theme: { ...document.theme, colors: { ...document.theme.colors, ...action.colors } } };
    case 'setTypography':
      return { ...document, theme: { ...document.theme, typography: { ...document.theme.typography, ...action.typography } } };
    case 'setComponents':
      return { ...document, theme: { ...document.theme, components: { ...document.theme.components, ...action.components } } };
    case 'setHeader':
      return { ...document, header: { ...document.header, ...action.header } };
  }
}

export function editorReducer(state: EditorState, action: EditorAction): EditorState {
  if (action.type === 'load') {
    return initialEditorState(action.document);
  }
  const document = apply(state.document, action);
  return document === state.document ? state : { ...state, document };
}

/** Whether there are edits the server has not got yet. */
export function isDirty(state: EditorState): boolean {
  return JSON.stringify(state.document) !== JSON.stringify(state.saved);
}

/** A new section id, in the format the server accepts. */
export function newSectionId(): string {
  return crypto.randomUUID();
}
