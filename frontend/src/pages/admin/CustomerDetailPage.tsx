import { useState } from 'react';
import dayjs from 'dayjs';
import { Link as RouterLink, useParams } from 'react-router-dom';
import Button from '@mui/material/Button';
import Grid from '@mui/material/Grid';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import Typography from '@mui/material/Typography';
import ArrowBackRoundedIcon from '@mui/icons-material/ArrowBackRounded';
import { KpiCard } from '@/components/KpiCard';
import { PageHeader } from '@/components/PageHeader';
import { StatusChip } from '@/components/StatusChip';
import { EmptyState, ErrorState, LoadingState } from '@/components/states';
import { useBusiness } from '@/features/business/api';
import { useCustomer, useCustomerHistory } from '@/features/customers/api';
import { ReservationDetailsDialog } from '@/features/reservations/ReservationDetailsDialog';
import { formatDaysAgo, formatLocalDate, formatMoney, formatWindow } from '@/utils/format';

export function CustomerDetailPage() {
  const id = Number(useParams().id);
  const { data: business } = useBusiness();
  const customer = useCustomer(id);
  const history = useCustomerHistory(id);
  const [selected, setSelected] = useState<number | null>(null);

  if (customer.isLoading || history.isLoading) {
    return <LoadingState />;
  }
  if (customer.isError || history.isError) {
    return <ErrorState error={customer.error ?? history.error} />;
  }
  const person = customer.data!;
  const stats = history.data!;
  const currency = business?.currency;

  return (
    <>
      <Button component={RouterLink} to="/admin/customers" startIcon={<ArrowBackRoundedIcon />} sx={{ mb: 1 }}>
        Customers
      </Button>
      <PageHeader
        title={person.fullName}
        description={[person.email, person.phone].filter(Boolean).join(' · ')}
      />

      <Grid container spacing={2} sx={{ mb: 3 }}>
        <Grid item xs={6} md={3}>
          <KpiCard label="Reservations" value={String(stats.totalReservations)} note={`${stats.upcomingReservations} upcoming`} />
        </Grid>
        <Grid item xs={6} md={3}>
          <KpiCard label="Total spent" value={formatMoney(stats.totalSpent, currency)} note={`Average ${formatMoney(stats.averageReservationValue, currency)}`} />
        </Grid>
        <Grid item xs={6} md={3}>
          <KpiCard
            label="Last visit"
            value={stats.lastVisitAt ? dayjs(stats.lastVisitAt).format('D MMM YYYY') : '—'}
            note={stats.lastVisitAt ? formatDaysAgo(stats.lastVisitAt) : undefined}
          />
        </Grid>
        <Grid item xs={6} md={3}>
          <KpiCard label="Favourite service" value={stats.favouriteServiceName ?? '—'} note={`${stats.cancelledReservations} cancelled`} />
        </Grid>
      </Grid>

      {person.notes ? (
        <Paper variant="outlined" sx={{ p: 2, mb: 3 }}>
          <Typography variant="caption" color="text.secondary" fontWeight={600}>
            Notes
          </Typography>
          <Typography variant="body2">{person.notes}</Typography>
        </Paper>
      ) : null}

      <Typography variant="h6" sx={{ mb: 1.5 }}>
        Reservation history
      </Typography>
      {stats.reservations.length === 0 ? (
        <EmptyState title="No reservations yet" />
      ) : (
        <TableContainer component={Paper} variant="outlined">
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>Date</TableCell>
                <TableCell>Service</TableCell>
                <TableCell align="center">Guests</TableCell>
                <TableCell sx={{ display: { xs: 'none', md: 'table-cell' } }}>Resources</TableCell>
                <TableCell align="right">Price</TableCell>
                <TableCell>Status</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {stats.reservations.map((reservation) => (
                <TableRow key={reservation.id} hover sx={{ cursor: 'pointer' }} onClick={() => setSelected(reservation.id)}>
                  <TableCell>
                    <Stack>
                      <Typography variant="body2">{formatLocalDate(reservation.localStartTime)}</Typography>
                      <Typography variant="caption" color="text.secondary">
                        {formatWindow(reservation)}
                      </Typography>
                    </Stack>
                  </TableCell>
                  <TableCell>{reservation.serviceName}</TableCell>
                  <TableCell align="center">{reservation.numberOfParticipants}</TableCell>
                  <TableCell sx={{ display: { xs: 'none', md: 'table-cell' } }}>
                    {reservation.resources.map((resource) => resource.name).join(', ')}
                  </TableCell>
                  <TableCell align="right">{formatMoney(reservation.finalPrice, reservation.currency)}</TableCell>
                  <TableCell>
                    <StatusChip status={reservation.status} />
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      )}
      <ReservationDetailsDialog id={selected} onClose={() => setSelected(null)} />
    </>
  );
}
