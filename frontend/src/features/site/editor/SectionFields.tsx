import Checkbox from '@mui/material/Checkbox';
import FormControlLabel from '@mui/material/FormControlLabel';
import FormGroup from '@mui/material/FormGroup';
import MenuItem from '@mui/material/MenuItem';
import Radio from '@mui/material/Radio';
import RadioGroup from '@mui/material/RadioGroup';
import Rating from '@mui/material/Rating';
import Stack from '@mui/material/Stack';
import Switch from '@mui/material/Switch';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { useEmployees, useServices } from '@/features/catalog/api';
import { sectionLabel } from '../catalog';
import type {
  AboutSection,
  ContactSection,
  GallerySection,
  HeroSection,
  PricingSection,
  Section,
  ServiceSource,
  ServicesSection,
  TeamSection,
  TestimonialsSection,
} from '../types';
import { ButtonsField, ImageField, ImagesField, ListField, OptionalText, type ContentUpdater } from './fields';

interface FieldsProps<S extends Section> {
  section: S;
  set: ContentUpdater<S>;
  /** Every section of the page, for buttons that scroll to one. */
  sections: Section[];
}

/** The content form of a section, by its type. */
export function SectionFields({ section, onChange, sections }: { section: Section; onChange: (section: Section) => void; sections: Section[] }) {
  const set = (patch: object) => onChange({ ...section, content: { ...section.content, ...patch } } as Section);
  switch (section.type) {
    case 'HERO':
      return <HeroFields section={section} set={set} sections={sections} />;
    case 'EVENTS':
      return <Headings section={section} set={set} eyebrow="What's on" title="Upcoming events" />;
    case 'SERVICES':
      return <ServicesFields section={section} set={set} sections={sections} />;
    case 'PRICING':
      return <PricingFields section={section} set={set} sections={sections} />;
    case 'ABOUT':
      return <AboutFields section={section} set={set} sections={sections} />;
    case 'GALLERY':
      return <GalleryFields section={section} set={set} sections={sections} />;
    case 'TESTIMONIALS':
      return <TestimonialsFields section={section} set={set} sections={sections} />;
    case 'TEAM':
      return <TeamFields section={section} set={set} sections={sections} />;
    case 'CONTACT':
      return <ContactFields section={section} set={set} sections={sections} />;
    default:
      return null;
  }
}

/** Eyebrow and title; the placeholders are what shows when they are left empty. */
function Headings<S extends Section>({
  section,
  set,
  eyebrow,
  title,
}: {
  section: S;
  set: ContentUpdater<S>;
  eyebrow: string;
  title: string;
}) {
  const content = section.content as { eyebrow?: string | null; title?: string | null };
  return (
    <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
      <OptionalText label="Small line above" value={content.eyebrow} placeholder={eyebrow} maxLength={60} onChange={(value) => set({ eyebrow: value } as Partial<S['content']>)} />
      <OptionalText label="Heading" value={content.title} placeholder={title} maxLength={120} onChange={(value) => set({ title: value } as Partial<S['content']>)} />
    </Stack>
  );
}

function HeroFields({ section, set, sections }: FieldsProps<HeroSection>) {
  const content = section.content;
  const usingDefaultButton = content.buttons == null;
  return (
    <Stack spacing={2.5}>
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
        <OptionalText label="Small line above" value={content.eyebrow} placeholder="Welcome" maxLength={60} onChange={(eyebrow) => set({ eyebrow })} />
        <OptionalText label="Title" value={content.title} placeholder="Your business name" maxLength={120} onChange={(title) => set({ title })} />
      </Stack>
      <OptionalText label="Subtitle" value={content.subtitle} placeholder="Your tagline" maxLength={300} onChange={(subtitle) => set({ subtitle })} />
      {section.layout === 'VIDEO' ? (
        <TextField
          label="YouTube or Vimeo link"
          placeholder="https://www.youtube.com/watch?v=…"
          value={content.videoUrl ?? ''}
          onChange={(event) => set({ videoUrl: event.target.value || null })}
          inputProps={{ maxLength: 300 }}
          helperText="Plays muted and on repeat behind your name. The first photo shows while it loads."
          fullWidth
        />
      ) : null}
      {section.layout !== 'CENTERED' ? (
        <ImagesField
          label={section.layout === 'SLIDESHOW' ? 'Photos (they take turns)' : 'Photo'}
          images={content.images ?? []}
          max={section.layout === 'SLIDESHOW' ? 8 : 1}
          onChange={(images) => set({ images })}
          emptyHint="Without one, your cover photo is used."
        />
      ) : null}
      <FormControlLabel
        control={<Switch checked={usingDefaultButton} onChange={(event) => set({ buttons: event.target.checked ? null : [{ label: 'Book now', action: 'BOOKING', target: null }] })} />}
        label="Show the usual booking button"
      />
      {usingDefaultButton ? null : (
        <ButtonsField
          buttons={content.buttons ?? []}
          max={3}
          onChange={(buttons) => set({ buttons })}
          sections={sections.filter((other) => other.id !== section.id).map((other) => ({ id: other.id, label: sectionLabel(other) }))}
        />
      )}
    </Stack>
  );
}

