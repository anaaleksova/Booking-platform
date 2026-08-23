import { useState } from 'react';
import { Link as RouterLink, useParams } from 'react-router-dom';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Grid from '@mui/material/Grid';
import Stack from '@mui/material/Stack';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import Typography from '@mui/material/Typography';
import ArrowBackRoundedIcon from '@mui/icons-material/ArrowBackRounded';
import { PageHeader } from '@/components/PageHeader';
import { EmptyState, ErrorState, LoadingState } from '@/components/states';
import { StatusChip } from '@/components/StatusChip';
import { BookingDetailsDialog } from '@/features/bookings/BookingDetailsDialog';
import { useCustomer, useCustomerHistory } from '@/features/customers/useCustomers';
import { formatBookingWindow, formatLocalDate, formatMoney } from '@/utils/format';
import type { Booking } from '@/types/api';

/** One customer's whole relationship with this business, in one screen. */
export function CustomerDetailPage() {
  const { id } = useParams();
  const customerId = Number(id);
  const { data: customer } = useCustomer(customerId);
  const { data: history, isLoading, isError, error, refetch } = useCustomerHistory(customerId);
  const [selected, setSelected] = useState<Booking | null>(null);

  return (
    <>
      <Button
        component={RouterLink}
        to="/dashboard/customers"
        startIcon={<ArrowBackRoundedIcon />}
        sx={{ mb: 2 }}
      >
        All customers
      </Button>

      <PageHeader
        title={customer?.name ?? history?.customerName ?? 'Customer'}
        description={[customer?.phone, customer?.email].filter(Boolean).join(' · ')}
      />

      {isError ? <ErrorState error={error} onRetry={() => void refetch()} /> : null}
      {isLoading ? <LoadingState /> : null}

      {history ? (
        <>
          <Grid container spacing={2.5} sx={{ mb: 3 }}>
            <Summary label="Total" value={history.totalAppointments} />
            <Summary label="Completed" value={history.completedAppointments} />
            <Summary label="Cancelled" value={history.cancelledAppointments} />
            <Summary label="No-shows" value={history.noShowAppointments} />
            <Summary label="Upcoming" value={history.upcomingAppointments} />
          </Grid>

          <Grid container spacing={2.5} sx={{ mb: 3 }}>
            <Summary label="Total spent" value={formatMoney(history.totalSpent)} />
            <Summary label="Average booking value" value={formatMoney(history.averageBookingValue)} />
            <Summary label="Favourite service" value={history.favouriteServiceName ?? '—'} />
          </Grid>

          {customer?.notes ? (
            <Card sx={{ mb: 3 }}>
              <CardContent>
                <Typography variant="subtitle2" gutterBottom>
                  Notes
                </Typography>
                <Typography variant="body2" sx={{ whiteSpace: 'pre-wrap' }}>
                  {customer.notes}
                </Typography>
              </CardContent>
            </Card>
          ) : null}

          <Card>
            {history.bookings.length === 0 ? (
              <EmptyState title="No appointments yet" description="This customer has not booked anything." />
            ) : (
              <TableContainer>
                <Table size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell>Date</TableCell>
                      <TableCell>Time</TableCell>
                      <TableCell>Service</TableCell>
                      <TableCell>Staff</TableCell>
                      <TableCell>Price</TableCell>
                      <TableCell>Status</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {history.bookings.map((booking) => (
                      <TableRow
                        key={booking.id}
                        hover
                        sx={{ cursor: 'pointer' }}
                        onClick={() => setSelected(booking)}
                      >
                        <TableCell>{formatLocalDate(booking.localStartDateTime)}</TableCell>
                        <TableCell>{formatBookingWindow(booking)}</TableCell>
                        <TableCell>{booking.serviceName}</TableCell>
                        <TableCell>{booking.staffName ?? '—'}</TableCell>
                        <TableCell>{formatMoney(booking.price, booking.currency)}</TableCell>
                        <TableCell>
                          <StatusChip status={booking.status} />
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
            )}
          </Card>
        </>
      ) : null}

      <BookingDetailsDialog booking={selected} open={selected !== null} onClose={() => setSelected(null)} />
    </>
  );
}

function Summary({ label, value }: { label: string; value: number | string }) {
  return (
    <Grid item xs={6} sm={4} md={2.4}>
      <Card sx={{ height: '100%' }}>
        <CardContent>
          <Stack spacing={0.5}>
            <Typography variant="body2" color="text.secondary">
              {label}
            </Typography>
            <Typography variant="h5">{value}</Typography>
          </Stack>
        </CardContent>
      </Card>
    </Grid>
  );
}
