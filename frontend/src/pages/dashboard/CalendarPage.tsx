import { useMemo, useState } from 'react';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Chip from '@mui/material/Chip';
import IconButton from '@mui/material/IconButton';
import MenuItem from '@mui/material/MenuItem';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import ToggleButton from '@mui/material/ToggleButton';
import ToggleButtonGroup from '@mui/material/ToggleButtonGroup';
import Tooltip from '@mui/material/Tooltip';
import Typography from '@mui/material/Typography';
import AddRoundedIcon from '@mui/icons-material/AddRounded';
import ChevronLeftRoundedIcon from '@mui/icons-material/ChevronLeftRounded';
import ChevronRightRoundedIcon from '@mui/icons-material/ChevronRightRounded';
import dayjs, { type Dayjs } from 'dayjs';
import { PageHeader } from '@/components/PageHeader';
import { ErrorState, TableLoadingRows } from '@/components/states';
import { StatusChip } from '@/components/StatusChip';
import { BookingFormDialog } from '@/features/bookings/BookingFormDialog';
import { BookingDetailsDialog } from '@/features/bookings/BookingDetailsDialog';
import { useBookings } from '@/features/bookings/useBookings';
import { useStaff } from '@/features/staff/useStaff';
import { formatBookingWindow, formatLocalTime } from '@/utils/format';
import type { Booking } from '@/types/api';

type CalendarView = 'day' | 'week' | 'month';

/** Monday-first week start, without pulling in a dayjs locale plugin. */
function startOfWeek(date: Dayjs): Dayjs {
  return date.subtract((date.day() + 6) % 7, 'day').startOf('day');
}

function rangeFor(view: CalendarView, anchor: Dayjs): { from: Dayjs; to: Dayjs } {
  if (view === 'day') {
    return { from: anchor.startOf('day'), to: anchor.startOf('day') };
  }
  if (view === 'week') {
    const from = startOfWeek(anchor);
    return { from, to: from.add(6, 'day') };
  }
  // A month view shows leading and trailing days so the grid is always full.
  const from = startOfWeek(anchor.startOf('month'));
  const to = startOfWeek(anchor.endOf('month')).add(6, 'day');
  return { from, to };
}

