import type { IconName } from '#lib/components/Icon.svelte';
import { m } from '#lib/paraglide/messages.js';
import { icons } from './icons.js';
import {
	isOn,
	parseNumber,
	type FieldDef,
	type FieldOption,
	type Values
} from './settings-model.js';

export interface FieldGroup {
	title?: () => string;
	description?: () => string;
	fields: FieldDef[];
}

/** Side panels with live data or actions that Save doesn't touch. */
export type SectionPanel = 'trash' | 'notifications' | 'nightly' | 'performance' | 'embedding';

export interface SettingsSection {
	id: string;
	title: () => string;
	description: () => string;
	icon: { name?: IconName; path?: string };
	groups: FieldGroup[];
	/** Rules that span more than one field; returns the keys at fault. */
	crossCheck?: (values: Values) => string[];
	panel?: SectionPanel;
	/** The maintenance row that processes what this section configures. */
	maintenanceHint?: boolean;
}

export interface SectionGroup {
	title: () => string;
	sections: SettingsSection[];
}

const bool = (key: string, label: () => string, def: boolean, hint?: () => string): FieldDef => ({
	key,
	kind: 'bool',
	default: def ? 'true' : 'false',
	label,
	hint
});

const int = (
	key: string,
	label: () => string,
	def: number,
	min: number,
	max: number,
	extra: Partial<FieldDef> = {}
): FieldDef => ({ key, kind: 'int', default: String(def), label, min, max, step: 1, ...extra });

const fraction = (
	key: string,
	label: () => string,
	def: string,
	hint: () => string,
	max = 1
): FieldDef => ({
	key,
	kind: 'decimal',
	default: def,
	label,
	hint,
	min: 0,
	max,
	step: 0.01,
	slider: true
});

const modeOptions: FieldOption[] = [
	{ value: 'missing', label: m.ops_set_mode_missing },
	{ value: 'all', label: m.ops_set_mode_all }
];

const deviceField = (prefix: string, hint = m.ops_set_device_hint): FieldDef => ({
	key: `${prefix}.Provider`,
	kind: 'select',
	default: 'auto',
	label: m.ops_set_device,
	hint,
	options: [
		{ value: 'auto', label: m.ops_set_device_auto },
		{ value: 'cuda', label: m.ops_set_device_gpu },
		{ value: 'cpu', label: m.ops_set_device_cpu }
	]
});

const preferLarge = (prefix: string) =>
	bool(`${prefix}.PreferThumbnailLarge`, m.ops_set_prefer_large, true, m.ops_set_prefer_large_hint);

/** Whether the feature runs in the nightly cycle, and on what. Same keys as the nightly section. */
const nightlyGroup = (feature: string): FieldGroup => ({
	title: m.ops_set_nightly_group,
	description: m.ops_set_nightly_group_hint,
	fields: [
		bool(`NightlyTaskSettings.${feature}.Enabled`, m.ops_set_nightly_feature_enabled, false),
		{
			key: `NightlyTaskSettings.${feature}.Mode`,
			kind: 'select',
			default: 'missing',
			label: m.ops_set_nightly_mode,
			options: modeOptions,
			visibleWhen: `NightlyTaskSettings.${feature}.Enabled`
		}
	]
});

const zero = m.ops_set_zero_unlimited;

const server: SettingsSection = {
	id: 'server',
	title: m.ops_set_server,
	description: m.ops_set_server_desc,
	icon: { path: icons.dns },
	groups: [
		{
			fields: [
				{
					key: 'ServerSettings.PublicUrl',
					kind: 'url',
					default: '',
					label: m.ops_set_server_public_url,
					hint: m.ops_set_server_public_url_hint,
					placeholder: 'https://photos.example.com'
				},
				int('ServerSettings.MaxUploadSizeMb', m.ops_set_server_max_upload, 0, 0, 1_000_000, {
					hint: zero,
					unit: () => 'MB'
				}),
				int('ServerSettings.SessionTimeoutMinutes', m.ops_set_server_session, 1440, 5, 43_200, {
					hint: m.ops_set_server_session_hint,
					unit: () => 'min'
				}),
				{
					key: 'ServerSettings.MapTileApiKey',
					kind: 'secret',
					default: '',
					label: m.ops_set_server_map_key,
					hint: m.ops_set_server_map_key_hint
				}
			]
		}
	]
};

