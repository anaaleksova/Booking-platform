import { useMutation, useQueryClient, type QueryKey } from '@tanstack/react-query';

/**
 * A mutation that refreshes the given cache prefixes when it succeeds, so
 * every view showing the changed data updates without knowing who changed it.
 */
export function useInvalidatingMutation<TVariables, TResult>(
  mutationFn: (variables: TVariables) => Promise<TResult>,
  invalidates: QueryKey[],
) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn,
    onSuccess: () => Promise.all(invalidates.map((queryKey) => queryClient.invalidateQueries({ queryKey }))),
  });
}
