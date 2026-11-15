import { useEffect, useState } from 'react';
import Button from '@mui/material/Button';
import Chip from '@mui/material/Chip';
import FormControlLabel from '@mui/material/FormControlLabel';
import Grid from '@mui/material/Grid';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import Switch from '@mui/material/Switch';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { errorMessage } from '@/api/client';
import { useToast } from '@/components/ToastProvider';
import { useSaveChannel } from '@/features/integrations/api';
import type { ChannelConnection } from '@/types/api';


export function ChannelCard({ channel }: { channel: ChannelConnection }) {
  const toast = useToast();
  const save = useSaveChannel();
  const name = channel.provider === 'INSTAGRAM' ? 'Instagram' : 'Facebook';
  const [accountId, setAccountId] = useState(channel.externalAccountId ?? '');
  const [displayName, setDisplayName] = useState(channel.displayName ?? '');
  const [enabled, setEnabled] = useState(channel.status === 'CONNECTED');

  useEffect(() => {
    setAccountId(channel.externalAccountId ?? '');
    setDisplayName(channel.displayName ?? '');
    setEnabled(channel.status === 'CONNECTED');
  }, [channel.externalAccountId, channel.displayName, channel.status]);

  const dirty =
    accountId !== (channel.externalAccountId ?? '') ||
    displayName !== (channel.displayName ?? '') ||
    enabled !== (channel.status === 'CONNECTED');

  const submit = async () => {
    try {
      await save.mutateAsync({
        provider: channel.provider,
        input: { externalAccountId: accountId.trim() || null, displayName: displayName.trim() || null, enabled },
      });
      toast.success(`${name} settings saved`);
    } catch (err) {
      toast.error(errorMessage(err));
    }
  };

  return (
    <Paper variant="outlined" sx={{ p: 2.5, height: '100%' }}>
      <Stack direction="row" alignItems="center" spacing={1} sx={{ mb: 0.5 }}>
        <Typography variant="subtitle1" fontWeight={700} sx={{ flex: 1 }}>
          {name}
        </Typography>
        <Chip size="small" variant="outlined" label="Manual" />
      </Stack>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
        {channel.provider === 'INSTAGRAM'
          ? 'The Instagram professional account whose direct messages n8n answers.'
          : 'The Facebook Page whose Messenger conversations n8n answers.'}{' '}
        Enter the id from Meta&apos;s developer dashboard.
      </Typography>
      <Grid container spacing={1.5}>
        <Grid item xs={12} sm={6}>
          <TextField
            fullWidth
            size="small"
            label={channel.provider === 'INSTAGRAM' ? 'Instagram account id' : 'Page id'}
            value={accountId}
            onChange={(event) => setAccountId(event.target.value)}
          />
        </Grid>
        <Grid item xs={12} sm={6}>
          <TextField
            fullWidth
            size="small"
            label="Name (optional)"
            placeholder={channel.provider === 'INSTAGRAM' ? '@serenity.studio' : 'Serenity Studio'}
            value={displayName}
            onChange={(event) => setDisplayName(event.target.value)}
          />
        </Grid>
      </Grid>
      <Stack direction="row" alignItems="center" justifyContent="space-between" sx={{ mt: 1 }}>
        <FormControlLabel
          control={<Switch checked={enabled} onChange={(event) => setEnabled(event.target.checked)} />}
          label="Take bookings here"
        />
        <Button variant="contained" disabled={!dirty || save.isPending} onClick={submit}>
          Save
        </Button>
      </Stack>
    </Paper>
  );
}
