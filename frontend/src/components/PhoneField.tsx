import { useEffect, useMemo, useState } from 'react';
import Box from '@mui/material/Box';
import InputAdornment from '@mui/material/InputAdornment';
import ListItemIcon from '@mui/material/ListItemIcon';
import MenuItem from '@mui/material/MenuItem';
import Select from '@mui/material/Select';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import {
  getCountries,
  getCountryCallingCode,
  getExampleNumber,
  isValidPhoneNumber,
  parsePhoneNumberFromString,
  type CountryCode,
} from 'libphonenumber-js/max';
import examples from 'libphonenumber-js/examples.mobile.json';

/*
 * One phone field for the whole app: a country picker (flag + dialling code)
 * and the local number, validated against that country's numbering plan with
 * the same library rules the server applies. The value it reports is always in
 * international form (+38970123456), which is what the API stores.
 */

const regionNames = typeof Intl.DisplayNames === 'function' ? new Intl.DisplayNames(['en'], { type: 'region' }) : null;

const COUNTRIES = getCountries()
  .map((code) => ({ code, name: regionNames?.of(code) ?? code, dial: getCountryCallingCode(code) }))
  .sort((a, b) => a.name.localeCompare(b.name));

/** True when the value is a real number for its country. */
export function isValidPhone(value: string): boolean {
  return Boolean(value) && isValidPhoneNumber(value);
}

/**
 * The country to preselect: the one a known number belongs to (e.g. the
 * business's own phone), else the browser's region, else the fallback.
 */
export function guessCountry(knownNumber?: string | null, fallback: CountryCode = 'US'): CountryCode {
  const fromNumber = knownNumber ? parsePhoneNumberFromString(knownNumber)?.country : undefined;
  if (fromNumber) {
    return fromNumber;
  }
  const region = navigator.language?.split('-')[1]?.toUpperCase();
  return region && COUNTRIES.some((country) => country.code === region) ? (region as CountryCode) : fallback;
}

function Flag({ code }: { code: string }) {
  return (
    <Box
      component="img"
      src={`/flags/${code}.svg`}
      alt=""
      loading="lazy"
      sx={{ width: 24, height: 16, borderRadius: '2px', objectFit: 'cover', boxShadow: '0 0 0 1px rgba(0,0,0,0.12)', display: 'block' }}
    />
  );
}

/** International value for what was typed, even when invalid, so validation can say so. */
function toInternational(country: CountryCode, national: string): string {
  const digits = national.replace(/\D/g, '');
  if (!digits) {
    return '';
  }
  return parsePhoneNumberFromString(national, country)?.number ?? `+${getCountryCallingCode(country)}${digits}`;
}

export function PhoneField({
  value,
  onChange,
  defaultCountry,
  label = 'Phone number',
  error,
  helperText,
}: {
  value: string;
  onChange: (international: string) => void;
  defaultCountry: CountryCode;
  label?: string;
  error?: boolean;
  /** Shown instead of the example number, e.g. a validation message. */
  helperText?: string;
}) {
  const parsed = value ? parsePhoneNumberFromString(value) : undefined;
  const [country, setCountry] = useState<CountryCode>(parsed?.country ?? defaultCountry);
  const [national, setNational] = useState(parsed?.formatNational() ?? '');

  // Follow a default that arrives later (the business loads after the form).
  useEffect(() => {
    if (!value) {
      setCountry(defaultCountry);
    }
    // Only when the default changes; typing must not reset the country.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [defaultCountry]);

  const example = useMemo(() => getExampleNumber(country, examples)?.formatNational(), [country]);

  const changeCountry = (next: CountryCode) => {
    setCountry(next);
    onChange(toInternational(next, national));
  };

  const changeNumber = (text: string) => {
    // A pasted international number picks its own country.
    const international = text.trim().startsWith('+') ? parsePhoneNumberFromString(text.trim()) : undefined;
    if (international?.country) {
      setCountry(international.country);
      setNational(international.formatNational());
      onChange(international.number);
      return;
    }
    setNational(text);
    onChange(toInternational(country, text));
  };

  // Tidy the spacing once they are done typing.
  const tidy = () => {
    const number = parsePhoneNumberFromString(national, country);
    if (number?.isValid()) {
      setNational(number.formatNational());
    }
  };

  return (
    <TextField
      fullWidth
      label={label}
      type="tel"
      autoComplete="tel-national"
      value={national}
      onChange={(event) => changeNumber(event.target.value)}
      onBlur={tidy}
      error={error}
      helperText={helperText || (example ? `Example: ${example}` : ' ')}
      FormHelperTextProps={{ sx: helperText ? undefined : { fontStyle: 'italic' } }}
      InputProps={{
        startAdornment: (
          <InputAdornment position="start" sx={{ mr: 1.5, height: 'auto', maxHeight: 'none' }}>
            <Select
              variant="standard"
              disableUnderline
              value={country}
              onChange={(event) => changeCountry(event.target.value as CountryCode)}
              inputProps={{ 'aria-label': 'Country' }}
              renderValue={(code) => (
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                  <Flag code={code} />
                  <Typography variant="body2" color="text.secondary">
                    +{getCountryCallingCode(code)}
                  </Typography>
                </Box>
              )}
              MenuProps={{ PaperProps: { sx: { maxHeight: 360 } } }}
              sx={{ pr: 0.5, borderRight: 1, borderColor: 'divider', '& .MuiSelect-select': { py: 0.5, pr: '28px !important' } }}
            >
              {COUNTRIES.map((option) => (
                <MenuItem key={option.code} value={option.code}>
                  <ListItemIcon sx={{ minWidth: 36 }}>
                    <Flag code={option.code} />
                  </ListItemIcon>
                  <Typography variant="body2" sx={{ flex: 1 }}>
                    {option.name}
                  </Typography>
                  <Typography variant="body2" color="text.secondary" sx={{ ml: 2 }}>
                    +{option.dial}
                  </Typography>
                </MenuItem>
              ))}
            </Select>
          </InputAdornment>
        ),
      }}
    />
  );
}
