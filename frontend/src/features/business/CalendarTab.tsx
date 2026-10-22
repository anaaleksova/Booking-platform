import Alert from '@mui/material/Alert';
import Paper from '@mui/material/Paper';
import Typography from '@mui/material/Typography';
import { LoadingState } from '@/components/states';
import { useBusinessSettings } from '@/features/business/api';

/**
 * How reservations reach the owner's calendar: the new / moved / cancelled
 * emails they already get are calendar invitations, which Gmail and Outlook
 * put in the calendar by themselves. Nothing to connect or subscribe to.
 */
export function CalendarTab() {
  const { data: settings, isLoading } = useBusinessSettings();

  if (isLoading || !settings) {
    return <LoadingState />;
  }
  const address = settings.notificationEmail?.trim() || 'your login email';

  return (
    <Paper variant="outlined" sx={{ p: 3, maxWidth: 820 }}>
      <Typography variant="subtitle1" fontWeight={700}>
        Your reservations in your calendar
      </Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
        Every new reservation — from the website, Instagram, Facebook or the admin panel — is emailed to{' '}
        <strong>{address}</strong> as a calendar invitation, with the customer's name, phone and what they booked. When a
        reservation moves, the event moves; when it is cancelled, the event is removed.
      </Typography>

      {!settings.emailNotificationsEnabled ? (
        <Alert severity="warning" sx={{ mt: 2 }}>
          Email confirmations are switched off in Booking rules, so nothing is sent to your calendar. Switch them on to
          use this.
        </Alert>
      ) : null}

      <Typography variant="subtitle2" fontWeight={700} sx={{ mt: 3 }}>
        Make it automatic — once
      </Typography>
      <Typography variant="body2" component="div" color="text.secondary">
        <ul style={{ margin: '6px 0 0', paddingLeft: 20, lineHeight: 1.7 }}>
          <li>
            <strong>Gmail / Google Calendar:</strong> in Google Calendar open Settings → Event settings →{' '}
            <em>Add invitations to my calendar</em> and choose <em>From everyone</em>. Then each reservation appears
            without you having to answer the email.
          </li>
          <li>
            <strong>Outlook:</strong> nothing to do — invitations go into your calendar as they arrive.
          </li>
          <li>
            <strong>Other email (e.g. iCloud):</strong> your calendar may ask you to accept each reservation.
          </li>
        </ul>
      </Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mt: 2 }}>
        To send them to another address, change <em>Send new reservations to</em> in Booking rules.
      </Typography>
    </Paper>
  );
}
