import Grid from '@mui/material/Grid';
import { PageHeader } from '@/components/PageHeader';
import { ErrorState, LoadingState } from '@/components/states';
import { useOwnCalendar } from '@/features/business/api';
import { BlockedTimeList, BlockTimeForm } from '@/features/schedule/TimeOff';

/** A staff member's own time off: added straight into their calendar, with the manager told. */
export function TimeOffPage() {
  const { data: me, isLoading, isError, error } = useOwnCalendar(true);

  if (isLoading) {
    return <LoadingState />;
  }
  if (isError || !me) {
    return <ErrorState error={error} />;
  }
  return (
    <>
      <PageHeader
        title="My time off"
        description="Days off, appointments, holidays. Customers cannot book you in this time; bookings you already have are passed to your manager to move."
      />
      <Grid container spacing={3}>
        <Grid item xs={12} md={5}>
          <BlockTimeForm ownerView={false} ownResourceId={me.resourceId} />
        </Grid>
        <Grid item xs={12} md={7}>
          <BlockedTimeList ownerView={false} />
        </Grid>
      </Grid>
    </>
  );
}