const users: SettingsSection = {
	id: 'users',
	title: m.ops_set_users,
	description: m.ops_set_users_desc,
	icon: { name: 'person' },
	groups: [
		{
			fields: [
				bool('UserSettings.DefaultIsActive', m.ops_set_users_active, true),
				{
					key: 'UserSettings.DefaultRole',
					kind: 'select',
					default: 'User',
					label: m.ops_set_users_role,
					options: [
						{ value: 'User', label: m.ops_set_users_role_user },
						{ value: 'Admin', label: m.ops_set_users_role_admin }
					]
				},
				int('UserSettings.DefaultStorageQuotaGb', m.ops_set_users_quota, 0, 0, 1_000_000, {
					hint: zero,
					unit: () => 'GB'
				})
			]
		}
	]
};

const notifications: SettingsSection = {
	id: 'notifications',
	title: m.ops_set_notifications,
	description: m.ops_set_notifications_desc,
	icon: { name: 'notifications' },
	panel: 'notifications',
	groups: [
		{
			fields: [
				bool('NotificationSettings.Enabled', m.ops_set_notifications_enabled, true),
				int('NotificationSettings.RetentionDays', m.ops_set_notifications_retention, 30, 0, 3650, {
					hint: m.ops_set_notifications_retention_hint,
					unit: m.ops_unit_days
				}),
				int('NotificationSettings.MaxPerUser', m.ops_set_notifications_max, 0, 0, 1_000_000, {
					hint: m.ops_set_notifications_max_hint
				})
			]
		},
		{
			title: m.ops_set_notifications_types,
			fields: [
				bool(
					'NotificationSettings.JobCompleted.Enabled',
					m.ops_set_notifications_job_completed,
					true
				),
				bool('NotificationSettings.JobFailed.Enabled', m.ops_set_notifications_job_failed, true),
				bool('NotificationSettings.ShareViewed.Enabled', m.ops_set_notifications_share_viewed, true)
			]
		}
	]
};

const trash: SettingsSection = {
	id: 'trash',
	title: m.ops_set_trash,
	description: m.ops_set_trash_desc,
	icon: { name: 'delete' },
	panel: 'trash',
	groups: [
		{
			fields: [
				bool('TrashSettings.Enabled', m.ops_set_trash_enabled, true, m.ops_set_trash_enabled_hint),
				int('TrashSettings.RetentionDays', m.ops_set_trash_retention, 30, 0, 3650, {
					hint: m.ops_set_trash_retention_hint,
					unit: m.ops_unit_days
				}),
				int('TrashSettings.MaxQuotaMb', m.ops_set_trash_quota, 0, 0, 100_000_000, {
					hint: zero,
					unit: () => 'MB'
				})
			]
		}
	]
};

const quality = (size: string, label: () => string, def: number) =>
	int(`TaskSettings.ThumbnailQuality.${size}`, label, def, 1, 100, { slider: true });

const images: SettingsSection = {
	id: 'images',
	title: m.ops_set_images,
	description: m.ops_set_images_desc,
	icon: { path: icons.image },
	groups: [
		{
			fields: [
				{
					key: 'TaskSettings.ThumbnailFormat',
					kind: 'select',
					default: 'JPEG',
					label: m.ops_set_images_format,
					hint: m.ops_set_images_format_hint,
					options: [
						{ value: 'JPEG', label: () => 'JPEG' },
						{ value: 'WebP', label: () => 'WebP' }
					]
				}
			]
		},
		{
			title: m.ops_set_images_quality,
			description: m.ops_set_images_quality_hint,
			fields: [
				quality('Small', m.ops_set_images_small, 75),
				quality('Medium', m.ops_set_images_medium, 80),
				quality('Large', m.ops_set_images_large, 85)
			]
		},
		nightlyGroup('Thumbnails')
	]
};

