import { forwardRef, useState } from 'react';
import IconButton from '@mui/material/IconButton';
import InputAdornment from '@mui/material/InputAdornment';
import TextField, { type TextFieldProps } from '@mui/material/TextField';
import VisibilityOffRoundedIcon from '@mui/icons-material/VisibilityOffRounded';
import VisibilityRoundedIcon from '@mui/icons-material/VisibilityRounded';

/**
 * A password input with an eye button to show or hide what was typed.
 * Forwards its ref so react-hook-form's `register` works on it.
 */
export const PasswordField = forwardRef<HTMLDivElement, Omit<TextFieldProps, 'type'>>(function PasswordField(
  { InputProps, ...props },
  ref,
) {
  const [visible, setVisible] = useState(false);
  return (
    <TextField
      {...props}
      ref={ref}
      type={visible ? 'text' : 'password'}
      InputProps={{
        ...InputProps,
        endAdornment: (
          <InputAdornment position="end">
            <IconButton
              edge="end"
              aria-label={visible ? 'Hide password' : 'Show password'}
              onClick={() => setVisible((shown) => !shown)}
              // Keep focus (and the caret) in the password box.
              onMouseDown={(event) => event.preventDefault()}
            >
              {visible ? <VisibilityOffRoundedIcon /> : <VisibilityRoundedIcon />}
            </IconButton>
          </InputAdornment>
        ),
      }}
    />
  );
});
