import { useState } from 'react';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import MenuItem from '@mui/material/MenuItem';
import Stack from '@mui/material/Stack';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import AddRoundedIcon from '@mui/icons-material/AddRounded';
import EventBusyRoundedIcon from '@mui/icons-material/EventBusyRounded';
import dayjs from 'dayjs';
import { DatePicker } from '@mui/x-date-pickers/DatePicker';
import { PageHeader } from '@/components/PageHeader';
import { EmptyState, ErrorState, TableLoadingRows } from '@/components/states';
import { StatusChip } from '@/components/StatusChip';
import { BookingFormDialog } from '@/features/bookings/BookingFormDialog';
import { BookingDetailsDialog } from '@/features/bookings/BookingDetailsDialog';
import { useBookings } from '@/features/bookings/useBookings';
import { useStaff } from '@/features/staff/useStaff';
import { formatBookingWindow, formatLocalDate, formatMoney } from '@/utils/format';
import type { Booking, BookingStatus } from '@/types/api';

const STATUSES: BookingStatus[] = ['CONFIRMED', 'COMPLETED', 'CANCELLED', 'NO_SHOW', 'PENDING'];

export function BookingsPage() {
  const [from, setFrom] = useState(dayjs().startOf('month'));
  const [to, setTo] = useState(dayjs().endOf('month'));
  const [status, setStatus] = useState<BookingStatus | ''>('');
  const [staffId, setStaffId] = useState<number | ''>('');
  const [createOpen, setCreateOpen] = useState(false);
  const [selected, setSelected] = useState<Booking | null>(null);

  const { data: staff = [] } = useStaff();
  const { data: bookings = [], isLoading, isError, error, refetch } = useBookings({
    from: from.format('YYYY-MM-DD'),
    to: to.format('YYYY-MM-DD'),
    status: status === '' ? undefined : status,
    staffId: staffId === '' ? undefined : staffId,
  });

  return (
    <>
      <PageHeader
        title="Bookings"
        description="Search and manage every appointment."
        action={
          <Button variant="contained" startIcon={<AddRoundedIcon />} onClick={() => setCreateOpen(true)}>
            New booking
          </Button>
        }
      />

      <Card sx={{ mb: 2.5 }}>
        <CardContent>
          <Stack direction={{ xs: 'column', md: 'row' }} spacing={2}>
            <DatePicker
              label="From"
              value={from}
              onChange={(value) => value && setFrom(value)}
              slotProps={{ textField: { size: 'small' } }}
            />
            <DatePicker
              label="To"
              value={to}
              onChange={(value) => value && setTo(value)}
              slotProps={{ textField: { size: 'small' } }}
            />
            <TextField
              select
              size="small"
              label="Status"
              value={status}
              onChange={(event) => setStatus(event.target.value as BookingStatus | '')}
              sx={{ minWidth: 160 }}
            >
              <MenuItem value="">All statuses</MenuItem>
              {STATUSES.map((value) => (
                <MenuItem key={value} value={value}>
                  {value.charAt(0) + value.slice(1).toLowerCase().replace('_', '-')}
                </MenuItem>
              ))}
            </TextField>
            <TextField
              select
              size="small"
              label="Staff"
              value={staffId}
              onChange={(event) => setStaffId(event.target.value === '' ? '' : Number(event.target.value))}
              sx={{ minWidth: 170 }}
            >
              <MenuItem value="">Everyone</MenuItem>
              {staff.map((member) => (
                <MenuItem key={member.id} value={member.id}>
                  {member.name}
                </MenuItem>
              ))}
            </TextField>
          </Stack>
        </CardContent>
      </Card>

      {isError ? <ErrorState error={error} onRetry={() => void refetch()} /> : null}

      <Card>
        {isLoading ? (
          <TableLoadingRows rows={6} />
        ) : bookings.length === 0 ? (
          <EmptyState
            title="No bookings in this range"
            description="Adjust the filters, or take a booking by hand."
            icon={<EventBusyRoundedIcon />}
            action={
              <Button variant="contained" onClick={() => setCreateOpen(true)}>
                New booking
              </Button>
            }
          />
        ) : (
          <TableContainer>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>Date</TableCell>
                  <TableCell>Time</TableCell>
                  <TableCell>Service</TableCell>
                  <TableCell>Customer</TableCell>
                  <TableCell>Staff</TableCell>
                  <TableCell>Price</TableCell>
                  <TableCell>Status</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {bookings.map((booking) => (
                  <TableRow
                    key={booking.id}
                    hover
                    onClick={() => setSelected(booking)}
                    sx={{ cursor: 'pointer' }}
                  >
                    <TableCell>{formatLocalDate(booking.localStartDateTime)}</TableCell>
                    <TableCell>{formatBookingWindow(booking)}</TableCell>
                    <TableCell>
                      <Typography variant="body2" fontWeight={600}>
                        {booking.serviceName}
                      </Typography>
                      {booking.resourceName ? (
                        <Typography variant="caption" color="text.secondary">
                          {booking.resourceName}
                        </Typography>
                      ) : null}
                    </TableCell>
                    <TableCell>{booking.customerName}</TableCell>
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

      <BookingFormDialog open={createOpen} onClose={() => setCreateOpen(false)} />
      <BookingDetailsDialog booking={selected} open={selected !== null} onClose={() => setSelected(null)} />
    </>
  );
}