export function CalendarPage() {
  const [view, setView] = useState<CalendarView>('week');
  const [anchor, setAnchor] = useState(dayjs());
  const [staffFilter, setStaffFilter] = useState<number | ''>('');
  const [createOpen, setCreateOpen] = useState(false);
  const [selected, setSelected] = useState<Booking | null>(null);

  const { from, to } = rangeFor(view, anchor);
  const { data: staff = [] } = useStaff(true);
  const { data: bookings = [], isLoading, isError, error, refetch } = useBookings({
    from: from.format('YYYY-MM-DD'),
    to: to.format('YYYY-MM-DD'),
    staffId: staffFilter === '' ? undefined : staffFilter,
  });

  // Bucketed by local date so each cell is a cheap lookup.
  const byDate = useMemo(() => {
    const map = new Map<string, Booking[]>();
    for (const booking of bookings) {
      const key = booking.localStartDateTime.slice(0, 10);
      const list = map.get(key);
      if (list) {
        list.push(booking);
      } else {
        map.set(key, [booking]);
      }
    }
    return map;
  }, [bookings]);

  const step = (direction: 1 | -1) => {
    const unit = view === 'day' ? 'day' : view === 'week' ? 'week' : 'month';
    setAnchor(anchor.add(direction, unit));
  };

  const title =
    view === 'month'
      ? anchor.format('MMMM YYYY')
      : view === 'week'
        ? `${from.format('D MMM')} – ${to.format('D MMM YYYY')}`
        : anchor.format('dddd, D MMMM YYYY');

  return (
    <>
      <PageHeader
        title="Calendar"
        description="Everything scheduled, in your business timezone."
        action={
          <Button variant="contained" startIcon={<AddRoundedIcon />} onClick={() => setCreateOpen(true)}>
            New booking
          </Button>
        }
      />

      <Card sx={{ mb: 2.5 }}>
        <CardContent>
          <Stack
            direction={{ xs: 'column', md: 'row' }}
            spacing={2}
            alignItems={{ xs: 'stretch', md: 'center' }}
            justifyContent="space-between"
          >
            <Stack direction="row" spacing={1} alignItems="center">
              <IconButton onClick={() => step(-1)} aria-label="Previous">
                <ChevronLeftRoundedIcon />
              </IconButton>
              <Button size="small" onClick={() => setAnchor(dayjs())}>
                Today
              </Button>
              <IconButton onClick={() => step(1)} aria-label="Next">
                <ChevronRightRoundedIcon />
              </IconButton>
              <Typography variant="h6" sx={{ ml: 1 }}>
                {title}
              </Typography>
            </Stack>

            <Stack direction="row" spacing={2} alignItems="center">
              <TextField
                select
                size="small"
                label="Staff"
                value={staffFilter}
                onChange={(event) =>
                  setStaffFilter(event.target.value === '' ? '' : Number(event.target.value))
                }
                sx={{ minWidth: 170 }}
              >
                <MenuItem value="">Everyone</MenuItem>
                {staff.map((member) => (
                  <MenuItem key={member.id} value={member.id}>
                    {member.name}
                  </MenuItem>
                ))}
              </TextField>
              <ToggleButtonGroup
                size="small"
                exclusive
                value={view}
                onChange={(_, value) => value && setView(value)}
              >
                <ToggleButton value="day">Day</ToggleButton>
                <ToggleButton value="week">Week</ToggleButton>
                <ToggleButton value="month">Month</ToggleButton>
              </ToggleButtonGroup>
            </Stack>
          </Stack>
        </CardContent>
      </Card>

      {isError ? <ErrorState error={error} onRetry={() => void refetch()} /> : null}
      {isLoading ? (
        <Card>
          <TableLoadingRows rows={6} />
        </Card>
      ) : view === 'day' ? (
        <DayView bookings={byDate.get(anchor.format('YYYY-MM-DD')) ?? []} onSelect={setSelected} />
      ) : view === 'week' ? (
        <WeekView from={from} byDate={byDate} onSelect={setSelected} />
      ) : (
        <MonthView anchor={anchor} from={from} to={to} byDate={byDate} onSelect={setSelected} />
      )}

      <BookingFormDialog
        open={createOpen}
        onClose={() => setCreateOpen(false)}
        initialDate={anchor.format('YYYY-MM-DD')}
        initialStaffId={staffFilter === '' ? null : staffFilter}
      />
      <BookingDetailsDialog booking={selected} open={selected !== null} onClose={() => setSelected(null)} />
    </>
  );
}

// --- views -----------------------------------------------------------------

function DayView({ bookings, onSelect }: { bookings: Booking[]; onSelect: (booking: Booking) => void }) {
  if (bookings.length === 0) {
    return (
      <Card>
        <CardContent>
          <Typography color="text.secondary" textAlign="center" sx={{ py: 6 }}>
            Nothing booked on this day.
          </Typography>
        </CardContent>
      </Card>
    );
  }

  return (
    <Stack spacing={1.5}>
      {bookings.map((booking) => (
        <Paper
          key={booking.id}
          variant="outlined"
          onClick={() => onSelect(booking)}
          sx={{
            p: 2,
            cursor: 'pointer',
            borderLeft: 4,
            borderLeftColor: booking.status === 'CANCELLED' ? 'error.light' : 'primary.main',
            '&:hover': { bgcolor: 'action.hover' },
          }}
        >
          <Stack direction={{ xs: 'column', sm: 'row' }} justifyContent="space-between" spacing={1}>
            <Stack spacing={0.5}>
              <Typography variant="subtitle2">
                {formatBookingWindow(booking)} · {booking.serviceName}
              </Typography>
              <Typography variant="body2" color="text.secondary">
                {[booking.customerName, booking.staffName, booking.resourceName].filter(Boolean).join(' · ')}
              </Typography>
            </Stack>
            <StatusChip status={booking.status} />
          </Stack>
        </Paper>
      ))}
    </Stack>
  );
}