/** All services, or a chosen few in the chosen order. */
function ServicePicker({
  source,
  serviceIds,
  onChange,
}: {
  source: ServiceSource;
  serviceIds: number[];
  onChange: (patch: { source?: ServiceSource; serviceIds?: number[] }) => void;
}) {
  const services = useServices();
  const active = (services.data ?? []).filter((service) => service.active);
  return (
    <Stack spacing={1}>
      <RadioGroup row value={source} onChange={(event) => onChange({ source: event.target.value as ServiceSource })}>
        <FormControlLabel value="ALL" control={<Radio />} label="All bookable services" />
        <FormControlLabel value="SELECTED" control={<Radio />} label="Only the ones I pick" />
      </RadioGroup>
      {source === 'SELECTED' ? (
        <FormGroup sx={{ pl: 1 }}>
          {active.length === 0 ? (
            <Typography variant="body2" color="text.secondary">
              You have no active services yet.
            </Typography>
          ) : null}
          {active.map((service) => (
            <FormControlLabel
              key={service.id}
              control={
                <Checkbox
                  checked={serviceIds.includes(service.id)}
                  onChange={(event) =>
                    onChange({ serviceIds: event.target.checked ? [...serviceIds, service.id] : serviceIds.filter((id) => id !== service.id) })
                  }
                />
              }
              label={service.name}
            />
          ))}
        </FormGroup>
      ) : null}
    </Stack>
  );
}

function ServicesFields({ section, set }: FieldsProps<ServicesSection>) {
  const content = section.content;
  return (
    <Stack spacing={2}>
      <Headings section={section} set={set} eyebrow="Price list" title="Services & prices" />
      <ServicePicker source={content.source} serviceIds={content.serviceIds ?? []} onChange={set} />
      <FormControlLabel
        control={<Switch checked={content.showBookButton} onChange={(event) => set({ showBookButton: event.target.checked })} />}
        label="Show a booking button under the list"
      />
    </Stack>
  );
}

function PricingFields({ section, set }: FieldsProps<PricingSection>) {
  const content = section.content;
  const services = useServices();
  return (
    <Stack spacing={2}>
      <Headings section={section} set={set} eyebrow="Prices" title="Our prices" />
      <ServicePicker source={content.source} serviceIds={content.serviceIds ?? []} onChange={set} />
      <TextField
        select
        label="Highlight"
        value={content.highlightServiceId ?? ''}
        onChange={(event) => set({ highlightServiceId: event.target.value === '' ? null : Number(event.target.value) })}
        helperText="Shown as “Most popular”"
        sx={{ maxWidth: 360 }}
      >
        <MenuItem value="">None</MenuItem>
        {(services.data ?? []).filter((service) => service.active).map((service) => (
          <MenuItem key={service.id} value={service.id}>
            {service.name}
          </MenuItem>
        ))}
      </TextField>
    </Stack>
  );
}

function AboutFields({ section, set }: FieldsProps<AboutSection>) {
  const content = section.content;
  const stats = content.stats ?? [];
  return (
    <Stack spacing={2.5}>
      <Headings section={section} set={set} eyebrow="About us" title="Welcome to …" />
      <OptionalText
        label="Text"
        value={content.text}
        placeholder="Your “About” text from Words above"
        maxLength={4000}
        multiline
        onChange={(text) => set({ text })}
      />
      {section.layout === 'TEXT_IMAGE' ? (
        <ImageField label="Photo" image={content.image} onChange={(image) => set({ image })} emptyHint="Without one, your cover photo is used." />
      ) : null}
      {section.layout === 'TEXT_STATS' ? (
        <ListField
          label="Numbers"
          items={stats}
          max={4}
          addLabel="Add a number"
          onAdd={() => set({ stats: [...stats, { value: '10+', label: 'years' }] })}
          onRemove={(index) => set({ stats: stats.filter((_, i) => i !== index) })}
          render={(stat, index) => (
            <Stack direction="row" spacing={1.5}>
              <TextField size="small" label="Number" value={stat.value} inputProps={{ maxLength: 20 }} sx={{ width: 120 }}
                onChange={(event) => set({ stats: stats.map((s, i) => (i === index ? { ...s, value: event.target.value } : s)) })} />
              <TextField size="small" label="What it counts" value={stat.label} inputProps={{ maxLength: 60 }} fullWidth
                onChange={(event) => set({ stats: stats.map((s, i) => (i === index ? { ...s, label: event.target.value } : s)) })} />
            </Stack>
          )}
        />
      ) : null}
    </Stack>
  );
}

