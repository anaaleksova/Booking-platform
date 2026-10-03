import Grid from '@mui/material/Grid';
import TextField from '@mui/material/TextField';

export interface CustomerForm {
  firstName: string;
  lastName: string;
  email: string;
  phone: string;
  notes: string;
}

export const EMPTY_CUSTOMER: CustomerForm = { firstName: '', lastName: '', email: '', phone: '', notes: '' };

const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export function customerErrors(form: CustomerForm): Partial<Record<keyof CustomerForm, string>> {
  return {
    firstName: form.firstName.trim() ? undefined : 'Enter your first name',
    lastName: form.lastName.trim() ? undefined : 'Enter your last name',
    email: EMAIL.test(form.email.trim()) ? undefined : 'Enter a valid email address',
    phone: !form.phone.trim()
      ? 'Enter your phone number'
      : /^\+?[0-9 ()./-]{6,40}$/.test(form.phone.trim())
        ? undefined
        : 'Enter a valid phone number',
  };
}

/** Step 4: who the reservation is for. No account needed; email and phone are required. */
export function DetailsStep({
  value,
  onChange,
  showErrors,
}: {
  value: CustomerForm;
  onChange: (value: CustomerForm) => void;
  showErrors: boolean;
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
      <Grid item xs={12} sm={6}>{field('email', 'Email', 'email', 'email')}</Grid>
      <Grid item xs={12} sm={6}>{field('phone', 'Phone', 'tel', 'tel')}</Grid>
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
