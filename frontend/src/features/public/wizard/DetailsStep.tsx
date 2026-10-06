import Grid from '@mui/material/Grid';
import TextField from '@mui/material/TextField';
import type { CountryCode } from 'libphonenumber-js/max';
import { PhoneField, isValidPhone } from '@/components/PhoneField';

export interface CustomerForm {
  firstName: string;
  lastName: string;
  email: string;
  phone: string;
  notes: string;
}

export const EMPTY_CUSTOMER: CustomerForm = { firstName: '', lastName: '', email: '', phone: '', notes: '' };

export function customerErrors(form: CustomerForm): Partial<Record<keyof CustomerForm, string>> {
  return {
    firstName: form.firstName.trim() ? undefined : 'Enter your first name',
    lastName: form.lastName.trim() ? undefined : 'Enter your last name',
    phone: !form.phone.trim()
      ? 'Enter your phone number'
      : isValidPhone(form.phone)
        ? undefined
        : 'This is not a valid number for the selected country',
  };
}

/**
 * Step 4: who the reservation is for. Needs a signed-in account; the email is
 * the account's and cannot be changed here, the phone is required.
 */
export function DetailsStep({
  value,
  onChange,
  showErrors,
  defaultCountry,
}: {
  value: CustomerForm;
  onChange: (value: CustomerForm) => void;
  showErrors: boolean;
  /** Preselected phone country, e.g. the business's own. */
  defaultCountry: CountryCode;
}) {
  const errors = customerErrors(value);
  const field = (key: keyof CustomerForm, label: string, autoComplete: string, type = 'text') => (
    <TextField
      fullWidth
      label={label}
      type={type}
      autoComplete={autoComplete}
      value={value[key]}
      onChange={(event) => onChange({ ...value, [key]: event.target.value })}
      error={showErrors && Boolean(errors[key])}
      helperText={(showErrors && errors[key]) || ' '}
    />
  );
  return (
    <Grid container spacing={2}>
      <Grid item xs={12} sm={6}>{field('firstName', 'First name', 'given-name')}</Grid>
      <Grid item xs={12} sm={6}>{field('lastName', 'Last name', 'family-name')}</Grid>
      <Grid item xs={12}>
        <PhoneField
          value={value.phone}
          onChange={(phone) => onChange({ ...value, phone })}
          defaultCountry={defaultCountry}
          error={showErrors && Boolean(errors.phone)}
          helperText={showErrors ? errors.phone : undefined}
        />
      </Grid>
      <Grid item xs={12}>
        <TextField
          fullWidth
          label="Email address"
          value={value.email}
          InputProps={{ readOnly: true }}
          helperText="From your account. Confirmations go here."
        />
      </Grid>
      <Grid item xs={12}>
        <TextField
          fullWidth
          multiline
          minRows={3}
          label="Anything we should know? (optional)"
          value={value.notes}
          onChange={(event) => onChange({ ...value, notes: event.target.value })}
        />
      </Grid>
    </Grid>
  );
}
