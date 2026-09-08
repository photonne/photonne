using Microsoft.JSInterop;
using MudBlazor;
using Photonne.Client.Web.Models;

namespace Photonne.Client.Web.Services;

/// <summary>
/// Acciones en lote del workspace, extraídas de /fotos para que TODAS las
/// superficies (buckets, álbum, carpeta, colecciones, búsqueda) las compartan.
/// Se registra transient: cada página tiene la suya y le engancha su rejilla
/// (<see cref="Grid"/>) y su <see cref="Selection"/>. Los diálogos de mover y
/// álbum son los mismos componentes en todas partes.
/// </summary>
public class BatchActions
{
    private readonly IAssetService _assetService;
    private readonly IFolderService _folderService;
    private readonly IAlbumsService _albumsService;
    private readonly IDialogService _dialogService;
    private readonly ISnackbar _snackbar;
    private readonly IJSRuntime _js;

    public BatchActions(
        IAssetService assetService,
        IFolderService folderService,
        IAlbumsService albumsService,
        IDialogService dialogService,
        ISnackbar snackbar,
        IJSRuntime js)
    {
        _assetService = assetService;
        _folderService = folderService;
        _albumsService = albumsService;
        _dialogService = dialogService;
        _snackbar = snackbar;
        _js = js;
    }

    public IAssetGridHost? Grid { get; set; }
    public SelectionState Selection { get; set; } = new();

    public bool Busy { get; private set; }

    /// <summary>La página escucha para repintar botones deshabilitados.</summary>
    public event Action? StateChanged;

    private List<Guid> SelectedIds => Selection.Selected.ToList();

    private List<string> MonthsOf(IReadOnlyCollection<Guid> ids) =>
        Grid == null
            ? new List<string>()
            : Grid.ItemsByIds(ids).Select(i => i.FileCreatedAt.ToString("yyyy-MM")).Distinct().ToList();

    public Task HandleShortcutAsync(string action) => action switch
    {
        "delete" => TrashAsync(),
        "favorite" => ToggleFavoritesAsync(),
        _ => Task.CompletedTask
    };

    public async Task ToggleFavoritesAsync()
    {
        if (Grid == null || Busy) return;
        var items = Grid.ItemsByIds(SelectedIds);
        if (items.Count == 0) return;
        // Si a alguna le falta el corazón, la acción es "marcar las que faltan";
        // solo cuando todas lo tienen ya, se desmarca todo. El endpoint es un
        // toggle de uno en uno (sin lote), así que se filtra antes de llamar.
        var marking = items.Any(i => !i.IsFavorite);
        var targets = items.Where(i => i.IsFavorite != marking).Select(i => i.Id).ToList();
        await RunAsync(async () =>
        {
            foreach (var id in targets)
            {
                await _assetService.ToggleFavoriteAsync(id);
            }
            Grid.UpdateAssets(targets, i => i.IsFavorite = marking);
            _snackbar.Add(marking
                ? $"{targets.Count} marcadas como favoritas"
                : $"{targets.Count} quitadas de favoritas", Severity.Success);
        });
    }

    public async Task ArchiveAsync()
    {
        if (Grid == null || Busy) return;
        var ids = SelectedIds;
        var months = MonthsOf(ids);
        await RunAsync(async () =>
        {
            await _assetService.ArchiveAssetsAsync(new ArchiveAssetsRequest { AssetIds = ids });
            Grid.RemoveAssets(ids);
            Selection.Clear();
            AddUndoSnackbar($"{ids.Count} archivadas", async () =>
            {
                await _assetService.UnarchiveAssetsAsync(new UnarchiveAssetsRequest { AssetIds = ids });
                if (Grid != null) await Grid.RestoreViewAsync(ids, months);
            });
        });
    }

    public async Task TrashAsync()
    {
        if (Grid == null || Busy) return;
        var ids = SelectedIds;
        var months = MonthsOf(ids);
        await RunAsync(async () =>
        {
            await _assetService.DeleteAssetsAsync(new DeleteAssetsRequest { AssetIds = ids });
            Grid.RemoveAssets(ids);
            Selection.Clear();
            AddUndoSnackbar($"{ids.Count} movidas a la papelera", async () =>
            {
                await _assetService.RestoreAssetsAsync(new RestoreAssetsRequest { AssetIds = ids });
                if (Grid != null) await Grid.RestoreViewAsync(ids, months);
            });
        });
    }

    public async Task MoveToFolderAsync()
    {
        if (Busy) return;
        var ids = SelectedIds;
        var parameters = new DialogParameters<Components.Workspace.MoveToFolderDialog>
        {
            { d => d.AssetIds, ids }
        };
        var dialog = await _dialogService.ShowAsync<Components.Workspace.MoveToFolderDialog>(
            "Mover a carpeta", parameters);
        var result = await dialog.Result;
        if (result is not { Canceled: false } ||
            result.Data is not Components.Workspace.MoveToFolderDialog.Result choice) return;

        await RunAsync(async () =>
        {
            await _folderService.MoveFolderAssetsAsync(new MoveFolderAssetsRequest
            {
                SourceFolderId = null,
                TargetFolderId = choice.TargetFolderId,
                AssetIds = ids,
                OrganizeByCaptureYear = choice.OrganizeByCaptureYear
            });
            Selection.Clear();
            _snackbar.Add($"{ids.Count} movidas de carpeta", Severity.Success);
        });
    }

    public async Task AddToAlbumAsync()
    {
        if (Busy) return;
        var ids = SelectedIds;
        var parameters = new DialogParameters<Components.Workspace.AlbumPickerDialog>
        {
            { d => d.AssetCount, ids.Count }
        };
        var dialog = await _dialogService.ShowAsync<Components.Workspace.AlbumPickerDialog>(
            "Añadir a álbum", parameters);
        var result = await dialog.Result;
        if (result is not { Canceled: false } || result.Data is not AlbumSummary album) return;

        await RunAsync(async () =>
        {
            await _albumsService.AddAssetsAsync(album.Id, ids);
            Selection.Clear();
            _snackbar.Add($"{ids.Count} añadidas a «{album.Name}»", Severity.Success);
        });
    }

    public async Task DownloadZipAsync()
    {
        if (Busy) return;
        var ids = SelectedIds;
        await RunAsync(async () =>
        {
            var bytes = await _assetService.DownloadZipAsync(ids);
            if (bytes is { Length: > 0 })
            {
                await _js.InvokeVoidAsync("downloadFileFromBytes",
                    $"photonne-{DateTime.Now:yyyyMMdd-HHmm}.zip", "application/zip", bytes);
            }
            else
            {
                _snackbar.Add("No se pudo generar el ZIP", Severity.Error);
            }
        });
    }

    /// <summary>Envuelve una acción propia de una vista con el mismo busy-lock.</summary>
    public async Task RunAsync(Func<Task> action)
    {
        if (Busy) return;
        Busy = true;
        StateChanged?.Invoke();
        try
        {
            await action();
        }
        finally
        {
            Busy = false;
            StateChanged?.Invoke();
        }
    }

    public void AddUndoSnackbar(string message, Func<Task> undo)
    {
        _snackbar.Add(message, Severity.Success, config =>
        {
            config.Action = "Deshacer";
            config.ActionColor = Color.Primary;
            config.OnClick = async _ =>
            {
                await undo();
                _snackbar.Add("Deshecho", Severity.Normal);
            };
        });
    }
}
