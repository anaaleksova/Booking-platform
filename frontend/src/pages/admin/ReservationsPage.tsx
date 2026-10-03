import { useMemo, useState } from 'react';
import dayjs from 'dayjs';
import isoWeek from 'dayjs/plugin/isoWeek';
import Button from '@mui/material/Button';
import Chip from '@mui/material/Chip';
import IconButton from '@mui/material/IconButton';
import MenuItem from '@mui/material/MenuItem';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import TextField from '@mui/material/TextField';
import ToggleButton from '@mui/material/ToggleButton';
import ToggleButtonGroup from '@mui/material/ToggleButtonGroup';
import Typography from '@mui/material/Typography';
import AddRoundedIcon from '@mui/icons-material/AddRounded';
import CalendarViewWeekRoundedIcon from '@mui/icons-material/CalendarViewWeekRounded';
import ChevronLeftRoundedIcon from '@mui/icons-material/ChevronLeftRounded';
import ChevronRightRoundedIcon from '@mui/icons-material/ChevronRightRounded';
import EventNoteRoundedIcon from '@mui/icons-material/EventNoteRounded';
import ViewListRoundedIcon from '@mui/icons-material/ViewListRounded';
import { PageHeader } from '@/components/PageHeader';
import { StatusChip } from '@/components/StatusChip';
import { EmptyState, ErrorState, TableLoadingRows } from '@/components/states';
import { useServices } from '@/features/catalog/api';
import { useReservations } from '@/features/reservations/api';
import { NewReservationDialog } from '@/features/reservations/NewReservationDialog';
import { ReservationDetailsDialog } from '@/features/reservations/ReservationDetailsDialog';
import { WeekCalendar } from '@/features/reservations/WeekCalendar';
import type { ReservationStatus } from '@/types/api';
import { STATUS_LABELS, formatLocalDate, formatMoney, formatWindow } from '@/utils/format';

dayjs.extend(isoWeek);

type View = 'list' | 'calendar';

