import { afterEach, describe, expect, it } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ToastProvider } from '@/components/ToastProvider';
import { fakeApi, type RecordedRequest } from '@/test/fakeApi';
import { LIGHT_COLORS, completeTheme } from '../theme';
import type { Section, SiteDocument, SiteState } from '../types';
import { SiteDesigner } from './SiteDesigner';

const shown = { desktop: true, mobile: true };
const sections: Section[] = [
  { type: 'HERO', id: 'hero', layout: 'FULL_WIDTH_IMAGE', visibility: shown, style: { tone: 'DEFAULT' }, content: {} },
  { type: 'SERVICES', id: 'services', layout: 'GROUPED_LIST', visibility: shown, style: { tone: 'SURFACE' }, content: { source: 'ALL', showBookButton: true } },
  { type: 'CONTACT', id: 'contact', layout: 'MAP_AND_INFO', visibility: shown, style: { tone: 'SURFACE' }, content: { showHours: true } },
];
const document: SiteDocument = {
  schemaVersion: 1,
  theme: completeTheme({ colors: LIGHT_COLORS }),
  header: { layout: 'LOGO_LEFT_NAV_RIGHT', sticky: true, transparentOverHero: true },
  footer: { layout: 'CENTERED' },
  sections,
};

/** A fake server that keeps a draft and a published copy, like the real one. */
function server() {
  let state: SiteState = { draft: document, published: document, draftVersion: 3, hasUnpublishedChanges: false };
  const changed = (draft: SiteDocument, published: SiteDocument) => JSON.stringify(draft) !== JSON.stringify(published);
  return {
    get state() {
      return state;
    },
    routes: {
      'GET /api/admin/site': () => ({ status: 200, data: state }),
      'PUT /api/admin/site/draft': (request: RecordedRequest) => {
        const body = request.data as { draftVersion: number; document: SiteDocument };
        if (body.draftVersion !== state.draftVersion) {
          return { status: 409, data: { error: 'SITE_DRAFT_OUTDATED', message: 'The page was changed in another window.' } };
        }
        state = { ...state, draft: body.document, draftVersion: state.draftVersion + 1, hasUnpublishedChanges: changed(body.document, state.published) };
        return { status: 200, data: state };
      },
      'POST /api/admin/site/publish': () => {
        state = { ...state, published: state.draft, draftVersion: state.draftVersion + 1, hasUnpublishedChanges: false, publishedAt: '2026-11-03T20:00:00Z' };
        return { status: 200, data: state };
      },
      'POST /api/admin/site/discard': () => {
        state = { ...state, draft: state.published, draftVersion: state.draftVersion + 1, hasUnpublishedChanges: false };
        return { status: 200, data: state };
      },
    },
  };
}

function renderDesigner() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={queryClient}>
      <ToastProvider>
        <SiteDesigner />
      </ToastProvider>
    </QueryClientProvider>,
  );
}

const sectionNames = () =>
  within(screen.getByRole('list')).getAllByRole('listitem').map((item) => within(item).getAllByText(/Hero|Services|Contact/)[0].textContent);

describe('SiteDesigner', () => {
  let api: ReturnType<typeof fakeApi> | undefined;
  afterEach(() => api?.restore());

  it('reorders the sections and saves the draft with the version it started from', async () => {
    const fake = server();
    api = fakeApi(fake.routes);
    renderDesigner();
    const user = userEvent.setup();

    await user.click(await screen.findByRole('button', { name: 'Move Contact up' }));
    expect(sectionNames()).toEqual(['Hero', 'Contact', 'Services']);
    expect(screen.getByRole('status')).toHaveTextContent('unsaved changes');

    await user.click(screen.getByRole('button', { name: 'Save draft' }));

    await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('not published yet'));
    const save = api.requests.find((request) => request.method === 'PUT')!;
    expect(save.data).toMatchObject({ draftVersion: 3 });
    expect((save.data as { document: SiteDocument }).document.sections.map((section) => section.id)).toEqual(['hero', 'contact', 'services']);
    // Nothing reached customers yet.
    expect(fake.state.published.sections.map((section) => section.id)).toEqual(['hero', 'services', 'contact']);
  });

  it('publishing saves unsaved edits first, then publishes them', async () => {
    const fake = server();
    api = fakeApi(fake.routes);
    renderDesigner();
    const user = userEvent.setup();

    await user.click(await screen.findByRole('checkbox', { name: 'Show Services' }));
    await user.click(screen.getByRole('button', { name: 'Publish' }));

    await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('Everything is published'));
    const writes = api.requests.filter((request) => request.method !== 'GET');
    expect(writes.map((request) => `${request.method} ${request.url}`)).toEqual(['PUT /api/admin/site/draft', 'POST /api/admin/site/publish']);
    expect(fake.state.published.sections[1].visibility).toEqual({ desktop: false, mobile: false });
  });

  it('adds and removes sections', async () => {
    api = fakeApi(server().routes);
    renderDesigner();
    const user = userEvent.setup();

    await user.click(await screen.findByRole('button', { name: 'Add a section' }));
    await user.click(screen.getByRole('menuitem', { name: /Reviews/ }));
    expect(screen.getAllByRole('listitem')).toHaveLength(4);

    await user.click(screen.getByRole('button', { name: 'Edit Hero' }));
    await user.click(screen.getByRole('button', { name: 'Remove Hero' }));
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Remove' }));

    await waitFor(() => expect(screen.getAllByRole('listitem')).toHaveLength(3));
    expect(screen.queryByRole('button', { name: 'Edit Hero' })).toBeNull();
  });

  it('discarding goes back to the published page', async () => {
    const fake = server();
    api = fakeApi(fake.routes);
    renderDesigner();
    const user = userEvent.setup();

    await user.click(await screen.findByRole('button', { name: 'Move Contact up' }));
    await user.click(screen.getByRole('button', { name: 'Save draft' }));
    await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('not published yet'));

    await user.click(screen.getByRole('button', { name: 'Discard changes' }));
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Discard' }));

    await waitFor(() => expect(sectionNames()).toEqual(['Hero', 'Services', 'Contact']));
    expect(screen.getByRole('status')).toHaveTextContent('Everything is published');
  });

  it('warns when the text colour is hard to read on the page', async () => {
    api = fakeApi(server().routes);
    renderDesigner();
    const user = userEvent.setup();

    await screen.findByRole('button', { name: 'Black page' });
    expect(screen.queryByText(/hard to read/)).toBeNull();

    // The black page's colours, but the text left dark.
    await user.click(screen.getByRole('button', { name: 'Black page' }));
    expect(screen.queryByText(/hard to read/)).toBeNull();
    const text = screen.getByLabelText('Text colour');
    // Colour inputs cannot be typed into; set the value as the picker would.
    const { fireEvent } = await import('@testing-library/react');
    fireEvent.change(text, { target: { value: '#111111' } });

    expect(screen.getByText('The text colour is hard to read on the page colour.')).toBeInTheDocument();
  });
});
