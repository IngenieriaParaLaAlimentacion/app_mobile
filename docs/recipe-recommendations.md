# Recipe recommendations (#75)

Home's **Recetas** tile requires an approved current establishment. The same flow
is accessible from **Alimentos**. The screen resolves the stored establishment
against current approved links, so restored navigation cannot pin a stale scope.

The form sends only establishment id, a strict positive integer up to 10000,
and meal type. Generation runs online with a longer request timeout, retains
input after errors, and aims for two or three backend proposals and can show one valid alternative. Recipes include
exact stock-unit quantities, preparation, nutritional percentages, GAPA groups,
cultural inspiration and explicit energy/nutritional shortfalls. Indicators are
returned by the backend; the client only formats them for display.

Choosing opens a confirmation explaining the stock deduction. The backend
creates the outflow atomically. Successful choices refresh history and return
`FOOD_MOVEMENT_CREATED_RESULT` to the parent. Alimentos refreshes stock and
movements; Home refreshes the shared movement cache for recent activity.

## API dependency

Requires the recommendation API from
[backend#170](https://github.com/IngenieriaParaLaAlimentacion/backend/issues/170),
including the read-only reuse preview endpoint added with this implementation.
The backend migration must deploy before the mobile feature is usable.

All paths are under `/api/v1`:

- `POST /recipe-recommendations`: generation.
- `GET /establishments/{id}/recipe-recommendations?chosen_only=true&page=N&page_size=20`:
  paginated chosen history.
- `POST /recipe-recommendations/{request}/proposals/{proposal}/choose`: selection.
- `POST /recipe-recommendations/{request}/proposals/{proposal}/reuse-preview` with
  `{"children_count":15}`: calculated preview, without stock deduction.
- `POST /recipe-recommendations/{request}/proposals/{proposal}/reuse` with
  `{"id":"<client UUID>","children_count":15}`: confirm reuse.

A changed reuse count invalidates the preview. Preview and confirmation use the
backend's quantities and indicators; the app does not scale ingredients itself.

## Retry behavior

200 replays and 201 creations both succeed. Every choice retry keeps the same
request/proposal pair. Reuse gets one client-generated UUID at confirmation,
retained for uncertain retries. Pending ids, scope and count are stored in the
screen's `SavedStateHandle`, including restoration after process recreation.
While a mutation is uncertain, generation, editing and another choice are locked;
**Reintentar confirmación** sends the same operation. Confirmed failures clear the
pending intent; stock conflicts never announce success.

Empty usable stock, access errors, model failure, conflicting choices and exact
stock shortages map to distinct Spanish messages. No offline outflow is queued.

## Verification

```bash
./scripts/compile.sh
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew :composeApp:testTstDebugUnitTest --no-daemon
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew :composeApp:assembleTstDebug --no-daemon
```

For a local emulator backend, set `api_url_tst=http://10.0.2.2:8080/` in ignored
`local.properties`. The debug-only network policy permits HTTP to the emulator
host and loopback; other hosts still require HTTPS. Release configuration is
unchanged.

Unit tests cover request bodies and decimals, domain errors, pagination, offline
and scope gating, confirmation, shortages, preview invalidation and stable reuse
ids through screen recreation and retry.

Availability warnings `recipes/cultural-unavailable` and
`recipes/fewer-alternatives` are displayed separately from missing nutrient data.
A single valid recipe remains selectable; warnings do not disable confirmation.
