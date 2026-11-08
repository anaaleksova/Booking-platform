import { useState } from 'react';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Chip from '@mui/material/Chip';
import Collapse from '@mui/material/Collapse';
import Divider from '@mui/material/Divider';
import FormControlLabel from '@mui/material/FormControlLabel';
import IconButton from '@mui/material/IconButton';
import ListItemText from '@mui/material/ListItemText';
import Menu from '@mui/material/Menu';
import MenuItem from '@mui/material/MenuItem';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import Switch from '@mui/material/Switch';
import TextField from '@mui/material/TextField';
import Tooltip from '@mui/material/Tooltip';
import Typography from '@mui/material/Typography';
import AddRoundedIcon from '@mui/icons-material/AddRounded';
import ArrowDownwardRoundedIcon from '@mui/icons-material/ArrowDownwardRounded';
import ArrowUpwardRoundedIcon from '@mui/icons-material/ArrowUpwardRounded';
import ContentCopyRoundedIcon from '@mui/icons-material/ContentCopyRounded';
import DeleteOutlineRoundedIcon from '@mui/icons-material/DeleteOutlineRounded';
import ExpandMoreRoundedIcon from '@mui/icons-material/ExpandMoreRounded';
import { ConfirmDialog } from '@/components/ConfirmDialog';
import { SECTION_CATALOG, SECTION_TYPES, isHidden, layoutLabel, sectionLabel, type LayoutInfo } from '../catalog';
import type { Section, SectionTone, SectionType } from '../types';
import { MAX_SECTIONS, newSectionId, type EditorAction } from './editorModel';
import { SectionFields } from './SectionFields';

const TONES: Array<{ value: SectionTone; label: string }> = [
  { value: 'DEFAULT', label: 'Page colour' },
  { value: 'SURFACE', label: 'Card colour' },
  { value: 'PRIMARY', label: 'Main colour' },
  { value: 'SECONDARY', label: 'Footer colour' },
];

/** The page's sections in order: each can move, hide, change layout, be copied, removed or edited. */
export function SectionsCard({ sections, dispatch }: { sections: Section[]; dispatch: (action: EditorAction) => void }) {
  const [open, setOpen] = useState<string | null>(null);
  const [adding, setAdding] = useState<HTMLElement | null>(null);
  const [removing, setRemoving] = useState<Section | null>(null);

  const add = (sectionType: SectionType) => {
    const id = newSectionId();
    dispatch({ type: 'add', sectionType, id });
    setAdding(null);
    setOpen(id);
  };

  return (
    <Paper variant="outlined" sx={{ p: 3 }}>
      <Typography variant="subtitle1" fontWeight={700} gutterBottom>
        Sections
      </Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
        Your page from top to bottom. Pick how each part looks, change the order, or hide what you don’t need.
      </Typography>

      <Stack spacing={1.25} component="ol" sx={{ listStyle: 'none', m: 0, p: 0 }}>
        {sections.map((section, index) => (
          <SectionRow
            key={section.id}
            section={section}
            sections={sections}
            first={index === 0}
            last={index === sections.length - 1}
            open={open === section.id}
            onToggle={() => setOpen(open === section.id ? null : section.id)}
            dispatch={dispatch}
            onRemove={() => setRemoving(section)}
            onDuplicate={() => {
              const newId = newSectionId();
              dispatch({ type: 'duplicate', id: section.id, newId });
              setOpen(newId);
            }}
            canAdd={sections.length < MAX_SECTIONS}
          />
        ))}
      </Stack>

      <Button startIcon={<AddRoundedIcon />} onClick={(event) => setAdding(event.currentTarget)} disabled={sections.length >= MAX_SECTIONS} sx={{ mt: 2 }}>
        Add a section
      </Button>
      <Menu anchorEl={adding} open={Boolean(adding)} onClose={() => setAdding(null)}>
        {SECTION_TYPES.map((type) => (
          <MenuItem key={type} onClick={() => add(type)} sx={{ maxWidth: 360, whiteSpace: 'normal' }}>
            <ListItemText primary={SECTION_CATALOG[type].label} secondary={SECTION_CATALOG[type].description} />
          </MenuItem>
        ))}
      </Menu>

      <ConfirmDialog
        open={Boolean(removing)}
        title="Remove this section?"
        message={removing ? `The ${sectionLabel(removing)} section and what you wrote in it will be removed from the draft.` : ''}
        confirmLabel="Remove"
        onConfirm={() => {
          if (removing) {
            dispatch({ type: 'remove', id: removing.id });
          }
          setRemoving(null);
        }}
        onClose={() => setRemoving(null)}
      />
    </Paper>
  );
}

