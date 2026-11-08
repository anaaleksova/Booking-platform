import { useEffect, useReducer, useState } from 'react';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { errorMessage } from '@/api/client';
import { ConfirmDialog } from '@/components/ConfirmDialog';
import { ErrorState, LoadingState } from '@/components/states';
import { useToast } from '@/components/ToastProvider';
import { useDiscardSiteDraft, usePublishSite, useSaveSiteDraft, useSite } from '../api';
import type { SiteDocument, SiteState } from '../types';
import { editorReducer, initialEditorState, isDirty, type EditorState } from './editorModel';
import { SectionsCard } from './SectionsCard';
import { StyleCard } from './StyleCard';
import { ThemeCard } from './ThemeCard';
import { TypographyCard } from './TypographyCard';

type Loaded = EditorState & { draftVersion: number; hasUnpublishedChanges: boolean };

type Action = Parameters<typeof editorReducer>[1] | { type: 'server'; state: SiteState };

function reducer(state: Loaded | null, action: Action): Loaded | null {
  if (action.type === 'server') {
    return {
      ...initialEditorState(action.state.draft),
      draftVersion: action.state.draftVersion,
      hasUnpublishedChanges: action.state.hasUnpublishedChanges,
    };
  }
  return state ? { ...state, ...editorReducer(state, action) } : state;
}

/**
 * The design of the business's homepage: theme and sections, edited as a
 * draft. Customers keep seeing the published page until "Publish".
 */
/**
 * @param onDocumentChange told the draft after every edit, for the live preview
 */
export function SiteDesigner({ onDocumentChange }: { onDocumentChange?: (document: SiteDocument) => void }) {
  const toast = useToast();
  const site = useSite();
  const save = useSaveSiteDraft();
  const publish = usePublishSite();
  const discard = useDiscardSiteDraft();
  const [state, dispatch] = useReducer(reducer, null);
  const [confirmDiscard, setConfirmDiscard] = useState(false);

  // The server's draft is loaded once; afterwards only the answers to this editor's own saves replace it.
  useEffect(() => {
    if (site.data && !state) {
      dispatch({ type: 'server', state: site.data });
    }
  }, [site.data, state]);

  const dirty = state ? isDirty(state) : false;
  const document = state?.document;

  useEffect(() => {
    if (document) {
      onDocumentChange?.(document);
    }
  }, [document, onDocumentChange]);

  useEffect(() => {
    if (!dirty) {
      return undefined;
    }
    const warn = (event: BeforeUnloadEvent) => {
      event.preventDefault();
      event.returnValue = '';
    };
    window.addEventListener('beforeunload', warn);
    return () => window.removeEventListener('beforeunload', warn);
  }, [dirty]);

  if (site.isError) {
    return <ErrorState error={site.error} />;
  }
  if (!state) {
    return <LoadingState />;
  }

  const busy = save.isPending || publish.isPending || discard.isPending;

  const saveDraft = async () => {
    const saved = await save.mutateAsync({ draftVersion: state.draftVersion, document: state.document });
    dispatch({ type: 'server', state: saved });
    return saved;
  };

  const onSave = () =>
    saveDraft()
      .then(() => toast.success('Draft saved. Publish when you’re ready for customers to see it.'))
      .catch((error) => toast.error(errorMessage(error)));

  const onPublish = async () => {
    try {
      if (dirty) {
        await saveDraft();
      }
      dispatch({ type: 'server', state: await publish.mutateAsync() });
      toast.success('Published: your page is live.');
    } catch (error) {
      toast.error(errorMessage(error));
    }
  };

  const onDiscard = () =>
    discard
      .mutateAsync()
      .then((discarded) => {
        dispatch({ type: 'server', state: discarded });
        toast.success('Changes thrown away');
      })
      .catch((error) => toast.error(errorMessage(error)))
      .finally(() => setConfirmDiscard(false));

  const status = dirty
    ? 'You have unsaved changes.'
    : state.hasUnpublishedChanges
      ? 'Saved, not published yet: customers still see the previous version.'
      : 'Everything is published.';

  return (
    <Stack spacing={3}>
      <ThemeCard colors={state.document.theme.colors} onChange={(colors) => dispatch({ type: 'setColors', colors })} />
      <TypographyCard typography={state.document.theme.typography} onChange={(typography) => dispatch({ type: 'setTypography', typography })} />
      <StyleCard components={state.document.theme.components} onChange={(components) => dispatch({ type: 'setComponents', components })} />
      <SectionsCard sections={state.document.sections} dispatch={dispatch} />

      <Paper
        variant="outlined"
        sx={{ position: 'sticky', bottom: 16, zIndex: 5, p: 2, boxShadow: '0 8px 30px rgba(15,23,42,0.12)' }}
      >
        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5} alignItems={{ sm: 'center' }}>
          <Box sx={{ flex: 1 }}>
            <Typography variant="body2" fontWeight={600} role="status">
              {status}
            </Typography>
          </Box>
          <Button onClick={() => setConfirmDiscard(true)} disabled={busy || (!dirty && !state.hasUnpublishedChanges)}>
            Discard changes
          </Button>
          <Button variant="outlined" onClick={onSave} disabled={busy || !dirty}>
            {save.isPending && !publish.isPending ? 'Saving…' : 'Save draft'}
          </Button>
          <Button variant="contained" onClick={onPublish} disabled={busy || (!dirty && !state.hasUnpublishedChanges)}>
            {publish.isPending ? 'Publishing…' : 'Publish'}
          </Button>
        </Stack>
      </Paper>

      <ConfirmDialog
        open={confirmDiscard}
        title="Discard your changes?"
        message="The design goes back to the page your customers see now. Your business details above are not affected."
        confirmLabel="Discard"
        busy={discard.isPending}
        onConfirm={onDiscard}
        onClose={() => setConfirmDiscard(false)}
      />
    </Stack>
  );
}
