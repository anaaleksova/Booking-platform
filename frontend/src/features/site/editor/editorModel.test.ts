import { describe, expect, it } from 'vitest';
import type { Section, SiteDocument } from '../types';
import { LIGHT_COLORS, completeTheme } from '../theme';
import { MAX_SECTIONS, editorReducer, initialEditorState, isDirty } from './editorModel';

const hero: Section = {
  type: 'HERO',
  id: 'hero',
  layout: 'FULL_WIDTH_IMAGE',
  visibility: { desktop: true, mobile: true },
  style: { tone: 'DEFAULT' },
  content: { images: [] },
};
const services: Section = {
  type: 'SERVICES',
  id: 'services',
  layout: 'GROUPED_LIST',
  visibility: { desktop: true, mobile: true },
  style: { tone: 'SURFACE' },
  content: { source: 'ALL', serviceIds: [], showBookButton: true },
};
const contact: Section = {
  type: 'CONTACT',
  id: 'contact',
  layout: 'MAP_AND_INFO',
  visibility: { desktop: true, mobile: true },
  style: { tone: 'SURFACE' },
  content: { showHours: true },
};

const page: SiteDocument = {
  schemaVersion: 1,
  theme: completeTheme({ colors: LIGHT_COLORS }),
  header: { layout: 'LOGO_LEFT_NAV_RIGHT', sticky: true, transparentOverHero: true },
  footer: { layout: 'CENTERED' },
  sections: [hero, services, contact],
};

const ids = (state: ReturnType<typeof initialEditorState>) => state.document.sections.map((section) => section.id);

describe('homepage editor', () => {
  it('moves a section up and down, and not past the ends', () => {
    let state = initialEditorState(page);

    state = editorReducer(state, { type: 'move', id: 'contact', by: -1 });
    expect(ids(state)).toEqual(['hero', 'contact', 'services']);

    state = editorReducer(state, { type: 'move', id: 'hero', by: 1 });
    expect(ids(state)).toEqual(['contact', 'hero', 'services']);

    const atTop = editorReducer(state, { type: 'move', id: 'contact', by: -1 });
    expect(atTop).toBe(state);
  });

  it('adds a section of a type at the end, with that type’s starting content', () => {
    const state = editorReducer(initialEditorState(page), { type: 'add', sectionType: 'TESTIMONIALS', id: 'reviews' });

    expect(ids(state)).toEqual(['hero', 'services', 'contact', 'reviews']);
    expect(state.document.sections[3]).toMatchObject({ type: 'TESTIMONIALS', layout: 'CARDS', content: { items: [] } });
  });

  it('adds nothing beyond the section limit', () => {
    let state = initialEditorState({ ...page, sections: [] });
    for (let i = 0; i < MAX_SECTIONS + 3; i++) {
      state = editorReducer(state, { type: 'add', sectionType: 'ABOUT', id: `about-${i}` });
    }
    expect(state.document.sections).toHaveLength(MAX_SECTIONS);
  });

  it('duplicates a section right after it, under a new id, as an independent copy', () => {
    let state = editorReducer(initialEditorState(page), { type: 'duplicate', id: 'services', newId: 'services-2' });
    expect(ids(state)).toEqual(['hero', 'services', 'services-2', 'contact']);

    state = editorReducer(state, {
      type: 'updateSection',
      id: 'services-2',
      update: (section) => ({ ...section, layout: 'GRID' }) as Section,
    });
    expect(state.document.sections[1].layout).toBe('GROUPED_LIST');
    expect(state.document.sections[2].layout).toBe('GRID');
  });

  it('removes a section', () => {
    const state = editorReducer(initialEditorState(page), { type: 'remove', id: 'services' });
    expect(ids(state)).toEqual(['hero', 'contact']);
  });

  it('hides and shows a section on every screen, keeping its settings', () => {
    let state = editorReducer(initialEditorState(page), { type: 'setShown', id: 'contact', shown: false });
    expect(state.document.sections[2]).toMatchObject({ visibility: { desktop: false, mobile: false }, layout: 'MAP_AND_INFO' });

    state = editorReducer(state, { type: 'setShown', id: 'contact', shown: true });
    expect(state.document.sections[2].visibility).toEqual({ desktop: true, mobile: true });
  });

  it('changes theme colours one at a time', () => {
    const state = editorReducer(initialEditorState(page), { type: 'setColors', colors: { primary: '#0f9d76' } });
    expect(state.document.theme.colors).toEqual({ ...LIGHT_COLORS, primary: '#0f9d76' });
  });

  it('changes fonts and styles field by field, keeping the rest', () => {
    let state = editorReducer(initialEditorState(page), { type: 'setTypography', typography: { headingFont: 'PLAYFAIR_DISPLAY' } });
    state = editorReducer(state, { type: 'setComponents', components: { buttonStyle: 'OUTLINE' } });

    expect(state.document.theme.typography).toMatchObject({ headingFont: 'PLAYFAIR_DISPLAY', bodyFont: 'SYSTEM', headingWeight: 800 });
    expect(state.document.theme.components).toMatchObject({ buttonStyle: 'OUTLINE', buttonShape: 'PILL' });
    expect(state.document.theme.colors).toEqual(LIGHT_COLORS);
  });

  it('knows when there is something to save, and that a reload from the server is saved', () => {
    const fresh = initialEditorState(page);
    expect(isDirty(fresh)).toBe(false);

    const edited = editorReducer(fresh, { type: 'remove', id: 'hero' });
    expect(isDirty(edited)).toBe(true);

    // Undoing by hand is not an edit.
    const undone = editorReducer(edited, { type: 'load', document: page });
    expect(isDirty(undone)).toBe(false);

    // The saved draft coming back from the server is the new baseline.
    const saved = editorReducer(edited, { type: 'load', document: edited.document });
    expect(isDirty(saved)).toBe(false);
    expect(ids(saved)).toEqual(['services', 'contact']);
  });

  it('never changes the document it was given', () => {
    const copy = structuredClone(page);
    let state = initialEditorState(page);
    state = editorReducer(state, { type: 'move', id: 'hero', by: 1 });
    state = editorReducer(state, { type: 'setShown', id: 'hero', shown: false });
    editorReducer(state, { type: 'setColors', colors: { text: '#000000' } });
    expect(page).toEqual(copy);
  });
});
