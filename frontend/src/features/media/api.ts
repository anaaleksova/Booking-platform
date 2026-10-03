import { apiClient } from '@/api/client';
import type { UploadedMedia } from '@/types/api';

/** Uploads an image; attach it afterwards by saving its id on the business, service or resource. */
export async function uploadImage(file: File): Promise<UploadedMedia> {
  const form = new FormData();
  form.append('file', file);
  return (
    await apiClient.post<UploadedMedia>('/api/admin/media', form, {
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 60_000,
    })
  ).data;
}

/** Image URLs from the API are relative to its origin, which may differ from the app's in some setups. */
export function mediaUrl(url?: string | null): string | undefined {
  if (!url) {
    return undefined;
  }
  return /^https?:\/\//.test(url) ? url : `${apiClient.defaults.baseURL ?? ''}${url}`;
}