function GalleryFields({ section, set }: FieldsProps<GallerySection>) {
  const content = section.content;
  return (
    <Stack spacing={2}>
      <Headings section={section} set={set} eyebrow="Gallery" title="Take a look inside" />
      <RadioGroup row value={content.source} onChange={(event) => set({ source: event.target.value as GallerySection['content']['source'] })}>
        <FormControlLabel value="GALLERY" control={<Radio />} label="My gallery (above)" />
        <FormControlLabel value="CUSTOM" control={<Radio />} label="Photos just for this section" />
      </RadioGroup>
      {content.source === 'CUSTOM' ? <ImagesField label="Photos" images={content.images ?? []} max={24} onChange={(images) => set({ images })} /> : null}
    </Stack>
  );
}

function TestimonialsFields({ section, set }: FieldsProps<TestimonialsSection>) {
  const items = section.content.items ?? [];
  const update = (index: number, patch: Partial<(typeof items)[number]>) =>
    set({ items: items.map((item, i) => (i === index ? { ...item, ...patch } : item)) });
  return (
    <Stack spacing={2}>
      <Headings section={section} set={set} eyebrow="Reviews" title="What our guests say" />
      <ListField
        label="Reviews"
        items={items}
        max={12}
        addLabel="Add a review"
        onAdd={() => set({ items: [...items, { quote: '', author: '', detail: null, rating: 5, photo: null }] })}
        onRemove={(index) => set({ items: items.filter((_, i) => i !== index) })}
        render={(item, index) => (
          <Stack spacing={1.5}>
            <TextField size="small" label="What they said" value={item.quote} multiline minRows={2} inputProps={{ maxLength: 600 }} onChange={(event) => update(index, { quote: event.target.value })} />
            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5} alignItems={{ sm: 'center' }}>
              <TextField size="small" label="Name" value={item.author} inputProps={{ maxLength: 80 }} onChange={(event) => update(index, { author: event.target.value })} />
              <TextField size="small" label="Detail" placeholder="e.g. Regular since 2021" value={item.detail ?? ''} inputProps={{ maxLength: 80 }} onChange={(event) => update(index, { detail: event.target.value || null })} />
              <Rating value={item.rating ?? null} onChange={(_, rating) => update(index, { rating })} aria-label="Stars" />
            </Stack>
          </Stack>
        )}
      />
    </Stack>
  );
}

function TeamFields({ section, set }: FieldsProps<TeamSection>) {
  const employees = useEmployees();
  const members = section.content.members ?? [];
  const picked = new Map(members.map((member) => [member.employeeId, member]));
  const active = (employees.data ?? []).filter((employee) => employee.active);
  return (
    <Stack spacing={2}>
      <Headings section={section} set={set} eyebrow="Our team" title="Meet the team" />
      <Typography variant="body2" color="text.secondary">
        Only the people you tick are shown on your page, with the name and photo from Employees.
      </Typography>
      {active.length === 0 ? (
        <Typography variant="body2" color="text.secondary">
          You have no active employees yet.
        </Typography>
      ) : null}
      {active.map((employee) => {
        const member = picked.get(employee.id);
        return (
          <Stack key={employee.id} spacing={1}>
            <FormControlLabel
              control={
                <Checkbox
                  checked={Boolean(member)}
                  onChange={(event) =>
                    set({
                      members: event.target.checked
                        ? [...members, { employeeId: employee.id, role: null, bio: null }]
                        : members.filter((m) => m.employeeId !== employee.id),
                    })
                  }
                />
              }
              label={employee.name}
            />
            {member ? (
              <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5} sx={{ pl: 4 }}>
                <TextField size="small" label="Role" placeholder="e.g. Head chef" value={member.role ?? ''} inputProps={{ maxLength: 80 }}
                  onChange={(event) => set({ members: members.map((m) => (m.employeeId === employee.id ? { ...m, role: event.target.value || null } : m)) })} />
                <TextField size="small" label="Short bio" value={member.bio ?? ''} inputProps={{ maxLength: 400 }} fullWidth
                  onChange={(event) => set({ members: members.map((m) => (m.employeeId === employee.id ? { ...m, bio: event.target.value || null } : m)) })} />
              </Stack>
            ) : null}
          </Stack>
        );
      })}
    </Stack>
  );
}

function ContactFields({ section, set }: FieldsProps<ContactSection>) {
  return (
    <Stack spacing={2}>
      <Headings section={section} set={set} eyebrow="Where we are" title="How to find us" />
      {section.layout === 'WITH_HOURS' ? null : (
        <FormControlLabel
          control={<Switch checked={section.content.showHours} onChange={(event) => set({ showHours: event.target.checked })} />}
          label="Show opening hours"
        />
      )}
    </Stack>
  );
}
