import Avatar from '@mui/material/Avatar';
import Box from '@mui/material/Box';
import ButtonBase from '@mui/material/ButtonBase';
import Grid from '@mui/material/Grid';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import CheckCircleRoundedIcon from '@mui/icons-material/CheckCircleRounded';
import ShuffleRoundedIcon from '@mui/icons-material/ShuffleRounded';
import { ParticipantStepper } from '@/components/ParticipantStepper';
import { mediaUrl } from '@/features/media/api';
import type { ChoosableRequirement, PublicService } from '@/types/api';
import { listedRequirements } from './useWizardState';

/**
 * Step 2 (when relevant): how many people, and who or what they would like —
 * "choose your therapist", "choose your table". "No preference" lets the
 * business assign the best free one.
 */
export function OptionsStep({
  service,
  people,
  picks,
  onPeople,
  onPicks,
}: {
  service: PublicService;
  people: number;
  picks: Record<number, number[]>;
  onPeople: (people: number) => void;
  onPicks: (picks: Record<number, number[]>) => void;
}) {
  return (
    <Stack spacing={4}>
      {service.maxParticipants > 1 ? (
        <section>
          <Typography variant="h6" fontWeight={800} gutterBottom>
            How many people?
          </Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
            Up to {service.maxParticipants} people.
          </Typography>
          <ParticipantStepper value={people} max={service.maxParticipants} onChange={onPeople} />
        </section>
      ) : null}

      {listedRequirements(service).map((requirement) => (
        <RequirementChoice
          key={requirement.requirementId}
          requirement={requirement}
          people={people}
          picked={picks[requirement.requirementId] ?? []}
          onChange={(ids) => {
            const next = { ...picks };
            if (ids.length) {
              next[requirement.requirementId] = ids;
            } else {
              delete next[requirement.requirementId];
            }
            onPicks(next);
          }}
        />
      ))}
    </Stack>
  );
}

function RequirementChoice({
  requirement,
  people,
  picked,
  onChange,
}: {
  requirement: ChoosableRequirement;
  people: number;
  picked: number[];
  onChange: (ids: number[]) => void;
}) {
  const many = requirement.quantity > 1;
  // A resource too small for the group can never be part of a valid choice on its own.
  const options = requirement.resources.filter(
    (option) =>
      many || ((option.capacity === null || option.capacity >= people) && !(!option.shared && option.minPartySize && people < option.minPartySize)),
  );

  const toggle = (id: number) => {
    if (picked.includes(id)) {
      onChange(picked.filter((value) => value !== id));
    } else if (!many) {
      onChange([id]);
    } else {
      onChange(picked.length >= requirement.quantity ? [...picked.slice(1), id] : [...picked, id]);
    }
  };

  return (
    <section>
      <Typography variant="h6" fontWeight={800} gutterBottom>
        {many ? `Choose ${requirement.quantity}: ${requirement.resourceTypeName}` : `Choose your ${requirement.resourceTypeName.toLowerCase()}`}
      </Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
        {many ? `${picked.length} of ${requirement.quantity} selected. ` : ''}Optional — with no preference we pick the best free one.
      </Typography>
      <Grid container spacing={1.5}>
        <Grid item xs={6} sm={4}>
          <OptionCard
            selected={picked.length === 0}
            onClick={() => onChange([])}
            title="No preference"
            subtitle="Fastest availability"
            avatar={
              <Avatar sx={{ width: 64, height: 64, bgcolor: 'action.selected', color: 'text.primary' }}>
                <ShuffleRoundedIcon />
              </Avatar>
            }
          />
        </Grid>
        {options.map((option) => (
          <Grid item xs={6} sm={4} key={option.id}>
            <OptionCard
              selected={picked.includes(option.id)}
              onClick={() => toggle(option.id)}
              title={option.name}
              subtitle={option.description ?? (option.capacity && option.capacity > 1 ? `Up to ${option.capacity} people` : undefined)}
              avatar={
                <Avatar src={mediaUrl(option.imageUrl)} sx={{ width: 64, height: 64, bgcolor: 'primary.main', fontSize: 24, fontWeight: 700 }}>
                  {option.name.charAt(0)}
                </Avatar>
              }
            />
          </Grid>
        ))}
      </Grid>
    </section>
  );
}

function OptionCard({
  selected,
  onClick,
  title,
  subtitle,
  avatar,
}: {
  selected: boolean;
  onClick: () => void;
  title: string;
  subtitle?: string;
  avatar: React.ReactNode;
}) {
  return (
    <ButtonBase
      onClick={onClick}
      aria-pressed={selected}
      sx={{
        position: 'relative',
        width: '100%',
        height: '100%',
        flexDirection: 'column',
        gap: 1,
        p: 2,
        borderRadius: 3,
        border: 2,
        borderColor: selected ? 'primary.main' : 'divider',
        bgcolor: selected ? (theme) => `${theme.palette.primary.main}0d` : 'background.paper',
      }}
    >
      {selected ? (
        <CheckCircleRoundedIcon color="primary" sx={{ position: 'absolute', top: 8, right: 8, fontSize: 20 }} />
      ) : null}
      {avatar}
      <Box sx={{ textAlign: 'center' }}>
        <Typography fontWeight={700}>{title}</Typography>
        {subtitle ? (
          <Typography variant="caption" color="text.secondary" sx={{ display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical', overflow: 'hidden' }}>
            {subtitle}
          </Typography>
        ) : null}
      </Box>
    </ButtonBase>
  );
}
