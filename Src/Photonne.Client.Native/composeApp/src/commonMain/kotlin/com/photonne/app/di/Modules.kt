package com.photonne.app.di

import com.photonne.app.data.account.AccountRepository
import com.photonne.app.data.actions.AssetActionsRepository
import com.photonne.app.data.admin.AdminRepository
import com.photonne.app.data.album.AlbumsRepository
import com.photonne.app.data.settings.ThemePreferenceStore
import com.photonne.app.data.settings.TimelineZoomStore
import com.photonne.app.data.folder.FoldersRepository
import com.photonne.app.ui.folder.FolderDetailViewModel
import com.photonne.app.ui.folder.FolderPermissionsViewModel
import com.photonne.app.ui.folder.FoldersViewModel
import com.photonne.app.data.api.LocalReachabilityProbe
import com.photonne.app.data.api.PhotonneApi
import com.photonne.app.data.api.PhotonneApiClient
import com.photonne.app.data.api.ServerUrlStore
import com.photonne.app.data.api.buildPhotonneHttpClient
import com.photonne.app.data.error.UiErrorFactory
import com.photonne.app.data.version.AppVersionStore
import com.photonne.app.data.auth.AuthRepository
import com.photonne.app.data.auth.AuthStateHolder
import com.photonne.app.data.asset.AssetDetailRepository
import com.photonne.app.data.auth.RememberedCredentialsStore
import com.photonne.app.data.auth.SessionBootstrapper
import com.photonne.app.data.auth.SettingsTokenStorage
import com.photonne.app.data.auth.TokenStorage
import com.photonne.app.data.map.MapRepository
import com.photonne.app.data.people.PeopleRepository
import com.photonne.app.data.search.SearchRepository
import com.photonne.app.data.notifications.NotificationsRepository
import com.photonne.app.data.timeline.MemoriesRepository
import com.photonne.app.data.timeline.TimelineRepository
import com.photonne.app.data.upload.UploadRepository
import com.photonne.app.ui.actions.AssetSharing
import com.photonne.app.ui.album.AlbumDetailViewModel
import com.photonne.app.ui.album.AlbumPermissionsViewModel
import com.photonne.app.ui.album.AlbumSharesViewModel
import com.photonne.app.ui.album.AlbumsViewModel
import com.photonne.app.ui.album.SentSharesViewModel
import com.photonne.app.ui.asset.AssetDetailViewModel
import com.photonne.app.ui.library.ArchivedViewModel
import com.photonne.app.ui.library.FavoritesViewModel
import com.photonne.app.ui.library.UnsupportedFilesViewModel
import com.photonne.app.ui.library.TrashViewModel
import com.photonne.app.ui.people.AssetFacesViewModel
import com.photonne.app.ui.people.PeopleViewModel
import com.photonne.app.ui.people.PersonDetailViewModel
import com.photonne.app.ui.people.PersonSuggestionsViewModel
import com.photonne.app.ui.login.LoginViewModel
import com.photonne.app.ui.map.MapViewModel
import com.photonne.app.ui.search.SearchViewModel
import com.photonne.app.ui.actions.AssetSelectionActionsViewModel
import com.photonne.app.ui.admin.AdminBackupViewModel
import com.photonne.app.ui.admin.DeviceConnectionViewModel
import com.photonne.app.ui.admin.AdminDuplicatesViewModel
import com.photonne.app.ui.admin.AdminFaceRecognitionSettingsViewModel
import com.photonne.app.ui.admin.AdminImageEmbeddingSettingsViewModel
import com.photonne.app.ui.admin.AdminObjectDetectionSettingsViewModel
import com.photonne.app.ui.admin.AdminRunTasksViewModel
import com.photonne.app.ui.admin.AdminSceneClassificationSettingsViewModel
import com.photonne.app.ui.admin.AdminTextRecognitionSettingsViewModel

