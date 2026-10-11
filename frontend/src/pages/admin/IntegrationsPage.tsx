import { useEffect, useState } from 'react';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Checkbox from '@mui/material/Checkbox';
import Chip from '@mui/material/Chip';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import FormControlLabel from '@mui/material/FormControlLabel';
import FormGroup from '@mui/material/FormGroup';
import Grid from '@mui/material/Grid';
import IconButton from '@mui/material/IconButton';
import InputAdornment from '@mui/material/InputAdornment';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import Switch from '@mui/material/Switch';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import TextField from '@mui/material/TextField';
import Tooltip from '@mui/material/Tooltip';
import Typography from '@mui/material/Typography';
import AddRoundedIcon from '@mui/icons-material/AddRounded';
import ContentCopyRoundedIcon from '@mui/icons-material/ContentCopyRounded';
import FacebookRoundedIcon from '@mui/icons-material/FacebookRounded';
import HubRoundedIcon from '@mui/icons-material/HubRounded';
import InstagramIcon from '@mui/icons-material/Instagram';
import VisibilityOffRoundedIcon from '@mui/icons-material/VisibilityOffRounded';
import VisibilityRoundedIcon from '@mui/icons-material/VisibilityRounded';
import { errorMessage } from '@/api/client';
import { ConfirmDialog } from '@/components/ConfirmDialog';
import { PageHeader } from '@/components/PageHeader';
import { ErrorState, LoadingState } from '@/components/states';
import { useToast } from '@/components/ToastProvider';
import {
  useCreateToken,
  useIntegrations,
  useRevokeToken,
  useRotateSigningSecret,
  useSaveChannel,
  useSaveN8n,
  useSendTestEvent,
} from '@/features/integrations/api';
import type {
  ChannelConnection,
  IntegrationEventDelivery,
  IntegrationPermission,
  IntegrationStatus,
  IntegrationToken,
  N8nConnection,
} from '@/types/api';
import { formatDaysAgo } from '@/utils/format';

const STATUS_CHIP: Record<IntegrationStatus, { label: string; color: 'success' | 'default' | 'warning' }> = {
  CONNECTED: { label: 'Connected', color: 'success' },
  NOT_CONNECTED: { label: 'Not connected', color: 'default' },
  DISABLED: { label: 'Disabled', color: 'warning' },
};

const PERMISSION_LABELS: Record<IntegrationPermission, string> = {
  READ_BUSINESS: 'Read business details',
  READ_SERVICES: 'Read services',
  READ_AVAILABILITY: 'Read availability and prices',
  CREATE_RESERVATION: 'Create reservations',
  READ_RESERVATION: 'Read reservations',
  CANCEL_RESERVATION: 'Cancel reservations',
  READ_CUSTOMER: 'Look up customers',
  CREATE_CUSTOMER: 'Create customers',
  MANAGE_CONVERSATIONS: 'Run chat conversations',
};

const DELIVERY_COLORS: Record<IntegrationEventDelivery['status'], 'success' | 'default' | 'error' | 'info'> = {
  DELIVERED: 'success',
  PENDING: 'info',
  FAILED: 'error',
  SKIPPED: 'default',
};

function copy(text: string, toast: ReturnType<typeof useToast>, what: string) {
  navigator.clipboard
    .writeText(text)
    .then(() => toast.success(`${what} copied`))
    .catch(() => toast.error('Could not copy — select the text and copy it manually.'));
}

/**
 * Where a business connects its automation layer (n8n) and the social
 * channels customers message it on. The automation layer only ever talks to
 * the integration API with a scoped token; booking rules stay in the platform.
 */
