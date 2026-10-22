import { useMemo } from 'react';
import dayjs, { type Dayjs } from 'dayjs';
import Box from '@mui/material/Box';
import Paper from '@mui/material/Paper';
import Tooltip from '@mui/material/Tooltip';
import Typography from '@mui/material/Typography';
import type { Reservation } from '@/types/api';
import { STATUS_LABELS, formatWindow } from '@/utils/format';

/** Tall enough that a one-hour reservation shows all three of its lines. */
const HOUR_HEIGHT = 72;
/** One line of a block: 12px text at a 1.3 line height. */
const LINE_HEIGHT = 15.6;
/** The block's top and bottom padding together. */
const BLOCK_PADDING = 8;
const STATUS_STYLE: Record<Reservation['status'], { bg: string; border: string }> = {
  PENDING: { bg: '#fff6e5', border: '#e8a33d' },
  CONFIRMED: { bg: '#eaf0fe', border: '#2f5bea' },
  COMPLETED: { bg: '#e7f6ee', border: '#0f9d58' },
  CANCELLED: { bg: '#f4f5f7', border: '#b0b6c3' },
  NO_SHOW: { bg: '#fdecea', border: '#c62828' },
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
                const reservation = placed.reservation;
                const style = STATUS_STYLE[reservation.status];
                const height = Math.max(placed.height - 2, LINE_HEIGHT + BLOCK_PADDING);
                const resources = reservation.resources.map((resource) => resource.name).join(', ');
                // Only the lines that fit whole: a short reservation shows its first line, not half of the third.
                const lines = [
                  { text: `${formatWindow(reservation).split(' – ')[0]} ${reservation.serviceName}`, strong: true },
                  { text: reservation.customerName, strong: false },
                  { text: resources, strong: false },
                ]
                  .filter((line) => line.text)
                  .slice(0, Math.max(1, Math.floor((height - BLOCK_PADDING) / LINE_HEIGHT)));
                return (
                  <Tooltip
                    key={reservation.id}
                    placement="right"
                    arrow
                    title={<ReservationSummary reservation={reservation} resources={resources} />}
                  >
                    <Box
                      component="button"
                      type="button"
                      onClick={() => onSelect(reservation)}
                      aria-label={`${formatWindow(reservation)} ${reservation.serviceName}, ${reservation.customerName}`}
                      sx={{
                        position: 'absolute',
                        top: placed.top,
                        height,
                        left: `calc(${(placed.column / placed.columns) * 100}% + 2px)`,
                        width: `calc(${100 / placed.columns}% - 4px)`,
                        bgcolor: style.bg,
                        borderRadius: 1,
                        px: 0.75,
                        py: `${BLOCK_PADDING / 2}px`,
                        textAlign: 'left',
                        overflow: 'hidden',
                        cursor: 'pointer',
                        border: 0,
                        borderLeftWidth: 3,
                        borderLeftStyle: 'solid',
                        borderLeftColor: style.border,
                        font: 'inherit',
                        textDecoration: reservation.status === 'CANCELLED' ? 'line-through' : 'none',
                        '&:hover': { filter: 'brightness(0.97)' },
                      }}
                    >
                      {lines.map((line) => (
                        <Typography
                          key={line.text}
                          variant="caption"
                          display="block"
                          noWrap
                          fontWeight={line.strong ? 700 : 400}
                          color={line.strong ? 'text.primary' : 'text.secondary'}
                          sx={{ lineHeight: `${LINE_HEIGHT}px` }}
                        >
                          {line.text}
                        </Typography>
                      ))}
                    </Box>
                  </Tooltip>
                );
              })}
            </Box>
          ))}
        </Box>
      </Box>
    </Paper>
  );
}

/** Everything about a reservation, for the hover card: nothing is lost to a narrow column. */
function ReservationSummary({ reservation, resources }: { reservation: Reservation; resources: string }) {
  return (
    <Box sx={{ py: 0.25 }}>
      <Typography variant="body2" fontWeight={700}>
        {reservation.serviceName}
      </Typography>
      <Typography variant="caption" display="block">
        {formatWindow(reservation)} · {STATUS_LABELS[reservation.status]}
      </Typography>
      <Typography variant="caption" display="block" sx={{ mt: 0.5 }}>
        {reservation.customerName}
        {reservation.numberOfParticipants > 1 ? ` · ${reservation.numberOfParticipants} people` : ''}
      </Typography>
      {reservation.customerPhone ? (
        <Typography variant="caption" display="block">
          {reservation.customerPhone}
        </Typography>
      ) : null}
      {resources ? (
        <Typography variant="caption" display="block" sx={{ mt: 0.5 }}>
          {resources}
        </Typography>
      ) : null}
    </Box>
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
