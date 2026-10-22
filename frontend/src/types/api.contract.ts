/**
 * Compile-time contract between the hand-written types in `api.ts` and the
 * API itself (`api.generated.ts`, generated from the backend's OpenAPI
 * description with `npm run gen:api`).
 *
 * Each line asserts that whatever the API returns for a schema fits the type
 * the frontend reads it as: every field the frontend uses exists, with a
 * compatible type. Rename or retype a field in the backend, regenerate, and
 * `tsc` fails here, instead of the UI quietly showing nothing.
 *
 * Nullability is not compared: the API omits null fields and the generated
 * schemas mark every response field optional, while `api.ts` says which
 * fields the UI may rely on.
 */
import type { components } from './api.generated';
import type * as Api from './api';

type Schemas = components['schemas'];

/**
 * Recursively drops null/undefined and optionality, so only names and types
 * are compared. String unions count as `string`: the backend sends enums as
 * strings, and `api.ts` narrows them to the values the UI knows.
 */
type Shape<T> = T extends string
  ? string
  : T extends readonly (infer U)[]
    ? Shape<U>[]
    : T extends object
      ? { [K in keyof T]-?: Shape<NonNullable<T[K]>> }
      : T;

/** True when every value the API can send for `Generated` is a valid `Frontend`. */
type Fits<Generated, Frontend> = [Shape<Generated>] extends [Shape<Frontend>] ? true : false;

type Assert<T extends true> = T;

export type Contract = [
  Assert<Fits<Schemas['CurrentUserResponse'], Api.CurrentUser>>,
  Assert<Fits<Schemas['AuthResponse'], Api.AuthResponse>>,
  Assert<Fits<Schemas['BusinessResponse'], Api.Business>>,
  Assert<Fits<Schemas['BusinessSettingsResponse'], Api.BusinessSettings>>,
  Assert<Fits<Schemas['BlockedTimeResponse'], Api.BlockedTime>>,
  Assert<Fits<Schemas['ResourceTypeResponse'], Api.ResourceType>>,
  Assert<Fits<Schemas['FloorPlanDtosFloorPlanResponse'], Api.FloorPlan>>,
  Assert<Fits<Schemas['ResourceResponse'], Api.Resource>>,
  Assert<Fits<Schemas['EmployeeResponse'], Api.Employee>>,
  Assert<Fits<Schemas['ServiceResponse'], Api.Service>>,
  Assert<Fits<Schemas['ServiceDeletionResponse'], Api.ServiceDeletion>>,
  Assert<Fits<Schemas['CustomerResponse'], Api.Customer>>,
  Assert<Fits<Schemas['ReservationResponse'], Api.Reservation>>,
  Assert<Fits<Schemas['CustomerHistoryResponse'], Api.CustomerHistory>>,
  Assert<Fits<Schemas['SlotResponse'], Api.Slot>>,
  Assert<Fits<Schemas['DaySlotsResponse'], Api.DaySlots>>,
  Assert<Fits<Schemas['QuoteResponse'], Api.PriceQuote>>,
  Assert<Fits<Schemas['DiscountResponse'], Api.Discount>>,
  Assert<Fits<Schemas['AnalyticsDtosRevenuePeriods'], Api.RevenuePeriods>>,
  Assert<Fits<Schemas['AnalyticsDtosRevenueReport'], Api.RevenueReport>>,
  Assert<Fits<Schemas['AnalyticsDtosReservationReport'], Api.ReservationReport>>,
  Assert<Fits<Schemas['AnalyticsDtosCustomerReport'], Api.CustomerReport>>,
  Assert<Fits<Schemas['AnalyticsDtosDashboard'], Api.Dashboard>>,
  Assert<Fits<Schemas['PublicBusinessResponse'], Api.PublicBusiness>>,
  Assert<Fits<Schemas['PublicServiceResponse'], Api.PublicService>>,
  Assert<Fits<Schemas['PublicReservationResponse'], Api.PublicReservation>>,
  Assert<Fits<Schemas['GalleryImageResponse'], Api.GalleryImage>>,
  Assert<Fits<Schemas['MediaControllerUploadResponse'], Api.UploadedMedia>>,
  Assert<Fits<Schemas['IntegrationAdminDtosOverview'], Api.IntegrationsOverview>>,
];
