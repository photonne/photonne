import { defineConfig } from '@hey-api/openapi-ts';

// The server's committed contract is the source of truth; regenerate with
// `npm run api:generate` (also runs on install).
export default defineConfig({
	input: '../Server.Api/openapi/v1.json',
	output: { path: 'src/lib/api/generated', tsConfigPath: './tsconfig.openapi.json' },
	plugins: [
		'@hey-api/client-fetch',
		'@hey-api/typescript',
		'@hey-api/sdk',
		'@tanstack/svelte-query'
	]
});
