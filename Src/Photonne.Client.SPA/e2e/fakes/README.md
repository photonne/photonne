# Fakes per area

Each `*.ts` file here default-exports a `FakeHandler` (see `../fake-api.ts`):
it looks at `context.method` and `context.path`, answers with
`context.json(...)` (or `context.route.fulfill(...)`) and returns `true`, or
returns `false` to let the next fake try. Keep one file per area (albums,
admin…) so areas never edit the same file. Use `context.state` to keep
fake data across requests and to let a test assert what was sent.