import com.photonne.app.ui.admin.AdminImageSettingsViewModel
import com.photonne.app.ui.admin.AdminLibrariesViewModel
import com.photonne.app.ui.admin.AdminMaintenanceViewModel
import com.photonne.app.ui.admin.AdminMetadataSettingsViewModel
import com.photonne.app.ui.admin.AdminNightlySettingsViewModel
import com.photonne.app.ui.admin.AdminNotificationSettingsViewModel
import com.photonne.app.ui.admin.AdminServerSettingsViewModel
import com.photonne.app.ui.admin.AdminServerViewModel
import com.photonne.app.ui.admin.AdminStatsViewModel
import com.photonne.app.ui.admin.AdminTrashSettingsViewModel
import com.photonne.app.ui.admin.AdminUserDefaultsViewModel
import com.photonne.app.ui.admin.AdminUsersViewModel
import com.photonne.app.ui.settings.AccountProfileViewModel
import com.photonne.app.ui.settings.AccountSecurityViewModel
import com.photonne.app.ui.settings.AccountStorageViewModel
import com.photonne.app.ui.settings.AppearanceViewModel
import com.photonne.app.ui.notifications.NotificationsViewModel
import com.photonne.app.ui.timeline.MemoriesViewModel
import com.photonne.app.ui.timeline.TimelineViewModel
import com.photonne.app.ui.devicebackup.DeviceBackupViewModel
import com.photonne.app.ui.devicebackup.EnrichmentStatusViewModel
import com.photonne.app.ui.explore.ExploreFacetsViewModel
import com.photonne.app.ui.upload.UploadViewModel
import com.photonne.app.ui.utilities.UtilitiesDuplicatesViewModel
import com.photonne.app.ui.utilities.UtilitiesLargeFilesViewModel
import com.photonne.app.ui.utilities.UtilitiesLocationsViewModel
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

data class PhotonneAppConfig(
    val apiBaseUrl: String? = null,
    val useFakeMemories: Boolean = false
)

expect fun platformModule(): org.koin.core.module.Module

