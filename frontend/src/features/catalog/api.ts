import { useQuery } from '@tanstack/react-query';
import { apiClient } from '@/api/client';
import { queryKeys } from '@/features/queryKeys';
import { useInvalidatingMutation } from '@/features/mutation';
import type {
  DayInterval,
  Employee,
  FloorPlan,
  FloorPlanInput,
  Resource,
  ResourceType,
  Service,
  ServiceDeletion,
  ServiceInput,
} from '@/types/api';

// --- services ------------------------------------------------------------

export function useServices() {
  return useQuery({
    queryKey: queryKeys.services,
    queryFn: async () => (await apiClient.get<Service[]>('/api/admin/services')).data,
  });
}

export function useService(id: number | undefined) {
  return useQuery({
    queryKey: queryKeys.service(id ?? 0),
    queryFn: async () => (await apiClient.get<Service>(`/api/admin/services/${id}`)).data,
    enabled: id !== undefined,
  });
}

export function useSaveService() {
  return useInvalidatingMutation(
    async ({ id, input }: { id?: number; input: ServiceInput }) =>
      id === undefined
        ? (await apiClient.post<Service>('/api/admin/services', input)).data
        : (await apiClient.put<Service>(`/api/admin/services/${id}`, input)).data,
    [queryKeys.services],
  );
}

export function useSetServiceActive() {
  return useInvalidatingMutation(
    async ({ id, active }: { id: number; active: boolean }) =>
      (await apiClient.patch<Service>(`/api/admin/services/${id}/active`, null, { params: { value: active } })).data,
    [queryKeys.services],
  );
}

export function useDeleteService() {
  return useInvalidatingMutation(
    async (id: number) => (await apiClient.delete<ServiceDeletion>(`/api/admin/services/${id}`)).data,
    [queryKeys.services],
  );
}

// --- resource types ----------------------------------------------------------

export interface ResourceTypeInput {
  name: string;
  description?: string;
  active: boolean;
}

export function useResourceTypes() {
  return useQuery({
    queryKey: queryKeys.resourceTypes,
    queryFn: async () => (await apiClient.get<ResourceType[]>('/api/admin/resource-types')).data,
  });
}

export function useSaveResourceType() {
  return useInvalidatingMutation(
    async ({ id, input }: { id?: number; input: ResourceTypeInput }) =>
      id === undefined
        ? (await apiClient.post<ResourceType>('/api/admin/resource-types', input)).data
        : (await apiClient.put<ResourceType>(`/api/admin/resource-types/${id}`, input)).data,
    [queryKeys.resourceTypes, queryKeys.resources, queryKeys.services],
  );
}

export function useDeleteResourceType() {
  return useInvalidatingMutation(
    async (id: number) => {
      await apiClient.delete(`/api/admin/resource-types/${id}`);
    },
    [queryKeys.resourceTypes],
  );
}

// --- floor plans -------------------------------------------------------------

/** A resource type's floors, in order; empty when none has been drawn. */
export function useFloorPlans(typeId: number | undefined) {
  return useQuery({
    queryKey: queryKeys.floorPlan(typeId ?? 0),
    queryFn: async () => (await apiClient.get<FloorPlan[]>(`/api/admin/resource-types/${typeId}/floor-plans`)).data,
    enabled: typeId !== undefined,
  });
}

/** Saves every floor of the type at once — floors left out are deleted. */
export function useSaveFloorPlans() {
  return useInvalidatingMutation(
    async ({ typeId, plans }: { typeId: number; plans: FloorPlanInput[] }) =>
      (await apiClient.put<FloorPlan[]>(`/api/admin/resource-types/${typeId}/floor-plans`, { plans })).data,
    [queryKeys.floorPlans, queryKeys.resourceTypes, ['public']],
  );
}

// --- resources -------------------------------------------------------------

export interface ResourceInput {
  resourceTypeId: number;
  name: string;
  description?: string;
  capacity: number | null;
  sharedSeating: boolean;
  minPartySize: number | null;
  active: boolean;
  imageMediaId?: string | null;
  serviceIds: number[];
}

export function useResources() {
  return useQuery({
    queryKey: queryKeys.resources,
    queryFn: async () => (await apiClient.get<Resource[]>('/api/admin/resources')).data,
  });
}

export function useSaveResource() {
  return useInvalidatingMutation(
    async ({ id, input }: { id?: number; input: ResourceInput }) =>
      id === undefined
        ? (await apiClient.post<Resource>('/api/admin/resources', input)).data
        : (await apiClient.put<Resource>(`/api/admin/resources/${id}`, input)).data,
    [queryKeys.resources, queryKeys.resourceTypes],
  );
}

export function useSetResourceActive() {
  return useInvalidatingMutation(
    async ({ id, active }: { id: number; active: boolean }) =>
      (await apiClient.patch<Resource>(`/api/admin/resources/${id}/active`, null, { params: { value: active } }))
        .data,
    [queryKeys.resources],
  );
}

export function useReplaceResourceHours() {
  return useInvalidatingMutation(
    async ({ id, intervals }: { id: number; intervals: DayInterval[] }) =>
      (await apiClient.put<DayInterval[]>(`/api/admin/resources/${id}/hours`, intervals)).data,
    [queryKeys.resources],
  );
}

export function useDeleteResource() {
  return useInvalidatingMutation(
    async (id: number) => {
      await apiClient.delete(`/api/admin/resources/${id}`);
    },
    [queryKeys.resources, queryKeys.resourceTypes],
  );
}

// --- employees -------------------------------------------------------------

export interface EmployeeInput {
  name: string;
  email?: string;
  phone?: string;
  imageMediaId?: string | null;
  active: boolean;
  capacity: number | null;
  serviceIds: number[];
}

export function useEmployees() {
  return useQuery({
    queryKey: queryKeys.employees,
    queryFn: async () => (await apiClient.get<Employee[]>('/api/admin/employees')).data,
  });
}

export function useSaveEmployee() {
  return useInvalidatingMutation(
    async ({ id, input, hours }: { id?: number; input: EmployeeInput; hours: DayInterval[] }) => {
      const saved =
        id === undefined
          ? (await apiClient.post<Employee>('/api/admin/employees', input)).data
          : (await apiClient.put<Employee>(`/api/admin/employees/${id}`, input)).data;
      await apiClient.put(`/api/admin/employees/${saved.id}/working-hours`, hours);
      return saved;
    },
    [queryKeys.employees, queryKeys.resources, queryKeys.resourceTypes],
  );
}

export function useSetEmployeeActive() {
  return useInvalidatingMutation(
    async ({ id, active }: { id: number; active: boolean }) =>
      (await apiClient.patch<Employee>(`/api/admin/employees/${id}/active`, null, { params: { value: active } }))
        .data,
    [queryKeys.employees, queryKeys.resources],
  );
}

export function useDeleteEmployee() {
  return useInvalidatingMutation(
    async (id: number) => {
      await apiClient.delete(`/api/admin/employees/${id}`);
    },
    [queryKeys.employees, queryKeys.resources, queryKeys.resourceTypes],
  );
}
