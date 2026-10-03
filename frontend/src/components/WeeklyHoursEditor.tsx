import Button from '@mui/material/Button';
import IconButton from '@mui/material/IconButton';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import Switch from '@mui/material/Switch';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import AddRoundedIcon from '@mui/icons-material/AddRounded';
import DeleteOutlineRoundedIcon from '@mui/icons-material/DeleteOutlineRounded';
import type { DayInterval } from '@/types/api';
import { DAY_NAMES } from '@/utils/format';

const DEFAULT_INTERVAL = { startTime: '09:00', endTime: '17:00' };

/**
 * A week of hours, shared by the business, each employee and each resource.
 *
 * <p>Several intervals on one day is how a lunch break is expressed
 * (09:00–13:00 plus 14:00–18:00), which is why there is no separate "break"
 * concept anywhere in the product.
 */
export function WeeklyHoursEditor({
  value,
  onChange,
}: {
  value: DayInterval[];
  onChange: (intervals: DayInterval[]) => void;
}) {
  const intervalsFor = (dayOfWeek: number) => value.filter((interval) => interval.dayOfWeek === dayOfWeek);

  const replaceDay = (dayOfWeek: number, dayIntervals: DayInterval[]) => {
    const others = value.filter((interval) => interval.dayOfWeek !== dayOfWeek);
    onChange(
      [...others, ...dayIntervals].sort(
        (a, b) => a.dayOfWeek - b.dayOfWeek || a.startTime.localeCompare(b.startTime),
      ),
    );
  };

  return (
    <Stack spacing={1.5}>
      {DAY_NAMES.map((name, index) => {
        const dayOfWeek = index + 1;
        const dayIntervals = intervalsFor(dayOfWeek);
        const isOpen = dayIntervals.length > 0;

        return (
          <Paper key={name} variant="outlined" sx={{ p: 1.5 }}>
            <Stack direction="row" alignItems="center" spacing={2} useFlexGap flexWrap="wrap">
              <Switch
                checked={isOpen}
                onChange={(event) =>
                  replaceDay(
                    dayOfWeek,
                    event.target.checked ? [{ dayOfWeek, ...DEFAULT_INTERVAL }] : [],
                  )
                }
              />
              <Typography variant="body2" fontWeight={600} sx={{ width: 96 }}>
                {name}
              </Typography>

              {isOpen ? (
                <Stack spacing={1} sx={{ flex: 1 }}>
                  {dayIntervals.map((interval, position) => (
                    <Stack key={position} direction="row" spacing={1} alignItems="center" useFlexGap flexWrap="wrap">
                      <TextField
                        type="time"
                        size="small"
                        value={interval.startTime.slice(0, 5)}
                        onChange={(event) => {
                          const next = [...dayIntervals];
                          next[position] = { ...interval, startTime: event.target.value };
                          replaceDay(dayOfWeek, next);
                        }}
                        inputProps={{ step: 300 }}
                      />
                      <Typography variant="body2" color="text.secondary">
                        to
                      </Typography>
                      <TextField
                        type="time"
                        size="small"
                        value={interval.endTime.slice(0, 5)}
                        onChange={(event) => {
                          const next = [...dayIntervals];
                          next[position] = { ...interval, endTime: event.target.value };
                          replaceDay(dayOfWeek, next);
                        }}
                        inputProps={{ step: 300 }}
                      />
                      <IconButton
                        size="small"
                        aria-label="Remove interval"
                        onClick={() =>
                          replaceDay(
                            dayOfWeek,
                            dayIntervals.filter((_, i) => i !== position),
                          )
                        }
                      >
                        <DeleteOutlineRoundedIcon fontSize="small" />
                      </IconButton>
                    </Stack>
                  ))}
                  <Button
                    size="small"
                    startIcon={<AddRoundedIcon />}
                    onClick={() =>
                      replaceDay(dayOfWeek, [...dayIntervals, { dayOfWeek, startTime: '14:00', endTime: '18:00' }])
                    }
                    sx={{ alignSelf: 'flex-start' }}
                  >
                    Add interval
                  </Button>
                </Stack>
              ) : (
                <Typography variant="body2" color="text.secondary">
                  Closed
                </Typography>
              )}
            </Stack>
          </Paper>
        );
      })}
    </Stack>
  );
}
