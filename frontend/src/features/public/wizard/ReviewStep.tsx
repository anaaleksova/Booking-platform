import Button from '@mui/material/Button';
import Divider from '@mui/material/Divider';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import type { PriceQuote } from '@/types/api';
import { formatMoney } from '@/utils/format';
import type { StepKey } from './useWizardState';

export interface ReviewLine {
  label: string;
  value: string;
  step: StepKey;
}

/** Step 5: everything at a glance, each part editable, the server's price, and confirm. */
export function ReviewStep({
  lines,
  onEdit,
  codeInput,
  onCodeInput,
  onApplyCode,
  codeError,
  quote,
}: {
  lines: ReviewLine[];
  onEdit: (step: StepKey) => void;
  codeInput: string;
  onCodeInput: (value: string) => void;
  onApplyCode: () => void;
  codeError: string | null;
  quote: PriceQuote | null;
}) {
  return (
    <Stack spacing={3}>
      <Stack divider={<Divider flexItem />} spacing={1.5}>
        {lines.map((line) => (
          <Stack key={line.label} direction="row" spacing={2} alignItems="center">
            <Typography variant="body2" color="text.secondary" sx={{ width: { xs: 72, sm: 110 }, flexShrink: 0 }}>
              {line.label}
            </Typography>
            <Typography fontWeight={600} sx={{ flex: 1, minWidth: 0, overflowWrap: 'anywhere' }}>
              {line.value}
            </Typography>
            <Button size="small" onClick={() => onEdit(line.step)}>
              Change
            </Button>
          </Stack>
        ))}
      </Stack>

      <div>
        <Stack direction="row" spacing={1}>
          <TextField
            label="Discount code"
            value={codeInput}
            onChange={(event) => onCodeInput(event.target.value.toUpperCase())}
            error={Boolean(codeError)}
            sx={{ flex: 1 }}
          />
          <Button variant="outlined" disabled={!codeInput.trim()} onClick={onApplyCode}>
            Apply
          </Button>
        </Stack>
        {codeError ? (
          <Typography variant="caption" color="error">
            {codeError}
          </Typography>
        ) : quote?.discountName ? (
          <Typography variant="caption" color="success.main">
            {quote.discountName} applied
          </Typography>
        ) : null}
      </div>

      {quote ? (
        <Stack spacing={0.75} sx={{ p: 2, borderRadius: 3, bgcolor: 'action.hover' }}>
          <Line label="Service" value={formatMoney(quote.originalPrice, quote.currency)} />
          {quote.discountAmount > 0 ? (
            <Line label={`Discount (${quote.discountCode})`} value={`− ${formatMoney(quote.discountAmount, quote.currency)}`} />
          ) : null}
          <Divider />
          <Line bold label="Total" value={formatMoney(quote.finalPrice, quote.currency)} />
          <Typography variant="caption" color="text.secondary">
            Paid at the venue.
          </Typography>
        </Stack>
      ) : null}
    </Stack>
  );
}

function Line({ label, value, bold }: { label: string; value: string; bold?: boolean }) {
  return (
    <Stack direction="row" justifyContent="space-between">
      <Typography variant={bold ? 'h6' : 'body2'} fontWeight={bold ? 800 : 400}>
        {label}
      </Typography>
      <Typography variant={bold ? 'h6' : 'body2'} fontWeight={bold ? 800 : 500}>
        {value}
      </Typography>
    </Stack>
  );
}
