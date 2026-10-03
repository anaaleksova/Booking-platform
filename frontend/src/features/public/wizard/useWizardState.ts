import { useCallback, useMemo } from 'react';
import { useSearchParams } from 'react-router-dom';
import type { ChoosableRequirement, PublicService, ResourceSelection, Slot } from '@/types/api';

export type StepKey = 'service' | 'options' | 'time' | 'spot' | 'details' | 'review';

export interface WizardChoices {
  serviceId: number | null;
  people: number;
  /** requirement id → chosen resource ids; absent = "no preference". */
  picks: Record<number, number[]>;
  date: string | null;
  /** "HH:mm", business-local. */
  time: string | null;
}

export const STEP_TITLES: Record<StepKey, string> = {
  service: 'Choose a service',
  options: 'Guests & preferences',
  time: 'Pick a date & time',
  spot: 'Choose your spot',
  details: 'Your details',
  review: 'Review & confirm',
};

/**
 * Choices made on a map of the place. They come after the time, because the
 * map shows what is free at that time.
 */
export function mappedRequirements(service: PublicService | undefined): ChoosableRequirement[] {
  return (service?.choosableRequirements ?? []).filter((requirement) => (requirement.floorPlans ?? []).length > 0);
}

/** Choices made from a list before the time — "choose your therapist". */
export function listedRequirements(service: PublicService | undefined): ChoosableRequirement[] {
  return (service?.choosableRequirements ?? []).filter((requirement) => !(requirement.floorPlans ?? []).length);
}

/** Whether a service needs the "guests & preferences" step at all. */
export function needsOptions(service: PublicService | undefined): boolean {
  return Boolean(service && (service.maxParticipants > 1 || listedRequirements(service).length > 0));
}

/** Whether a service has a "choose your table on the map" step. */
export function needsSpot(service: PublicService | undefined): boolean {
  return mappedRequirements(service).length > 0;
}

export function stepsFor(service: PublicService | undefined): StepKey[] {
  return [
    'service',
    ...(needsOptions(service) ? (['options'] as StepKey[]) : []),
    'time',
    ...(needsSpot(service) ? (['spot'] as StepKey[]) : []),
    'details',
    'review',
  ];
}

/** The title of a step; the map step names what is chosen ("Choose your table"). */
export function stepTitle(step: StepKey, service: PublicService | undefined): string {
  const mapped = mappedRequirements(service);
  if (step === 'spot' && mapped.length === 1) {
    return `Choose your ${mapped[0].resourceTypeName.toLowerCase()}`;
  }
  return STEP_TITLES[step];
}

/** Picks without the map choices — what is left when the time changes. */
export function withoutMappedPicks(service: PublicService | undefined, picks: Record<number, number[]>): Record<number, number[]> {
  const next = { ...picks };
  mappedRequirements(service).forEach((requirement) => delete next[requirement.requirementId]);
  return next;
}

/** Only complete picks are sent; a partial pick for "choose 2" falls back to automatic assignment. */
export function selectionsFrom(service: PublicService | undefined, picks: Record<number, number[]>): ResourceSelection[] {
  return (service?.choosableRequirements ?? [])
    .filter((requirement) => picks[requirement.requirementId]?.length === requirement.quantity)
    .map((requirement) => ({ requirementId: requirement.requirementId, resourceIds: picks[requirement.requirementId] }));
}

/** A slot fits the customer's picks when every picked resource is free in it. */
export function slotFitsPicks(slot: Slot, selections: ResourceSelection[]): boolean {
  return selections.every((selection) => {
    const free = slot.requirements.find((requirement) => requirement.requirementId === selection.requirementId);
    return free !== undefined && selection.resourceIds.every((id) => free.resources.some((resource) => resource.id === id));
  });
}

function parsePicks(raw: string | null): Record<number, number[]> {
  const picks: Record<number, number[]> = {};
  (raw ?? '').split(';').forEach((part) => {
    const [requirement, ids] = part.split(':');
    const resourceIds = (ids ?? '').split(',').map(Number).filter((id) => Number.isInteger(id) && id > 0);
    if (Number(requirement) > 0 && resourceIds.length) {
      picks[Number(requirement)] = resourceIds;
    }
  });
  return picks;
}

function formatPicks(picks: Record<number, number[]>): string {
  return Object.entries(picks)
    .filter(([, ids]) => ids.length)
    .map(([requirement, ids]) => `${requirement}:${ids.join(',')}`)
    .join(';');
}

/**
 * The wizard's choices, kept in the URL: the back button returns to the
 * previous step, a refresh keeps your place, and links (like "next available"
 * chips) can open the wizard at a step with choices already made.
 */
export function useWizardState() {
  const [params, setParams] = useSearchParams();

  const choices: WizardChoices = useMemo(
    () => ({
      serviceId: Number(params.get('service')) || null,
      people: Math.max(1, Number(params.get('people')) || 1),
      picks: parsePicks(params.get('pick')),
      date: params.get('date'),
      time: params.get('time'),
    }),
    [params],
  );
  const step = (params.get('step') as StepKey | null) ?? null;

  /**
   * Moves to a step, applying changes. Pushes a history entry so "back"
   * returns to the previous step; {@code replace} is for changes within a step
   * (another day, another person) that should not each become a back-stop.
   */
  const go = useCallback(
    (nextStep: StepKey, patch: Partial<WizardChoices> = {}, options: { replace?: boolean } = {}) => {
      const merged = { ...choices, ...patch };
      const next = new URLSearchParams();
      if (merged.serviceId) next.set('service', String(merged.serviceId));
      if (merged.people > 1) next.set('people', String(merged.people));
      const picks = formatPicks(merged.picks);
      if (picks) next.set('pick', picks);
      if (merged.date) next.set('date', merged.date);
      if (merged.time) next.set('time', merged.time);
      next.set('step', nextStep);
      setParams(next, { replace: options.replace });
      if (!options.replace) {
        window.scrollTo({ top: 0, behavior: 'smooth' });
      }
    },
    [choices, setParams],
  );

  return { choices, step, go };
}