export function ReservationsPage() {
  const [view, setView] = useState<View>('list');
  const [from, setFrom] = useState(dayjs().format('YYYY-MM-DD'));
  const [to, setTo] = useState(dayjs().add(13, 'day').format('YYYY-MM-DD'));
  const [weekStart, setWeekStart] = useState(dayjs().startOf('isoWeek'));
  const [status, setStatus] = useState<ReservationStatus | ''>('');
  const [serviceId, setServiceId] = useState<number | ''>('');
  const [search, setSearch] = useState('');
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [creating, setCreating] = useState(false);

  const { data: services } = useServices();
  const filters =
    view === 'list'
      ? { from, to, status: status || undefined, serviceId: serviceId || undefined }
      : {
          from: weekStart.format('YYYY-MM-DD'),
          to: weekStart.add(6, 'day').format('YYYY-MM-DD'),
          status: status || undefined,
          serviceId: serviceId || undefined,
        };
  const { data, isLoading, isError, error, refetch } = useReservations(filters);

  const visible = useMemo(() => {
    const term = search.trim().toLowerCase();
    if (!term) {
      return data ?? [];
    }
    return (data ?? []).filter((reservation) =>
      [reservation.customerName, reservation.customerEmail, reservation.serviceName, ...reservation.resources.map((r) => r.name)]
        .filter(Boolean)
        .some((value) => value!.toLowerCase().includes(term)),
    );
  }, [data, search]);

  return (
    <>
      <PageHeader
        title="Reservations"
        description="Every reservation with its assigned resources, price and status."
        action={
          <Button variant="contained" startIcon={<AddRoundedIcon />} onClick={() => setCreating(true)}>
            New reservation
          </Button>
        }
      />

      <Paper variant="outlined" sx={{ p: 2, mb: 2 }}>
        <Stack direction={{ xs: 'column', md: 'row' }} spacing={1.5} alignItems={{ md: 'center' }} useFlexGap flexWrap="wrap">
          <ToggleButtonGroup size="small" exclusive value={view} onChange={(_, value) => value && setView(value)}>
            <ToggleButton value="list">
              <ViewListRoundedIcon fontSize="small" sx={{ mr: 0.5 }} /> List
            </ToggleButton>
            <ToggleButton value="calendar">
              <CalendarViewWeekRoundedIcon fontSize="small" sx={{ mr: 0.5 }} /> Calendar
            </ToggleButton>
          </ToggleButtonGroup>

          {view === 'list' ? (
            <Stack direction="row" spacing={1}>
              <TextField type="date" label="From" value={from} InputLabelProps={{ shrink: true }} onChange={(e) => e.target.value && setFrom(e.target.value)} />
              <TextField type="date" label="To" value={to} InputLabelProps={{ shrink: true }} onChange={(e) => e.target.value && setTo(e.target.value)} />
            </Stack>
          ) : (
            <Stack direction="row" spacing={1} alignItems="center">
              <IconButton aria-label="Previous week" onClick={() => setWeekStart(weekStart.subtract(1, 'week'))}>
                <ChevronLeftRoundedIcon />
              </IconButton>
              <Button size="small" onClick={() => setWeekStart(dayjs().startOf('isoWeek'))}>
                This week
              </Button>
              <IconButton aria-label="Next week" onClick={() => setWeekStart(weekStart.add(1, 'week'))}>
                <ChevronRightRoundedIcon />
              </IconButton>
              <Typography variant="subtitle2">
                {weekStart.format('D MMM')} – {weekStart.add(6, 'day').format('D MMM YYYY')}
              </Typography>
            </Stack>
          )}

          <TextField select label="Status" value={status} onChange={(e) => setStatus(e.target.value as ReservationStatus | '')} sx={{ minWidth: 150 }}>
            <MenuItem value="">All statuses</MenuItem>
            {(Object.keys(STATUS_LABELS) as ReservationStatus[]).map((value) => (
              <MenuItem key={value} value={value}>
                {STATUS_LABELS[value]}
              </MenuItem>
            ))}
          </TextField>
          <TextField select label="Service" value={serviceId} onChange={(e) => setServiceId(e.target.value === '' ? '' : Number(e.target.value))} sx={{ minWidth: 170 }}>
            <MenuItem value="">All services</MenuItem>
            {(services ?? []).map((service) => (
              <MenuItem key={service.id} value={service.id}>
                {service.name}
              </MenuItem>
            ))}
          </TextField>
          <TextField label="Search" placeholder="Customer, service, resource" value={search} onChange={(e) => setSearch(e.target.value)} sx={{ flex: 1, minWidth: 200 }} />
        </Stack>
      </Paper>

      {isError ? <ErrorState error={error} onRetry={() => refetch()} /> : null}

      {view === 'calendar' ? (
        isLoading ? (
          <TableLoadingRows rows={8} />
        ) : (
          <WeekCalendar weekStart={weekStart} reservations={visible} onSelect={(reservation) => setSelectedId(reservation.id)} />
        )
      ) : isLoading ? (
        <Paper variant="outlined">
          <TableLoadingRows rows={6} />
        </Paper>
      ) : visible.length === 0 ? (
        <EmptyState
          icon={<EventNoteRoundedIcon />}
          title="No reservations in this range"
          description="Try another date range or filter, or add a reservation taken by phone."
          action={
            <Button variant="outlined" onClick={() => setCreating(true)}>
              New reservation
            </Button>
          }
        />
      ) : (
        <TableContainer component={Paper} variant="outlined">
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>Date & time</TableCell>
                <TableCell>Customer</TableCell>
                <TableCell>Service</TableCell>
                <TableCell align="center">Guests</TableCell>
                <TableCell sx={{ display: { xs: 'none', lg: 'table-cell' } }}>Resources</TableCell>
                <TableCell align="right">Price</TableCell>
                <TableCell>Status</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {visible.map((reservation) => (
                <TableRow key={reservation.id} hover sx={{ cursor: 'pointer' }} onClick={() => setSelectedId(reservation.id)}>
                  <TableCell sx={{ whiteSpace: 'nowrap' }}>
                    <Typography variant="body2" fontWeight={600}>
                      {formatLocalDate(reservation.localStartTime)}
                    </Typography>
                    <Typography variant="caption" color="text.secondary">
                      {formatWindow(reservation)}
                    </Typography>
                  </TableCell>
                  <TableCell>
                    <Typography variant="body2">{reservation.customerName}</Typography>
                    <Typography variant="caption" color="text.secondary">
                      {reservation.customerEmail ?? reservation.customerPhone}
                    </Typography>
                  </TableCell>
                  <TableCell>{reservation.serviceName}</TableCell>
                  <TableCell align="center">{reservation.numberOfParticipants}</TableCell>
                  <TableCell sx={{ display: { xs: 'none', lg: 'table-cell' } }}>
                    <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap">
                      {reservation.resources.map((resource) => (
                        <Chip key={resource.resourceId} size="small" variant="outlined" label={resource.name} />
                      ))}
                    </Stack>
                  </TableCell>
                  <TableCell align="right" sx={{ whiteSpace: 'nowrap' }}>
                    <Typography variant="body2" fontWeight={600}>
                      {formatMoney(reservation.finalPrice, reservation.currency)}
                    </Typography>
                    {reservation.discountAmount > 0 ? (
                      <Typography variant="caption" color="text.secondary">
                        − {formatMoney(reservation.discountAmount, reservation.currency)} {reservation.discountCode}
                      </Typography>
                    ) : null}
                  </TableCell>
                  <TableCell>
                    <StatusChip status={reservation.status} />
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      )}

      <ReservationDetailsDialog id={selectedId} onClose={() => setSelectedId(null)} />
      <NewReservationDialog open={creating} onClose={() => setCreating(false)} />
    </>
  );
}
