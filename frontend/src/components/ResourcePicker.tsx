import Chip from '@mui/material/Chip';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import type { ResourceSelection, SlotRequirement } from '@/types/api';

/**
 * Chips for choosing concrete resources per requirement — "Employee: Anna",
 * "Table: Terrace". Used by customers (choosable requirements only) and by
 * admins (any requirement). The server validates the choice again.
 */
export function ResourcePicker({
  requirements,
  value,
  onChange,
}: {
  requirements: SlotRequirement[];
  value: ResourceSelection[];
  onChange: (selections: ResourceSelection[]) => void;
}) {
  const selectedFor = (requirementId: number) =>
    value.find((selection) => selection.requirementId === requirementId)?.resourceIds ?? [];

  const toggle = (requirement: SlotRequirement, resourceId: number) => {
    const current = selectedFor(requirement.requirementId);
    let next: number[];
    if (current.includes(resourceId)) {
      next = current.filter((id) => id !== resourceId);
    } else if (requirement.quantity === 1) {
      next = [resourceId];
    } else {
      next = current.length >= requirement.quantity ? [...current.slice(1), resourceId] : [...current, resourceId];
    }
    onChange([
      ...value.filter((selection) => selection.requirementId !== requirement.requirementId),
      ...(next.length ? [{ requirementId: requirement.requirementId, resourceIds: next }] : []),
    ]);
  };

  return (
    <Stack spacing={2}>
      {requirements.map((requirement) => {
        const selected = selectedFor(requirement.requirementId);
        return (
          <div key={requirement.requirementId}>
            <Typography variant="subtitle2" gutterBottom>
              {requirement.resourceTypeName}
              <Typography component="span" variant="caption" color="text.secondary" sx={{ ml: 1 }}>
                {requirement.quantity > 1
                  ? `choose ${requirement.quantity} (${selected.length} selected)`
                  : selected.length
                    ? 'selected'
                    : 'optional — we assign one if you skip'}
              </Typography>
            </Typography>
            {requirement.resources.length === 0 ? (
              <Typography variant="body2" color="text.secondary">
                None free at this time.
              </Typography>
            ) : (
              <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
                {requirement.resources.map((resource) => (
                  <Chip
                    key={resource.id}
                    label={resource.capacity && resource.capacity > 1 ? `${resource.name} · ${resource.capacity}` : resource.name}
                    color={selected.includes(resource.id) ? 'primary' : 'default'}
                    variant={selected.includes(resource.id) ? 'filled' : 'outlined'}
                    onClick={() => toggle(requirement, resource.id)}
                  />
                ))}
              </Stack>
            )}
          </div>
        );
      })}
    </Stack>
  );
}

/** Only selections that name exactly as many resources as their requirement needs. */
export function completeSelections(requirements: SlotRequirement[], selections: ResourceSelection[]) {
  return selections.filter((selection) => {
    const requirement = requirements.find((item) => item.requirementId === selection.requirementId);
    return requirement !== undefined && selection.resourceIds.length === requirement.quantity;
  });
}
