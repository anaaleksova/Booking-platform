import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { AxiosError, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios';
import { VerificationCodeForm } from '@/features/auth/VerificationCodeForm';

function apiError(status: number, error: string, message: string) {
  const config = { headers: {} } as InternalAxiosRequestConfig;
  return new AxiosError('failed', 'ERR', config, null, {
    status,
    data: { error, message },
    config,
    headers: {},
  } as AxiosResponse);
}

function renderForm(onVerify = vi.fn().mockResolvedValue(undefined), onResend = vi.fn().mockResolvedValue(undefined)) {
  render(<VerificationCodeForm email="ana@example.com" onVerify={onVerify} onResend={onResend} onBack={vi.fn()} />);
  return { onVerify, onResend, input: screen.getByLabelText(/verification code/i) };
}

describe('VerificationCodeForm', () => {
  it('names the address the code went to', () => {
    renderForm();
    expect(screen.getByText('ana@example.com')).toBeInTheDocument();
  });

  it('accepts only digits, at most six', async () => {
    const { input } = renderForm();
    await userEvent.type(input, '12a34-5678');
    expect(input).toHaveValue('123456');
  });

  it('refuses an incomplete code without calling the server', async () => {
    const { input, onVerify } = renderForm();
    await userEvent.type(input, '123');
    await userEvent.click(screen.getByRole('button', { name: /verify/i }));

    expect(screen.getByText('Enter the 6-digit code')).toBeInTheDocument();
    expect(onVerify).not.toHaveBeenCalled();
  });

  it('sends a complete code', async () => {
    const { input, onVerify } = renderForm();
    await userEvent.type(input, '654321');
    await userEvent.click(screen.getByRole('button', { name: /verify/i }));

    expect(onVerify).toHaveBeenCalledWith('654321');
  });

  it('shows a wrong code and keeps it for correcting', async () => {
    const { input } = renderForm(vi.fn().mockRejectedValue(
      apiError(400, 'INVALID_VERIFICATION_CODE', 'That code is not right. 4 tries left.'),
    ));
    await userEvent.type(input, '000000');
    await userEvent.click(screen.getByRole('button', { name: /verify/i }));

    expect(await screen.findByText('That code is not right. 4 tries left.')).toBeInTheDocument();
    expect(input).toHaveValue('000000');
  });

  it('clears an expired code and offers a new one straight away', async () => {
    const { input } = renderForm(vi.fn().mockRejectedValue(
      apiError(410, 'VERIFICATION_EXPIRED', 'This code has expired. Ask for a new one.'),
    ));
    expect(screen.getByRole('button', { name: /resend code in/i })).toBeInTheDocument();

    await userEvent.type(input, '000000');
    await userEvent.click(screen.getByRole('button', { name: /verify/i }));

    expect(await screen.findByText('This code has expired. Ask for a new one.')).toBeInTheDocument();
    expect(input).toHaveValue('');
    expect(screen.getByRole('button', { name: 'Resend code' })).toBeInTheDocument();
  });
});