fun commonModule(config: PhotonneAppConfig) = module {
    single { config }
    singleOf(::AuthStateHolder)
    single<TokenStorage> { SettingsTokenStorage(get()) }
    single { RememberedCredentialsStore(get()) }
    single { ServerUrlStore(get()) }
    single<HttpClient> {
        val urlStore = get<ServerUrlStore>()
        buildPhotonneHttpClient(
            engine = get<HttpClientEngine>(),
            baseUrlProvider = { urlStore.requireBaseUrl() },
            tokenStorage = get(),
            authState = get(),
            // Resolved lazily (only invoked on a request-time connection
            // failure) so the probe→client construction order isn't a cycle.
            onConnectionError = { get<LocalReachabilityProbe>().requestReprobe() }
        )
    }
    single<PhotonneApi> {
        val urlStore = get<ServerUrlStore>()
        PhotonneApiClient(get(), baseUrlProvider = { urlStore.requireBaseUrl() })
    }
    single { LocalReachabilityProbe(get(), get(), get()) }
    single { com.photonne.app.data.api.ForegroundRecovery(get(), get()) }
    single { AppVersionStore(get()) }
    single { UiErrorFactory(urlStore = get(), versionStore = get()) }
    singleOf(::AuthRepository)
    singleOf(::AccountRepository)
    singleOf(::SessionBootstrapper)
    singleOf(::AdminRepository)
    single { com.photonne.app.data.utilities.UtilitiesRepository(get()) }
    single { com.photonne.app.db.PhotonneDatabase(com.photonne.app.data.db.createPhotonneDatabaseDriver()) }
    single { com.photonne.app.data.devicebackup.BackupLedger(get()) }
    single { com.photonne.app.data.devicebackup.DeviceBackupStateStore(get()) }
    single {
        com.photonne.app.data.devicebackup.DeviceBackupRepository(
            gallery = get(),
            api = get(),
            uploads = get(),
            stateStore = get(),
            ledger = get()
        )
    }
    single { com.photonne.app.data.devicebackup.EnrichmentRepository(get()) }
    single { com.photonne.app.data.devicebackup.BackupRunner(repository = get(), gallery = get()) }
    single { com.photonne.app.data.devicebackup.createBackgroundSyncScheduler() }
    single { ThemePreferenceStore(get()) }
    single { TimelineZoomStore(get()) }
    single { TimelineRepository(api = get()) }
    single { com.photonne.app.data.timeline.TimelineBucketStore(api = get()) }
    singleOf(::MemoriesRepository)
    singleOf(::NotificationsRepository)
    singleOf(::AssetDetailRepository)
    singleOf(::AlbumsRepository)
    singleOf(::FoldersRepository)
    singleOf(::AssetActionsRepository)
    singleOf(::SearchRepository)
    singleOf(::UploadRepository)
    singleOf(::MapRepository)
    singleOf(::PeopleRepository)
    single { com.photonne.app.data.library.UnsupportedFilesRepository(get()) }
    viewModelOf(::LoginViewModel)
    viewModelOf(::TimelineViewModel)
    viewModelOf(::MemoriesViewModel)
    viewModelOf(::NotificationsViewModel)
    viewModelOf(::AssetDetailViewModel)
    viewModelOf(::AlbumsViewModel)
    viewModelOf(::AlbumDetailViewModel)
    viewModelOf(::AlbumSharesViewModel)
    viewModelOf(::SentSharesViewModel)
    viewModelOf(::AlbumPermissionsViewModel)
    viewModelOf(::FoldersViewModel)
    viewModelOf(::FolderDetailViewModel)
    viewModelOf(::FolderPermissionsViewModel)
    viewModelOf(::SearchViewModel)
    viewModelOf(::ArchivedViewModel)
    viewModelOf(::TrashViewModel)
    viewModelOf(::FavoritesViewModel)
    viewModelOf(::UnsupportedFilesViewModel)
    viewModelOf(::AssetSelectionActionsViewModel)
    viewModelOf(::UploadViewModel)
    viewModelOf(::DeviceBackupViewModel)
    viewModelOf(::EnrichmentStatusViewModel)
    viewModelOf(::UtilitiesDuplicatesViewModel)
    viewModelOf(::UtilitiesLargeFilesViewModel)
    viewModelOf(::UtilitiesLocationsViewModel)
    viewModelOf(::ExploreFacetsViewModel)
    viewModelOf(::MapViewModel)
    viewModelOf(::PeopleViewModel)
    viewModelOf(::PersonDetailViewModel)
    viewModelOf(::PersonSuggestionsViewModel)
    viewModelOf(::AssetFacesViewModel)
    viewModelOf(::AccountProfileViewModel)
    viewModelOf(::AccountSecurityViewModel)
    viewModelOf(::AccountStorageViewModel)
    viewModelOf(::AppearanceViewModel)
    viewModelOf(::AdminUsersViewModel)
    viewModelOf(::AdminStatsViewModel)
    viewModelOf(::AdminServerViewModel)
    viewModelOf(::AdminLibrariesViewModel)
    viewModelOf(::AdminImageSettingsViewModel)
    viewModelOf(::AdminMetadataSettingsViewModel)
    viewModelOf(::AdminNightlySettingsViewModel)
    viewModelOf(::AdminNotificationSettingsViewModel)
    viewModelOf(::AdminServerSettingsViewModel)
    viewModelOf(::AdminTrashSettingsViewModel)
    viewModelOf(::AdminUserDefaultsViewModel)
    viewModelOf(::AdminDuplicatesViewModel)
    viewModelOf(::AdminMaintenanceViewModel)
    viewModelOf(::AdminFaceRecognitionSettingsViewModel)
    viewModelOf(::AdminObjectDetectionSettingsViewModel)
    viewModelOf(::AdminSceneClassificationSettingsViewModel)
    viewModelOf(::AdminTextRecognitionSettingsViewModel)
    viewModelOf(::AdminImageEmbeddingSettingsViewModel)
    viewModelOf(::AdminRunTasksViewModel)
    viewModelOf(::DeviceConnectionViewModel)
    viewModel { AdminBackupViewModel(get(), get<AssetSharing>()) }
}