function WeekView({
  from,
  byDate,
  onSelect,
}: {
  from: Dayjs;
  byDate: Map<string, Booking[]>;
  onSelect: (booking: Booking) => void;
}) {
  const days = Array.from({ length: 7 }, (_, index) => from.add(index, 'day'));

  return (
    <Box
      sx={{
        display: 'grid',
        gridTemplateColumns: { xs: '1fr', sm: 'repeat(2, 1fr)', lg: 'repeat(7, 1fr)' },
        gap: 1.5,
      }}
    >
      {days.map((day) => {
        const key = day.format('YYYY-MM-DD');
        const dayBookings = byDate.get(key) ?? [];
        const isToday = day.isSame(dayjs(), 'day');
        return (
          <Paper
            key={key}
            variant="outlined"
            sx={{ p: 1.5, minHeight: 180, borderColor: isToday ? 'primary.main' : undefined }}
          >
            <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 1 }}>
              <Typography variant="caption" color="text.secondary" fontWeight={600}>
                {day.format('ddd D MMM')}
              </Typography>
              {dayBookings.length > 0 ? <Chip size="small" label={dayBookings.length} /> : null}
            </Stack>
            <Stack spacing={0.75}>
              {dayBookings.map((booking) => (
                <BookingPill key={booking.id} booking={booking} onSelect={onSelect} />
              ))}
            </Stack>
          </Paper>
        );
      })}
    </Box>
  );
}

function MonthView({
  anchor,
  from,
  to,
  byDate,
  onSelect,
}: {
  anchor: Dayjs;
  from: Dayjs;
  to: Dayjs;
  byDate: Map<string, Booking[]>;
  onSelect: (booking: Booking) => void;
}) {
  const days: Dayjs[] = [];
  for (let cursor = from; !cursor.isAfter(to, 'day'); cursor = cursor.add(1, 'day')) {
    days.push(cursor);
  }

  return (
    <Box>
      <Box
        sx={{
          display: { xs: 'none', md: 'grid' },
          gridTemplateColumns: 'repeat(7, 1fr)',
          gap: 1,
          mb: 1,
        }}
      >
        {['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'].map((label) => (
          <Typography key={label} variant="caption" color="text.secondary" fontWeight={600} textAlign="center">
            {label}
          </Typography>
        ))}
      </Box>
      <Box
        sx={{
          display: 'grid',
          gridTemplateColumns: { xs: 'repeat(2, 1fr)', sm: 'repeat(4, 1fr)', md: 'repeat(7, 1fr)' },
          gap: 1,
        }}
      >
        {days.map((day) => {
          const key = day.format('YYYY-MM-DD');
          const dayBookings = byDate.get(key) ?? [];
          const inMonth = day.isSame(anchor, 'month');
          const isToday = day.isSame(dayjs(), 'day');
          return (
            <Paper
              key={key}
              variant="outlined"
              sx={{
                p: 1,
                minHeight: 108,
                opacity: inMonth ? 1 : 0.45,
                borderColor: isToday ? 'primary.main' : undefined,
              }}
            >
              <Typography variant="caption" fontWeight={isToday ? 700 : 500}>
                {day.format('D')}
              </Typography>
              <Stack spacing={0.5} sx={{ mt: 0.5 }}>
                {dayBookings.slice(0, 3).map((booking) => (
                  <BookingPill key={booking.id} booking={booking} onSelect={onSelect} dense />
                ))}
                {dayBookings.length > 3 ? (
                  <Typography variant="caption" color="text.secondary">
                    +{dayBookings.length - 3} more
                  </Typography>
                ) : null}
              </Stack>
            </Paper>
          );
        })}
      </Box>
    </Box>
  );
}

function BookingPill({
  booking,
  onSelect,
  dense = false,
}: {
  booking: Booking;
  onSelect: (booking: Booking) => void;
  dense?: boolean;
}) {
  const cancelled = booking.status === 'CANCELLED';
  return (
    <Tooltip title={`${booking.serviceName} · ${booking.customerName ?? ''}`}>
      <Box
        onClick={() => onSelect(booking)}
        sx={{
          px: 1,
          py: 0.5,
          borderRadius: 1,
          cursor: 'pointer',
          bgcolor: cancelled ? 'action.hover' : 'primary.main',
          color: cancelled ? 'text.disabled' : 'primary.contrastText',
          textDecoration: cancelled ? 'line-through' : 'none',
          overflow: 'hidden',
          whiteSpace: 'nowrap',
          textOverflow: 'ellipsis',
          fontSize: dense ? 11 : 12,
          fontWeight: 600,
        }}
      >
        {formatLocalTime(booking.localStartDateTime)} {booking.serviceName}
      </Box>
    </Tooltip>
  );
}
