import { Link as RouterLink } from 'react-router-dom';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Divider from '@mui/material/Divider';
import Grid from '@mui/material/Grid';
import List from '@mui/material/List';
import ListItem from '@mui/material/ListItem';
import ListItemText from '@mui/material/ListItemText';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import AddRoundedIcon from '@mui/icons-material/AddRounded';
import EventAvailableRoundedIcon from '@mui/icons-material/EventAvailableRounded';
import { PageHeader } from '@/components/PageHeader';
import { EmptyState, ErrorState, LoadingState } from '@/components/states';
import { StatusChip } from '@/components/StatusChip';
import { useDashboardStats } from '@/features/bookings/useBookings';
import { formatBookingWindow, formatMoney } from '@/utils/format';

export function DashboardHomePage() {
  const { data, isLoading, isError, error, refetch } = useDashboardStats();

  return (
    <>
      <PageHeader
        title="Dashboard"
        description="Today at a glance."
        action={
          <Button component={RouterLink} to="/dashboard/calendar" variant="contained" startIcon={<AddRoundedIcon />}>
            New booking
          </Button>
        }
      />

      {isLoading ? <LoadingState /> : null}
      {isError ? <ErrorState error={error} onRetry={() => void refetch()} /> : null}

      {data ? (
        <>
          <Grid container spacing={2.5} sx={{ mb: 3 }}>
            <StatCard label="Today's bookings" value={data.todayBookings} />
            <StatCard label="Upcoming" value={data.upcomingBookings} />
            <StatCard label="Completed (30 days)" value={data.completedBookings} />
            <StatCard label="Cancelled (30 days)" value={data.cancelledBookings} />
          </Grid>

          <Card>
            <CardContent>
              <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 1 }}>
                <Typography variant="h6">Today's schedule</Typography>
                {data.todayRevenue !== null && data.todayRevenue > 0 ? (
                  <Typography variant="body2" color="text.secondary">
                    Expected value: {formatMoney(data.todayRevenue, data.currency)}
                  </Typography>
                ) : null}
              </Stack>
              <Divider />
              {data.todaySchedule.length === 0 ? (
                <EmptyState
                  title="Nothing booked today"
                  description="Bookings taken from your public page or added by hand will appear here."
                  icon={<EventAvailableRoundedIcon />}
                  action={
                    <Button component={RouterLink} to="/dashboard/calendar" variant="contained">
                      Add a booking
                    </Button>
                  }
                />
              ) : (
                <List disablePadding>
                  {data.todaySchedule.map((booking) => (
                    <ListItem key={booking.id} divider secondaryAction={<StatusChip status={booking.status} />}>
                      <ListItemText
                        primary={
                          <Typography variant="body2" fontWeight={600}>
                            {formatBookingWindow(booking)} · {booking.serviceName}
                          </Typography>
                        }
                        secondary={[booking.customerName, booking.staffName, booking.resourceName]
                          .filter(Boolean)
                          .join(' · ')}
                      />
                    </ListItem>
                  ))}
                </List>
              )}
            </CardContent>
          </Card>
        </>
      ) : null}
    </>
  );
}

function StatCard({ label, value }: { label: string; value: number }) {
  return (
    <Grid item xs={6} md={3}>
      <Card sx={{ height: '100%' }}>
        <CardContent>
          <Typography variant="body2" color="text.secondary" gutterBottom>
            {label}
          </Typography>
          <Typography variant="h4">{value}</Typography>
        </CardContent>
      </Card>
    </Grid>
  );
}
