---
name: feature-screen-generator
description: Generate new feature screens following the project's MVVM architecture with Action, Destination, Navigation, Screen, State, and ViewModel files. Use this skill whenever the user asks to create a new screen, scaffold a feature, bootstrap a page, add a new UI flow, create boilerplate for a screen, or mentions creating any combination of Action/Destination/Navigation/Screen/State/ViewModel files. Also trigger when implementing a new feature that needs its own screen, even if the user doesn't explicitly say "new screen."
---

# Feature Screen Generator

Use this skill when creating new screens/features in the Extra marketplace app.

## When to Use This Skill

- User asks to "create a new screen" or "add a new feature"
- User wants to implement a new UI flow
- User mentions creating Action, Screen, ViewModel, or Navigation files

## Required Information

Before generating, gather from the user:

1. **Feature Name** (PascalCase): e.g., `EnterBio`, `ProductDetails`, `EditProfile`
2. **Feature Group**: The parent folder (e.g., `profile`, `listing`, `checkout`, `activity`)
3. **Feature Path**: The subfolder name (snake_case): e.g., `enter_bio`, `product_details`
4. **Has Navigation Parameters?**: Does this screen receive parameters (e.g., `userId`, `listingId`)?
5. **Key User Actions**: What can the user do on this screen?
6. **Navigation Destinations**: Where can the user navigate from this screen?

## File Structure

For a feature named `{FeatureName}` in group `{feature_group}` with path `{feature_path}`, create files at:

```
composeApp/src/commonMain/kotlin/com/extra/marketplace/presentation/feature/{feature_group}/{feature_path}/
├── {FeatureName}Action.kt
├── {FeatureName}Destination.kt
├── {FeatureName}State.kt
├── {FeatureName}ViewModel.kt
├── {FeatureName}Navigation.kt
└── {FeatureName}Screen.kt
```

Also update these integration files:
- `composeApp/src/commonMain/kotlin/com/extra/marketplace/presentation/navigation/Route.kt` - Add route definition
- `composeApp/src/commonMain/kotlin/com/extra/marketplace/di/ViewModelsModule.kt` - Register ViewModel with Koin
- `composeApp/src/commonMain/kotlin/com/extra/marketplace/app/AppNavHost.kt` - Register screen in navigation graph

## Generation Order

Create files in this order to avoid compilation errors:

1. `{FeatureName}Action.kt`
2. `{FeatureName}Destination.kt`
3. `{FeatureName}State.kt`
4. `{FeatureName}ViewModel.kt`
5. `{FeatureName}Navigation.kt`
6. `{FeatureName}Screen.kt`
7. Update `Route.kt`
8. Update `ViewModelsModule.kt`
9. Update `AppNavHost.kt`

## Templates

See the `templates/` folder for code templates:
- `templates/Action.kt.template`
- `templates/Destination.kt.template`
- `templates/State.kt.template`
- `templates/ViewModel.kt.template`
- `templates/Navigation.kt.template`
- `templates/Screen.kt.template`

When generating files from these templates, strip all `GUIDELINES` comment blocks at the bottom of each template — they are for your reference only and should not appear in generated output.

## Key Patterns

### Action Pattern
- Use `sealed interface` — actions carry no shared state and only represent intent, so an interface is lighter and more idiomatic
- `data object` for parameterless actions
- `data class` for actions with parameters
- Naming: `{Verb}{Subject}` (e.g., `BackClick`, `SaveClick`, `BioChanged`)

### Destination Pattern
- Use `sealed class` — destinations may carry navigation data and benefit from class inheritance for shared behavior
- `data object` for destinations without params
- `data class` for destinations with params

### State Pattern
- `data class` with default values
- Always include `destination: {FeatureName}Destination? = null`
- Always include `screenError: DataError? = null` for screens with API calls
- Include `isLoading: Boolean = true` for screens that load data

### ViewModel Pattern
- Constructor injection for dependencies
- `_state` private MutableStateFlow, `state` public StateFlow
- Use `SharingStarted.WhileSubscribed(5000L)` — this keeps the flow active for 5 seconds after the last subscriber disconnects, surviving configuration changes without leaking resources
- Single `onAction()` function dispatching to private handlers — this centralizes action routing and makes it easy to trace what happens for any user interaction
- Required: `clearDestination()` and `navigate()` methods

### Navigation Pattern
- `navigateTo{FeatureName}()` extension on NavController
- `{featureName}Screen()` extension on NavGraphBuilder
- Use type-safe routes with `composable<Route.{FeatureName}>`
- Internal `navigate()` function for destination handling

### Screen Pattern
- Two composables: `{FeatureName}ScreenRoot` (stateful) and `{FeatureName}Screen` (stateless)
- ScreenRoot handles ViewModel injection with `koinViewModel()`
- Use `collectAsStateWithLifecycle()` for state
- `LaunchedEffect(state.destination)` for navigation observation
- Always call `viewModel.clearDestination()` after navigating
- Use `ExScaffold` as root layout

### Route Pattern
- Add to `Route.kt`
- Use `@Serializable` and `@SerialName("snake_case")`
- `data object` without params, `data class` with params

### Koin Registration
- Add to `ViewModelsModule.kt`
- Use `viewModelOf(::ClassName)` for simple cases (no route params)
- For screens with route params, pass the `Route` object directly (not individual params):
  ```kotlin
  viewModel { (route: Route.FeatureName) ->
      FeatureNameViewModel(route = route, someUseCase = get())
  }
  ```
- **Never** pass multiple same-type params via `parametersOf` individually — use the Route object

## Example Usage

### Example 1: Simple screen (no route params)

**User**: Create a screen for editing user profile with fields for name and bio

**Response**: Generate files for `EditProfile` in `profile/edit_profile/` with:
- Actions: `BackClick`, `SaveClick`, `NameChanged(value: String)`, `BioChanged(value: String)`
- Destinations: `Back`
- State: `name`, `bio`, `isLoading`, `isSaving`, `saveEnabled`
- ViewModel: Inject `UpdateProfileUseCase`, handle save logic
- Screen: Form with two inputs and save button

Route.kt addition:
```kotlin
@Serializable
@SerialName("edit_profile")
data object EditProfile : Route()
```

ViewModelsModule.kt addition:
```kotlin
viewModelOf(::EditProfileViewModel)
```

AppNavHost.kt addition:
```kotlin
editProfileScreen(navController)
```

### Example 2: Screen with route parameters

**User**: Create a screen to view listing details, it receives a listingId

**Response**: Generate files for `ListingDetails` in `listing/listing_details/` with:
- Actions: `BackClick`, `FavoriteClick`, `BuyClick`
- Destinations: `Back`, `Checkout(listingId: String)`
- State: `listing: ListingModel? = null`, `isLoading`, `isFavorite`
- ViewModel: Inject `GetListingUseCase`, receive `route: Route.ListingDetails`
- Screen: Listing detail layout with buy button

Route.kt addition:
```kotlin
@Serializable
@SerialName("listing_details")
data class ListingDetails(val listingId: String) : Route()
```

ViewModelsModule.kt addition (pass Route object, not individual params):
```kotlin
viewModel { (route: Route.ListingDetails) ->
    ListingDetailsViewModel(route = route, getListingUseCase = get())
}
```