function SectionRow({
  section,
  sections,
  first,
  last,
  open,
  onToggle,
  dispatch,
  onRemove,
  onDuplicate,
  canAdd,
}: {
  section: Section;
  sections: Section[];
  first: boolean;
  last: boolean;
  open: boolean;
  onToggle: () => void;
  dispatch: (action: EditorAction) => void;
  onRemove: () => void;
  onDuplicate: () => void;
  canAdd: boolean;
}) {
  const hidden = isHidden(section);
  const label = sectionLabel(section);
  const layouts = SECTION_CATALOG[section.type].layouts as Record<string, LayoutInfo>;
  const update = (changed: Partial<Section>) => dispatch({ type: 'updateSection', id: section.id, update: (current) => ({ ...current, ...changed }) as Section });
  const panelId = `section-editor-${section.id}`;

  return (
    <Box component="li">
      <Paper variant="outlined" sx={{ opacity: hidden ? 0.6 : 1 }}>
        <Stack direction="row" alignItems="center" spacing={0.5} sx={{ pl: 1, pr: 1, py: 0.5 }}>
          <Stack>
            <IconButton size="small" aria-label={`Move ${label} up`} disabled={first} onClick={() => dispatch({ type: 'move', id: section.id, by: -1 })}>
              <ArrowUpwardRoundedIcon fontSize="small" />
            </IconButton>
            <IconButton size="small" aria-label={`Move ${label} down`} disabled={last} onClick={() => dispatch({ type: 'move', id: section.id, by: 1 })}>
              <ArrowDownwardRoundedIcon fontSize="small" />
            </IconButton>
          </Stack>
          <Box
            component="button"
            type="button"
            onClick={onToggle}
            aria-expanded={open}
            aria-controls={panelId}
            sx={{ flex: 1, minWidth: 0, textAlign: 'left', background: 'none', border: 0, p: 1, cursor: 'pointer', color: 'inherit', font: 'inherit', borderRadius: 1 }}
          >
            <Stack direction="row" spacing={1} alignItems="center">
              <Typography fontWeight={700}>{label}</Typography>
              {hidden ? <Chip label="Hidden" size="small" /> : null}
            </Stack>
            <Typography variant="body2" color="text.secondary" noWrap>
              {layoutLabel(section)}
            </Typography>
          </Box>
          <Tooltip title={hidden ? 'Show on the page' : 'Hide from the page'}>
            <Switch checked={!hidden} onChange={(event) => dispatch({ type: 'setShown', id: section.id, shown: event.target.checked })} inputProps={{ 'aria-label': `Show ${label}` }} />
          </Tooltip>
          <IconButton aria-label={open ? `Close ${label}` : `Edit ${label}`} onClick={onToggle}>
            <ExpandMoreRoundedIcon sx={{ transform: open ? 'rotate(180deg)' : 'none', transition: 'transform 200ms ease' }} />
          </IconButton>
        </Stack>

        <Collapse in={open} unmountOnExit>
          <Divider />
          <Stack id={panelId} spacing={2.5} sx={{ p: 2.5 }}>
            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
              <TextField
                select
                label="Layout"
                value={section.layout}
                onChange={(event) => update({ layout: event.target.value } as Partial<Section>)}
                helperText={layouts[section.layout]?.description}
                sx={{ flex: 1 }}
              >
                {Object.entries(layouts).map(([value, info]) => (
                  <MenuItem key={value} value={value}>
                    {info.label}
                  </MenuItem>
                ))}
              </TextField>
              <TextField
                select
                label="Background"
                value={section.style.tone}
                onChange={(event) => update({ style: { ...section.style, tone: event.target.value as SectionTone } })}
                sx={{ minWidth: 180 }}
              >
                {TONES.map((tone) => (
                  <MenuItem key={tone.value} value={tone.value}>
                    {tone.label}
                  </MenuItem>
                ))}
              </TextField>
            </Stack>

            <SectionFields section={section} sections={sections} onChange={(changed) => dispatch({ type: 'updateSection', id: section.id, update: () => changed })} />

            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1} alignItems={{ sm: 'center' }}>
              <FormControlLabel
                control={<Switch size="small" checked={section.visibility.desktop} onChange={(event) => update({ visibility: { ...section.visibility, desktop: event.target.checked } })} />}
                label="On computers"
              />
              <FormControlLabel
                control={<Switch size="small" checked={section.visibility.mobile} onChange={(event) => update({ visibility: { ...section.visibility, mobile: event.target.checked } })} />}
                label="On phones"
              />
              <Box sx={{ flex: 1 }} />
              <Button size="small" startIcon={<ContentCopyRoundedIcon />} onClick={onDuplicate} disabled={!canAdd} aria-label={`Duplicate ${label}`}>
                Duplicate
              </Button>
              <Button size="small" color="error" startIcon={<DeleteOutlineRoundedIcon />} onClick={onRemove} aria-label={`Remove ${label}`}>
                Remove
              </Button>
            </Stack>
          </Stack>
        </Collapse>
      </Paper>
    </Box>
  );
}