const metadata: SettingsSection = {
	id: 'metadata',
	title: m.ops_set_metadata,
	description: m.ops_set_metadata_desc,
	icon: { name: 'info' },
	groups: [
		{
			title: m.ops_set_metadata_extract,
			fields: [
				bool('MetadataSettings.ExtractDateTime', m.ops_set_metadata_datetime, true),
				bool('MetadataSettings.ExtractGps', m.ops_set_metadata_gps, true),
				bool('MetadataSettings.ExtractCameraInfo', m.ops_set_metadata_camera, true),
				bool('MetadataSettings.ExtractIptc', m.ops_set_metadata_iptc, true),
				bool('MetadataSettings.ReadXmpSidecar', m.ops_set_metadata_xmp, true)
			]
		},
		{
			fields: [
				{
					key: 'MetadataSettings.DefaultTimezone',
					kind: 'timezone',
					default: 'UTC',
					label: m.ops_set_metadata_timezone,
					hint: m.ops_set_metadata_timezone_hint
				}
			]
		},
		nightlyGroup('Metadata')
	]
};

const worker = (key: string, label: () => string, def: number, max = 32) =>
	int(`TaskSettings.${key}`, label, def, 1, max, { slider: true });

const performance: SettingsSection = {
	id: 'performance',
	title: m.ops_set_performance,
	description: m.ops_set_performance_desc,
	icon: { path: icons.speed },
	panel: 'performance',
	groups: [
		{
			title: m.ops_set_performance_io,
			description: m.ops_set_performance_io_hint,
			fields: [
				worker('ThumbnailWorkers', m.ops_set_performance_thumbnails, 2, 16),
				worker('MetadataWorkers', m.ops_set_performance_metadata, 2)
			]
		},
		{
			title: m.ops_set_performance_ml,
			description: m.ops_set_performance_ml_hint,
			fields: [
				worker('FaceRecognitionWorkers', m.ops_set_faces, 1),
				worker('ObjectDetectionWorkers', m.ops_set_objects, 1),
				worker('SceneClassificationWorkers', m.ops_set_scenes, 1),
				worker('TextRecognitionWorkers', m.ops_set_text, 1),
				worker('ImageEmbeddingWorkers', m.ops_set_embedding, 1)
			]
		},
		{
			title: m.ops_set_performance_backfill,
			fields: [
				int('TaskSettings.BackfillBatchSize', m.ops_set_performance_batch, 500, 1, 5000, {
					hint: m.ops_set_performance_batch_hint
				})
			]
		}
	]
};

const nightlyFeatures: [string, () => string][] = [
	['Metadata', m.ops_set_metadata],
	['Thumbnails', m.ops_set_nightly_thumbnails],
	['FaceRecognition', m.ops_set_faces],
	['ObjectDetection', m.ops_set_objects],
	['SceneClassification', m.ops_set_scenes],
	['TextRecognition', m.ops_set_text],
	['ImageEmbedding', m.ops_set_embedding]
];

const nightly: SettingsSection = {
	id: 'nightly',
	title: m.ops_set_nightly,
	description: m.ops_set_nightly_desc,
	icon: { path: icons.night },
	panel: 'nightly',
	groups: [
		{
			fields: [
				bool('NightlyTaskSettings.Enabled', m.ops_set_nightly_enabled, false),
				{
					key: 'NightlyTaskSettings.ScheduleTime',
					kind: 'time',
					default: '02:00',
					label: m.ops_set_nightly_time,
					visibleWhen: 'NightlyTaskSettings.Enabled'
				},
				{
					key: 'NightlyTaskSettings.Timezone',
					kind: 'timezone',
					default: 'UTC',
					label: m.ops_set_nightly_timezone,
					visibleWhen: 'NightlyTaskSettings.Enabled'
				}
			]
		},
		{
			title: m.ops_set_nightly_modules,
			description: m.ops_set_nightly_modules_hint,
			fields: nightlyFeatures.flatMap(([feature, label]) => [
				bool(`NightlyTaskSettings.${feature}.Enabled`, label, false),
				{
					key: `NightlyTaskSettings.${feature}.Mode`,
					kind: 'select',
					default: 'missing',
					label: m.ops_set_nightly_mode,
					options: modeOptions,
					visibleWhen: `NightlyTaskSettings.${feature}.Enabled`
				} satisfies FieldDef
			])
		},
		{
			title: m.ops_set_nightly_after,
			fields: [
				bool('NightlyTaskSettings.FaceClustering.Enabled', m.ops_set_nightly_clustering, true),
				bool('NightlyTaskSettings.TrashCleanup.Enabled', m.ops_set_nightly_trash, false),
				bool('NightlyTaskSettings.IndexingCoverage.Enabled', m.ops_set_nightly_coverage, false)
			]
		}
	]
};

