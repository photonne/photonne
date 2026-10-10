import type { FakeHandler } from '../fake-api';
import { library } from '../fake-api';

const handle: FakeHandler = async ({ method, path, authorized, json }) => {
	if (method === 'GET' && path === '/api/assets/favorites') {
		if (!authorized) return json(401).then(() => true);
		const items = library.buckets
			.flatMap((bucket) => library.items(bucket.key))
			.filter((item) => item.isFavorite);
		await json(200, { items, hasMore: false, nextCursor: null });
		return true;
	}
	return false;
};

export default handle;