export function IntegrationsPage() {
  const toast = useToast();
  const { data, isLoading, isError, error, refetch } = useIntegrations();
  const [tokenDialog, setTokenDialog] = useState(false);
  const [newSecret, setNewSecret] = useState<string | null>(null);
  const [revoking, setRevoking] = useState<IntegrationToken | null>(null);
  const revoke = useRevokeToken();

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
              The access tokens for sending Instagram and Facebook messages are stored in n8n&apos;s credentials,
              never in the platform.
            </Alert>
          </Paper>
        </Grid>
        <Grid item xs={12} md={6}>
          {instagram ? <ChannelCard channel={instagram} /> : null}
        </Grid>
        <Grid item xs={12} md={6}>
          {facebook ? <ChannelCard channel={facebook} /> : null}
        </Grid>

        <Grid item xs={12}>
          <Paper variant="outlined" sx={{ p: 2.5 }}>
            <Stack direction={{ xs: 'column', sm: 'row' }} justifyContent="space-between" spacing={1} sx={{ mb: 2 }}>
              <div>
                <Typography variant="subtitle1" fontWeight={700}>
                  API tokens
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  Each token works for this business only, with the permissions you choose. It never opens the admin
                  panel.
                </Typography>
              </div>
              <Button variant="contained" startIcon={<AddRoundedIcon />} onClick={() => setTokenDialog(true)} sx={{ alignSelf: 'flex-start' }}>
                New token
              </Button>
            </Stack>
            {data.tokens.length === 0 ? (
              <Typography variant="body2" color="text.secondary">
                No tokens yet. Create one for n8n.
              </Typography>
            ) : (
              <TableContainer>
                <Table size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell>Name</TableCell>
                      <TableCell sx={{ display: { xs: 'none', md: 'table-cell' } }}>Token</TableCell>
                      <TableCell sx={{ display: { xs: 'none', md: 'table-cell' } }}>Permissions</TableCell>
                      <TableCell>Last used</TableCell>
                      <TableCell />
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {data.tokens.map((token) => (
                      <TableRow key={token.id}>
                        <TableCell>
                          <Typography variant="body2" fontWeight={600}>
                            {token.name}
                          </Typography>
                          {token.revokedAt ? <Chip size="small" label="Revoked" sx={{ mt: 0.5 }} /> : null}
                        </TableCell>
                        <TableCell sx={{ display: { xs: 'none', md: 'table-cell' }, fontFamily: 'monospace' }}>
                          {token.prefix}…
                        </TableCell>
                        <TableCell sx={{ display: { xs: 'none', md: 'table-cell' } }}>
                          {token.permissions.length === data.availablePermissions.length
                            ? 'All'
                            : token.permissions.map((permission) => PERMISSION_LABELS[permission]).join(', ')}
                        </TableCell>
                        <TableCell>{formatDaysAgo(token.lastUsedAt)}</TableCell>
                        <TableCell align="right">
                          {token.revokedAt ? null : (
                            <Button size="small" color="error" onClick={() => setRevoking(token)}>
                              Revoke
                            </Button>
                          )}
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
            )}
          </Paper>
        </Grid>

        <Grid item xs={12}>
          <DeliveriesCard events={data.recentEvents} />
        </Grid>
      </Grid>

      <NewTokenDialog
        open={tokenDialog}
        permissions={data.availablePermissions}
        onClose={() => setTokenDialog(false)}
        onCreated={(secret) => {
          setTokenDialog(false);
          setNewSecret(secret);
        }}
      />

      <Dialog open={newSecret !== null} onClose={() => setNewSecret(null)} maxWidth="sm" fullWidth>
        <DialogTitle>Copy your new token</DialogTitle>
        <DialogContent>
          <Alert severity="warning" sx={{ mb: 2 }}>
            This is the only time the token is shown. Store it in an n8n credential (Header Auth, name
            &quot;Authorization&quot;, value &quot;Bearer &lt;token&gt;&quot;).
          </Alert>
          {newSecret ? <CopyField label="Token" value={newSecret} /> : null}
        </DialogContent>
        <DialogActions>
          <Button variant="contained" onClick={() => setNewSecret(null)}>
            Done
          </Button>
        </DialogActions>
      </Dialog>

      <ConfirmDialog
        open={revoking !== null}
        title="Revoke token?"
        message={`Anything using "${revoking?.name}" stops working immediately. This cannot be undone.`}
        confirmLabel="Revoke"
        busy={revoke.isPending}
        onClose={() => setRevoking(null)}
        onConfirm={async () => {
          if (!revoking) {
            return;
          }
          try {
            await revoke.mutateAsync(revoking.id);
            toast.success('Token revoked');
            setRevoking(null);
          } catch (err) {
            toast.error(errorMessage(err));
          }
        }}
      />
    </>
  );
}

function StatusTile({ icon, name, status }: { icon: JSX.Element; name: string; status: IntegrationStatus }) {
  const chip = STATUS_CHIP[status];
  return (
    <Paper variant="outlined" sx={{ p: 2, display: 'flex', alignItems: 'center', gap: 1.5 }}>
      <Box sx={{ color: 'text.secondary', display: 'flex' }}>{icon}</Box>
      <Typography variant="subtitle1" fontWeight={700} sx={{ flex: 1 }}>
        {name}
      </Typography>
      <Chip size="small" variant={status === 'CONNECTED' ? 'filled' : 'outlined'} color={chip.color} label={chip.label} />
    </Paper>
  );
}

function CopyField({ label, value }: { label: string; value: string }) {
  const toast = useToast();
  return (
    <TextField
      fullWidth
      size="small"
      label={label}
      value={value}
      sx={{ mb: 1.5 }}
      InputProps={{
        readOnly: true,
        sx: { fontFamily: 'monospace', fontSize: 13 },
        endAdornment: (
          <InputAdornment position="end">
            <Tooltip title="Copy">
              <IconButton edge="end" onClick={() => copy(value, toast, label)}>
                <ContentCopyRoundedIcon fontSize="small" />
              </IconButton>
            </Tooltip>
          </InputAdornment>
        ),
      }}
    />
  );
}

function N8nCard({ n8n }: { n8n: N8nConnection }) {
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
                    <IconButton edge="end" onClick={() => copy(n8n.signingSecret ?? '', toast, 'Signing secret')}>
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

function ChannelCard({ channel }: { channel: ChannelConnection }) {
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
        <Chip size="small" variant="outlined" label="Connect with Meta — coming soon" />
      </Stack>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
        {channel.provider === 'INSTAGRAM'
          ? 'The Instagram professional account whose direct messages n8n answers.'
          : 'The Facebook Page whose Messenger conversations n8n answers.'}{' '}
        Until the Meta sign-in is available, enter the id from Meta&apos;s developer dashboard.
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

function NewTokenDialog({
  open,
  permissions,
  onClose,
  onCreated,
}: {
  open: boolean;
  permissions: IntegrationPermission[];
  onClose: () => void;
  onCreated: (secret: string) => void;
}) {
  const toast = useToast();
  const create = useCreateToken();
  const [name, setName] = useState('n8n');
  const [granted, setGranted] = useState<IntegrationPermission[]>(permissions);

  useEffect(() => {
    if (open) {
      setName('n8n');
      setGranted(permissions);
    }
  }, [open, permissions]);

  const toggle = (permission: IntegrationPermission) =>
    setGranted((current) =>
      current.includes(permission) ? current.filter((item) => item !== permission) : [...current, permission],
    );

  const submit = async () => {
    try {
      const created = await create.mutateAsync({ name: name.trim(), permissions: granted });
      onCreated(created.secret);
    } catch (err) {
      toast.error(errorMessage(err));
    }
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle>New API token</DialogTitle>
      <DialogContent>
        <TextField autoFocus fullWidth label="Name" value={name} onChange={(event) => setName(event.target.value)} sx={{ mt: 1, mb: 2 }} />
        <Typography variant="subtitle2" sx={{ mb: 0.5 }}>
          Permissions
        </Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
          The booking workflows need all of them. Grant less for tools that only read.
        </Typography>
        <FormGroup>
          {permissions.map((permission) => (
            <FormControlLabel
              key={permission}
              control={<Checkbox size="small" checked={granted.includes(permission)} onChange={() => toggle(permission)} />}
              label={PERMISSION_LABELS[permission]}
            />
          ))}
        </FormGroup>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button variant="contained" disabled={!name.trim() || granted.length === 0 || create.isPending} onClick={submit}>
          Create token
        </Button>
      </DialogActions>
    </Dialog>
  );
}

function DeliveriesCard({ events }: { events: IntegrationEventDelivery[] }) {
  return (
    <Paper variant="outlined" sx={{ p: 2.5 }}>
      <Typography variant="subtitle1" fontWeight={700}>
        Recent events
      </Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
        What the platform sent to n8n. Failed deliveries are retried with increasing delays.
      </Typography>
      {events.length === 0 ? (
        <Typography variant="body2" color="text.secondary">
          Nothing sent yet. Events appear once n8n is connected and a reservation changes.
        </Typography>
      ) : (
        <TableContainer>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>Event</TableCell>
                <TableCell>Reservation</TableCell>
                <TableCell>Status</TableCell>
                <TableCell sx={{ display: { xs: 'none', md: 'table-cell' } }}>Detail</TableCell>
                <TableCell sx={{ display: { xs: 'none', sm: 'table-cell' } }}>When</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {events.map((event) => (
                <TableRow key={event.id}>
                  <TableCell sx={{ fontFamily: 'monospace', fontSize: 13 }}>{event.eventType}</TableCell>
                  <TableCell>{event.reservationId ? `#${event.reservationId}` : '—'}</TableCell>
                  <TableCell>
                    <Chip size="small" variant="outlined" color={DELIVERY_COLORS[event.status]} label={event.status.toLowerCase()} />
                  </TableCell>
                  <TableCell sx={{ display: { xs: 'none', md: 'table-cell' } }}>
                    <Typography variant="caption" color="text.secondary">
                      {event.status === 'PENDING' && event.attempts === 0
                        ? `Due ${new Date(event.dueAt).toLocaleString()}`
                        : event.lastError ?? (event.responseStatus ? `HTTP ${event.responseStatus}` : '')}
                      {event.attempts > 1 ? ` · ${event.attempts} attempts` : ''}
                    </Typography>
                  </TableCell>
                  <TableCell sx={{ display: { xs: 'none', sm: 'table-cell' } }}>
                    {new Date(event.deliveredAt ?? event.createdAt).toLocaleString()}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      )}
    </Paper>
  );
}