const fractionOf = (values: Values, key: string, fallback: number) =>
	parseNumber({ kind: 'decimal' }, values[key] ?? '') ?? fallback;

const faces: SettingsSection = {
	id: 'faces',
	title: m.ops_set_faces,
	description: m.ops_set_faces_desc,
	icon: { path: icons.face },
	maintenanceHint: true,
	groups: [
		{
			fields: [
				bool('FaceRecognition.Enabled', m.ops_set_faces_enabled, true, m.ops_set_master_hint),
				deviceField('FaceRecognition')
			]
		},
		{
			title: m.ops_set_model_group,
			fields: [
				fraction(
					'FaceRecognition.MinDetectionScore',
					m.ops_set_faces_min_score,
					'0.5',
					m.ops_set_faces_min_score_hint
				),
				fraction(
					'FaceRecognition.ClusteringThreshold',
					m.ops_set_faces_clustering,
					'0.42',
					m.ops_set_faces_clustering_hint
				),
				fraction(
					'FaceRecognition.SuggestionThreshold',
					m.ops_set_faces_suggestion,
					'0.55',
					m.ops_set_faces_suggestion_hint
				),
				int('FaceRecognition.MinFacesForCluster', m.ops_set_faces_min_faces, 2, 1, 10, {
					slider: true
				}),
				int(
					'FaceRecognition.KnnSwitchoverThreshold',
					m.ops_set_faces_knn_switch,
					1500,
					1,
					10_000_000,
					{
						hint: m.ops_set_faces_knn_switch_hint
					}
				),
				int('FaceRecognition.KnnNeighbors', m.ops_set_faces_knn, 20, 5, 50, {
					slider: true,
					hint: m.ops_set_faces_knn_hint
				}),
				preferLarge('FaceRecognition')
			]
		},
		nightlyGroup('FaceRecognition')
	],
	// The suggestion band lies between the two thresholds: below the
	// clustering one there is no band, and nothing is ever suggested.
	crossCheck: (values) =>
		fractionOf(values, 'FaceRecognition.SuggestionThreshold', 0.55) <
		fractionOf(values, 'FaceRecognition.ClusteringThreshold', 0.42)
			? ['FaceRecognition.SuggestionThreshold']
			: []
};

const objects: SettingsSection = {
	id: 'objects',
	title: m.ops_set_objects,
	description: m.ops_set_objects_desc,
	icon: { path: icons.category },
	maintenanceHint: true,
	groups: [
		{
			fields: [
				bool('ObjectDetection.Enabled', m.ops_set_objects_enabled, true, m.ops_set_master_hint),
				deviceField('ObjectDetection')
			]
		},
		{
			title: m.ops_set_model_group,
			fields: [
				fraction(
					'ObjectDetection.MinScore',
					m.ops_set_min_score,
					'0.25',
					m.ops_set_objects_min_score_hint
				),
				fraction(
					'ObjectDetection.MinNormalizedSize',
					m.ops_set_objects_min_size,
					'0.02',
					m.ops_set_objects_min_size_hint,
					0.25
				),
				int('ObjectDetection.MaxObjectsPerAsset', m.ops_set_objects_max, 50, 10, 200, {
					slider: true,
					step: 5,
					hint: m.ops_set_objects_max_hint
				}),
				preferLarge('ObjectDetection')
			]
		},
		nightlyGroup('ObjectDetection')
	]
};

