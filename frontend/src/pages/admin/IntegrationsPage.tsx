import Alert from '@mui/material/Alert';
import Grid from '@mui/material/Grid';
import Paper from '@mui/material/Paper';
import Typography from '@mui/material/Typography';
import FacebookRoundedIcon from '@mui/icons-material/FacebookRounded';
import HubRoundedIcon from '@mui/icons-material/HubRounded';
import InstagramIcon from '@mui/icons-material/Instagram';
import { PageHeader } from '@/components/PageHeader';
import { ErrorState, LoadingState } from '@/components/states';
import { useIntegrations } from '@/features/integrations/api';
import { ChannelCard } from '@/features/integrations/components/ChannelCard';
import { DeliveriesCard } from '@/features/integrations/components/DeliveriesCard';
import { CopyField, StatusTile } from '@/features/integrations/components/IntegrationFields';
import { MetaConnectCard } from '@/features/integrations/components/MetaConnectCard';
import { N8nCard } from '@/features/integrations/components/N8nCard';
import { TokensCard } from '@/features/integrations/components/TokensCard';

/**
 * Where a business connects its automation layer (n8n) and the social
 * channels customers message it on. The automation layer only ever talks to
 * the integration API with a scoped token; booking rules stay in the platform.
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
        description="Take reservations from Instagram and Facebook, and automate confirmations and reminders with n8n."
      />

      <Grid container spacing={2} sx={{ mb: 3 }}>
        <Grid item xs={12} sm={4}>
          <StatusTile icon={<InstagramIcon />} name="Instagram" status={instagram?.status ?? 'NOT_CONNECTED'} />
        </Grid>
        <Grid item xs={12} sm={4}>
          <StatusTile icon={<FacebookRoundedIcon />} name="Facebook" status={facebook?.status ?? 'NOT_CONNECTED'} />
        </Grid>
        <Grid item xs={12} sm={4}>
          <StatusTile icon={<HubRoundedIcon />} name="n8n" status={data.n8n.status} />
        </Grid>
      </Grid>

      <Grid container spacing={2}>
        <Grid item xs={12} lg={6}>
          <N8nCard n8n={data.n8n} />
        </Grid>
        <Grid item xs={12} lg={6}>
          <Paper variant="outlined" sx={{ p: 2.5, height: '100%' }}>
            <Typography variant="subtitle1" fontWeight={700}>
              Connection details for n8n
            </Typography>
            <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
              Use these in the n8n credential and workflow configuration.
            </Typography>
            <CopyField label="Integration API base URL" value={data.apiBaseUrl} />
            <CopyField label="Business ID" value={String(data.businessId)} />
            <Alert severity="info" variant="outlined" sx={{ mt: 1 }}>
              {data.meta.connected
                ? 'The platform answers your Instagram and Messenger chats; your Page’s access is kept encrypted and never shown here.'
                : 'Connect with Facebook below and the platform answers your chats — you only need your own n8n for custom automations.'}
            </Alert>
          </Paper>
        </Grid>
        <Grid item xs={12}>
          <MetaConnectCard meta={data.meta} />
        </Grid>
        {data.meta.connected ? null : (
          <>
            {data.meta.available ? (
              <Grid item xs={12}>
                <Typography variant="subtitle2" color="text.secondary">
                  Or enter the ids by hand (advanced)
                </Typography>
              </Grid>
            ) : null}
            <Grid item xs={12} md={6}>
              {instagram ? <ChannelCard channel={instagram} /> : null}
            </Grid>
            <Grid item xs={12} md={6}>
              {facebook ? <ChannelCard channel={facebook} /> : null}
            </Grid>
          </>
        )}

        <Grid item xs={12}>
          <TokensCard tokens={data.tokens} availablePermissions={data.availablePermissions} />
        </Grid>

        <Grid item xs={12}>
          <DeliveriesCard events={data.recentEvents} />
        </Grid>
      </Grid>
    </>
  );
}
