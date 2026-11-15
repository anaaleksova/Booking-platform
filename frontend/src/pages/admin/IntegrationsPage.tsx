import Grid from '@mui/material/Grid';
import FacebookRoundedIcon from '@mui/icons-material/FacebookRounded';
import InstagramIcon from '@mui/icons-material/Instagram';
import { PageHeader } from '@/components/PageHeader';
import { ErrorState, LoadingState } from '@/components/states';
import { useIntegrations } from '@/features/integrations/api';
import { ChannelCard } from '@/features/integrations/components/ChannelCard';
import { StatusTile } from '@/features/integrations/components/IntegrationFields';
import { MetaConnectCard } from '@/features/integrations/components/MetaConnectCard';

/**
 * Where a business connects the Facebook Page and Instagram account customers
 * message it on. The platform's own automation answers those chats, so an
 * owner has nothing else to set up here.
 */
export function IntegrationsPage() {
  const { data, isLoading, isError, error, refetch } = useIntegrations();

  if (isError) {
    return <ErrorState error={error} onRetry={() => refetch()} />;
  }
  if (isLoading || !data) {
    return <LoadingState />;
  }

  const instagram = data.channels.find((channel) => channel.provider === 'INSTAGRAM');
  const facebook = data.channels.find((channel) => channel.provider === 'FACEBOOK');

  return (
    <>
      <PageHeader
        title="Integrations"
        description="Take reservations from Instagram and Facebook Messenger, and share your events there."
      />

      <Grid container spacing={2} sx={{ mb: 3 }}>
        <Grid item xs={12} sm={6}>
          <StatusTile icon={<InstagramIcon />} name="Instagram" status={instagram?.status ?? 'NOT_CONNECTED'} />
        </Grid>
        <Grid item xs={12} sm={6}>
          <StatusTile icon={<FacebookRoundedIcon />} name="Facebook" status={facebook?.status ?? 'NOT_CONNECTED'} />
        </Grid>
      </Grid>

      <Grid container spacing={2}>
        <Grid item xs={12}>
          <MetaConnectCard meta={data.meta} />
        </Grid>
        {/* Without the platform's Facebook app, entering the ids by hand is the only way in. */}
        {data.meta.available || data.meta.connected ? null : (
          <>
            <Grid item xs={12} md={6}>
              {instagram ? <ChannelCard channel={instagram} /> : null}
            </Grid>
            <Grid item xs={12} md={6}>
              {facebook ? <ChannelCard channel={facebook} /> : null}
            </Grid>
          </>
        )}
      </Grid>
    </>
  );
}
