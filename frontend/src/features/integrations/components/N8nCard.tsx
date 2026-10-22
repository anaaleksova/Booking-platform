import { useEffect, useState } from 'react';
import Button from '@mui/material/Button';
import FormControlLabel from '@mui/material/FormControlLabel';
import IconButton from '@mui/material/IconButton';
import InputAdornment from '@mui/material/InputAdornment';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import Switch from '@mui/material/Switch';
import TextField from '@mui/material/TextField';
import Tooltip from '@mui/material/Tooltip';
import Typography from '@mui/material/Typography';
import ContentCopyRoundedIcon from '@mui/icons-material/ContentCopyRounded';
import VisibilityOffRoundedIcon from '@mui/icons-material/VisibilityOffRounded';
import VisibilityRoundedIcon from '@mui/icons-material/VisibilityRounded';
import { errorMessage } from '@/api/client';
import { ConfirmDialog } from '@/components/ConfirmDialog';
import { useToast } from '@/components/ToastProvider';
import { useRotateSigningSecret, useSaveN8n, useSendTestEvent } from '@/features/integrations/api';
import type { N8nConnection } from '@/types/api';
import { copyToClipboard } from './IntegrationFields';

/** The n8n webhook: where events go, its signing secret, and a test delivery. */
export function N8nCard({ n8n }: { n8n: N8nConnection }) {
  const toast = useToast();
  const save = useSaveN8n();
  const rotate = useRotateSigningSecret();
  const test = useSendTestEvent();
  const [url, setUrl] = useState(n8n.webhookUrl ?? '');
  const [enabled, setEnabled] = useState(n8n.status === 'CONNECTED');
  const [showSecret, setShowSecret] = useState(false);
  const [confirmRotate, setConfirmRotate] = useState(false);

  useEffect(() => {
    setUrl(n8n.webhookUrl ?? '');
    setEnabled(n8n.status === 'CONNECTED');
  }, [n8n.webhookUrl, n8n.status]);

  const dirty = url !== (n8n.webhookUrl ?? '') || enabled !== (n8n.status === 'CONNECTED');

  const submit = async () => {
    try {
      await save.mutateAsync({ webhookUrl: url.trim() || null, enabled });
      toast.success(enabled ? 'n8n connected' : 'n8n settings saved');
    } catch (err) {
      toast.error(errorMessage(err));
    }
  };

  const sendTest = async () => {
    try {
      const result = await test.mutateAsync();
      if (result.delivered) {
        toast.success(`Test event delivered (HTTP ${result.httpStatus})`);
      } else {
        toast.error(`Test event not delivered: ${result.error ?? 'unknown error'}`);
      }
    } catch (err) {
      toast.error(errorMessage(err));
    }
  };

  return (
    <Paper variant="outlined" sx={{ p: 2.5, height: '100%' }}>
      <Typography variant="subtitle1" fontWeight={700}>
        n8n
      </Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
        Reservation events (created, cancelled, reminder due) are posted to this webhook, signed with the secret
        below.
      </Typography>
      <TextField
        fullWidth
        size="small"
        label="Event webhook URL"
        placeholder="http://localhost:5678/webhook/booking-events"
        value={url}
        onChange={(event) => setUrl(event.target.value)}
        sx={{ mb: 1 }}
      />
      <FormControlLabel
        control={<Switch checked={enabled} onChange={(event) => setEnabled(event.target.checked)} />}
        label="Send events to n8n"
      />
      <Stack direction="row" spacing={1} sx={{ mt: 1, mb: 2.5 }}>
        <Button variant="contained" disabled={!dirty || save.isPending} onClick={submit}>
          Save
        </Button>
        <Button variant="outlined" disabled={!n8n.webhookUrl || test.isPending} onClick={sendTest}>
          {test.isPending ? 'Sending…' : 'Send test event'}
        </Button>
      </Stack>
      {n8n.signingSecret ? (
        <>
          <TextField
            fullWidth
            size="small"
            label="Signing secret"
            type={showSecret ? 'text' : 'password'}
            value={n8n.signingSecret}
            InputProps={{
              readOnly: true,
              sx: { fontFamily: 'monospace', fontSize: 13 },
              endAdornment: (
                <InputAdornment position="end">
                  <Tooltip title={showSecret ? 'Hide' : 'Show'}>
                    <IconButton onClick={() => setShowSecret(!showSecret)}>
                      {showSecret ? <VisibilityOffRoundedIcon fontSize="small" /> : <VisibilityRoundedIcon fontSize="small" />}
                    </IconButton>
                  </Tooltip>
                  <Tooltip title="Copy">
                    <IconButton edge="end" onClick={() => copyToClipboard(n8n.signingSecret ?? '', toast, 'Signing secret')}>
                      <ContentCopyRoundedIcon fontSize="small" />
                    </IconButton>
                  </Tooltip>
                </InputAdornment>
              ),
            }}
          />
          <Button size="small" sx={{ mt: 0.5 }} onClick={() => setConfirmRotate(true)}>
            Generate a new secret
          </Button>
        </>
      ) : null}
      <ConfirmDialog
        open={confirmRotate}
        title="Generate a new signing secret?"
        message="n8n rejects events until you put the new secret into its configuration."
        confirmLabel="Generate"
        busy={rotate.isPending}
        onClose={() => setConfirmRotate(false)}
        onConfirm={async () => {
          try {
            await rotate.mutateAsync();
            toast.success('New signing secret generated');
            setConfirmRotate(false);
          } catch (err) {
            toast.error(errorMessage(err));
          }
        }}
      />
    </Paper>
  );
}