const scenes: SettingsSection = {
	id: 'scenes',
	title: m.ops_set_scenes,
	description: m.ops_set_scenes_desc,
	icon: { path: icons.landscape },
	maintenanceHint: true,
	groups: [
		{
			fields: [
				bool('SceneClassification.Enabled', m.ops_set_scenes_enabled, true, m.ops_set_master_hint),
				deviceField('SceneClassification')
			]
		},
		{
			title: m.ops_set_model_group,
			fields: [
				fraction(
					'SceneClassification.MinScore',
					m.ops_set_scenes_min_score,
					'0.15',
					m.ops_set_scenes_min_score_hint
				),
				int('SceneClassification.MaxScenesPerAsset', m.ops_set_scenes_max, 5, 1, 20, {
					slider: true,
					hint: m.ops_set_scenes_max_hint
				}),
				preferLarge('SceneClassification')
			]
		},
		nightlyGroup('SceneClassification')
	]
};

const text: SettingsSection = {
	id: 'text',
	title: m.ops_set_text,
	description: m.ops_set_text_desc,
	icon: { path: icons.text },
	maintenanceHint: true,
	groups: [
		{
			fields: [
				bool('TextRecognition.Enabled', m.ops_set_text_enabled, true, m.ops_set_master_hint),
				{
					...deviceField('TextRecognition'),
					// OCR on a GPU reserves a lot of video memory.
					warnWhen: { value: 'cuda', message: m.ops_set_device_ocr_warning }
				}
			]
		},
		{
			title: m.ops_set_model_group,
			fields: [
				fraction(
					'TextRecognition.MinScore',
					m.ops_set_min_score,
					'0.5',
					m.ops_set_text_min_score_hint
				),
				int('TextRecognition.MaxLinesPerAsset', m.ops_set_text_max, 200, 50, 500, {
					slider: true,
					step: 10,
					hint: m.ops_set_text_max_hint
				}),
				preferLarge('TextRecognition')
			]
		},
		nightlyGroup('TextRecognition')
	]
};

const embedding: SettingsSection = {
	id: 'embedding',
	title: m.ops_set_embedding,
	description: m.ops_set_embedding_desc,
	icon: { path: icons.imageSearch },
	maintenanceHint: true,
	panel: 'embedding',
	groups: [
		{
			fields: [
				bool('Embedding.Enabled', m.ops_set_embedding_enabled, true, m.ops_set_master_hint),
				deviceField('Embedding')
			]
		},
		{
			title: m.ops_set_model_group,
			fields: [
				{
					key: 'Embedding.ModelVersion',
					kind: 'text',
					default: 'mclip-vit-b32-v1',
					label: m.ops_set_embedding_model,
					hint: m.ops_set_embedding_model_hint,
					required: true
				},
				fraction(
					'Embedding.MaxCosineDistance',
					m.ops_set_embedding_distance,
					'0.85',
					m.ops_set_embedding_distance_hint
				),
				preferLarge('Embedding')
			]
		},
		nightlyGroup('ImageEmbedding')
	]
};

export const sectionGroups: SectionGroup[] = [
	{ title: m.ops_set_group_general, sections: [server, users, notifications, trash] },
	{ title: m.ops_set_group_processing, sections: [images, metadata, performance, nightly] },
	{ title: m.ops_set_group_ai, sections: [faces, objects, scenes, text, embedding] }
];

export const sections = sectionGroups.flatMap((group) => group.sections);

export function findSection(id: string) {
	return sections.find((section) => section.id === id);
}

export function sectionFields(section: SettingsSection) {
	return section.groups.flatMap((group) => group.fields);
}

/** A field is shown unless it depends on a switch that is off. */
export function isVisible(field: FieldDef, values: Values) {
	return field.visibleWhen === undefined || isOn(values[field.visibleWhen]);
}
