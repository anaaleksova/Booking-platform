import { useMemo } from 'react';
import dayjs, { type Dayjs } from 'dayjs';
import Box from '@mui/material/Box';
import Paper from '@mui/material/Paper';
import Typography from '@mui/material/Typography';
import type { Reservation } from '@/types/api';
import { formatWindow } from '@/utils/format';

const HOUR_HEIGHT = 56;
const STATUS_STYLE: Record<Reservation['status'], { bg: string; border: string }> = {
  PENDING: { bg: '#fff6e5', border: '#e8a33d' },
  CONFIRMED: { bg: '#eaf0fe', border: '#2f5bea' },
  COMPLETED: { bg: '#e7f6ee', border: '#0f9d58' },
  CANCELLED: { bg: '#f4f5f7', border: '#b0b6c3' },
};

interface Placed {
  reservation: Reservation;
  top: number;
  height: number;
  column: number;
  columns: number;
}

/**
 * A week grid of reservations, positioned by their business-local start and
 * end. Overlapping reservations share the day's width side by side, which is
 * exactly what several rooms in parallel look like.
 */
export function WeekCalendar({
  weekStart,
  reservations,
  onSelect,
}: {
  weekStart: Dayjs;
  reservations: Reservation[];
  onSelect: (reservation: Reservation) => void;
}) {
  const days = Array.from({ length: 7 }, (_, index) => weekStart.add(index, 'day'));

  const { firstHour, lastHour } = useMemo(() => {
    let first = 9;
    let last = 18;
    reservations.forEach((reservation) => {
      first = Math.min(first, dayjs(reservation.localStartTime).hour());
      const end = dayjs(reservation.localEndTime);
      last = Math.max(last, end.minute() > 0 ? end.hour() + 1 : end.hour());
    });
    return { firstHour: first, lastHour: Math.min(24, last) };
  }, [reservations]);

  const placedByDay = useMemo(() => {
    const map = new Map<string, Placed[]>();
    days.forEach((day) => {
      const key = day.format('YYYY-MM-DD');
      const items = reservations
        .filter((reservation) => reservation.localStartTime.startsWith(key))
        .sort((a, b) => a.localStartTime.localeCompare(b.localStartTime));
      map.set(key, layout(items, firstHour));
    });
    return map;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [reservations, weekStart.valueOf(), firstHour]);

  const hours = Array.from({ length: lastHour - firstHour }, (_, index) => firstHour + index);

  return (
    <Paper variant="outlined" sx={{ overflowX: 'auto' }}>
      <Box sx={{ minWidth: 840 }}>
        <Box sx={{ display: 'grid', gridTemplateColumns: '56px repeat(7, 1fr)', borderBottom: 1, borderColor: 'divider' }}>
          <Box />
          {days.map((day) => (
            <Box
              key={day.valueOf()}
              sx={{ py: 1, textAlign: 'center', borderLeft: 1, borderColor: 'divider' }}
            >
              <Typography variant="caption" color="text.secondary">
                {day.format('ddd')}
              </Typography>
              <Typography
                variant="subtitle2"
                sx={{ color: day.isSame(dayjs(), 'day') ? 'primary.main' : 'text.primary', fontWeight: 700 }}
              >
                {day.format('D MMM')}
              </Typography>
            </Box>
          ))}
        </Box>
        <Box sx={{ display: 'grid', gridTemplateColumns: '56px repeat(7, 1fr)' }}>
          <Box>
            {hours.map((hour) => (
              <Box key={hour} sx={{ height: HOUR_HEIGHT, pr: 1, textAlign: 'right' }}>
                <Typography variant="caption" color="text.secondary">
                  {String(hour).padStart(2, '0')}:00
                </Typography>
              </Box>
            ))}
          </Box>
          {days.map((day) => (
            <Box
              key={day.valueOf()}
              sx={{
                position: 'relative',
                borderLeft: 1,
                borderColor: 'divider',
                height: hours.length * HOUR_HEIGHT,
                backgroundImage: `repeating-linear-gradient(to bottom, transparent 0, transparent ${HOUR_HEIGHT - 1}px, rgba(15,23,42,0.06) ${HOUR_HEIGHT - 1}px, rgba(15,23,42,0.06) ${HOUR_HEIGHT}px)`,
              }}
            >
              {(placedByDay.get(day.format('YYYY-MM-DD')) ?? []).map((placed) => {
                const style = STATUS_STYLE[placed.reservation.status];
                return (
                  <Box
                    key={placed.reservation.id}
                    component="button"
                    type="button"
                    onClick={() => onSelect(placed.reservation)}
                    title={`${placed.reservation.serviceName} — ${placed.reservation.customerName}`}
                    sx={{
                      position: 'absolute',
                      top: placed.top,
                      height: Math.max(placed.height - 2, 20),
                      left: `calc(${(placed.column / placed.columns) * 100}% + 2px)`,
                      width: `calc(${100 / placed.columns}% - 4px)`,
                      bgcolor: style.bg,
                      borderRadius: 1,
                      p: 0.5,
                      textAlign: 'left',
                      overflow: 'hidden',
                      cursor: 'pointer',
                      border: 0,
                      borderLeftWidth: 3,
                      borderLeftStyle: 'solid',
                      borderLeftColor: style.border,
                      font: 'inherit',
                      textDecoration: placed.reservation.status === 'CANCELLED' ? 'line-through' : 'none',
                      '&:hover': { filter: 'brightness(0.97)' },
                    }}
                  >
                    <Typography variant="caption" fontWeight={700} display="block" noWrap>
                      {formatWindow(placed.reservation).split(' – ')[0]} {placed.reservation.serviceName}
                    </Typography>
                    <Typography variant="caption" color="text.secondary" display="block" noWrap>
                      {placed.reservation.customerName}
                    </Typography>
                    <Typography variant="caption" color="text.secondary" display="block" noWrap>
                      {placed.reservation.resources.map((resource) => resource.name).join(', ')}
                    </Typography>
                  </Box>
                );
              })}
            </Box>
          ))}
        </Box>
      </Box>
    </Paper>
  );
}

/** Greedy column assignment within clusters of overlapping reservations. */
function layout(items: Reservation[], firstHour: number): Placed[] {
  const minutes = (local: string) => {
    const time = dayjs(local);
    return (time.hour() - firstHour) * 60 + time.minute();
  };
  const placed: Placed[] = [];
  let cluster: Placed[] = [];
  let clusterEnd = -1;
  const columnEnds: number[] = [];

  const flush = () => {
    const columns = Math.max(1, columnEnds.length);
    cluster.forEach((item) => (item.columns = columns));
    placed.push(...cluster);
    cluster = [];
    columnEnds.length = 0;
  };

  items.forEach((reservation) => {
    const start = minutes(reservation.localStartTime);
    const end = Math.max(start + 15, minutes(reservation.localEndTime));
    if (start >= clusterEnd) {
      flush();
    }
    let column = columnEnds.findIndex((columnEnd) => columnEnd <= start);
    if (column === -1) {
      column = columnEnds.length;
      columnEnds.push(end);
    } else {
      columnEnds[column] = end;
    }
    clusterEnd = Math.max(clusterEnd, end);
    cluster.push({
      reservation,
      top: (start / 60) * HOUR_HEIGHT,
      height: ((end - start) / 60) * HOUR_HEIGHT,
      column,
      columns: 1,
    });
  });
  flush();
  return placed;
}
