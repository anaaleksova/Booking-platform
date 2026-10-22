import Avatar from '@mui/material/Avatar';
import Box from '@mui/material/Box';
import ButtonBase from '@mui/material/ButtonBase';
import Chip from '@mui/material/Chip';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import CheckCircleRoundedIcon from '@mui/icons-material/CheckCircleRounded';
import { mediaUrl } from '@/features/media/api';
import type { ChoosableOption } from '@/types/api';
import { statusLabel, type SeatingRules } from './seatingRules';

/** The same choice as the map, as a list: for small screens, and for anything not drawn. */
export function SpotList({
  resources,
  rules,
  picked,
  people,
  onToggle,
}: {
  resources: ChoosableOption[];
  rules: SeatingRules;
  picked: number[];
  people: number;
  onToggle: (option: ChoosableOption) => void;
}) {
  return (
    <Stack spacing={1}>
      {resources.map((option) => {
        const status = rules.statusOf(option);
        const isPicked = picked.includes(option.id);
        return (
          <ButtonBase
            key={option.id}
            onClick={() => onToggle(option)}
            disabled={status !== 'free'}
            aria-pressed={isPicked}
            sx={{
              justifyContent: 'flex-start',
              gap: 1.5,
              p: 1.5,
              borderRadius: 3,
              border: 2,
              borderColor: isPicked ? 'primary.main' : 'divider',
              textAlign: 'left',
              opacity: status === 'free' ? 1 : 0.55,
            }}
          >
            <Avatar src={mediaUrl(option.imageUrl)} sx={{ bgcolor: 'primary.main' }}>
              {option.name.charAt(0)}
            </Avatar>
            <Box sx={{ flex: 1, minWidth: 0 }}>
              <Typography fontWeight={700}>{option.name}</Typography>
              <Typography variant="caption" color="text.secondary">
                {[option.capacity ? `${option.capacity} seats` : null, option.description].filter(Boolean).join(' · ')}
              </Typography>
            </Box>
            {isPicked ? (
              <CheckCircleRoundedIcon color="primary" />
            ) : status !== 'free' ? (
              <Chip size="small" label={statusLabel(status, people, option, rules.seatsLeftOf(option))} />
            ) : null}
          </ButtonBase>
        );
      })}
    </Stack>
  );
}
