import { useEffect, useRef, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import Alert from '@mui/material/Alert';
import Button from '@mui/material/Button';
import Chip from '@mui/material/Chip';
import CircularProgress from '@mui/material/CircularProgress';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogContentText from '@mui/material/DialogContentText';
import DialogTitle from '@mui/material/DialogTitle';
import FormControlLabel from '@mui/material/FormControlLabel';
import Paper from '@mui/material/Paper';
import Radio from '@mui/material/Radio';
import RadioGroup from '@mui/material/RadioGroup';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import FacebookRoundedIcon from '@mui/icons-material/FacebookRounded';
import InstagramIcon from '@mui/icons-material/Instagram';
import { errorMessage } from '@/api/client';
import { ConfirmDialog } from '@/components/ConfirmDialog';
import { useToast } from '@/components/ToastProvider';
import {
  useChooseMetaPage,
  useCompleteMetaConnect,
  useDisconnectMeta,
  useStartMetaConnect,
} from '@/features/integrations/api';
import type { MetaConnection, MetaPageChoice } from '@/types/api';

/**
 * "Connect with Facebook": the owner signs in with Meta and picks their Page;
 * the platform fills in the Facebook and Instagram channels itself. Meta sends
 * the browser back to this page with ?code&state, which is handed to the API
 * once and then removed from the address bar.
 */
export function MetaConnectCard({ meta }: { meta: MetaConnection }) {
  const toast = useToast();
  const [params, setParams] = useSearchParams();
  const start = useStartMetaConnect();
  const complete = useCompleteMetaConnect();
  const choose = useChooseMetaPage();
  const disconnect = useDisconnectMeta();
  const [choice, setChoice] = useState<{ sessionId: string; pages: MetaPageChoice[] } | null>(null);
  const [picked, setPicked] = useState('');
  const [confirmDisconnect, setConfirmDisconnect] = useState(false);
  const handled = useRef(false);

  useEffect(() => {
    const code = params.get('code');
    const state = params.get('state');
    const denied = params.get('error');
    if (handled.current || (!code && !denied)) {
      return;
    }
    handled.current = true;
    setParams({}, { replace: true });
    if (denied || !code || !state) {
      toast.error('Facebook was not connected — the sign-in was cancelled.');
      return;
    }
    complete
      .mutateAsync({ code, state })
      .then((result) => {
        if (result.needsPageChoice && result.sessionId && result.pages) {
          setChoice({ sessionId: result.sessionId, pages: result.pages });
          setPicked(result.pages.find((page) => !page.connectedElsewhere)?.id ?? '');
        } else {
          toast.success(`Connected ${result.connection?.pageName ?? 'your Page'}`);
        }
      })
      .catch((err) => toast.error(errorMessage(err)));
    // Runs once, for the redirect back from Meta.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const connect = async () => {
    try {
      window.location.assign(await start.mutateAsync());
    } catch (err) {
      toast.error(errorMessage(err));
    }
  };

  const confirmPage = async () => {
    if (!choice || !picked) return;
    try {
      const connection = await choose.mutateAsync({ sessionId: choice.sessionId, pageId: picked });
      setChoice(null);
      toast.success(`Connected ${connection.pageName ?? 'your Page'}`);
    } catch (err) {
      toast.error(errorMessage(err));
    }
  };

  const doDisconnect = async () => {
    try {
      await disconnect.mutateAsync();
      setConfirmDisconnect(false);
      toast.success('Facebook and Instagram disconnected');
    } catch (err) {
      toast.error(errorMessage(err));
    }
  };

  return (
    <Paper variant="outlined" sx={{ p: 2.5 }}>
      <Stack direction="row" alignItems="center" spacing={1} sx={{ mb: 0.5 }}>
        <FacebookRoundedIcon sx={{ color: '#1877F2' }} />
        <InstagramIcon sx={{ color: '#C13584' }} />
        <Typography variant="subtitle1" fontWeight={700} sx={{ flex: 1 }}>
          Facebook &amp; Instagram
        </Typography>
        {meta.connected ? <Chip size="small" color="success" label="Connected" /> : null}
      </Stack>

      {complete.isPending ? (
        <Stack direction="row" spacing={1.5} alignItems="center" sx={{ py: 2 }}>
          <CircularProgress size={20} />
          <Typography variant="body2">Finishing the connection with Facebook…</Typography>
        </Stack>
      ) : meta.connected ? (
        <>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
            Customers can book by messaging your Page on Messenger
            {meta.instagramUsername ? ' or your Instagram account' : ''}.
          </Typography>
          <Stack spacing={1} sx={{ mb: 2 }}>
            <Stack direction="row" spacing={1} alignItems="center">
              <FacebookRoundedIcon fontSize="small" color="action" />
              <Typography variant="body2" fontWeight={600}>
                {meta.pageName}
              </Typography>
            </Stack>
            <Stack direction="row" spacing={1} alignItems="center">
              <InstagramIcon fontSize="small" color="action" />
              {meta.instagramUsername ? (
                <Typography variant="body2" fontWeight={600}>
                  @{meta.instagramUsername}
                </Typography>
              ) : (
                <Typography variant="body2" color="text.secondary">
                  No Instagram account is linked to this Page.
                </Typography>
              )}
            </Stack>
          </Stack>
          {!meta.instagramUsername ? (
            <Alert severity="info" variant="outlined" sx={{ mb: 2 }}>
              To take bookings on Instagram, switch your Instagram to a professional account, link it to this
              Facebook Page (Instagram → Settings → Account type and tools), then click Reconnect.
            </Alert>
          ) : null}
          <Stack direction="row" spacing={1} justifyContent="flex-end">
            <Button color="error" onClick={() => setConfirmDisconnect(true)}>
              Disconnect
            </Button>
            <Button variant="outlined" onClick={connect} disabled={start.isPending}>
              Reconnect
            </Button>
          </Stack>
        </>
      ) : meta.available ? (
        <>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 1.5 }}>
            Let customers book by messaging your business on Messenger and Instagram. Sign in with Facebook and choose
            your business&apos;s Page — we set up the rest.
          </Typography>
          <Typography variant="body2" color="text.secondary" component="ul" sx={{ mt: 0, mb: 2, pl: 2.5 }}>
            <li>You need to be an admin of your business&apos;s Facebook Page.</li>
            <li>For Instagram, use a professional (business or creator) account linked to that Page.</li>
          </Typography>
          <Button
            variant="contained"
            startIcon={<FacebookRoundedIcon />}
            onClick={connect}
            disabled={start.isPending}
            sx={{ bgcolor: '#1877F2', '&:hover': { bgcolor: '#166FE5' } }}
          >
            Connect with Facebook
          </Button>
        </>
      ) : (
        <Typography variant="body2" color="text.secondary">
          Signing in with Facebook is not set up on this platform yet. Until it is, enter your Page and Instagram ids
          below.
        </Typography>
      )}

      <Dialog open={choice !== null} onClose={choose.isPending ? undefined : () => setChoice(null)} maxWidth="xs" fullWidth>
        <DialogTitle>Which Page is this business?</DialogTitle>
        <DialogContent>
          <DialogContentText sx={{ mb: 1 }}>You manage several Facebook Pages. Choose the one for this business.</DialogContentText>
          <RadioGroup value={picked} onChange={(event) => setPicked(event.target.value)}>
            {choice?.pages.map((page) => (
              <FormControlLabel
                key={page.id}
                value={page.id}
                disabled={page.connectedElsewhere}
                control={<Radio />}
                label={
                  <span>
                    {page.name}
                    <Typography component="span" variant="body2" color="text.secondary">
                      {page.connectedElsewhere
                        ? ' · used by another business'
                        : page.instagramUsername
                          ? ` · @${page.instagramUsername}`
                          : ' · no Instagram'}
                    </Typography>
                  </span>
                }
              />
            ))}
          </RadioGroup>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button onClick={() => setChoice(null)} disabled={choose.isPending}>
            Cancel
          </Button>
          <Button variant="contained" onClick={confirmPage} disabled={!picked || choose.isPending}>
            Connect this Page
          </Button>
        </DialogActions>
      </Dialog>

      <ConfirmDialog
        open={confirmDisconnect}
        title="Disconnect Facebook and Instagram?"
        message="Customers will no longer be able to book by messaging your Page or Instagram account. You can connect again at any time."
        confirmLabel="Disconnect"
        busy={disconnect.isPending}
        onConfirm={doDisconnect}
        onClose={() => setConfirmDisconnect(false)}
      />
    </Paper>
  );
}
