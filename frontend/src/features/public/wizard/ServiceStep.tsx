import { useMemo } from 'react';
import Avatar from '@mui/material/Avatar';
import Box from '@mui/material/Box';
import ButtonBase from '@mui/material/ButtonBase';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import ChevronRightRoundedIcon from '@mui/icons-material/ChevronRightRounded';
import { mediaUrl } from '@/features/media/api';
import type { PublicService } from '@/types/api';
import { formatDuration, formatMoney } from '@/utils/format';

/** Step 1: what to book. Tapping a service moves straight on. */
export function ServiceStep({
  services,
  selectedId,
  onSelect,
}: {
  services: PublicService[];
  selectedId: number | null;
  onSelect: (service: PublicService) => void;
}) {
  const groups = useMemo(() => {
    const map = new Map<string, PublicService[]>();
    services.forEach((service) => {
      const key = service.category?.trim() || 'Services';
      map.set(key, [...(map.get(key) ?? []), service]);
    });
    return [...map.entries()];
  }, [services]);

  return (
    <Stack spacing={3}>
      {groups.map(([category, items]) => (
        <div key={category}>
          {groups.length > 1 ? (
            <Typography variant="overline" color="text.secondary" sx={{ letterSpacing: '0.15em' }}>
              {category}
            </Typography>
          ) : null}
          <Stack spacing={1.25}>
            {items.map((service) => {
              const selected = service.id === selectedId;
              return (
                <ButtonBase
                  key={service.id}
                  onClick={() => onSelect(service)}
                  sx={{
                    display: 'flex',
                    gap: 2,
                    p: 1.5,
                    textAlign: 'left',
                    justifyContent: 'flex-start',
                    borderRadius: 3,
                    border: 2,
                    borderColor: selected ? 'primary.main' : 'divider',
                    bgcolor: 'background.paper',
                    transition: 'border-color 120ms',
                    '&:hover': { borderColor: selected ? 'primary.main' : 'text.disabled' },
                  }}
                >
                  <Avatar
                    variant="rounded"
                    src={mediaUrl(service.imageUrl)}
                    sx={{ width: 64, height: 64, borderRadius: 2.5, bgcolor: 'primary.main', fontWeight: 800 }}
                  >
                    {service.name.charAt(0)}
                  </Avatar>
                  <Box sx={{ flex: 1, minWidth: 0 }}>
                    <Typography fontWeight={700}>{service.name}</Typography>
                    {service.description ? (
                      <Typography variant="body2" color="text.secondary" sx={{ display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical', overflow: 'hidden' }}>
                        {service.description}
                      </Typography>
                    ) : null}
                    <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                      {formatDuration(service.durationMinutes)}
                      {service.maxParticipants > 1 ? ` · up to ${service.maxParticipants} people` : ''}
                    </Typography>
                  </Box>
                  <Stack alignItems="flex-end" sx={{ flexShrink: 0 }}>
                    <Typography fontWeight={800}>{formatMoney(service.price, service.currency)}</Typography>
                    {service.pricePerParticipant ? (
                      <Typography variant="caption" color="text.secondary">
                        per person
                      </Typography>
                    ) : null}
                  </Stack>
                  <ChevronRightRoundedIcon sx={{ color: 'text.disabled', alignSelf: 'center' }} />
                </ButtonBase>
              );
            })}
          </Stack>
        </div>
      ))}
    </Stack>
  );
}
